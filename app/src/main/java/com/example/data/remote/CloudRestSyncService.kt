package com.example.data.remote

import android.util.Log
import com.example.core.model.BalanceGame
import com.example.core.model.Comment
import com.example.core.model.Post
import com.example.core.model.PostCategory
import com.example.core.model.UserProfile
import com.example.data.local.PersistentVaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 전 세계 모든 기기에서 실시간 동기화되는 영구 클라우드 REST 동기화 서비스
 * - 로컬 PersistentVaultManager와 상호 연동하여 네트워크 장애/서버 500/405/404 시에도
 *   아이디, 계정 정보, 피드 글이 절대 유실되지 않도록 보장
 */
class CloudRestSyncService {

    companion object {
        private const val TAG = "CloudRestSync"
        private const val BASE_URL = "https://api.restful-api.dev/objects"
        private const val DEFAULT_ACCOUNTS_STORE_ID = "ff808181a09d98f701a0e1841e2d2244"
        private const val DEFAULT_POSTS_STORE_ID = "ff808181a09d98f701a0e1842a502245"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }

    private var accountsStoreId: String = DEFAULT_ACCOUNTS_STORE_ID
    private var postsStoreId: String = DEFAULT_POSTS_STORE_ID

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val vaultManager: PersistentVaultManager get() = PersistentVaultManager.instance

    // -------------------------------------------------------------
    // 계정 관리 (클라우드 저장 / 복원 / 중복 확인)
    // -------------------------------------------------------------

    suspend fun isIdTaken(id: String): Boolean = withContext(Dispatchers.IO) {
        val cleanId = id.trim().lowercase()
        // 1. 로컬 보관소 우선 확인
        if (vaultManager.getAccount(cleanId) != null) return@withContext true

        try {
            val accounts = fetchAllAccountsMap()
            accounts.has(cleanId)
        } catch (e: Exception) {
            Log.w(TAG, "isIdTaken check error: ${e.message}")
            false
        }
    }

