package com.example.data.remote

import android.util.Log
import com.example.core.model.BalanceGame
import com.example.core.model.Comment
import com.example.core.model.Post
import com.example.core.model.PostCategory
import com.example.core.model.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class FirestoreCommunityService {

    private val restSyncService = CloudRestSyncService()

    private val db by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Firestore instance: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "FirestoreCommunity"
        private const val COLLECTION_POSTS = "community_posts"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_ACCOUNTS = "accounts"
    }

    /**
     * 클라우드 상에서 아이디 중복 여부 확인
     */
    suspend fun isIdTakenInCloud(id: String): Boolean {
        val cleanId = id.trim().lowercase()
        val restTaken = restSyncService.isIdTaken(cleanId)
        if (restTaken) return true

        val firestore = db ?: return false
        return try {
            val doc = firestore.collection(COLLECTION_ACCOUNTS).document(cleanId).get().await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 계정 정보 클라우드 영구 저장 (아이디, 비밀번호, 프로필)
     */
    suspend fun saveAccountInCloud(
        id: String,
        password: String,
        userProfile: UserProfile
    ): Boolean {
        val cleanId = id.trim().lowercase()
        val restSuccess = restSyncService.saveAccount(cleanId, password, userProfile)

        val firestore = db
        if (firestore != null) {
            try {
                val accountData = hashMapOf(
                    "id" to cleanId,
                    "password" to password,
                    "nickname" to userProfile.nickname,
                    "gender" to userProfile.gender,
                    "age" to userProfile.age,
                    "birthYear" to userProfile.birthYear,
                    "bio" to userProfile.bio,
                    "avatarResId" to (userProfile.avatarResId ?: 0),
                    "avatarUri" to (userProfile.avatarUri ?: ""),
                    "interests" to userProfile.interests,
                    "personality" to userProfile.personality,
                    "preferredFriendStyle" to userProfile.preferredFriendStyle,
                    "specialNotes" to userProfile.specialNotes,
                    "isAgeVisible" to userProfile.isAgeVisible,
                    "isGenderVisible" to userProfile.isGenderVisible,
                    "createdAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection(COLLECTION_ACCOUNTS).document(cleanId).set(accountData).await()
                saveUserProfile(userProfile)
                Log.d(TAG, "Account successfully saved in cloud DB: $cleanId")
            } catch (e: Exception) {
                Log.w(TAG, "Firestore notice: ${e.message}")
            }
        }
        return restSuccess
    }

    /**
     * 클라우드 계정 정보 실시간 업데이트 (아이디 문서 및 유저 프로필 문서 동시 동기화)
     */
    suspend fun updateAccountProfileInCloud(id: String, userProfile: UserProfile): Boolean {
        val cleanId = id.trim().lowercase()
        val restSuccess = restSyncService.updateAccountProfile(cleanId, userProfile)

        val firestore = db
        if (firestore != null) {
            try {
                val accountUpdates = hashMapOf<String, Any>(
                    "nickname" to userProfile.nickname,
                    "gender" to userProfile.gender,
                    "age" to userProfile.age,
                    "birthYear" to userProfile.birthYear,
                    "bio" to userProfile.bio,
                    "avatarResId" to (userProfile.avatarResId ?: 0),
                    "avatarUri" to (userProfile.avatarUri ?: ""),
                    "interests" to userProfile.interests,
                    "personality" to userProfile.personality,
                    "preferredFriendStyle" to userProfile.preferredFriendStyle,
                    "specialNotes" to userProfile.specialNotes,
                    "isAgeVisible" to userProfile.isAgeVisible,
                    "isGenderVisible" to userProfile.isGenderVisible,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection(COLLECTION_ACCOUNTS).document(cleanId)
                    .set(accountUpdates, SetOptions.merge()).await()
                saveUserProfile(userProfile)
            } catch (e: Exception) {
                Log.w(TAG, "Account update notice: ${e.message}")
            }
        }
        return restSuccess
    }

    /**
     * 클라우드 계정 문서 실시간 관찰 (변경사항 실시간 수신)
     */
    fun observeAccountFromCloud(id: String): Flow<Map<String, Any>?> {
        return restSyncService.observeAccount(id)
    }

    /**
     * 마지막 로그인 세션을 클라우드(REST + Firestore)에 보존
     */
    suspend fun saveSessionInCloud(user: UserProfile, password: String = "") {
        restSyncService.saveSessionToCloud(user, password)
        val firestore = db ?: return
        try {
            val sessionData = hashMapOf(
                "userId" to user.id,
                "isLoggedIn" to true,
                "nickname" to user.nickname,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection(COLLECTION_ACCOUNTS).document("_last_active_session").set(sessionData).await()
        } catch (e: Exception) {
            Log.w(TAG, "saveSessionInCloud firestore: ${e.message}")
        }
    }

    /**
     * 클라우드에 저장된 모든 등록 계정 및 세션 통합 동기화
     */
    suspend fun syncAllAccountsAndSession(): CloudRestSyncService.CloudSyncResult {
        val restResult = restSyncService.restoreAllAccountsAndSessionFromCloud()
        val firestore = db
        if (firestore != null) {
            try {
                val snapshot = firestore.collection(COLLECTION_ACCOUNTS).get().await()
                val firestoreAccounts = mutableMapOf<String, Pair<String, UserProfile>>()
                for (doc in snapshot.documents) {
                    if (doc.id == "_last_active_session") continue
                    val data = doc.data ?: continue
                    val id = doc.id
                    val pwd = data["password"] as? String ?: ""
                    val profile = parseUserProfile(id, data)
                    firestoreAccounts[id] = Pair(pwd, profile)
                }
                if (firestoreAccounts.isNotEmpty()) {
                    com.example.data.local.PersistentVaultManager.instance.mergeAccountsFromMap(firestoreAccounts)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore syncAllAccounts notice: ${e.message}")
            }
        }
        return restResult
    }

    /**
     * 클라우드 계정 정보 조회 (로그인 인증 및 프로필 복원용)
     */
    suspend fun getAccountFromCloud(id: String): Map<String, Any>? {
        val cleanId = id.trim().lowercase()
        val restAccount = restSyncService.getAccount(cleanId)
        if (restAccount != null) return restAccount

        val firestore = db ?: return null
        return try {
            val doc = firestore.collection(COLLECTION_ACCOUNTS).document(cleanId).get().await()
            if (doc.exists()) doc.data else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get account from cloud: ${e.message}")
            null
        }
    }

    /**
     * 특정 사용자 프로필 실시간 관찰
     */
    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val cleanId = userId.trim().lowercase()
        return flow {
            restSyncService.observeAccount(cleanId).collect { map ->
                if (map != null) {
                    val profile = parseUserProfile(cleanId, map)
                    emit(profile)
                } else {
                    emit(null)
                }
            }
        }
    }

    /**
     * 사용자 프로필 및 스탯(하트, 방문자수/조회수, 정보) 서버 저장/동기화
     */
    suspend fun saveUserProfile(user: UserProfile): Boolean {
        val restSuccess = restSyncService.updateAccountProfile(user.id, user)
        val firestore = db
        if (firestore != null) {
            try {
                val data = hashMapOf(
                    "id" to user.id,
                    "nickname" to user.nickname,
                    "age" to user.age,
                    "birthYear" to user.birthYear,
                    "gender" to user.gender,
                    "bio" to user.bio,
                    "mbti" to user.mbti,
                    "interests" to user.interests,
                    "location" to user.location,
                    "heartBalance" to user.heartBalance,
                    "profileViews" to user.profileViews,
                    "googleEmail" to user.googleEmail,
                    "phoneNumber" to user.phoneNumber,
                    "avatarResId" to (user.avatarResId ?: 0),
                    "avatarUri" to (user.avatarUri ?: ""),
                    "personality" to user.personality,
                    "preferredFriendStyle" to user.preferredFriendStyle,
                    "specialNotes" to user.specialNotes,
                    "isAgeVisible" to user.isAgeVisible,
                    "isGenderVisible" to user.isGenderVisible,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection(COLLECTION_USERS).document(user.id).set(data).await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore saveUserProfile notice: ${e.message}")
            }
        }
        return restSuccess
    }

    /**
     * 회원 탈퇴: 클라우드 Firestore 및 REST 동기화 서버에서 계정 및 프로필 완전 삭제
     */
    suspend fun deleteAccountFromCloud(id: String): Boolean {
        val cleanId = id.trim().lowercase()
        restSyncService.deleteAccount(cleanId)
        val firestore = db
        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_ACCOUNTS).document(cleanId).delete().await()
                firestore.collection(COLLECTION_USERS).document(cleanId).delete().await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore delete notice: ${e.message}")
            }
        }
        return true
    }

    /**
     * 프로필 방문자 수(조회수) 1 증가
     */
    suspend fun incrementProfileViews(userId: String) {
        val firestore = db ?: return
        try {
            firestore.collection(COLLECTION_USERS).document(userId)
                .update("profileViews", FieldValue.increment(1))
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to increment profile views: ${e.message}")
        }
    }

    /**
     * 사용자 보유 하트 업데이트
     */
    suspend fun updateHeartBalance(userId: String, newBalance: Int) {
        val firestore = db ?: return
        try {
            firestore.collection(COLLECTION_USERS).document(userId)
                .update("heartBalance", newBalance)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update heart balance: ${e.message}")
        }
    }

    /**
     * 실시간 게시글 스트림 관찰
     */
    fun observePosts(): Flow<List<Post>> {
        return restSyncService.observePosts()
    }

    /**
     * 일회성 최신 게시글 목록 조회
     */
    suspend fun fetchPosts(): List<Post> {
        return restSyncService.fetchPosts()
    }

    /**
     * 새 게시글 영구 저장
     */
    suspend fun createPost(post: Post): Boolean {
        val restSuccess = restSyncService.createPost(post)
        val firestore = db
        if (firestore != null) {
            try {
                val postData = hashMapOf(
                    "category" to post.category.name,
                    "title" to post.title,
                    "content" to post.content,
                    "authorTag" to post.authorTag,
                    "authorId" to post.authorId,
                    "likesCount" to post.likesCount,
                    "viewsCount" to post.viewsCount,
                    "commentsCount" to 0,
                    "createdAt" to post.createdAt,
                    "comments" to emptyList<Map<String, Any>>()
                )
                if (post.balanceGame != null) {
                    postData["balanceGame"] = mapOf(
                        "optionA" to post.balanceGame.optionA,
                        "optionB" to post.balanceGame.optionB,
                        "votesA" to post.balanceGame.votesA,
                        "votesB" to post.balanceGame.votesB
                    )
                }
                firestore.collection(COLLECTION_POSTS).document(post.id).set(postData).await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore createPost notice: ${e.message}")
            }
        }
        return restSuccess
    }

    /**
     * 댓글 추가
     */
    suspend fun addComment(postId: String, comment: Comment): Boolean {
        val restSuccess = restSyncService.addComment(postId, comment)
        val firestore = db
        if (firestore != null) {
            try {
                val commentData = mapOf(
                    "id" to comment.id,
                    "authorTag" to comment.authorTag,
                    "content" to comment.content,
                    "authorId" to comment.authorId,
                    "isAuthor" to comment.isAuthor,
                    "likesCount" to comment.likesCount,
                    "createdAt" to comment.createdAt
                )
                firestore.collection(COLLECTION_POSTS).document(postId).update(
                    "comments", FieldValue.arrayUnion(commentData),
                    "commentsCount", FieldValue.increment(1)
                ).await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore addComment notice: ${e.message}")
            }
        }
        return restSuccess
    }

    /**
     * 게시글 삭제
     */
    suspend fun deletePost(postId: String): Boolean {
        val restSuccess = restSyncService.deletePost(postId)
        val firestore = db
        if (firestore != null) {
            try {
                firestore.collection(COLLECTION_POSTS).document(postId).delete().await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore deletePost notice: ${e.message}")
            }
        }
        return restSuccess
    }

    /**
     * 댓글 삭제
     */
    suspend fun deleteComment(postId: String, commentId: String): Boolean {
        return restSyncService.deleteComment(postId, commentId)
    }

    /**
     * 조회수 1 증가
     */
    suspend fun incrementPostViews(postId: String) {
        restSyncService.incrementViews(postId)
        val firestore = db ?: return
        try {
            firestore.collection(COLLECTION_POSTS).document(postId)
                .update("viewsCount", FieldValue.increment(1))
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore incrementPostViews notice: ${e.message}")
        }
    }

    /**
     * 밸런스 게임 투표
     */
    suspend fun voteBalanceGame(postId: String, option: String) {
        restSyncService.voteBalanceGame(postId, option)
    }

    /**
     * 게시글 시딩 (클라우드 저장소가 비어있을 때 최초 1회 초기화)
     */
    suspend fun seedInitialPostsIfEmpty(defaultPosts: List<Post>): Boolean {
        val existing = restSyncService.fetchPosts()
        if (existing.isEmpty() && defaultPosts.isNotEmpty()) {
            for (p in defaultPosts) {
                restSyncService.createPost(p)
            }
            return true
        }
        return false
    }

    /**
     * 유해 콘텐츠 신고 접수 (게시글, 댓글, 채팅/유저)
     */
    suspend fun submitReport(
        type: String,
        targetId: String,
        reporterId: String,
        reason: String,
        extraInfo: Map<String, Any> = emptyMap()
    ): Boolean {
        val firestore = db ?: return true
        return try {
            val reportData = hashMapOf<String, Any>(
                "type" to type,
                "targetId" to targetId,
                "reporterId" to reporterId,
                "reason" to reason,
                "createdAt" to System.currentTimeMillis()
            )
            reportData.putAll(extraInfo)
            firestore.collection("reports").add(reportData).await()
            true
        } catch (e: Exception) {
            true
        }
    }

    private fun parseUserProfile(userId: String, map: Map<String, Any>): UserProfile {
        val nickname = map["nickname"] as? String ?: userId
        val age = (map["age"] as? Number)?.toInt() ?: 24
        val birthYear = (map["birthYear"] as? Number)?.toInt() ?: 2002
        val gender = map["gender"] as? String ?: "여성"
        val bio = map["bio"] as? String ?: ""
        val mbti = map["mbti"] as? String ?: "INFP"
        val interests = (map["interests"] as? List<String>) ?: listOf("일상", "소통", "음악")
        val personality = map["personality"] as? String ?: "다정하고 긍정적인"
        val friendStyle = map["preferredFriendStyle"] as? String ?: "편하게 일상 나눌 친구"
        val specialNotes = map["specialNotes"] as? String ?: ""
        val isAgeVisible = map["isAgeVisible"] as? Boolean ?: true
        val isGenderVisible = map["isGenderVisible"] as? Boolean ?: true
        val avatarResId = (map["avatarResId"] as? Number)?.toInt()?.takeIf { it != 0 }
        val avatarUri = map["avatarUri"] as? String
        val heartBalance = (map["heartBalance"] as? Number)?.toInt() ?: 20
        val profileViews = (map["profileViews"] as? Number)?.toInt() ?: 0

        return UserProfile(
            id = userId,
            nickname = nickname,
            age = age,
            birthYear = birthYear,
            gender = gender,
            bio = bio,
            mbti = mbti,
            photoUrls = emptyList(),
            interests = interests,
            location = "서울",
            heartBalance = heartBalance,
            profileViews = profileViews,
            isVerified = true,
            googleEmail = "$userId@anon.local",
            phoneNumber = "",
            avatarResId = avatarResId,
            avatarUri = avatarUri,
            personality = personality,
            preferredFriendStyle = friendStyle,
            specialNotes = specialNotes,
            isAgeVisible = isAgeVisible,
            isGenderVisible = isGenderVisible
        )
    }
}
