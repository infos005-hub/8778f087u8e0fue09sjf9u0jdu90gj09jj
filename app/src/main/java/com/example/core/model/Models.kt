package com.example.core.model

import java.util.UUID

enum class AgeGroup(val label: String, val badgeColorHex: Long) {
    ADULT("성인 (19세 이상)", 0xFFA855F7),
    MINOR("미성년자 (18세 이하)", 0xFF38BDF8)
}

data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val nickname: String,
    val age: Int,
    val birthYear: Int,
    val gender: String, // "남성", "여성"
    val bio: String,
    val mbti: String,
    val photoUrls: List<String>,
    val interests: List<String>,
    val location: String = "서울 마포구",
    val heartBalance: Int = 12,
    val profileViews: Int = 34,
    val isVerified: Boolean = true,
    val googleEmail: String = "user@gmail.com",
    val phoneNumber: String = "010-8765-4321",
    val avatarResId: Int? = null,
    val avatarUri: String? = null,
    val personality: String = "다정하고 긍정적인",
    val preferredFriendStyle: String = "편하게 일상 나눌 친구",
    val specialNotes: String = "",
    val isAgeVisible: Boolean = true,
    val isGenderVisible: Boolean = true
) {
    val isAdult: Boolean get() = age >= 19
    val ageGroup: AgeGroup get() = if (isAdult) AgeGroup.ADULT else AgeGroup.MINOR
    val displayAge: String get() = if (isAgeVisible) "${age}세" else "비공개"
    val displayGender: String get() = if (isGenderVisible) gender else "비공개"
}

data class AppSettings(
    val pushNotificationsEnabled: Boolean = true,
    val matchAlertsEnabled: Boolean = true,
    val chatAlertsEnabled: Boolean = true,
    val communityCommentAlertsEnabled: Boolean = true,
    val marketingAlertsEnabled: Boolean = false,
    val safetyFilterActive: Boolean = true,
    val blockedUserCount: Int = 0
)

enum class PostCategory(val label: String) {
    ALL("전체"),
    BALANCE_GAME("⚖️ 밸런스 게임"),
    WORRIES("💬 고민/익명톡"),
    DAILY("✨ 일상/취미")
}

data class BalanceGame(
    val optionA: String,
    val optionB: String,
    val votesA: Int,
    val votesB: Int,
    val userVotedOption: String? = null // "A" or "B" or null
) {
    val totalVotes: Int get() = votesA + votesB
    val percentageA: Int get() = if (totalVotes == 0) 50 else ((votesA.toFloat() / totalVotes) * 100).toInt()
    val percentageB: Int get() = if (totalVotes == 0) 50 else 100 - percentageA
}

data class Comment(
    val id: String = UUID.randomUUID().toString(),
    val postId: String,
    val authorTag: String,
    val content: String,
    val likesCount: Int = 0,
    val timeAgo: String = "방금 전",
    val isAuthor: Boolean = false,
    val isMyComment: Boolean = false,
    val authorId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class Post(
    val id: String = UUID.randomUUID().toString(),
    val category: PostCategory,
    val title: String,
    val content: String,
    val authorTag: String,
    val timeAgo: String,
    val likesCount: Int,
    val viewsCount: Int = 0,
    val commentsCount: Int,
    val balanceGame: BalanceGame? = null,
    val tags: List<String> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val isLiked: Boolean = false,
    val isMyPost: Boolean = false,
    val authorId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderNickname: String,
    val text: String,
    val originalText: String = text,
    val isFiltered: Boolean = false,
    val timestamp: String = "18:20",
    val isFromMe: Boolean = false
)

data class ChatRoom(
    val id: String = UUID.randomUUID().toString(),
    val peerUser: UserProfile,
    val lastMessage: String,
    val lastTime: String,
    val unreadCount: Int = 0,
    val messages: List<ChatMessage> = emptyList()
)

data class VisitorInfo(
    val id: String = UUID.randomUUID().toString(),
    val maskedNickname: String, // e.g. "러닝***"
    val age: Int,
    val interests: List<String>,
    val visitedTimeAgo: String,
    val isUnlocked: Boolean = false // 프리미엄 인앱 결제 해금 여부
)