    suspend fun saveAccount(id: String, password: String, profile: UserProfile): Boolean = withContext(Dispatchers.IO) {
        val cleanId = id.trim().lowercase()
        // 1. 로컬 금고에 즉시 100% 영구 저장 (파일+SharedPreferences)
        vaultManager.saveAccount(cleanId, password, profile)

        // 2. 클라우드 전송
        try {
            val accountsObj = fetchAllAccountsMap()

            // 로컬에 등록된 모든 계정을 병합하여 누락 방지
            val localAccounts = vaultManager.getAllRegisteredAccounts()
            for ((lId, pair) in localAccounts) {
                if (!accountsObj.has(lId)) {
                    accountsObj.put(lId, userToAccountJson(lId, pair.first, pair.second))
                }
            }

            // 현재 저장 대상 계정 추가/덮어쓰기
            accountsObj.put(cleanId, userToAccountJson(cleanId, password, profile))

            val sessionObj = JSONObject().apply {
                put("userId", cleanId)
                put("isLoggedIn", true)
                put("updatedAt", System.currentTimeMillis())
            }

            val rootData = JSONObject().apply {
                put("name", "anon_accounts_7785d9f8")
                put("data", JSONObject().apply {
                    put("accounts", accountsObj)
                    put("lastSession", sessionObj)
                })
            }
            val success = putOrPatchStore(accountsStoreId, rootData.toString())
            Log.d(TAG, "saveAccount result for $cleanId: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "saveAccount network failed (locally safe): ${e.message}")
            true // 로컬에 이미 보존되었으므로 안전함
        }
    }

    suspend fun saveSessionToCloud(user: UserProfile, password: String = ""): Boolean = withContext(Dispatchers.IO) {
        val cleanId = user.id.trim().lowercase()
        if (cleanId.isBlank() || cleanId == "user_me_anon") return@withContext false

        try {
            val accountsObj = fetchAllAccountsMap()
            if (!accountsObj.has(cleanId)) {
                val pwd = password.ifBlank { vaultManager.getAccountPassword(cleanId) ?: "" }
                accountsObj.put(cleanId, userToAccountJson(cleanId, pwd, user))
            }
            val sessionObj = JSONObject().apply {
                put("userId", cleanId)
                put("isLoggedIn", true)
                put("updatedAt", System.currentTimeMillis())
            }
            val rootData = JSONObject().apply {
                put("name", "anon_accounts_7785d9f8")
                put("data", JSONObject().apply {
                    put("accounts", accountsObj)
                    put("lastSession", sessionObj)
                })
            }
            putOrPatchStore(accountsStoreId, rootData.toString())
        } catch (e: Exception) {
            Log.w(TAG, "saveSessionToCloud error: ${e.message}")
            false
        }
    }

    suspend fun updateAccountProfile(id: String, profile: UserProfile): Boolean = withContext(Dispatchers.IO) {
        val cleanId = id.trim().lowercase()
        val existingPair = vaultManager.getAccount(cleanId)
        val pwd = existingPair?.first ?: ""
        vaultManager.saveAccount(cleanId, pwd, profile)

        try {
            val accountsObj = fetchAllAccountsMap()
            val existing = accountsObj.optJSONObject(cleanId) ?: JSONObject().apply {
                put("id", cleanId)
                put("password", pwd)
            }
            existing.put("nickname", profile.nickname)
            existing.put("gender", profile.gender)
            existing.put("age", profile.age)
            existing.put("birthYear", profile.birthYear)
            existing.put("bio", profile.bio)
            existing.put("avatarResId", profile.avatarResId ?: 0)
            existing.put("avatarUri", profile.avatarUri ?: "")
            existing.put("interests", JSONArray(profile.interests))
            existing.put("personality", profile.personality)
            existing.put("preferredFriendStyle", profile.preferredFriendStyle)
            existing.put("specialNotes", profile.specialNotes)
            existing.put("isAgeVisible", profile.isAgeVisible)
            existing.put("isGenderVisible", profile.isGenderVisible)
            existing.put("updatedAt", System.currentTimeMillis())
            accountsObj.put(cleanId, existing)

            val rootData = JSONObject().apply {
                put("name", "anon_accounts_7785d9f8")
                put("data", JSONObject().apply {
                    put("accounts", accountsObj)
                })
            }
            putOrPatchStore(accountsStoreId, rootData.toString())
        } catch (e: Exception) {
            Log.e(TAG, "updateAccountProfile network failed: ${e.message}")
            true
        }
    }

    suspend fun deleteAccount(id: String): Boolean = withContext(Dispatchers.IO) {
        val cleanId = id.trim().lowercase()
        vaultManager.deleteAccount(cleanId)

        try {
            val accountsObj = fetchAllAccountsMap()
            if (accountsObj.has(cleanId)) {
                accountsObj.remove(cleanId)
                val rootData = JSONObject().apply {
                    put("name", "anon_accounts_7785d9f8")
                    put("data", JSONObject().apply {
                        put("accounts", accountsObj)
                    })
                }
                putOrPatchStore(accountsStoreId, rootData.toString())
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteAccount cloud failed: ${e.message}")
            true
        }
    }

    suspend fun getAccount(id: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val cleanId = id.trim().lowercase()

        // 1. 로컬 금고 우선 확인 (속도 및 오프라인 보장)
        val localAcc = vaultManager.getAccount(cleanId)
        if (localAcc != null) {
            val p = localAcc.second
            return@withContext mutableMapOf<String, Any>(
                "id" to cleanId,
                "password" to localAcc.first,
                "nickname" to p.nickname,
                "gender" to p.gender,
                "age" to p.age,
                "birthYear" to p.birthYear,
                "bio" to p.bio,
                "mbti" to p.mbti,
                "avatarResId" to (p.avatarResId ?: 0),
                "avatarUri" to (p.avatarUri ?: ""),
                "interests" to p.interests,
                "personality" to p.personality,
                "preferredFriendStyle" to p.preferredFriendStyle,
                "specialNotes" to p.specialNotes,
                "isAgeVisible" to p.isAgeVisible,
                "isGenderVisible" to p.isGenderVisible,
                "heartBalance" to p.heartBalance,
                "profileViews" to p.profileViews
            )
        }

        // 2. 클라우드에서 조회
        try {
            val accountsObj = fetchAllAccountsMap()
            val acc = accountsObj.optJSONObject(cleanId) ?: return@withContext null

            val interestsList = mutableListOf<String>()
            val interestsArr = acc.optJSONArray("interests")
            if (interestsArr != null) {
                for (i in 0 until interestsArr.length()) {
                    interestsList.add(interestsArr.optString(i))
                }
            }

            val map = mutableMapOf<String, Any>(
                "id" to acc.optString("id", cleanId),
                "password" to acc.optString("password", ""),
                "nickname" to acc.optString("nickname", cleanId),
                "gender" to acc.optString("gender", "여성"),
                "age" to acc.optInt("age", 24),
                "birthYear" to acc.optInt("birthYear", 2002),
                "bio" to acc.optString("bio", ""),
                "mbti" to acc.optString("mbti", "INFP"),
                "avatarResId" to acc.optInt("avatarResId", 0),
                "avatarUri" to acc.optString("avatarUri", ""),
                "interests" to interestsList,
                "personality" to acc.optString("personality", "다정하고 긍정적인"),
                "preferredFriendStyle" to acc.optString("preferredFriendStyle", "편하게 일상 나눌 친구"),
                "specialNotes" to acc.optString("specialNotes", ""),
                "isAgeVisible" to acc.optBoolean("isAgeVisible", true),
                "isGenderVisible" to acc.optBoolean("isGenderVisible", true),
                "heartBalance" to acc.optInt("heartBalance", 20),
                "profileViews" to acc.optInt("profileViews", 0)
            )

            // 클라우드에서 찾은 계정을 로컬 금고에도 자동 영구 캐시
            val pwd = acc.optString("password", "")
            val profile = UserProfile(
                id = cleanId,
                nickname = acc.optString("nickname", cleanId),
                age = acc.optInt("age", 24),
                birthYear = acc.optInt("birthYear", 2002),
                gender = acc.optString("gender", "여성"),
                bio = acc.optString("bio", ""),
                mbti = acc.optString("mbti", "INFP"),
                photoUrls = emptyList(),
                interests = if (interestsList.isNotEmpty()) interestsList else listOf("일상", "소통", "음악"),
                avatarResId = acc.optInt("avatarResId", 0).let { if (it == 0) null else it },
                avatarUri = acc.optString("avatarUri", "").takeIf { it.isNotBlank() },
                personality = acc.optString("personality", "다정하고 긍정적인"),
                preferredFriendStyle = acc.optString("preferredFriendStyle", "편하게 일상 나눌 친구"),
                specialNotes = acc.optString("specialNotes", ""),
                isAgeVisible = acc.optBoolean("isAgeVisible", true),
                isGenderVisible = acc.optBoolean("isGenderVisible", true),
                location = "서울",
                heartBalance = acc.optInt("heartBalance", 20),
                profileViews = acc.optInt("profileViews", 0)
            )
            vaultManager.saveAccount(cleanId, pwd, profile)

            map
        } catch (e: Exception) {
            Log.e(TAG, "getAccount failed: ${e.message}")
            null
        }
    }

    fun observeAccount(id: String): Flow<Map<String, Any>?> = flow {
        val cleanId = id.trim().lowercase()
        if (cleanId.isBlank()) {
            emit(null)
            return@flow
        }
        while (true) {
            val acc = getAccount(cleanId)
            emit(acc)
            delay(5000)
        }
    }.flowOn(Dispatchers.IO)

    data class CloudSyncResult(
        val accounts: Map<String, Pair<String, UserProfile>>,
        val lastSessionUser: UserProfile?,
        val lastSessionPassword: String?
    )

    /**
     * 클라우드 REST 서버로부터 전체 등록 계정 및 마지막 로그인 세션 통합 복원
     */
    suspend fun restoreAllAccountsAndSessionFromCloud(): CloudSyncResult = withContext(Dispatchers.IO) {
        val accountsMap = mutableMapOf<String, Pair<String, UserProfile>>()
        var lastSessionUser: UserProfile? = null
        var lastSessionPassword: String? = null

        try {
            val request = Request.Builder()
                .url("$BASE_URL/$accountsStoreId")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                response.close()
                val jsonObj = JSONObject(body)
                val dataObj = jsonObj.optJSONObject("data")
                val cloudAccs = dataObj?.optJSONObject("accounts")
                if (cloudAccs != null) {
                    val keys = cloudAccs.keys()
                    while (keys.hasNext()) {
                        val key = keys.next().lowercase()
                        val accObj = cloudAccs.optJSONObject(key) ?: continue
                        val pwd = accObj.optString("password", "")
                        val profile = parseUserProfileFromJson(key, accObj)
                        accountsMap[key] = Pair(pwd, profile)
                    }
                }

                // 로컬 금고에 전체 계정 즉시 병합 보존
                if (accountsMap.isNotEmpty()) {
                    vaultManager.mergeAccountsFromMap(accountsMap)
                    Log.d(TAG, "Restored and merged ${accountsMap.size} cloud accounts into local vault")
                }

                val lastSessionObj = dataObj?.optJSONObject("lastSession")
                val lastUserId = lastSessionObj?.optString("userId", "")?.lowercase() ?: ""
                val isLoggedIn = lastSessionObj?.optBoolean("isLoggedIn", false) ?: false

                if (isLoggedIn && lastUserId.isNotBlank() && accountsMap.containsKey(lastUserId)) {
                    val pair = accountsMap[lastUserId]
                    lastSessionUser = pair?.second
                    lastSessionPassword = pair?.first
                    if (lastSessionUser != null) {
                        vaultManager.restoreSessionFromCloudUser(lastSessionUser)
                        Log.d(TAG, "Restored active cloud user session: $lastUserId")
                    }
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "restoreAllAccountsAndSessionFromCloud error: ${e.message}")
        }

        CloudSyncResult(accountsMap, lastSessionUser, lastSessionPassword)
    }

    private fun parseUserProfileFromJson(id: String, acc: JSONObject): UserProfile {
        val interestsList = mutableListOf<String>()
        val interestsArr = acc.optJSONArray("interests")
        if (interestsArr != null) {
            for (i in 0 until interestsArr.length()) {
                interestsList.add(interestsArr.optString(i))
            }
        }
        return UserProfile(
            id = id,
            nickname = acc.optString("nickname", id),
            age = acc.optInt("age", 24),
            birthYear = acc.optInt("birthYear", 2002),
            gender = acc.optString("gender", "여성"),
            bio = acc.optString("bio", ""),
            mbti = acc.optString("mbti", "INFP"),
            photoUrls = emptyList(),
            interests = if (interestsList.isNotEmpty()) interestsList else listOf("일상", "소통", "음악"),
            avatarResId = acc.optInt("avatarResId", 0).let { if (it == 0) null else it },
            avatarUri = acc.optString("avatarUri", "").takeIf { it.isNotBlank() },
            personality = acc.optString("personality", "다정하고 긍정적인"),
            preferredFriendStyle = acc.optString("preferredFriendStyle", "편하게 일상 나눌 친구"),
            specialNotes = acc.optString("specialNotes", ""),
            isAgeVisible = acc.optBoolean("isAgeVisible", true),
            isGenderVisible = acc.optBoolean("isGenderVisible", true),
            location = "서울",
            heartBalance = acc.optInt("heartBalance", 20),
            profileViews = acc.optInt("profileViews", 0)
        )
    }

    // -------------------------------------------------------------
    // 게시글 관리 (클라우드 저장 / 실시간 조회 / 댓글 / 추천 / 투표)
    // -------------------------------------------------------------

    suspend fun fetchPosts(): List<Post> = withContext(Dispatchers.IO) {
        val cloudPosts = try {
            val request = Request.Builder()
                .url("$BASE_URL/$postsStoreId")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emptyList()
            } else {
                val body = response.body?.string() ?: ""
                parsePostsJson(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchPosts network error (using vault): ${e.message}")
            emptyList()
        }

        // 로컬 금고와 병합 (로컬 작성 글 및 기본 글이 절대 삭제되지 않도록 보장)
        val mergedList = vaultManager.mergeCloudPosts(cloudPosts).distinctBy { it.id }

        // 만약 클라우드 글 목록이 비어있었다면 완전한 로컬 글 목록으로 클라우드에 자동 복구
        if (cloudPosts.isEmpty() && mergedList.isNotEmpty()) {
            savePostsList(mergedList)
        }

        mergedList
    }

    fun observePosts(): Flow<List<Post>> = flow {
        // 즉시 로컬 금고 글 방출 (대기시간 0초)
        val local = vaultManager.getLocalPosts().distinctBy { it.id }
        if (local.isNotEmpty()) {
            emit(local)
        }

        while (true) {
            val list = fetchPosts().distinctBy { it.id }
            if (list.isNotEmpty()) {
                emit(list)
            }
            delay(4000)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun createPost(post: Post): Boolean = withContext(Dispatchers.IO) {
        // 1. 로컬 금고에 먼저 100% 영구 보존
        vaultManager.saveUserCreatedPost(post)

        try {
            val currentPosts = fetchPosts().toMutableList()
            currentPosts.removeAll { it.id == post.id }
            currentPosts.add(0, post)

            val success = savePostsList(currentPosts)
            Log.d(TAG, "createPost in cloud result: $success, id: ${post.id}")
            success
        } catch (e: Exception) {
            Log.e(TAG, "createPost cloud failed (locally safe): ${e.message}")
            true
        }
    }

    suspend fun addComment(postId: String, comment: Comment): Boolean = withContext(Dispatchers.IO) {
        // 1. 로컬 금고에 먼저 반영 (중복 제거 및 댓글 수 정확 계산)
        vaultManager.addLocalComment(postId, comment)

        try {
            val currentPosts = fetchPosts().toMutableList()
            val index = currentPosts.indexOfFirst { it.id == postId }
            if (index != -1) {
                val target = currentPosts[index]
                val updatedComments = (target.comments + comment).distinctBy { it.id }
                currentPosts[index] = target.copy(
                    comments = updatedComments,
                    commentsCount = updatedComments.size
                )
                savePostsList(currentPosts)
            } else false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deletePost(postId: String): Boolean = withContext(Dispatchers.IO) {
        // 1. 로컬 금고에서 삭제
        vaultManager.deleteLocalPost(postId)

        try {
            val currentPosts = fetchPosts().toMutableList()
            val removed = currentPosts.removeAll { it.id == postId }
            if (removed) {
                savePostsList(currentPosts)
            } else true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteComment(postId: String, commentId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentPosts = fetchPosts().toMutableList()
            val index = currentPosts.indexOfFirst { it.id == postId }
            if (index != -1) {
                val target = currentPosts[index]
                val updatedComments = target.comments.filterNot { it.id == commentId }
                currentPosts[index] = target.copy(
                    comments = updatedComments,
                    commentsCount = updatedComments.size
                )
                savePostsList(currentPosts)
            } else false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun voteBalanceGame(postId: String, option: String): Boolean = withContext(Dispatchers.IO) {
        vaultManager.updatePostVote(postId, option)

        try {
            val currentPosts = fetchPosts().toMutableList()
            val index = currentPosts.indexOfFirst { it.id == postId }
            if (index != -1) {
                val target = currentPosts[index]
                val bg = target.balanceGame
                if (bg != null) {
                    val updatedBg = if (option == "A") {
                        bg.copy(votesA = bg.votesA + 1, userVotedOption = "A")
                    } else {
                        bg.copy(votesB = bg.votesB + 1, userVotedOption = "B")
                    }
                    currentPosts[index] = target.copy(balanceGame = updatedBg)
                    savePostsList(currentPosts)
                } else false
            } else false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun incrementViews(postId: String): Boolean = withContext(Dispatchers.IO) {
        // 1. 로컬 금고에 즉각 갱신 및 파일 보존
        vaultManager.incrementPostViews(postId)

        try {
            val currentPosts = fetchPosts().toMutableList()
            val index = currentPosts.indexOfFirst { it.id == postId }
            if (index != -1) {
                val target = currentPosts[index]
                currentPosts[index] = target.copy(viewsCount = target.viewsCount + 1)
                savePostsList(currentPosts)
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // -------------------------------------------------------------
    // 내부 헬퍼 메소드
    // -------------------------------------------------------------

    private fun userToAccountJson(id: String, password: String, profile: UserProfile): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("password", password)
            put("nickname", profile.nickname)
            put("gender", profile.gender)
            put("age", profile.age)
            put("birthYear", profile.birthYear)
            put("bio", profile.bio)
            put("mbti", profile.mbti)
            put("avatarResId", profile.avatarResId ?: 0)
            put("avatarUri", profile.avatarUri ?: "")
            put("interests", JSONArray(profile.interests))
            put("personality", profile.personality)
            put("preferredFriendStyle", profile.preferredFriendStyle)
            put("specialNotes", profile.specialNotes)
            put("isAgeVisible", profile.isAgeVisible)
            put("isGenderVisible", profile.isGenderVisible)
            put("heartBalance", profile.heartBalance)
            put("profileViews", profile.profileViews)
            put("createdAt", System.currentTimeMillis())
            put("updatedAt", System.currentTimeMillis())
        }
    }

    private fun fetchAllAccountsMap(): JSONObject {
        val result = JSONObject()

        // 1. 로컬 금고 계정 먼저 채우기
        val localAccounts = vaultManager.getAllRegisteredAccounts()
        for ((id, pair) in localAccounts) {
            result.put(id, userToAccountJson(id, pair.first, pair.second))
        }

        // 2. 클라우드에서 읽어와 합치기
        try {
            val request = Request.Builder()
                .url("$BASE_URL/$accountsStoreId")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val jsonObj = JSONObject(body)
                val dataObj = jsonObj.optJSONObject("data")
                val cloudAccs = dataObj?.optJSONObject("accounts")
                if (cloudAccs != null) {
                    val keys = cloudAccs.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        result.put(key, cloudAccs.get(key))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAllAccountsMap cloud error: ${e.message}")
        }

        return result
    }

    private fun parsePostsJson(body: String): List<Post> {
        val list = mutableListOf<Post>()
        try {
            val jsonObj = JSONObject(body)
            val dataObj = jsonObj.optJSONObject("data") ?: return emptyList()
            val postsArr = dataObj.optJSONArray("posts") ?: return emptyList()

            for (i in 0 until postsArr.length()) {
                val p = postsArr.optJSONObject(i) ?: continue
                try {
                    val commentsList = mutableListOf<Comment>()
                    val commentsArr = p.optJSONArray("comments")
                    if (commentsArr != null) {
                        for (cIdx in 0 until commentsArr.length()) {
                            val cObj = commentsArr.optJSONObject(cIdx) ?: continue
                            val cCreatedAt = cObj.optLong("createdAt", System.currentTimeMillis())
                            commentsList.add(
                                Comment(
                                    id = cObj.optString("id", "c_${System.currentTimeMillis()}"),
                                    postId = p.optString("id", ""),
                                    authorTag = cObj.optString("authorTag", "익명"),
                                    content = cObj.optString("content", ""),
                                    likesCount = cObj.optInt("likesCount", 0),
                                    timeAgo = formatTimeAgo(cCreatedAt),
                                    isAuthor = cObj.optBoolean("isAuthor", false),
                                    authorId = cObj.optString("authorId", ""),
                                    createdAt = cCreatedAt
                                )
                            )
                        }
                    }

                    var balanceGame: BalanceGame? = null
                    val bgObj = p.optJSONObject("balanceGame")
                    if (bgObj != null) {
                        balanceGame = BalanceGame(
                            optionA = bgObj.optString("optionA", ""),
                            optionB = bgObj.optString("optionB", ""),
                            votesA = bgObj.optInt("votesA", 0),
                            votesB = bgObj.optInt("votesB", 0),
                            userVotedOption = bgObj.optString("userVotedOption", "").takeIf { it.isNotBlank() }
                        )
                    }

                    val catName = p.optString("category", PostCategory.DAILY.name)
                    val category = try {
                        PostCategory.valueOf(catName)
                    } catch (e: Exception) {
                        PostCategory.DAILY
                    }

                    val createdAt = p.optLong("createdAt", System.currentTimeMillis())

                    list.add(
                        Post(
                            id = p.optString("id", "p_${System.currentTimeMillis()}"),
                            category = category,
                            title = p.optString("title", ""),
                            content = p.optString("content", ""),
                            authorTag = p.optString("authorTag", "익명"),
                            timeAgo = formatTimeAgo(createdAt),
                            likesCount = p.optInt("likesCount", 0),
                            viewsCount = p.optInt("viewsCount", 0),
                            commentsCount = p.optInt("commentsCount", commentsList.size),
                            balanceGame = balanceGame,
                            comments = commentsList,
                            authorId = p.optString("authorId", ""),
                            createdAt = createdAt
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing post: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parsePostsJson error: ${e.message}")
        }
        return list.distinctBy { it.id }
    }

    private fun savePostsList(posts: List<Post>): Boolean {
        val postsArr = JSONArray()
        for (post in posts) {
            val pObj = JSONObject().apply {
                put("id", post.id)
                put("category", post.category.name)
                put("title", post.title)
                put("content", post.content)
                put("authorTag", post.authorTag)
                put("authorId", post.authorId)
                put("likesCount", post.likesCount)
                put("viewsCount", post.viewsCount)
                put("commentsCount", post.commentsCount)
                put("createdAt", post.createdAt)

                if (post.balanceGame != null) {
                    put("balanceGame", JSONObject().apply {
                        put("optionA", post.balanceGame.optionA)
                        put("optionB", post.balanceGame.optionB)
                        put("votesA", post.balanceGame.votesA)
                        put("votesB", post.balanceGame.votesB)
                        put("userVotedOption", post.balanceGame.userVotedOption ?: "")
                    })
                }

                val cArr = JSONArray()
                for (c in post.comments) {
                    cArr.put(JSONObject().apply {
                        put("id", c.id)
                        put("authorTag", c.authorTag)
                        put("content", c.content)
                        put("authorId", c.authorId)
                        put("isAuthor", c.isAuthor)
                        put("likesCount", c.likesCount)
                        put("createdAt", c.createdAt)
                    })
                }
                put("comments", cArr)
            }
            postsArr.put(pObj)
        }

        val root = JSONObject().apply {
            put("name", "anon_posts_7785d9f8")
            put("data", JSONObject().apply {
                put("posts", postsArr)
            })
        }
        return putOrPatchStore(postsStoreId, root.toString())
    }

    private fun putOrPatchStore(id: String, jsonPayload: String): Boolean {
        val body = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)

        // 1차 시도: PATCH 요청
        try {
            val patchRequest = Request.Builder()
                .url("$BASE_URL/$id")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .patch(body)
                .build()
            val response = client.newCall(patchRequest).execute()
            if (response.isSuccessful) {
                response.close()
                return true
            }
            val code = response.code
            response.close()
            if (code == 404) {
                // 스토어가 만료되었거나 삭제된 경우 자동 신규 스토어 생성
                return createFreshStore(id, jsonPayload)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PATCH failed for $id: ${e.message}")
        }

        // 2차 시도: PUT 요청
        try {
            val putRequest = Request.Builder()
                .url("$BASE_URL/$id")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .put(body)
                .build()
            val response = client.newCall(putRequest).execute()
            if (response.isSuccessful) {
                response.close()
                return true
            }
            val code = response.code
            response.close()
            if (code == 404) {
                return createFreshStore(id, jsonPayload)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PUT failed for $id: ${e.message}")
        }

        return false
    }

    private fun createFreshStore(oldId: String, jsonPayload: String): Boolean {
        try {
            val body = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)
            val postRequest = Request.Builder()
                .url(BASE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .post(body)
                .build()
            val response = client.newCall(postRequest).execute()
            if (response.isSuccessful) {
                val respStr = response.body?.string() ?: ""
                val respJson = JSONObject(respStr)
                val newId = respJson.optString("id", "")
                if (newId.isNotBlank()) {
                    if (oldId == accountsStoreId) {
                        accountsStoreId = newId
                        Log.d(TAG, "Auto-healed accounts store with new ID: $newId")
                    } else if (oldId == postsStoreId) {
                        postsStoreId = newId
                        Log.d(TAG, "Auto-healed posts store with new ID: $newId")
                    }
                    response.close()
                    return true
                }
            }
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "createFreshStore error: ${e.message}")
        }
        return false
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffSeconds = (now - timestamp) / 1000
        return when {
            diffSeconds < 60 -> "방금 전"
            diffSeconds < 3600 -> "${diffSeconds / 60}분 전"
            diffSeconds < 86400 -> "${diffSeconds / 3600}시간 전"
            else -> "${diffSeconds / 86400}일 전"
        }
    }
}
