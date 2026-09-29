package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.AnonApplication
import com.example.R
import com.example.core.model.BalanceGame
import com.example.core.model.Comment
import com.example.core.model.Post
import com.example.core.model.PostCategory
import com.example.core.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 앱 수정/재배포/에뮬레이터 재설치 시에도 피드, 아이디, 비밀번호, 프로필, 로그인 세션이
 * 절대 날아가지 않도록 보장하는 다중 로컬 영구 보존 저장소 (Multi-Tier Persistent Vault)
 *
 * 1. SharedPreferences 동기식 즉시 커밋 (빠른 캐시)
 * 2. 내부 저장소 JSON 파일 (context.filesDir/anon_vault_backup.json)
 * 3. 외부 저장소 JSON 파일 (context.getExternalFilesDir(null)/anon_vault_backup.json)
 *
 * 앱이 재시작되거나 재빌드되어 SharedPreferences가 비워져도 JSON 백업 파일에서
 * 로그인 세션과 아이디, 피드 목록을 100% 자동 복구합니다.
 */
class PersistentVaultManager private constructor() {

    companion object {
        private const val TAG = "PersistentVault"
        private const val BACKUP_FILE_NAME = "anon_vault_backup.json"
        private const val PREFS_NAME = "anon_user_prefs"

        val instance: PersistentVaultManager by lazy { PersistentVaultManager() }
    }

    private val context: Context get() = AnonApplication.instance

