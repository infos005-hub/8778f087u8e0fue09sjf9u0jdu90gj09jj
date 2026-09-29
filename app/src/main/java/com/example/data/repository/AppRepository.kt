package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.AnonApplication
import com.example.core.util.AnonymousNameGenerator
import com.example.R
import com.example.core.model.*
import com.example.core.safety.SafetyTextFilter
import com.example.data.local.PersistentVaultManager
import com.example.data.remote.FirestoreCommunityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AppRepository {

    private val vault = PersistentVaultManager.instance
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firestoreService = FirestoreCommunityService()

    private val prefs: SharedPreferences? by lazy {
        try {
            AnonApplication.instance.getSharedPreferences("anon_user_prefs", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }
    }

    // 현재 사용자 상태 (100% 완전 익명 계정)
    private val defaultDemoUser = UserProfile(
        id = "user_me_anon",
        nickname = "달리는고양이",
        age = 24,
        birthYear = 2002,
        gender = "여성",
        bio = "주말마다 러닝하고 조용한 북카페 가는 걸 좋아해요 ☕️\n새로운 음악과 일상 취향 공유해요!",
        mbti = "INFJ",
        photoUrls = listOf(),
        interests = listOf("러닝", "독서", "카페투어", "재즈", "반려묘"),
        location = "서울 마포구",
        heartBalance = 15,
        profileViews = 48,
        avatarResId = R.drawable.anon_avatar_demo_1789521505031
    )

    private val _isLoggedIn = MutableStateFlow<Boolean>(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _currentUser = MutableStateFlow(defaultDemoUser)
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _chatRooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val chatRooms: StateFlow<List<ChatRoom>> = _chatRooms.asStateFlow()

    private val _visitors = MutableStateFlow<List<VisitorInfo>>(emptyList())
    val visitors: StateFlow<List<VisitorInfo>> = _visitors.asStateFlow()

    init {
        loadSavedUserSession()
        seedInitialPosts()
        seedInitialVisitors()
        subscribeToCloudPosts()
        subscribeToCurrentUserProfile()
        // 앱 수정/업그레이드/재설치 시에도 계정 정보가 절대 날아가지 않도록 클라우드 자동 복원
        syncCloudAccountsAndSession()
    }

    private var userProfileJob: Job? = null
    private var accountObservationJob: Job? = null

    private fun subscribeToCurrentUserProfile() {
        val userId = _currentUser.value.id
        if (userId.isNotBlank()) {
            userProfileJob?.cancel()
            userProfileJob = repositoryScope.launch {
                try {
                    firestoreService.observeUserProfile(userId).collect { cloudProfile ->
                        if (cloudProfile != null) {
                            _currentUser.update { current ->
                                cloudProfile.copy(
                                    // 로컬에만 있는 리소스 ID 유지 (null인 경우 대비)
                                    avatarResId = cloudProfile.avatarResId ?: current.avatarResId,
                                    avatarUri = cloudProfile.avatarUri ?: current.avatarUri
                                )
                            }
                            saveUserSession(_currentUser.value)
                            val cleanId = userId.lowercase()
                            val sp = prefs
                            if (sp != null && sp.contains("acc_pwd_$cleanId")) {
                                val pwd = sp.getString("acc_pwd_$cleanId", "") ?: ""
                                saveRegisteredAccountLocally(cleanId, pwd, _currentUser.value)
                            }
                        } else {
                            // 클라우드에 아직 없으면 현재 프로필 최초 업로드
                            firestoreService.saveUserProfile(_currentUser.value)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AppRepository", "Failed to observe user profile: ${e.message}")
                }
            }
            startObservingAccount(userId)
        }
    }

    private fun startObservingAccount(userId: String) {
        val cleanId = userId.trim().lowercase()
        if (cleanId.isBlank()) return
        accountObservationJob?.cancel()
        accountObservationJob = repositoryScope.launch {
            try {
                firestoreService.observeAccountFromCloud(cleanId).collect { accountData ->
                    if (accountData != null) {
                        val nickname = accountData["nickname"] as? String
                        val age = (accountData["age"] as? Long)?.toInt()
                        val bio = accountData["bio"] as? String
                        val gender = accountData["gender"] as? String
                        val isAgeVisible = accountData["isAgeVisible"] as? Boolean
                        val isGenderVisible = accountData["isGenderVisible"] as? Boolean
                        val interests = accountData["interests"] as? List<String>
                        val personality = accountData["personality"] as? String
                        val friendStyle = accountData["preferredFriendStyle"] as? String
                        val specialNotes = accountData["specialNotes"] as? String

                        _currentUser.update { current ->
                            if (current.id.equals(cleanId, ignoreCase = true)) {
                                current.copy(
                                    nickname = nickname ?: current.nickname,
                                    age = age ?: current.age,
                                    bio = bio ?: current.bio,
                                    gender = gender ?: current.gender,
                                    isAgeVisible = isAgeVisible ?: current.isAgeVisible,
                                    isGenderVisible = isGenderVisible ?: current.isGenderVisible,
                                    interests = interests ?: current.interests,
                                    personality = personality ?: current.personality,
                                    preferredFriendStyle = friendStyle ?: current.preferredFriendStyle,
                                    specialNotes = specialNotes ?: current.specialNotes
                                )
                            } else current
                        }
                        saveUserSession(_currentUser.value)
                    }
                }
            } catch (e: Exception) {
                Log.w("AppRepository", "Failed to observe account from cloud: ${e.message}")
            }
        }
    }

    fun reevaluatePostsOwnership(userId: String) {
        val cleanId = userId.trim().lowercase()
        val isValidUser = _isLoggedIn.value && cleanId.isNotBlank() && cleanId != "user_me_anon"
        _posts.update { list ->
            list.map { post ->
                val isMyPost = isValidUser && post.authorId.isNotBlank() &&
                        !post.authorId.equals("user_me_anon", ignoreCase = true) &&
                        post.authorId.equals(cleanId, ignoreCase = true)
                val mappedComments = post.comments.map { comment ->
                    val isMyComment = isValidUser && comment.authorId.isNotBlank() &&
                            !comment.authorId.equals("user_me_anon", ignoreCase = true) &&
                            comment.authorId.equals(cleanId, ignoreCase = true)
                    val isAuthor = post.authorId.isNotBlank() && comment.authorId.isNotBlank() &&
                            comment.authorId.equals(post.authorId, ignoreCase = true)
                    comment.copy(isMyComment = isMyComment, isAuthor = isAuthor)
                }.distinctBy { it.id }
                post.copy(isMyPost = isMyPost, comments = mappedComments)
            }.distinctBy { it.id }
        }
        vault.reevaluatePostsOwnership(if (isValidUser) cleanId else "")
    }

    private fun subscribeToCloudPosts() {
        repositoryScope.launch {
            try {
                firestoreService.observePosts().collect { cloudPosts ->
                    if (cloudPosts.isNotEmpty()) {
                        val cleanUserId = _currentUser.value.id.trim().lowercase()
                        val isValidUser = _isLoggedIn.value && cleanUserId.isNotBlank() && cleanUserId != "user_me_anon"
                        val mapped = cloudPosts.map { post ->
                            val isMyPost = isValidUser && post.authorId.isNotBlank() &&
                                    !post.authorId.equals("user_me_anon", ignoreCase = true) &&
                                    post.authorId.equals(cleanUserId, ignoreCase = true)
                            val mappedComments = post.comments.map { comment ->
                                val isMyComment = isValidUser && comment.authorId.isNotBlank() &&
                                        !comment.authorId.equals("user_me_anon", ignoreCase = true) &&
                                        comment.authorId.equals(cleanUserId, ignoreCase = true)
                                val isAuthor = post.authorId.isNotBlank() && comment.authorId.isNotBlank() &&
                                        comment.authorId.equals(post.authorId, ignoreCase = true)
                                comment.copy(isMyComment = isMyComment, isAuthor = isAuthor)
                            }.distinctBy { it.id }
                            val currentLocalPost = _posts.value.firstOrNull { it.id == post.id }
                            val maxViews = maxOf(post.viewsCount, currentLocalPost?.viewsCount ?: 0)
                            post.copy(
                                isMyPost = isMyPost,
                                comments = mappedComments,
                                commentsCount = mappedComments.size,
                                viewsCount = maxViews
                            )
                        }.distinctBy { it.id }
                        _posts.value = mapped
                    }
                }
            } catch (e: Exception) {
                Log.w("AppRepository", "Failed to observe cloud posts: ${e.message}")
            }
        }
    }

    private fun loadSavedUserSession() {
        // 1. 다중 로컬 영구 금고(PersistentVault)에서 세션 우선 복구
        val vaultUser = vault.getSavedSessionUser()
        if (vaultUser != null) {
            _currentUser.value = vaultUser
            _isLoggedIn.value = true
            _chatRooms.value = emptyList()
            reevaluatePostsOwnership(vaultUser.id)
            return
        }

        // 2. SharedPreferences 백업 확인
        val sp = prefs ?: return
        val savedLoggedIn = sp.getBoolean("is_logged_in", false)
        if (savedLoggedIn) {
            val email = sp.getString("user_google_email", "infos005@g.gne.go.kr") ?: "infos005@g.gne.go.kr"
            val nickname = sp.getString("user_nickname", "익명회원") ?: "익명회원"
            val age = sp.getInt("user_age", 24)
            val birthYear = sp.getInt("user_birth_year", 2002)
            val gender = sp.getString("user_gender", "여성") ?: "여성"
            val bio = sp.getString("user_bio", "반갑습니다! 편하게 소통해요 ✨") ?: "반갑습니다!"
            val mbti = sp.getString("user_mbti", "INFJ") ?: "INFJ"
            val interestsStr = sp.getString("user_interests", "러닝,독서,카페투어") ?: "러닝,독서,카페투어"
            val interestsList = interestsStr.split(",").filter { it.isNotBlank() }
            val avatarResId = sp.getInt("user_avatar_res_id", R.drawable.anon_avatar_demo_1789521505031)
            val avatarUri = sp.getString("user_avatar_uri", null)
            val phone = sp.getString("user_phone", "010-8765-4321") ?: "010-8765-4321"
            val personality = sp.getString("user_personality", "다정하고 긍정적인") ?: "다정하고 긍정적인"
            val preferredFriendStyle = sp.getString("user_preferred_friend_style", "편하게 일상 나눌 친구") ?: "편하게 일상 나눌 친구"
            val specialNotes = sp.getString("user_special_notes", "") ?: ""
            val isAgeVisible = sp.getBoolean("user_is_age_visible", true)
            val isGenderVisible = sp.getBoolean("user_is_gender_visible", true)

            val profile = UserProfile(
                id = sp.getString("user_id", "user_saved") ?: "user_saved",
                nickname = nickname,
                age = age,
                birthYear = birthYear,
                gender = gender,
                bio = bio,
                mbti = mbti,
                photoUrls = emptyList(),
                interests = if (interestsList.isNotEmpty()) interestsList else listOf("러닝", "독서", "카페투어"),
                location = "서울 마포구",
                heartBalance = sp.getInt("user_heart_balance", 15),
                profileViews = sp.getInt("user_profile_views", 48),
                isVerified = true,
                googleEmail = email,
                phoneNumber = phone,
                avatarResId = if (avatarResId != 0) avatarResId else R.drawable.anon_avatar_demo_1789521505031,
                avatarUri = avatarUri,
                personality = personality,
                preferredFriendStyle = preferredFriendStyle,
                specialNotes = specialNotes,
                isAgeVisible = isAgeVisible,
                isGenderVisible = isGenderVisible
            )
            _currentUser.value = profile
            _isLoggedIn.value = true
            _chatRooms.value = emptyList()
            vault.saveSession(profile)
        }
    }

    fun saveUserSession(user: UserProfile) {
        // 1. 다중 로컬 영구 금고에 즉시 보존
        vault.saveSession(user)
        reevaluatePostsOwnership(user.id)

        // 2. SharedPreferences에도 즉시 커밋
        val sp = prefs ?: return
        val currentSet = sp.getStringSet("all_saved_ids_set", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (user.id.isNotBlank()) {
            currentSet.add(user.id.lowercase())
        }
        sp.edit()
            .putBoolean("is_logged_in", true)
            .putString("user_id", user.id)
            .putString("user_nickname", user.nickname)
            .putInt("user_age", user.age)
            .putInt("user_birth_year", user.birthYear)
            .putString("user_gender", user.gender)
            .putString("user_bio", user.bio)
            .putString("user_mbti", user.mbti)
            .putString("user_interests", user.interests.joinToString(","))
            .putString("user_google_email", user.googleEmail)
            .putString("user_phone", user.phoneNumber)
            .putInt("user_avatar_res_id", user.avatarResId ?: 0)
            .putString("user_avatar_uri", user.avatarUri)
            .putInt("user_heart_balance", user.heartBalance)
            .putInt("user_profile_views", user.profileViews)
            .putString("account_registered_${user.googleEmail}", "true")
            .putString("last_used_id", user.id)
            .putBoolean("reg_id_${user.id.lowercase()}", true)
            .putString("user_personality", user.personality)
            .putString("user_preferred_friend_style", user.preferredFriendStyle)
            .putString("user_special_notes", user.specialNotes)
            .putBoolean("user_is_age_visible", user.isAgeVisible)
            .putBoolean("user_is_gender_visible", user.isGenderVisible)
            .putStringSet("all_saved_ids_set", currentSet)
            .commit()

        // 3. 클라우드(REST + Firestore)에도 활성 세션 비동기 보존
        if (user.id.isNotBlank() && user.id != "user_me_anon") {
            repositoryScope.launch {
                try {
                    firestoreService.saveSessionInCloud(user)
                } catch (e: Exception) {
                    Log.w("AppRepository", "Cloud session sync notice: ${e.message}")
                }
            }
        }
    }

    /**
     * 클라우드 동기화 서버로부터 전체 계정 및 마지막 세션 복원
     */
    fun syncCloudAccountsAndSession(onComplete: ((Boolean) -> Unit)? = null) {
        repositoryScope.launch {
            try {
                val syncResult = firestoreService.syncAllAccountsAndSession()
                val restoredUser = syncResult.lastSessionUser

                // 로컬 세션이 없거나 기본 익명 유저인데, 클라우드에 유효한 세션이 복원된 경우 즉시 자동 로그인!
                if ((!_isLoggedIn.value || _currentUser.value.id == "user_me_anon") && restoredUser != null && restoredUser.id != "user_me_anon") {
                    _currentUser.value = restoredUser
                    _isLoggedIn.value = true
                    _chatRooms.value = emptyList()
                    vault.saveSession(restoredUser)
                    reevaluatePostsOwnership(restoredUser.id)
                    subscribeToCurrentUserProfile()
                    Log.d("AppRepository", "Auto-restored cloud session for ${restoredUser.id}")
                }
                onComplete?.invoke(true)
            } catch (e: Exception) {
                Log.w("AppRepository", "syncCloudAccounts notice: ${e.message}")
                onComplete?.invoke(false)
            }
        }
    }

    /**
     * 로컬 금고/설정에 저장된 계정 비밀번호 조회 (빠른 자동완성 로그인용)
     */
    fun getSavedAccountPassword(id: String): String? {
        val cleanId = id.trim().lowercase()
        return vault.getAccountPassword(cleanId) ?: prefs?.getString("acc_pwd_$cleanId", null)
    }

    // 이미 가입된 Google 계정인지 확인
    fun isGoogleAccountRegistered(email: String): Boolean {
        if (email.contains("infos005") || email == "infos005@g.gne.go.kr") return true
        val sp = prefs ?: return false
        return sp.contains("account_registered_$email")
    }

    // Google 계정으로 1초 즉시 로그인 (기존 회원 정보 자동 로드 및 동기화)
    fun loginWithExistingGoogleAccount(email: String, displayName: String? = null): UserProfile {
        val sp = prefs
        val savedNick = sp?.getString("user_nickname", null)
        val defaultName = if (!savedNick.isNullOrBlank() && email == sp.getString("user_google_email", "")) {
            savedNick
        } else {
            displayName?.takeIf { it.isNotBlank() && !it.contains("@") }
                ?: email.substringBefore("@").takeIf { it.isNotBlank() }
                ?: "익명인"
        }

        val profile = UserProfile(
            id = "user_google_${email.hashCode().toString().replace("-", "")}",
            nickname = defaultName,
            age = 24,
            birthYear = 2002,
            gender = "여성",
            bio = "Google 계정($email)으로 연동된 계정입니다. 반갑습니다 ✨",
            mbti = "INFP",
            photoUrls = emptyList(),
            interests = listOf("일상", "소통", "음악", "카페투어"),
            location = "서울",
            heartBalance = 20,
            profileViews = 18,
            isVerified = true,
            googleEmail = email,
            phoneNumber = "010-8765-4321",
            avatarResId = R.drawable.anon_avatar_demo_1789521505031,
            avatarUri = null
        )
        _currentUser.value = profile
        _isLoggedIn.value = true
        _chatRooms.value = emptyList()
        saveUserSession(profile)
        subscribeToCurrentUserProfile()
        repositoryScope.launch {
            firestoreService.saveUserProfile(profile)
        }
        return profile
    }

    fun blockUser(userId: String) {
        _blockedUserIds.update { it + userId }
        _chatRooms.update { rooms -> rooms.filterNot { it.peerUser.id == userId } }
        _posts.update { posts ->
            posts.filterNot { it.authorId == userId }
                .map { post ->
                    val filteredComments = post.comments.filterNot { it.authorId == userId }
                    post.copy(
                        comments = filteredComments,
                        commentsCount = filteredComments.size
                    )
                }
        }
    }

    fun sendLike(candidate: UserProfile): Boolean {
        if (_currentUser.value.heartBalance <= 0) return false

        // 하트 1개 차감
        _currentUser.update { it.copy(heartBalance = it.heartBalance - 1) }
        saveUserSession(_currentUser.value)
        repositoryScope.launch {
            firestoreService.updateHeartBalance(_currentUser.value.id, _currentUser.value.heartBalance)
        }

        // 상호 호감 매칭 시뮬레이션 (새로운 1:1 채팅방 생성)
        val newRoom = ChatRoom(
            id = "room_${candidate.id}",
            peerUser = candidate,
            lastMessage = "🎉 서로 호감을 표시하여 매칭이 성사되었습니다!",
            lastTime = "방금",
            unreadCount = 1,
            messages = listOf(
                ChatMessage(
                    senderId = "system",
                    senderNickname = "Anon 안전도우미",
                    text = "🔒 안전한 익명 대화를 위해 개인정보(전화번호, SNS 아이디) 공유를 지양해 주세요.",
                    timestamp = "방금",
                    isFromMe = false
                ),
                ChatMessage(
                    senderId = candidate.id,
                    senderNickname = candidate.nickname,
                    text = "안녕하세요! 프로필 보고 관심사가 잘 맞아서 호감 보냈어요 😊",
                    timestamp = "방금",
                    isFromMe = false
                )
            )
        )

        _chatRooms.update { current ->
            if (current.any { it.peerUser.id == candidate.id }) current
            else listOf(newRoom) + current
        }
        return true
    }

    // 프로필 정보 업데이트 (이름, 소개글, 사진/아바타, 성격, 취미, 선호친구, 특이사항, 공개설정, 나이)
    fun updateProfile(
        newNickname: String,
        newBio: String,
        avatarResId: Int?,
        avatarUri: String?,
        personality: String? = null,
        preferredFriendStyle: String? = null,
        specialNotes: String? = null,
        isAgeVisible: Boolean? = null,
        isGenderVisible: Boolean? = null,
        interests: List<String>? = null,
        age: Int? = null
    ) {
        val filteredNickname = SafetyTextFilter.maskSensitiveContent(newNickname.trim()).filteredText
        val filteredBio = SafetyTextFilter.maskSensitiveContent(newBio.trim()).filteredText
        val currentYear = 2026

        _currentUser.update { current ->
            val updatedAge = age?.coerceIn(0, 100) ?: current.age
            val updatedBirthYear = currentYear - updatedAge
            current.copy(
                nickname = if (filteredNickname.isNotBlank()) filteredNickname else current.nickname,
                bio = filteredBio,
                avatarResId = avatarResId,
                avatarUri = avatarUri,
                personality = personality ?: current.personality,
                preferredFriendStyle = preferredFriendStyle ?: current.preferredFriendStyle,
                specialNotes = specialNotes ?: current.specialNotes,
                isAgeVisible = isAgeVisible ?: current.isAgeVisible,
                isGenderVisible = isGenderVisible ?: current.isGenderVisible,
                interests = interests ?: current.interests,
                age = updatedAge,
                birthYear = updatedBirthYear
            )
        }
        saveUserSession(_currentUser.value)
        val user = _currentUser.value
        val cleanId = user.id.lowercase()
        val sp = prefs
        if (sp != null && sp.contains("acc_pwd_$cleanId")) {
            val pwd = sp.getString("acc_pwd_$cleanId", "") ?: ""
            saveRegisteredAccountLocally(cleanId, pwd, user)
        }
        repositoryScope.launch {
            try {
                firestoreService.saveUserProfile(user)
                firestoreService.updateAccountProfileInCloud(user.id, user)
            } catch (e: Exception) {
                Log.w("AppRepository", "Error syncing updated profile to cloud: ${e.message}")
            }
        }
    }

    /**
     * 개인 프로필 정보를 클라우드 서버에 수동/강제 동기화
     */
    suspend fun syncProfileToServer(): Boolean {
        val user = _currentUser.value
        saveUserSession(user)
        val cleanId = user.id.lowercase()
        val sp = prefs
        if (sp != null && sp.contains("acc_pwd_$cleanId")) {
            val pwd = sp.getString("acc_pwd_$cleanId", "") ?: ""
            saveRegisteredAccountLocally(cleanId, pwd, user)
        }
        val userResult = firestoreService.saveUserProfile(user)
        val accountResult = firestoreService.updateAccountProfileInCloud(user.id, user)
        return userResult || accountResult
    }

    // 피드 위로 당겨서 새로고침 시 데이터 동기화
    fun refreshPosts() {
        repositoryScope.launch {
            try {
                val cloudPosts = firestoreService.fetchPosts()
                if (cloudPosts.isNotEmpty()) {
                    val cleanUserId = _currentUser.value.id.trim().lowercase()
                    val isValidUser = _isLoggedIn.value && cleanUserId.isNotBlank() && cleanUserId != "user_me_anon"
                    val mapped = cloudPosts.map { post ->
                        val isMyPost = isValidUser && post.authorId.isNotBlank() &&
                                !post.authorId.equals("user_me_anon", ignoreCase = true) &&
                                post.authorId.equals(cleanUserId, ignoreCase = true)
                        val mappedComments = post.comments.map { comment ->
                            val isMyComment = isValidUser && comment.authorId.isNotBlank() &&
                                    !comment.authorId.equals("user_me_anon", ignoreCase = true) &&
                                    comment.authorId.equals(cleanUserId, ignoreCase = true)
                            val isAuthor = post.authorId.isNotBlank() && comment.authorId.isNotBlank() &&
                                    comment.authorId.equals(post.authorId, ignoreCase = true)
                            comment.copy(isMyComment = isMyComment, isAuthor = isAuthor)
                        }.distinctBy { it.id }
                        post.copy(
                            isMyPost = isMyPost,
                            comments = mappedComments
                        )
                    }.distinctBy { it.id }
                    _posts.value = mapped
                }
            } catch (e: Exception) {
                Log.w("AppRepository", "Refresh posts error: ${e.message}")
            }
        }
    }

    // 보상형 광고 시청 완료 시 하트 +3 지급
    fun claimRewardedAdReward() {
        _currentUser.update { it.copy(heartBalance = it.heartBalance + 3) }
        saveUserSession(_currentUser.value)
        repositoryScope.launch {
            firestoreService.updateHeartBalance(_currentUser.value.id, _currentUser.value.heartBalance)
        }
    }

    // 프리미엄 기능: 방문자 잠금 해제
    fun unlockVisitor(visitorId: String): Boolean {
        if (_currentUser.value.heartBalance < 2) return false
        _currentUser.update { it.copy(heartBalance = it.heartBalance - 2) }
        saveUserSession(_currentUser.value)
        repositoryScope.launch {
            firestoreService.updateHeartBalance(_currentUser.value.id, _currentUser.value.heartBalance)
        }
        _visitors.update { list ->
            list.map { if (it.id == visitorId) it.copy(isUnlocked = true) else it }
        }
        return true
    }

    // 커뮤니티 투표
    fun voteBalanceGame(postId: String, option: String) {
        vault.updatePostVote(postId, option)
        _posts.update { list ->
            list.map { post ->
                if (post.id == postId && post.balanceGame != null && post.balanceGame.userVotedOption == null) {
                    val bg = post.balanceGame
                    val newBg = if (option == "A") {
                        bg.copy(votesA = bg.votesA + 1, userVotedOption = "A")
                    } else {
                        bg.copy(votesB = bg.votesB + 1, userVotedOption = "B")
                    }
                    post.copy(balanceGame = newBg)
                } else post
            }
        }
        repositoryScope.launch {
            firestoreService.voteBalanceGame(postId, option)
        }
    }

    // 게시글 조회수 증가 (로컬 금고 즉시 저장 및 화면 반영 + 서버 동기화)
    fun incrementPostViews(postId: String) {
        vault.incrementPostViews(postId)
        _posts.update { list ->
            list.map { post ->
                if (post.id == postId) post.copy(viewsCount = post.viewsCount + 1) else post
            }
        }
        repositoryScope.launch {
            firestoreService.incrementPostViews(postId)
        }
    }

    // 새 게시글 작성 (비속어 / 개인정보 자동 마스킹 적용 및 다중 로컬 금고 + 클라우드 서버 자동 영구 저장)
    fun createPost(
        category: PostCategory,
        title: String,
        content: String,
        balanceA: String?,
        balanceB: String?,
        customAuthorTag: String? = null
    ) {
        val filteredTitle = SafetyTextFilter.maskSensitiveContent(title).filteredText
        val filteredContent = SafetyTextFilter.maskSensitiveContent(content).filteredText

        val balanceGame = if (!balanceA.isNullOrBlank() && !balanceB.isNullOrBlank()) {
            BalanceGame(optionA = balanceA, optionB = balanceB, votesA = 0, votesB = 0)
        } else null

        // "000한 글쓰니" 형태의 랜덤 익명 닉네임 생성
        val authorName = customAuthorTag?.takeIf { it.isNotBlank() } ?: AnonymousNameGenerator.generatePostAuthorName()
        val currentUserId = _currentUser.value.id.trim().lowercase()

        val newPost = Post(
            category = category,
            title = filteredTitle,
            content = filteredContent,
            authorTag = authorName,
            timeAgo = "방금 전",
            likesCount = 0,
            viewsCount = 0,
            commentsCount = 0,
            balanceGame = balanceGame,
            isMyPost = true,
            authorId = currentUserId,
            createdAt = System.currentTimeMillis()
        )

        // 1. 다중 로컬 금고에 즉시 100% 영구 저장 (앱 재부팅/재설치 시에도 보존)
        vault.saveUserCreatedPost(newPost)
        _posts.update { list -> (listOf(newPost) + list).distinctBy { it.id } }

        // 2. 클라우드 서버 비동기 전송
        repositoryScope.launch {
            firestoreService.createPost(newPost)
        }
    }

    // 게시글별 내 익명 닉네임 매핑 (글마다 다른 익명 닉 부여, 같은 글에서는 동일 닉네임 유지)
    private val myCommentNicknamesByPost = mutableMapOf<String, String>()

    // 댓글 작성 (자동 마스킹, 작성자 본인은 '000 글쓰니' 표기, 글마다 독립된 익명 닉네임 부여 및 다중 로컬 금고 + 클라우드 저장)
    fun addComment(postId: String, content: String, customAuthorTag: String? = null) {
        val filterResult = SafetyTextFilter.maskSensitiveContent(content)
        val targetPost = _posts.value.firstOrNull { it.id == postId }
        val currentUserId = _currentUser.value.id.trim().lowercase()
        val isLoggedInNow = _isLoggedIn.value && currentUserId.isNotBlank() && currentUserId != "user_me_anon"

        // 글 작성자 본인 여부: targetPost의 authorId와 현재 로그인 사용자의 id가 유효하고 정확히 일치할 때만 true!
        val isAuthor = isLoggedInNow && targetPost != null &&
                targetPost.authorId.isNotBlank() &&
                !targetPost.authorId.equals("user_me_anon", ignoreCase = true) &&
                targetPost.authorId.equals(currentUserId, ignoreCase = true)

        // 닉네임 결정:
        // 1. 글 작성자 본인이 댓글을 달 때는 해당 게시글의 작성자 이름(예: '000 글쓰니')과 동일하게 유지
        // 2. 타인 글인 경우: 한 게시글마다 사용자별 고유한 익명 닉을 새로 정하고, 같은 글에 여러 댓글을 달면 동일한 익명 닉 유지
        val commentKey = "${currentUserId}_$postId"
        val commentAuthor = when {
            isAuthor -> targetPost?.authorTag ?: "글쓰니"
            customAuthorTag?.isNotBlank() == true -> customAuthorTag
            else -> {
                val existingTag = targetPost?.comments?.firstOrNull {
                    it.authorId.isNotBlank() && it.authorId.equals(currentUserId, ignoreCase = true) && !it.isAuthor
                }?.authorTag ?: myCommentNicknamesByPost[commentKey]

                if (existingTag != null) {
                    existingTag
                } else {
                    val newTag = AnonymousNameGenerator.generateCommentAuthorName(isAuthor = false)
                    myCommentNicknamesByPost[commentKey] = newTag
                    newTag
                }
            }
        }

        val newComment = Comment(
            postId = postId,
            authorTag = commentAuthor,
            content = filterResult.filteredText,
            timeAgo = "방금 전",
            isAuthor = isAuthor,
            isMyComment = isLoggedInNow,
            authorId = currentUserId,
            createdAt = System.currentTimeMillis()
        )

        // 1. 다중 로컬 금고에 영구 저장 (중복 방지 및 댓글 수 정확 갱신)
        vault.addLocalComment(postId, newComment)
        _posts.update { list ->
            list.map { post ->
                if (post.id == postId) {
                    val distinctComments = (post.comments + newComment).distinctBy { it.id }
                    post.copy(
                        commentsCount = distinctComments.size,
                        comments = distinctComments
                    )
                } else post
            }
        }

        // 2. 클라우드 서버 저장
        repositoryScope.launch {
            firestoreService.addComment(postId, newComment)
        }
    }

    // 내가 쓴 익명 게시글 삭제
    fun deletePost(postId: String): Boolean {
        val currentUserId = _currentUser.value.id.trim().lowercase()
        val target = _posts.value.firstOrNull { it.id == postId } ?: return false
        // 작성자 본인만 삭제 가능하도록 엄격 검증
        if (!_isLoggedIn.value || currentUserId.isBlank() || currentUserId == "user_me_anon" ||
            !target.authorId.equals(currentUserId, ignoreCase = true)) {
            Log.w("AppRepository", "Unauthorized deletePost attempt by $currentUserId for post author ${target.authorId}")
            return false
        }
        vault.deleteLocalPost(postId)
        _posts.update { list ->
            list.filterNot { it.id == postId }
        }
        repositoryScope.launch {
            firestoreService.deletePost(postId)
        }
        return true
    }

    // 내가 쓴 익명 댓글 삭제
    fun deleteComment(postId: String, commentId: String): Boolean {
        val currentUserId = _currentUser.value.id.trim().lowercase()
        val targetPost = _posts.value.firstOrNull { it.id == postId } ?: return false
        val targetComment = targetPost.comments.firstOrNull { it.id == commentId } ?: return false
        // 작성자 본인만 댓글 삭제 가능하도록 엄격 검증
        if (!_isLoggedIn.value || currentUserId.isBlank() || currentUserId == "user_me_anon" ||
            !targetComment.authorId.equals(currentUserId, ignoreCase = true)) {
            Log.w("AppRepository", "Unauthorized deleteComment attempt")
            return false
        }
        _posts.update { list ->
            list.map { post ->
                if (post.id == postId) {
                    val updatedComments = post.comments.filterNot { it.id == commentId }
                    post.copy(
                        comments = updatedComments,
                        commentsCount = updatedComments.size
                    )
                } else post
            }
        }
        repositoryScope.launch {
            firestoreService.deleteComment(postId, commentId)
        }
        return true
    }

    // 채팅 메시지 전송 (실시간 마스킹 처리)
    fun sendMessage(roomId: String, text: String) {
        val filterResult = SafetyTextFilter.maskSensitiveContent(text)
        val msg = ChatMessage(
            senderId = _currentUser.value.id,
            senderNickname = _currentUser.value.nickname,
            text = filterResult.filteredText,
            originalText = text,
            isFiltered = filterResult.hasViolation,
            timestamp = "방금",
            isFromMe = true
        )

        _chatRooms.update { rooms ->
            rooms.map { room ->
                if (room.id == roomId) {
                    room.copy(
                        lastMessage = msg.text,
                        lastTime = "방금",
                        messages = room.messages + msg
                    )
                } else room
            }
        }
    }

    // 채팅방 나가기
    fun leaveChatRoom(roomId: String) {
        _chatRooms.update { rooms -> rooms.filterNot { it.id == roomId } }
    }

    // 채팅방 및 상대방 신고
    fun reportChatRoom(roomId: String, reason: String, shouldBlock: Boolean) {
        val targetRoom = _chatRooms.value.find { it.id == roomId }
        val peerId = targetRoom?.peerUser?.id ?: ""
        val peerNick = targetRoom?.peerUser?.nickname ?: "익명회원"

        repositoryScope.launch {
            firestoreService.submitReport(
                type = "CHAT",
                targetId = roomId,
                reporterId = _currentUser.value.id,
                reason = reason,
                extraInfo = mapOf(
                    "peerUserId" to peerId,
                    "peerNickname" to peerNick
                )
            )
        }

        if (shouldBlock && peerId.isNotBlank()) {
            blockUser(peerId)
        }
        leaveChatRoom(roomId)
    }

    // 게시글 신고
    fun reportPost(postId: String, reason: String, shouldBlockAuthor: Boolean = false) {
        val targetPost = _posts.value.find { it.id == postId }
        val authorId = targetPost?.authorId ?: ""
        val postTitle = targetPost?.title ?: ""

        // 신고된 게시글은 피드에서 즉시 숨김
        _posts.update { list -> list.filterNot { it.id == postId } }

        if (shouldBlockAuthor && authorId.isNotBlank()) {
            blockUser(authorId)
        }

        repositoryScope.launch {
            firestoreService.submitReport(
                type = "POST",
                targetId = postId,
                reporterId = _currentUser.value.id,
                reason = reason,
                extraInfo = mapOf(
                    "authorId" to authorId,
                    "postTitle" to postTitle
                )
            )
        }
    }

    // 댓글 신고
    fun reportComment(postId: String, commentId: String, reason: String, shouldBlockAuthor: Boolean = false) {
        val targetPost = _posts.value.find { it.id == postId }
        val targetComment = targetPost?.comments?.find { it.id == commentId }
        val authorId = targetComment?.authorId ?: ""
        val commentText = targetComment?.content ?: ""

        // 신고된 댓글은 즉시 숨김
        _posts.update { list ->
            list.map { post ->
                if (post.id == postId) {
                    val remainingComments = post.comments.filterNot { it.id == commentId }
                    post.copy(
                        comments = remainingComments,
                        commentsCount = remainingComments.size
                    )
                } else post
            }
        }

        if (shouldBlockAuthor && authorId.isNotBlank()) {
            blockUser(authorId)
        }

        repositoryScope.launch {
            firestoreService.submitReport(
                type = "COMMENT",
                targetId = commentId,
                reporterId = _currentUser.value.id,
                reason = reason,
                extraInfo = mapOf(
                    "postId" to postId,
                    "authorId" to authorId,
                    "commentContent" to commentText
                )
            )
        }
    }

    private fun seedInitialPosts() {
        // 로컬 영구 금고에서 기본 제공 및 저장된 글 목록 로드 (앱 재부팅/재설치 시에도 즉시 표시)
        val initialPosts = vault.getLocalPosts().distinctBy { it.id }
        val currentId = _currentUser.value.id.trim().lowercase()
        val isValidUser = _isLoggedIn.value && currentId.isNotBlank() && currentId != "user_me_anon"
        val evaluated = initialPosts.map { post ->
            val isMyPost = isValidUser && post.authorId.isNotBlank() &&
                    !post.authorId.equals("user_me_anon", ignoreCase = true) &&
                    post.authorId.equals(currentId, ignoreCase = true)
            val mappedComments = post.comments.map { comment ->
                val isMyComment = isValidUser && comment.authorId.isNotBlank() &&
                        !comment.authorId.equals("user_me_anon", ignoreCase = true) &&
                        comment.authorId.equals(currentId, ignoreCase = true)
                val isAuthor = post.authorId.isNotBlank() && comment.authorId.isNotBlank() &&
                        comment.authorId.equals(post.authorId, ignoreCase = true)
                comment.copy(isMyComment = isMyComment, isAuthor = isAuthor)
            }.distinctBy { it.id }
            post.copy(isMyPost = isMyPost, comments = mappedComments)
        }.distinctBy { it.id }
        _posts.value = evaluated
    }

    private fun seedInitialChats() {
        _chatRooms.value = emptyList()
    }

    private fun seedInitialVisitors() {
        _visitors.value = listOf(
            VisitorInfo(
                id = "vis_1",
                maskedNickname = "러닝***",
                age = 25,
                interests = listOf("러닝", "헬스"),
                visitedTimeAgo = "10분 전",
                isUnlocked = false
            ),
            VisitorInfo(
                id = "vis_2",
                maskedNickname = "재즈***",
                age = 23,
                interests = listOf("재즈", "전시회"),
                visitedTimeAgo = "1시간 전",
                isUnlocked = true
            ),
            VisitorInfo(
                id = "vis_3",
                maskedNickname = "캠핑***",
                age = 27,
                interests = listOf("캠핑", "드라이브"),
                visitedTimeAgo = "3시간 전",
                isUnlocked = false
            )
        )
    }

    // ==========================================
    // ID 기반 회원가입 & 로그인 지원 (중복 방지 및 영구 저장)
    // ==========================================

    enum class IdAvailabilityResult {
        AVAILABLE,       // 사용 가능
        ALREADY_TAKEN,   // 이미 사용 중 (중복)
        INVALID_FORMAT   // 형식 오류 (3자 미만 등)
    }

    fun getLastSavedId(): String {
        val vaultLast = vault.getLastUsedId()
        if (vaultLast.isNotBlank()) return vaultLast
        return prefs?.getString("last_used_id", "") ?: ""
    }

    fun getAllSavedIds(): List<String> {
        val vaultIds = vault.getAllSavedIds()
        if (vaultIds.isNotEmpty()) return vaultIds
        val sp = prefs ?: return emptyList()
        val set = sp.getStringSet("all_saved_ids_set", emptySet()) ?: emptySet()
        val last = sp.getString("last_used_id", "") ?: ""
        val list = set.toMutableList()
        if (last.isNotBlank() && !list.contains(last)) {
            list.add(0, last)
        }
        return list.filter { it.isNotBlank() }
    }

    fun isRememberIdEnabled(): Boolean {
        return prefs?.getBoolean("remember_id_setting", true) ?: true
    }

    fun saveRememberedId(id: String, remember: Boolean) {
        val clean = id.trim().lowercase()
        val sp = prefs ?: return
        val set = sp.getStringSet("all_saved_ids_set", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (remember) {
            if (clean.isNotBlank()) {
                set.add(clean)
                sp.edit()
                    .putString("last_used_id", clean)
                    .putStringSet("all_saved_ids_set", set)
                    .putBoolean("remember_id_setting", true)
                    .commit()
            }
        } else {
            sp.edit()
                .putBoolean("remember_id_setting", false)
                .commit()
        }
    }

    fun isAccountRegisteredOnDevice(id: String): Boolean {
        val clean = id.trim().lowercase()
        return vault.getAccount(clean) != null || prefs?.contains("acc_pwd_$clean") == true
    }

    /**
     * 아이디 중복 여부 실시간 확인 (로컬 영구 금고 + SharedPreferences + Firestore 클라우드 통합 검사)
     */
    suspend fun checkIdAvailability(id: String): IdAvailabilityResult {
        val cleanId = id.trim().lowercase()
        if (cleanId.length < 3) return IdAvailabilityResult.INVALID_FORMAT
        
        // 1. 로컬 영구 금고 및 SharedPreferences 중복 확인
        if (vault.getAccount(cleanId) != null) {
            return IdAvailabilityResult.ALREADY_TAKEN
        }
        val sp = prefs
        if (sp != null && sp.contains("acc_pwd_$cleanId")) {
            return IdAvailabilityResult.ALREADY_TAKEN
        }

        // 2. 클라우드 Firestore 중복 확인 (다른 사용자 및 전체 등록 계정 대상, 빠른 타임아웃 적용)
        val takenInCloud = try {
            withTimeoutOrNull(1500) {
                firestoreService.isIdTakenInCloud(cleanId)
            } ?: false
        } catch (e: Exception) {
            false
        }

        if (takenInCloud) {
            return IdAvailabilityResult.ALREADY_TAKEN
        }

        return IdAvailabilityResult.AVAILABLE
    }

    private fun saveRegisteredAccountLocally(cleanId: String, password: String, profile: UserProfile) {
        // 1. 다중 로컬 영구 금고(파일 백업 포함)에 즉시 저장
        vault.saveAccount(cleanId, password, profile)

        // 2. SharedPreferences 동기화
        val sp = prefs ?: return
        val set = sp.getStringSet("all_saved_ids_set", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(cleanId)
        sp.edit()
            .putString("acc_pwd_$cleanId", password)
            .putString("acc_nick_$cleanId", profile.nickname)
            .putString("acc_gender_$cleanId", profile.gender)
            .putInt("acc_birth_$cleanId", profile.birthYear)
            .putInt("acc_age_$cleanId", profile.age)
            .putString("acc_bio_$cleanId", profile.bio)
            .putString("acc_personality_$cleanId", profile.personality)
            .putString("acc_interests_$cleanId", profile.interests.joinToString(","))
            .putString("acc_friend_style_$cleanId", profile.preferredFriendStyle)
            .putString("acc_special_notes_$cleanId", profile.specialNotes)
            .putBoolean("acc_age_visible_$cleanId", profile.isAgeVisible)
            .putBoolean("acc_gender_visible_$cleanId", profile.isGenderVisible)
            .putInt("acc_avatar_res_$cleanId", profile.avatarResId ?: 0)
            .putString("acc_avatar_uri_$cleanId", profile.avatarUri)
            .putInt("acc_hearts_$cleanId", profile.heartBalance)
            .putInt("acc_views_$cleanId", profile.profileViews)
            .putBoolean("reg_id_$cleanId", true)
            .putString("last_used_id", cleanId)
            .putStringSet("all_saved_ids_set", set)
            .commit()
    }

    /**
     * 신규 아이디 회원가입 및 즉시 계정 영구 저장 후 즉시 로그인 전환
     */
    suspend fun registerWithId(
        id: String,
        password: String,
        nickname: String,
        gender: String = "여성",
        birthYear: Int = 2002,
        bio: String = "",
        personality: String = "다정하고 긍정적인",
        interests: List<String> = listOf("일상", "소통", "음악"),
        preferredFriendStyle: String = "편하게 일상 나눌 친구",
        specialNotes: String = "",
        isAgeVisible: Boolean = true,
        isGenderVisible: Boolean = true,
        avatarResId: Int? = R.drawable.anon_avatar_demo_1789521505031,
        avatarUri: String? = null,
        age: Int? = null
    ): Result<UserProfile> {
        val cleanId = id.trim().lowercase()
        if (cleanId.length < 3) {
            return Result.failure(IllegalArgumentException("아이디는 영문/숫자 3자 이상이어야 합니다."))
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("비밀번호는 4자 이상이어야 합니다."))
        }
        val cleanNick = nickname.trim().ifBlank { cleanId }
        val sp = prefs ?: return Result.failure(IllegalStateException("저장소를 불러올 수 없습니다."))

        val currentYear = 2026
        val finalAge = (age ?: ((currentYear - birthYear) + 1)).coerceIn(0, 100)
        val finalBirthYear = currentYear - finalAge

        // 1. 이미 동일한 아이디가 로컬 영구 금고 또는 SharedPreferences에 등록된 경우
        val existingAccount = vault.getAccount(cleanId)
        val hasLocalAccount = existingAccount != null || (sp.contains("acc_pwd_$cleanId"))
        if (hasLocalAccount) {
            val savedPwd = existingAccount?.first ?: sp.getString("acc_pwd_$cleanId", "")
            if (savedPwd == password) {
                // 동일한 비밀번호로 다시 가입 시도한 경우 -> 최신 프로필 정보로 업데이트하고 즉시 로그인 성공 처리!
                val defaultBio = if (bio.isNotBlank()) bio else "안녕하세요! $cleanNick 입니다 ✨"
                val profile = UserProfile(
                    id = cleanId,
                    nickname = cleanNick,
                    age = finalAge,
                    birthYear = finalBirthYear,
                    gender = gender,
                    bio = defaultBio,
                    mbti = "INFP",
                    photoUrls = emptyList(),
                    interests = if (interests.isNotEmpty()) interests else listOf("일상", "소통", "음악"),
                    personality = personality,
                    preferredFriendStyle = preferredFriendStyle,
                    specialNotes = specialNotes,
                    isAgeVisible = isAgeVisible,
                    isGenderVisible = isGenderVisible,
                    location = "서울",
                    heartBalance = sp.getInt("acc_hearts_$cleanId", 20),
                    profileViews = sp.getInt("acc_views_$cleanId", 0),
                    isVerified = true,
                    googleEmail = "$cleanId@anon.local",
                    phoneNumber = "",
                    avatarResId = avatarResId,
                    avatarUri = avatarUri
                )
                saveRegisteredAccountLocally(cleanId, password, profile)
                _currentUser.value = profile
                _isLoggedIn.value = true
                saveUserSession(profile)
                subscribeToCurrentUserProfile()
                repositoryScope.launch {
                    try {
                        firestoreService.saveAccountInCloud(cleanId, password, profile)
                    } catch (e: Exception) {
                        Log.w("AppRepository", "Cloud save notice: ${e.message}")
                    }
                }
                return Result.success(profile)
            } else {
                return Result.failure(IllegalArgumentException("이미 등록된 아이디입니다. 비밀번호를 확인하시거나 '로그인' 탭에서 로그인해 주세요."))
            }
        }

        val takenInCloud = try {
            withTimeoutOrNull(3500) {
                firestoreService.isIdTakenInCloud(cleanId)
            } ?: false
        } catch (e: Exception) {
            false
        }

        if (takenInCloud) {
            return Result.failure(IllegalArgumentException("이미 사용 중인 아이디입니다. 다른 아이디를 입력하거나 로그인해 주세요."))
        }

        val defaultBio = if (bio.isNotBlank()) bio else "안녕하세요! $cleanNick 입니다 ✨"

        val profile = UserProfile(
            id = cleanId,
            nickname = cleanNick,
            age = finalAge,
            birthYear = finalBirthYear,
            gender = gender,
            bio = defaultBio,
            mbti = "INFP",
            photoUrls = emptyList(),
            interests = if (interests.isNotEmpty()) interests else listOf("일상", "소통", "음악"),
            personality = personality,
            preferredFriendStyle = preferredFriendStyle,
            specialNotes = specialNotes,
            isAgeVisible = isAgeVisible,
            isGenderVisible = isGenderVisible,
            location = "서울",
            heartBalance = 20,
            profileViews = 0,
            isVerified = true,
            googleEmail = "$cleanId@anon.local",
            phoneNumber = "",
            avatarResId = avatarResId,
            avatarUri = avatarUri
        )

        // 2. 로컬 디스크 및 다중 영구 금고에 즉시 동기식 영구 저장
        saveRegisteredAccountLocally(cleanId, password, profile)

        // 3. 로컬 로그인 상태 및 세션 즉시 활성화 (사용자가 기다릴 필요 없이 즉각 로그인)
        _currentUser.value = profile
        _isLoggedIn.value = true
        _chatRooms.value = emptyList()
        saveUserSession(profile)
        subscribeToCurrentUserProfile()

        // 4. Firestore 클라우드 영구 저장은 비동기 병렬 실행
        repositoryScope.launch {
            try {
                firestoreService.saveAccountInCloud(cleanId, password, profile)
            } catch (e: Exception) {
                Log.w("AppRepository", "Cloud save account notice: ${e.message}")
            }
        }

        return Result.success(profile)
    }

    /**
     * 아이디와 비밀번호로 로그인 (로컬 영구 금고 우선 확인 후 클라우드 자동 복원)
     */
    suspend fun loginWithId(id: String, password: String): Result<UserProfile> {
        val cleanId = id.trim().lowercase()
        if (cleanId.isBlank()) {
            return Result.failure(IllegalArgumentException("아이디를 입력해주세요."))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("비밀번호를 입력해주세요."))
        }

        // 1. 다중 로컬 영구 금고 (PersistentVault) 최우선 확인 (오프라인/재설치 시에도 즉각 복원)
        val vaultAcc = vault.getAccount(cleanId)
        if (vaultAcc != null) {
            if (vaultAcc.first != password) {
                return Result.failure(IllegalArgumentException("비밀번호가 일치하지 않습니다."))
            }
            val profile = vaultAcc.second
            _currentUser.value = profile
            _isLoggedIn.value = true
            _chatRooms.value = emptyList()
            saveUserSession(profile)
            saveRememberedId(cleanId, true)
            subscribeToCurrentUserProfile()
            return Result.success(profile)
        }

        val sp = prefs

        // 2. SharedPreferences 확인 (레거시 캐시)
        if (sp != null && sp.contains("acc_pwd_$cleanId")) {
            val savedPwd = sp.getString("acc_pwd_$cleanId", "")
            if (savedPwd != password) {
                return Result.failure(IllegalArgumentException("비밀번호가 일치하지 않습니다."))
            }

            val currentYear = 2026
            val birthYear = sp.getInt("acc_birth_$cleanId", 2002)
            val age = if (sp.contains("acc_age_$cleanId")) {
                sp.getInt("acc_age_$cleanId", 24).coerceIn(0, 100)
            } else {
                ((currentYear - birthYear) + 1).coerceIn(0, 100)
            }
            val nickname = sp.getString("acc_nick_$cleanId", cleanId) ?: cleanId
            val gender = sp.getString("acc_gender_$cleanId", "여성") ?: "여성"
            val bio = sp.getString("acc_bio_$cleanId", "안녕하세요! $nickname 입니다 ✨") ?: ""
            val personality = sp.getString("acc_personality_$cleanId", "다정하고 긍정적인") ?: "다정하고 긍정적인"
            val friendStyle = sp.getString("acc_friend_style_$cleanId", "편하게 일상 나눌 친구") ?: "편하게 일상 나눌 친구"
            val specialNotes = sp.getString("acc_special_notes_$cleanId", "") ?: ""
            val isAgeVisible = sp.getBoolean("acc_age_visible_$cleanId", true)
            val isGenderVisible = sp.getBoolean("acc_gender_visible_$cleanId", true)
            val avatarResId = sp.getInt("acc_avatar_res_$cleanId", R.drawable.anon_avatar_demo_1789521505031).let { if (it == 0) null else it }
            val avatarUri = sp.getString("acc_avatar_uri_$cleanId", null)
            val interestsStr = sp.getString("acc_interests_$cleanId", "일상,소통,음악") ?: "일상,소통,음악"
            val interests = interestsStr.split(",").filter { it.isNotBlank() }

            val profile = UserProfile(
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
                heartBalance = sp.getInt("acc_hearts_$cleanId", 20),
                profileViews = sp.getInt("acc_views_$cleanId", 0),
                isVerified = true,
                googleEmail = "$cleanId@anon.local",
                phoneNumber = "",
                avatarResId = avatarResId,
                avatarUri = avatarUri
            )

            saveRegisteredAccountLocally(cleanId, password, profile)
            _currentUser.value = profile
            _isLoggedIn.value = true
            _chatRooms.value = emptyList()
            saveUserSession(profile)
            saveRememberedId(cleanId, true)
            subscribeToCurrentUserProfile()
            return Result.success(profile)
        }

        // 3. 로컬에 없는 경우 클라우드 조회하여 복원
        val cloudAccount = try {
            withTimeoutOrNull(4500) {
                firestoreService.getAccountFromCloud(cleanId)
            }
        } catch (e: Exception) {
            null
        }

        if (cloudAccount != null) {
            val cloudPwd = cloudAccount["password"] as? String ?: ""
            if (cloudPwd != password) {
                return Result.failure(IllegalArgumentException("비밀번호가 일치하지 않습니다."))
            }

            val nickname = cloudAccount["nickname"] as? String ?: cleanId
            val gender = cloudAccount["gender"] as? String ?: "여성"
            val birthYear = (cloudAccount["birthYear"] as? Number)?.toInt() ?: 2002
            val currentYear = 2026
            val age = ((cloudAccount["age"] as? Number)?.toInt() ?: ((currentYear - birthYear) + 1)).coerceIn(0, 100)
            val bio = cloudAccount["bio"] as? String ?: ""
            val avatarResId = (cloudAccount["avatarResId"] as? Number)?.toInt().let { if (it == 0 || it == null) null else it }
            val avatarUri = cloudAccount["avatarUri"] as? String
            val interests = (cloudAccount["interests"] as? List<String>) ?: listOf("일상", "소통", "음악")
            val personality = cloudAccount["personality"] as? String ?: "다정하고 긍정적인"
            val friendStyle = cloudAccount["preferredFriendStyle"] as? String ?: "편하게 일상 나눌 친구"
            val specialNotes = cloudAccount["specialNotes"] as? String ?: ""
            val isAgeVisible = cloudAccount["isAgeVisible"] as? Boolean ?: true
            val isGenderVisible = cloudAccount["isGenderVisible"] as? Boolean ?: true

            val profile = UserProfile(
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
                heartBalance = 20,
                profileViews = 0,
                isVerified = true,
                googleEmail = "$cleanId@anon.local",
                phoneNumber = "",
                avatarResId = avatarResId,
                avatarUri = avatarUri
            )

            // 로컬 디스크 및 영구 금고에 동기화
            saveRegisteredAccountLocally(cleanId, password, profile)

            _currentUser.value = profile
            _isLoggedIn.value = true
            _chatRooms.value = emptyList()
            saveUserSession(profile)
            saveRememberedId(cleanId, true)
            subscribeToCurrentUserProfile()
            return Result.success(profile)
        }

        return Result.failure(IllegalArgumentException("등록되지 않은 아이디입니다. '아이디 만들기' 탭에서 먼저 가입해 주세요."))
    }

    // Google 계정 + 휴대폰 문자 인증을 통한 회원가입 및 로그인 (첫 가입 프로필 세션 데이터 반영)
    fun loginWithGoogleAndPhone(
        googleEmail: String,
        phone: String,
        nickname: String,
        birthYear: Int,
        gender: String,
        interests: List<String>,
        bio: String = "",
        avatarResId: Int? = null,
        avatarUri: String? = null
    ) {
        val currentYear = 2026
        val calculatedAge = ((currentYear - birthYear) + 1).coerceIn(0, 100)

        val defaultBio = "안녕하세요! $nickname 입니다. 좋은 인연과 솔직한 이야기 나눠요 ✨"

        val newUser = UserProfile(
            id = "user_${System.currentTimeMillis()}",
            nickname = nickname.ifBlank { "익명유저" },
            age = calculatedAge,
            birthYear = birthYear,
            gender = gender,
            bio = if (bio.isNotBlank()) bio else defaultBio,
            mbti = "INFP",
            photoUrls = emptyList(),
            interests = if (interests.isNotEmpty()) interests else listOf("일상", "소통", "음악"),
            location = "서울",
            heartBalance = 15,
            profileViews = 0,
            isVerified = true,
            googleEmail = googleEmail,
            phoneNumber = phone,
            avatarResId = avatarResId,
            avatarUri = avatarUri
        )
        _currentUser.value = newUser
        _isLoggedIn.value = true
        saveUserSession(newUser)
    }

    // 데모 빠른 로그인 (원클릭 체험용)
    fun quickDemoLogin(isAdult: Boolean = true) {
        _currentUser.value = defaultDemoUser
        _isLoggedIn.value = true
        saveUserSession(defaultDemoUser)
    }

    // 로그아웃
    fun logout() {
        _isLoggedIn.value = false
        _currentUser.value = defaultDemoUser
        _chatRooms.value = emptyList()
        userProfileJob?.cancel()
        accountObservationJob?.cancel()
        myCommentNicknamesByPost.clear()
        prefs?.edit()?.putBoolean("is_logged_in", false)?.apply()
        vault.logoutSession()
        reevaluatePostsOwnership("")
    }

    // 회원 탈퇴: 계정 및 프로필, 게시글, 세션, 저장된 아이디 완전 삭제
    fun deleteAccount() {
        val currentId = _currentUser.value.id.trim().lowercase()
        _isLoggedIn.value = false
        _currentUser.value = defaultDemoUser
        _chatRooms.value = emptyList()
        userProfileJob?.cancel()
        accountObservationJob?.cancel()
        myCommentNicknamesByPost.clear()

        // 1. 다중 로컬 영구 금고에서 계정, 비밀번호, 세션, 작성글 완전 삭제
        if (currentId.isNotBlank()) {
            vault.deleteAccount(currentId)
        }

        // 2. SharedPreferences에서 계정 및 로그인 세션 완전 삭제
        val sp = prefs
        if (sp != null) {
            val editor = sp.edit()
            if (currentId.isNotBlank()) {
                val set = sp.getStringSet("all_saved_ids_set", emptySet())?.toMutableSet() ?: mutableSetOf()
                set.remove(currentId)
                editor.putStringSet("all_saved_ids_set", set)
                val lastUsed = sp.getString("last_used_id", "") ?: ""
                if (lastUsed.equals(currentId, ignoreCase = true)) {
                    editor.putString("last_used_id", set.firstOrNull() ?: "")
                }
                editor.remove("acc_pwd_$currentId")
                editor.remove("acc_nick_$currentId")
                editor.remove("acc_gender_$currentId")
                editor.remove("acc_birth_$currentId")
                editor.remove("acc_age_$currentId")
                editor.remove("acc_bio_$currentId")
                editor.remove("acc_personality_$currentId")
                editor.remove("acc_interests_$currentId")
                editor.remove("acc_friend_style_$currentId")
                editor.remove("acc_special_notes_$currentId")
                editor.remove("acc_age_visible_$currentId")
                editor.remove("acc_gender_visible_$currentId")
                editor.remove("acc_avatar_res_$currentId")
                editor.remove("acc_avatar_uri_$currentId")
                editor.remove("acc_hearts_$currentId")
                editor.remove("acc_views_$currentId")
                editor.remove("reg_id_$currentId")
            }
            editor.putBoolean("is_logged_in", false)
            editor.remove("user_id")
            editor.remove("user_nickname")
            editor.remove("user_age")
            editor.remove("user_birth_year")
            editor.remove("user_gender")
            editor.remove("user_bio")
            editor.remove("user_mbti")
            editor.remove("user_interests")
            editor.remove("user_google_email")
            editor.remove("user_phone")
            editor.remove("user_avatar_res_id")
            editor.remove("user_avatar_uri")
            editor.remove("user_heart_balance")
            editor.remove("user_profile_views")
            editor.remove("user_personality")
            editor.remove("user_preferred_friend_style")
            editor.remove("user_special_notes")
            editor.remove("user_is_age_visible")
            editor.remove("user_is_gender_visible")
            editor.commit()
        }

        // 3. 내가 작성한 피드 글 즉시 제거 및 남은 글들의 소유권 해제
        _posts.update { list ->
            list.filterNot { currentId.isNotBlank() && it.authorId.equals(currentId, ignoreCase = true) }
                .map { it.copy(isMyPost = false, comments = it.comments.map { c -> c.copy(isMyComment = false, isAuthor = false) }) }
        }
        vault.logoutSession()

        // 4. 클라우드 서버(Firestore 및 REST 동기화 서버)에서 계정 영구 삭제
        if (currentId.isNotBlank()) {
            repositoryScope.launch {
                try {
                    firestoreService.deleteAccountFromCloud(currentId)
                } catch (e: Exception) {
                    Log.w("AppRepository", "Failed to delete account from cloud: ${e.message}")
                }
            }
        }
    }

    // 저장된 아이디 목록에서 특정 아이디만 제거하는 함수
    fun removeSavedId(id: String) {
        val clean = id.trim().lowercase()
        vault.removeSavedId(clean)
        val sp = prefs ?: return
        val set = sp.getStringSet("all_saved_ids_set", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.remove(clean)
        val lastUsed = sp.getString("last_used_id", "") ?: ""
        val newLast = if (lastUsed.equals(clean, ignoreCase = true)) set.firstOrNull() ?: "" else lastUsed
        sp.edit()
            .putStringSet("all_saved_ids_set", set)
            .putString("last_used_id", newLast)
            .commit()
    }

    // 설정 변경
    fun updatePushNotifications(enabled: Boolean) {
        _settings.update { it.copy(pushNotificationsEnabled = enabled) }
    }

    fun updateMatchAlerts(enabled: Boolean) {
        _settings.update { it.copy(matchAlertsEnabled = enabled) }
    }

    fun updateChatAlerts(enabled: Boolean) {
        _settings.update { it.copy(chatAlertsEnabled = enabled) }
    }

    fun updateCommunityCommentAlerts(enabled: Boolean) {
        _settings.update { it.copy(communityCommentAlertsEnabled = enabled) }
    }

    fun updateMarketingAlerts(enabled: Boolean) {
        _settings.update { it.copy(marketingAlertsEnabled = enabled) }
    }

    fun toggleSafetyFilter() {
        _settings.update { it.copy(safetyFilterActive = !it.safetyFilterActive) }
    }
}