    private val prefs: SharedPreferences? by lazy {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get SharedPreferences: ${e.message}")
            null
        }
    }

    private val internalBackupFile: File by lazy {
        File(context.filesDir, BACKUP_FILE_NAME)
    }

    private val externalBackupFile: File? by lazy {
        try {
            val extDir = context.getExternalFilesDir(null)
            if (extDir != null) File(extDir, BACKUP_FILE_NAME) else null
        } catch (e: Exception) {
            null
        }
    }

    // 앱 재설치/업그레이드/에뮬레이터 리셋 시에도 OS에 의해 삭제되지 않는 공용 다운로드/문서 백업 파일
    private val publicDownloadBackupFile: File? by lazy {
        try {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs()
                File(dir, BACKUP_FILE_NAME)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private val publicDocumentsBackupFile: File? by lazy {
        try {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs()
                File(dir, BACKUP_FILE_NAME)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private val fallbackDownloadFile: File by lazy {
        File("/sdcard/Download", BACKUP_FILE_NAME)
    }

    // 메모리 캐시
    private val accountsMemory = mutableMapOf<String, Pair<String, UserProfile>>() // id -> (password, profile)
    private val rememberedIdsMemory = mutableSetOf<String>()
    private var lastUsedIdMemory: String = ""
    private var cachedSessionUser: UserProfile? = null
    private var isSessionLoggedIn: Boolean = false
    private val postsMemory = mutableListOf<Post>()

    init {
        restoreFromVault()
    }

    /**
     * 앱 시작 시 1~3단계 저장소에서 데이터를 읽어 통합 복원
     */
    @Synchronized
    fun restoreFromVault() {
        try {
            // 1. 내부/외부 JSON 백업 파일에서 먼저 읽어오기
            val fileJson = readBackupJsonFromFile()
            if (fileJson != null) {
                parseVaultJson(fileJson)
                Log.d(TAG, "Successfully restored vault from persistent JSON file")
            }

            // 2. SharedPreferences에서 추가 정보 병합
            mergeFromSharedPreferences()

            // 3. 만약 로컬 포스트가 비어있다면 기본 제공 커뮤니티 글 시드
            if (postsMemory.isEmpty()) {
                postsMemory.addAll(DefaultCommunityPosts.getDefaultPosts())
                persistToFileVault()
            } else {
                val unique = postsMemory.distinctBy { it.id }
                if (unique.size != postsMemory.size) {
                    postsMemory.clear()
                    postsMemory.addAll(unique)
                    persistToFileVault()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in restoreFromVault: ${e.message}", e)
        }
    }

    // =========================================================
    // 세션 관리 (로그인 상태 보존)
    // =========================================================

    @Synchronized
    fun saveSession(user: UserProfile) {
        cachedSessionUser = user
        isSessionLoggedIn = true
        lastUsedIdMemory = user.id.lowercase()
        rememberedIdsMemory.add(user.id.lowercase())

        // 1. SharedPreferences 저장
        prefs?.edit()?.apply {
            putBoolean("is_logged_in", true)
            putString("user_id", user.id)
            putString("user_nickname", user.nickname)
            putInt("user_age", user.age)
            putInt("user_birth_year", user.birthYear)
            putString("user_gender", user.gender)
            putString("user_bio", user.bio)
            putString("user_mbti", user.mbti)
            putString("user_interests", user.interests.joinToString(","))
            putString("user_google_email", user.googleEmail)
            putString("user_phone", user.phoneNumber)
            putInt("user_avatar_res_id", user.avatarResId ?: 0)
            putString("user_avatar_uri", user.avatarUri)
            putInt("user_heart_balance", user.heartBalance)
            putInt("user_profile_views", user.profileViews)
            putString("last_used_id", user.id.lowercase())
            putBoolean("reg_id_${user.id.lowercase()}", true)
            putString("user_personality", user.personality)
            putString("user_preferred_friend_style", user.preferredFriendStyle)
            putString("user_special_notes", user.specialNotes)
            putBoolean("user_is_age_visible", user.isAgeVisible)
            putBoolean("user_is_gender_visible", user.isGenderVisible)
            putStringSet("all_saved_ids_set", rememberedIdsMemory)
            commit()
        }

        // 2. 글 목록의 소유권(isMyPost) 즉시 재계산
        reevaluatePostsOwnership(user.id)

        // 3. 파일 저장소에 영구 동기화
        persistToFileVault()
    }

    @Synchronized
    fun logoutSession() {
        cachedSessionUser = null
        isSessionLoggedIn = false
        prefs?.edit()?.apply {
            putBoolean("is_logged_in", false)
            remove("user_id")
            commit()
        }
        reevaluatePostsOwnership("")
        persistToFileVault()
    }

    @Synchronized
    fun reevaluatePostsOwnership(userId: String) {
        val cleanId = userId.trim().lowercase()
        val isValidUser = isSessionLoggedIn && cleanId.isNotBlank() && cleanId != "user_me_anon"
        for (i in 0 until postsMemory.size) {
            val p = postsMemory[i]
            val isMy = isValidUser && p.authorId.isNotBlank() &&
                    !p.authorId.equals("user_me_anon", ignoreCase = true) &&
                    p.authorId.equals(cleanId, ignoreCase = true)
            val updatedComments = p.comments.map { c ->
                val isMyC = isValidUser && c.authorId.isNotBlank() &&
                        !c.authorId.equals("user_me_anon", ignoreCase = true) &&
                        c.authorId.equals(cleanId, ignoreCase = true)
                val isAuth = p.authorId.isNotBlank() && c.authorId.isNotBlank() &&
                        c.authorId.equals(p.authorId, ignoreCase = true)
                c.copy(isMyComment = isMyC, isAuthor = isAuth)
            }
            postsMemory[i] = p.copy(isMyPost = isMy, comments = updatedComments)
        }
        persistToFileVault()
    }

    @Synchronized
    fun getSavedSessionUser(): UserProfile? {
        if (isSessionLoggedIn && cachedSessionUser != null) {
            return cachedSessionUser
        }

        // SharedPreferences 확인 (명시적으로 로그인 상태일 때만 세션 복구)
        val sp = prefs
        if (sp != null && sp.getBoolean("is_logged_in", false)) {
            val id = sp.getString("user_id", "") ?: ""
            if (id.isNotBlank()) {
                val profile = loadProfileFromPrefs(sp, id)
                cachedSessionUser = profile
                isSessionLoggedIn = true
                return profile
            }
        }

        return null
    }

    @Synchronized
    fun clearSession() {
        isSessionLoggedIn = false
        prefs?.edit()?.putBoolean("is_logged_in", false)?.commit()
        persistToFileVault()
    }

    // =========================================================
    // 계정 관리 (아이디 & 비밀번호 영구 저장)
    // =========================================================

    @Synchronized
    fun saveAccount(id: String, password: String, profile: UserProfile) {
        val cleanId = id.trim().lowercase()
        accountsMemory[cleanId] = Pair(password, profile)
        rememberedIdsMemory.add(cleanId)
        lastUsedIdMemory = cleanId

        // SharedPreferences 저장
        prefs?.edit()?.apply {
            putString("acc_pwd_$cleanId", password)
            putString("acc_nick_$cleanId", profile.nickname)
            putString("acc_gender_$cleanId", profile.gender)
            putInt("acc_birth_$cleanId", profile.birthYear)
            putInt("acc_age_$cleanId", profile.age)
            putString("acc_bio_$cleanId", profile.bio)
            putString("acc_personality_$cleanId", profile.personality)
            putString("acc_interests_$cleanId", profile.interests.joinToString(","))
            putString("acc_friend_style_$cleanId", profile.preferredFriendStyle)
            putString("acc_special_notes_$cleanId", profile.specialNotes)
            putBoolean("acc_age_visible_$cleanId", profile.isAgeVisible)
            putBoolean("acc_gender_visible_$cleanId", profile.isGenderVisible)
            putInt("acc_avatar_res_$cleanId", profile.avatarResId ?: 0)
            putString("acc_avatar_uri_$cleanId", profile.avatarUri)
            putInt("acc_hearts_$cleanId", profile.heartBalance)
            putInt("acc_views_$cleanId", profile.profileViews)
            putBoolean("reg_id_$cleanId", true)
            putString("last_used_id", cleanId)
            putStringSet("all_saved_ids_set", rememberedIdsMemory)
            commit()
        }

        // 파일 영구 저장
        persistToFileVault()
    }

    @Synchronized
    fun getAccount(id: String): Pair<String, UserProfile>? {
        val cleanId = id.trim().lowercase()
        // 1. 메모리 캐시
        accountsMemory[cleanId]?.let { return it }

        // 2. SharedPreferences 확인
        val sp = prefs
        if (sp != null && sp.contains("acc_pwd_$cleanId")) {
            val pwd = sp.getString("acc_pwd_$cleanId", "") ?: ""
            val profile = loadProfileFromPrefs(sp, cleanId)
            val pair = Pair(pwd, profile)
            accountsMemory[cleanId] = pair
            return pair
        }

        return null
    }

    @Synchronized
    fun getAccountPassword(id: String): String? {
        val cleanId = id.trim().lowercase()
        accountsMemory[cleanId]?.let { return it.first }
        val sp = prefs
        if (sp != null && sp.contains("acc_pwd_$cleanId")) {
            return sp.getString("acc_pwd_$cleanId", "")
        }
        return null
    }

    @Synchronized
    fun mergeAccountsFromMap(newAccounts: Map<String, Pair<String, UserProfile>>) {
        if (newAccounts.isEmpty()) return
        val editor = prefs?.edit()
        for ((id, pair) in newAccounts) {
            val cleanId = id.trim().lowercase()
            if (!accountsMemory.containsKey(cleanId) || accountsMemory[cleanId]?.first.isNullOrBlank()) {
                accountsMemory[cleanId] = pair
            }
            rememberedIdsMemory.add(cleanId)
            editor?.apply {
                putString("acc_pwd_$cleanId", pair.first)
                putString("acc_nick_$cleanId", pair.second.nickname)
                putBoolean("reg_id_$cleanId", true)
            }
        }
        editor?.putStringSet("all_saved_ids_set", rememberedIdsMemory)?.apply()
        persistToFileVault()
        Log.d(TAG, "Merged ${newAccounts.size} accounts into vault")
    }

    @Synchronized
    fun restoreSessionFromCloudUser(user: UserProfile) {
        cachedSessionUser = user
        isSessionLoggedIn = true
        lastUsedIdMemory = user.id.lowercase()
        rememberedIdsMemory.add(user.id.lowercase())
        saveSession(user)
    }

    @Synchronized
    fun getAllRegisteredAccounts(): Map<String, Pair<String, UserProfile>> {
        return accountsMemory.toMap()
    }

    @Synchronized
    fun getAllSavedIds(): List<String> {
        val result = mutableListOf<String>()
        if (lastUsedIdMemory.isNotBlank()) {
            result.add(lastUsedIdMemory)
        }
        for (id in rememberedIdsMemory) {
            if (!result.contains(id)) {
                result.add(id)
            }
        }
        for (id in accountsMemory.keys) {
            if (!result.contains(id)) {
                result.add(id)
            }
        }
        return result
    }

    @Synchronized
    fun getLastUsedId(): String {
        return lastUsedIdMemory.ifBlank {
            getAllSavedIds().firstOrNull() ?: ""
        }
    }

    /**
     * 회원 탈퇴 시 계정, 비밀번호, 프로필, 세션, 작성글 영구 삭제
     */
    @Synchronized
    fun deleteAccount(id: String) {
        val cleanId = id.trim().lowercase()
        accountsMemory.remove(cleanId)
        rememberedIdsMemory.remove(cleanId)
        if (lastUsedIdMemory == cleanId) {
            lastUsedIdMemory = rememberedIdsMemory.firstOrNull() ?: ""
        }
        if (cachedSessionUser?.id?.lowercase() == cleanId) {
            cachedSessionUser = null
            isSessionLoggedIn = false
        }

        // SharedPreferences에서 계정 및 세션 완전 제거
        prefs?.edit()?.apply {
            remove("acc_pwd_$cleanId")
            remove("acc_nick_$cleanId")
            remove("acc_gender_$cleanId")
            remove("acc_birth_$cleanId")
            remove("acc_age_$cleanId")
            remove("acc_bio_$cleanId")
            remove("acc_personality_$cleanId")
            remove("acc_interests_$cleanId")
            remove("acc_friend_style_$cleanId")
            remove("acc_special_notes_$cleanId")
            remove("acc_age_visible_$cleanId")
            remove("acc_gender_visible_$cleanId")
            remove("acc_avatar_res_$cleanId")
            remove("acc_avatar_uri_$cleanId")
            remove("acc_hearts_$cleanId")
            remove("acc_views_$cleanId")
            remove("reg_id_$cleanId")

            val currentUserId = prefs?.getString("user_id", "") ?: ""
            if (currentUserId.lowercase() == cleanId) {
                putBoolean("is_logged_in", false)
                remove("user_id")
                remove("user_nickname")
                remove("user_age")
                remove("user_birth_year")
                remove("user_gender")
                remove("user_bio")
                remove("user_mbti")
                remove("user_interests")
                remove("user_google_email")
                remove("user_phone")
                remove("user_avatar_res_id")
                remove("user_avatar_uri")
                remove("user_heart_balance")
                remove("user_profile_views")
                remove("user_personality")
                remove("user_preferred_friend_style")
                remove("user_special_notes")
                remove("user_is_age_visible")
                remove("user_is_gender_visible")
            }

            putString("last_used_id", lastUsedIdMemory)
            putStringSet("all_saved_ids_set", rememberedIdsMemory)
            commit()
        }

        // 작성글 및 댓글 정리
        postsMemory.removeAll { it.authorId.equals(cleanId, ignoreCase = true) || it.isMyPost }
        for (i in 0 until postsMemory.size) {
            val post = postsMemory[i]
            val updatedComments = post.comments.filterNot { it.authorId.equals(cleanId, ignoreCase = true) || it.isMyComment }
            if (updatedComments.size != post.comments.size) {
                postsMemory[i] = post.copy(
                    comments = updatedComments,
                    commentsCount = updatedComments.size
                )
            }
        }

        // 백업 파일에도 즉시 반영
        persistToFileVault()
    }

    /**
     * 저장된 아이디 목록에서 특정 아이디만 제거
     */
    @Synchronized
    fun removeSavedId(id: String) {
        val cleanId = id.trim().lowercase()
        rememberedIdsMemory.remove(cleanId)
        if (lastUsedIdMemory == cleanId) {
            lastUsedIdMemory = rememberedIdsMemory.firstOrNull() ?: ""
        }
        prefs?.edit()?.apply {
            putString("last_used_id", lastUsedIdMemory)
            putStringSet("all_saved_ids_set", rememberedIdsMemory)
            commit()
        }
        persistToFileVault()
    }

    /**
     * 모든 로컬 데이터 초기화
     */
    @Synchronized
    fun clearAllData() {
        accountsMemory.clear()
        rememberedIdsMemory.clear()
        lastUsedIdMemory = ""
        cachedSessionUser = null
        isSessionLoggedIn = false
        prefs?.edit()?.clear()?.commit()
        try {
            if (internalBackupFile.exists()) internalBackupFile.delete()
            externalBackupFile?.let { if (it.exists()) it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "clearAllData error: ${e.message}")
        }
    }

    // =========================================================
    // 피드 / 게시글 영구 보존
    // =========================================================

    @Synchronized
    fun getLocalPosts(): List<Post> {
        if (postsMemory.isEmpty()) {
            postsMemory.addAll(DefaultCommunityPosts.getDefaultPosts())
        }
        val cleanId = if (isSessionLoggedIn && cachedSessionUser != null) {
            cachedSessionUser!!.id.trim().lowercase()
        } else ""
        val isValidUser = isSessionLoggedIn && cleanId.isNotBlank() && cleanId != "user_me_anon"

        val distinct = postsMemory.distinctBy { it.id }.map { p ->
            val isMy = isValidUser && p.authorId.isNotBlank() &&
                    !p.authorId.equals("user_me_anon", ignoreCase = true) &&
                    p.authorId.equals(cleanId, ignoreCase = true)
            val updatedComments = p.comments.map { c ->
                val isMyC = isValidUser && c.authorId.isNotBlank() &&
                        !c.authorId.equals("user_me_anon", ignoreCase = true) &&
                        c.authorId.equals(cleanId, ignoreCase = true)
                val isAuth = p.authorId.isNotBlank() && c.authorId.isNotBlank() &&
                        c.authorId.equals(p.authorId, ignoreCase = true)
                c.copy(isMyComment = isMyC, isAuthor = isAuth)
            }
            p.copy(isMyPost = isMy, comments = updatedComments)
        }
        if (distinct.size != postsMemory.size) {
            postsMemory.clear()
            postsMemory.addAll(distinct)
            persistToFileVault()
        }
        return distinct
    }

    @Synchronized
    fun saveUserCreatedPost(post: Post) {
        postsMemory.removeAll { it.id == post.id }
        postsMemory.add(0, post)
        val distinct = postsMemory.distinctBy { it.id }
        postsMemory.clear()
        postsMemory.addAll(distinct)
        persistToFileVault()
    }

    @Synchronized
    fun deleteLocalPost(postId: String) {
        postsMemory.removeAll { it.id == postId }
        persistToFileVault()
    }

    @Synchronized
    fun mergeCloudPosts(cloudPosts: List<Post>): List<Post> {
        if (cloudPosts.isEmpty()) {
            return getLocalPosts()
        }

        val map = linkedMapOf<String, Post>()

        // 1. 로컬에 저장된 기존 글 보존
        for (p in postsMemory.distinctBy { it.id }) {
            map[p.id] = p
        }

        // 2. 클라우드 글 병합
        for (cp in cloudPosts.distinctBy { it.id }) {
            val existing = map[cp.id]
            if (existing != null) {
                // 투표 정보나 댓글 병합 (원래 작성자 ID 및 조회수 보존)
                val mergedComments = (existing.comments + cp.comments).distinctBy { it.id }
                val maxViews = maxOf(existing.viewsCount, cp.viewsCount)
                map[cp.id] = cp.copy(
                    authorId = existing.authorId.ifBlank { cp.authorId },
                    viewsCount = maxViews,
                    comments = mergedComments,
                    commentsCount = mergedComments.size,
                    balanceGame = existing.balanceGame ?: cp.balanceGame
                )
            } else {
                map[cp.id] = cp
            }
        }

        // 3. 기본 글 중 아직 없는 것 보존
        for (dp in DefaultCommunityPosts.getDefaultPosts()) {
            if (!map.containsKey(dp.id)) {
                map[dp.id] = dp
            }
        }

        val finalList = map.values.distinctBy { it.id }.sortedByDescending { it.createdAt }
        postsMemory.clear()
        postsMemory.addAll(finalList)
        persistToFileVault()
        return finalList
    }

    @Synchronized
    fun incrementPostViews(postId: String): Int {
        val index = postsMemory.indexOfFirst { it.id == postId }
        if (index != -1) {
            val post = postsMemory[index]
            val newViews = post.viewsCount + 1
            postsMemory[index] = post.copy(viewsCount = newViews)
            persistToFileVault()
            return newViews
        }
        return 0
    }

    @Synchronized
    fun updatePostVote(postId: String, option: String) {
        val index = postsMemory.indexOfFirst { it.id == postId }
        if (index != -1) {
            val post = postsMemory[index]
            val bg = post.balanceGame
            if (bg != null) {
                val newBg = if (option == "A") {
                    bg.copy(votesA = bg.votesA + 1, userVotedOption = "A")
                } else {
                    bg.copy(votesB = bg.votesB + 1, userVotedOption = "B")
                }
                postsMemory[index] = post.copy(balanceGame = newBg)
                persistToFileVault()
            }
        }
    }

    @Synchronized
    fun addLocalComment(postId: String, comment: Comment) {
        val index = postsMemory.indexOfFirst { it.id == postId }
        if (index != -1) {
            val post = postsMemory[index]
            val distinctComments = (post.comments + comment).distinctBy { it.id }
            val updated = post.copy(
                commentsCount = distinctComments.size,
                comments = distinctComments
            )
            postsMemory[index] = updated
            persistToFileVault()
        }
    }

    // =========================================================
    // 파일 입출력 및 JSON 파싱/직렬화
    // =========================================================

    private fun persistToFileVault() {
        try {
            val root = JSONObject()

            // 1. 세션 정보
            root.put("isSessionLoggedIn", isSessionLoggedIn)
            root.put("lastUsedId", lastUsedIdMemory)
            root.put("rememberedIds", JSONArray(rememberedIdsMemory))

            if (cachedSessionUser != null) {
                root.put("sessionUser", userToJson(cachedSessionUser!!))
            }

            // 2. 전체 계정 목록
            val accountsObj = JSONObject()
            for ((id, pair) in accountsMemory) {
                val accJson = JSONObject().apply {
                    put("password", pair.first)
                    put("profile", userToJson(pair.second))
                }
                accountsObj.put(id, accJson)
            }
            root.put("accounts", accountsObj)

            // 3. 포스트 목록
            val postsArr = JSONArray()
            for (p in postsMemory) {
                postsArr.put(postToJson(p))
            }
            root.put("posts", postsArr)

            val jsonString = root.toString(2)

            // 내부 저장소 쓰기
            try {
                internalBackupFile.writeText(jsonString)
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing internal backup: ${e.message}")
            }

            // 외부 전용 저장소 백업 쓰기 (보조)
            externalBackupFile?.let { extFile ->
                try {
                    extFile.writeText(jsonString)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed writing external backup: ${e.message}")
                }
            }

            // 공용 다운로드 폴더 백업 (앱 재설치/업그레이드 후에도 100% 보존)
            publicDownloadBackupFile?.let { pubFile ->
                try {
                    pubFile.writeText(jsonString)
                } catch (e: Exception) {
                    Log.w(TAG, "Public download backup skipped: ${e.message}")
                }
            }

            // 공용 문서 폴더 백업 (이중 안전장치)
            publicDocumentsBackupFile?.let { docFile ->
                try {
                    docFile.writeText(jsonString)
                } catch (e: Exception) {
                    Log.w(TAG, "Public doc backup skipped: ${e.message}")
                }
            }

            try {
                if (fallbackDownloadFile.parentFile?.exists() == true || fallbackDownloadFile.parentFile?.mkdirs() == true) {
                    fallbackDownloadFile.writeText(jsonString)
                }
            } catch (e: Exception) {
                // ignore fallback
            }
        } catch (e: Exception) {
            Log.e(TAG, "persistToFileVault error: ${e.message}", e)
        }
    }

    private fun readBackupJsonFromFile(): JSONObject? {
        val backupCandidates = listOfNotNull(
            internalBackupFile,
            externalBackupFile,
            publicDownloadBackupFile,
            publicDocumentsBackupFile,
            fallbackDownloadFile
        )

        for (file in backupCandidates) {
            try {
                if (file.exists() && file.length() > 10) {
                    val content = file.readText().trim()
                    if (content.startsWith("{") && content.endsWith("}")) {
                        Log.d(TAG, "Restored vault backup from: ${file.absolutePath}")
                        return JSONObject(content)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read backup file ${file.name}: ${e.message}")
            }
        }

        return null
    }

    private fun parseVaultJson(root: JSONObject) {
        isSessionLoggedIn = root.optBoolean("isSessionLoggedIn", false)
        lastUsedIdMemory = root.optString("lastUsedId", "")

        val remArr = root.optJSONArray("rememberedIds")
        if (remArr != null) {
            for (i in 0 until remArr.length()) {
                val id = remArr.optString(i)
                if (id.isNotBlank()) rememberedIdsMemory.add(id.lowercase())
            }
        }

        val sessionUserObj = root.optJSONObject("sessionUser")
        if (sessionUserObj != null) {
            cachedSessionUser = jsonToUser(sessionUserObj)
        }

        val accountsObj = root.optJSONObject("accounts")
        if (accountsObj != null) {
            val keys = accountsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next().lowercase()
                val accJson = accountsObj.optJSONObject(key) ?: continue
                val pwd = accJson.optString("password", "")
                val profJson = accJson.optJSONObject("profile")
                if (profJson != null) {
                    val profile = jsonToUser(profJson)
                    accountsMemory[key] = Pair(pwd, profile)
                    rememberedIdsMemory.add(key)
                }
            }
        }

        val postsArr = root.optJSONArray("posts")
        if (postsArr != null && postsArr.length() > 0) {
            val loaded = mutableListOf<Post>()
            for (i in 0 until postsArr.length()) {
                val pObj = postsArr.optJSONObject(i) ?: continue
                val p = jsonToPost(pObj)
                if (p != null) loaded.add(p)
            }
            if (loaded.isNotEmpty()) {
                postsMemory.clear()
                postsMemory.addAll(loaded.distinctBy { it.id })
            }
        }
    }

    private fun mergeFromSharedPreferences() {
        val sp = prefs ?: return
        val spLastUsed = sp.getString("last_used_id", "") ?: ""
        if (spLastUsed.isNotBlank()) {
            lastUsedIdMemory = spLastUsed.lowercase()
        }

        val spSavedIds = sp.getStringSet("all_saved_ids_set", emptySet()) ?: emptySet()
        for (id in spSavedIds) {
            if (id.isNotBlank()) rememberedIdsMemory.add(id.lowercase())
        }

        // SharedPreferences에 저장된 계정 목록 탐색
        val allEntries = sp.all
        for ((key, value) in allEntries) {
            if (key.startsWith("acc_pwd_") && value is String) {
                val cleanId = key.removePrefix("acc_pwd_").lowercase()
                if (cleanId.isNotBlank() && !accountsMemory.containsKey(cleanId)) {
                    val profile = loadProfileFromPrefs(sp, cleanId)
                    accountsMemory[cleanId] = Pair(value, profile)
                    rememberedIdsMemory.add(cleanId)
                }
            }
        }

        if (sp.getBoolean("is_logged_in", false)) {
            val currentId = sp.getString("user_id", "") ?: ""
            if (currentId.isNotBlank()) {
                cachedSessionUser = loadProfileFromPrefs(sp, currentId)
                isSessionLoggedIn = true
            }
        }
    }

    private fun loadProfileFromPrefs(sp: SharedPreferences, cleanId: String): UserProfile {
        val currentYear = 2026
        val birthYear = sp.getInt("acc_birth_$cleanId", sp.getInt("user_birth_year", 2002))
        val age = if (sp.contains("acc_age_$cleanId")) {
            sp.getInt("acc_age_$cleanId", 24)
        } else {
            sp.getInt("user_age", 24)
        }
        val nickname = sp.getString("acc_nick_$cleanId", sp.getString("user_nickname", cleanId)) ?: cleanId
        val gender = sp.getString("acc_gender_$cleanId", sp.getString("user_gender", "여성")) ?: "여성"
        val bio = sp.getString("acc_bio_$cleanId", sp.getString("user_bio", "")) ?: ""
        val personality = sp.getString("acc_personality_$cleanId", sp.getString("user_personality", "다정하고 긍정적인")) ?: "다정하고 긍정적인"
        val friendStyle = sp.getString("acc_friend_style_$cleanId", sp.getString("user_preferred_friend_style", "편하게 일상 나눌 친구")) ?: "편하게 일상 나눌 친구"
        val specialNotes = sp.getString("acc_special_notes_$cleanId", sp.getString("user_special_notes", "")) ?: ""
        val isAgeVisible = sp.getBoolean("acc_age_visible_$cleanId", sp.getBoolean("user_is_age_visible", true))
        val isGenderVisible = sp.getBoolean("acc_gender_visible_$cleanId", sp.getBoolean("user_is_gender_visible", true))
        val avatarResId = sp.getInt("acc_avatar_res_$cleanId", sp.getInt("user_avatar_res_id", R.drawable.anon_avatar_demo_1789521505031)).let { if (it == 0) null else it }
        val avatarUri = sp.getString("acc_avatar_uri_$cleanId", sp.getString("user_avatar_uri", null))
        val interestsStr = sp.getString("acc_interests_$cleanId", sp.getString("user_interests", "일상,소통,음악")) ?: "일상,소통,음악"
        val interests = interestsStr.split(",").filter { it.isNotBlank() }

        return UserProfile(
            id = cleanId,
            nickname = nickname,
            age = age,
            birthYear = birthYear,
            gender = gender,
            bio = bio,
            mbti = "INFP",
            photoUrls = emptyList(),
            interests = interests,
            personality = personality,
            preferredFriendStyle = friendStyle,
            specialNotes = specialNotes,
            isAgeVisible = isAgeVisible,
            isGenderVisible = isGenderVisible,
            location = "서울",
            heartBalance = sp.getInt("acc_hearts_$cleanId", sp.getInt("user_heart_balance", 20)),
            profileViews = sp.getInt("acc_views_$cleanId", sp.getInt("user_profile_views", 0)),
            isVerified = true,
            googleEmail = "$cleanId@anon.local",
            phoneNumber = "",
            avatarResId = avatarResId,
            avatarUri = avatarUri
        )
    }

    private fun userToJson(user: UserProfile): JSONObject {
        return JSONObject().apply {
            put("id", user.id)
            put("nickname", user.nickname)
            put("age", user.age)
            put("birthYear", user.birthYear)
            put("gender", user.gender)
            put("bio", user.bio)
            put("mbti", user.mbti)
            put("interests", JSONArray(user.interests))
            put("location", user.location)
            put("heartBalance", user.heartBalance)
            put("profileViews", user.profileViews)
            put("googleEmail", user.googleEmail)
            put("phoneNumber", user.phoneNumber)
            put("avatarResId", user.avatarResId ?: 0)
            put("avatarUri", user.avatarUri ?: "")
            put("personality", user.personality)
            put("preferredFriendStyle", user.preferredFriendStyle)
            put("specialNotes", user.specialNotes)
            put("isAgeVisible", user.isAgeVisible)
            put("isGenderVisible", user.isGenderVisible)
        }
    }

    private fun jsonToUser(json: JSONObject): UserProfile {
        val interestsList = mutableListOf<String>()
        val arr = json.optJSONArray("interests")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                interestsList.add(arr.optString(i))
            }
        }
        val avatarResId = json.optInt("avatarResId", 0).let { if (it == 0) null else it }
        val avatarUri = json.optString("avatarUri", "").takeIf { it.isNotBlank() }

        return UserProfile(
            id = json.optString("id", "user_saved"),
            nickname = json.optString("nickname", "익명회원"),
            age = json.optInt("age", 24),
            birthYear = json.optInt("birthYear", 2002),
            gender = json.optString("gender", "여성"),
            bio = json.optString("bio", ""),
            mbti = json.optString("mbti", "INFP"),
            photoUrls = emptyList(),
            interests = if (interestsList.isNotEmpty()) interestsList else listOf("일상", "소통", "음악"),
            location = json.optString("location", "서울"),
            heartBalance = json.optInt("heartBalance", 20),
            profileViews = json.optInt("profileViews", 0),
            isVerified = true,
            googleEmail = json.optString("googleEmail", ""),
            phoneNumber = json.optString("phoneNumber", ""),
            avatarResId = avatarResId,
            avatarUri = avatarUri,
            personality = json.optString("personality", "다정하고 긍정적인"),
            preferredFriendStyle = json.optString("preferredFriendStyle", "편하게 일상 나눌 친구"),
            specialNotes = json.optString("specialNotes", ""),
            isAgeVisible = json.optBoolean("isAgeVisible", true),
            isGenderVisible = json.optBoolean("isGenderVisible", true)
        )
    }

    private fun postToJson(post: Post): JSONObject {
        return JSONObject().apply {
            put("id", post.id)
            put("category", post.category.name)
            put("title", post.title)
            put("content", post.content)
            put("authorTag", post.authorTag)
            put("timeAgo", post.timeAgo)
            put("likesCount", post.likesCount)
            put("viewsCount", post.viewsCount)
            put("commentsCount", post.commentsCount)
            put("isLiked", post.isLiked)
            put("isMyPost", post.isMyPost)
            put("authorId", post.authorId)
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
                    put("postId", c.postId)
                    put("authorTag", c.authorTag)
                    put("content", c.content)
                    put("likesCount", c.likesCount)
                    put("timeAgo", c.timeAgo)
                    put("isAuthor", c.isAuthor)
                    put("isMyComment", c.isMyComment)
                    put("authorId", c.authorId)
                    put("createdAt", c.createdAt)
                })
            }
            put("comments", cArr)
        }
    }

    private fun jsonToPost(json: JSONObject): Post? {
        val id = json.optString("id", "")
        if (id.isBlank()) return null
        val catStr = json.optString("category", PostCategory.DAILY.name)
        val category = try { PostCategory.valueOf(catStr) } catch (e: Exception) { PostCategory.DAILY }

        val bgObj = json.optJSONObject("balanceGame")
        val balanceGame = if (bgObj != null) {
            BalanceGame(
                optionA = bgObj.optString("optionA", ""),
                optionB = bgObj.optString("optionB", ""),
                votesA = bgObj.optInt("votesA", 0),
                votesB = bgObj.optInt("votesB", 0),
                userVotedOption = bgObj.optString("userVotedOption", "").takeIf { it.isNotBlank() }
            )
        } else null

        val cleanUser = if (isSessionLoggedIn && cachedSessionUser != null) {
            cachedSessionUser!!.id.trim().lowercase()
        } else ""
        val authorId = json.optString("authorId", "")
        val isValidUser = isSessionLoggedIn && cleanUser.isNotBlank() && cleanUser != "user_me_anon"
        val isMyPost = isValidUser && authorId.isNotBlank() && !authorId.equals("user_me_anon", ignoreCase = true) && authorId.equals(cleanUser, ignoreCase = true)

        val commentsList = mutableListOf<Comment>()
        val cArr = json.optJSONArray("comments")
        if (cArr != null) {
            for (i in 0 until cArr.length()) {
                val c = cArr.optJSONObject(i) ?: continue
                val commentAuthorId = c.optString("authorId", "")
                val isMyComment = isValidUser && commentAuthorId.isNotBlank() && !commentAuthorId.equals("user_me_anon", ignoreCase = true) && commentAuthorId.equals(cleanUser, ignoreCase = true)
                val isAuthor = authorId.isNotBlank() && commentAuthorId.isNotBlank() && commentAuthorId.equals(authorId, ignoreCase = true)

                commentsList.add(
                    Comment(
                        id = c.optString("id", "c_${System.currentTimeMillis()}"),
                        postId = c.optString("postId", id),
                        authorTag = c.optString("authorTag", "익명"),
                        content = c.optString("content", ""),
                        likesCount = c.optInt("likesCount", 0),
                        timeAgo = c.optString("timeAgo", "방금 전"),
                        isAuthor = isAuthor,
                        isMyComment = isMyComment,
                        authorId = commentAuthorId,
                        createdAt = c.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        return Post(
            id = id,
            category = category,
            title = json.optString("title", ""),
            content = json.optString("content", ""),
            authorTag = json.optString("authorTag", "익명"),
            timeAgo = json.optString("timeAgo", "방금 전"),
            likesCount = json.optInt("likesCount", 0),
            viewsCount = json.optInt("viewsCount", 0),
            commentsCount = json.optInt("commentsCount", commentsList.size),
            balanceGame = balanceGame,
            comments = commentsList,
            isLiked = json.optBoolean("isLiked", false),
            isMyPost = isMyPost,
            authorId = authorId,
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }
}
