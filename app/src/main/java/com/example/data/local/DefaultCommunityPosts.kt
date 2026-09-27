package com.example.data.local

import com.example.R
import com.example.core.model.BalanceGame
import com.example.core.model.Comment
import com.example.core.model.Post
import com.example.core.model.PostCategory

object DefaultCommunityPosts {

    fun getDefaultPosts(): List<Post> {
        val now = System.currentTimeMillis()
        val oneHour = 3600 * 1000L
        val oneDay = 24 * 3600 * 1000L

        return listOf(
            Post(
                id = "post_seed_balance_1",
                category = PostCategory.BALANCE_GAME,
                title = "주 4일제 월 280 vs 주 5일제 월 430",
                content = "다들 워라밸이랑 연봉 중에 어떤 걸 더 중요하게 생각하시나요? 요즘 퇴근하고 나면 체력이 방전돼서 고민인데 여러분 선택은?!",
                authorTag = "칼퇴꿈나무",
                timeAgo = "10분 전",
                likesCount = 42,
                viewsCount = 380,
                commentsCount = 3,
                balanceGame = BalanceGame(
                    optionA = "주 4일제 (월 280만)",
                    optionB = "주 5일제 (월 430만)",
                    votesA = 68,
                    votesB = 32,
                    userVotedOption = null
                ),
                comments = listOf(
                    Comment(
                        id = "c_seed_1",
                        postId = "post_seed_balance_1",
                        authorTag = "시간부자",
                        content = "젊을 땐 돈 모으는 게 최고지만 건강 잃으면 다 필요 없더라고요.. 주 4일제 무조건 갑니다 🌿",
                        likesCount = 14,
                        timeAgo = "8분 전",
                        isAuthor = false,
                        authorId = "user_seed_1",
                        createdAt = now - (8 * 60 * 1000L)
                    ),
                    Comment(
                        id = "c_seed_2",
                        postId = "post_seed_balance_1",
                        authorTag = "현실주의자",
                        content = "월 150 차이면 1년에 거의 2천만원인데 5일제 버티고 주말에 호캉스 갑니다 ㅎㅎ",
                        likesCount = 9,
                        timeAgo = "5분 전",
                        isAuthor = false,
                        authorId = "user_seed_2",
                        createdAt = now - (5 * 60 * 1000L)
                    ),
                    Comment(
                        id = "c_seed_3",
                        postId = "post_seed_balance_1",
                        authorTag = "칼퇴꿈나무",
                        content = "댓글 보니까 둘 다 일리가 있네요! 고민 더 깊어집니다 🥹",
                        likesCount = 5,
                        timeAgo = "방금 전",
                        isAuthor = true,
                        authorId = "author_seed_1",
                        createdAt = now - (2 * 60 * 1000L)
                    )
                ),
                createdAt = now - (10 * 60 * 1000L)
            ),
            Post(
                id = "post_seed_daily_1",
                category = PostCategory.DAILY,
                title = "오늘 날씨 너무 좋아서 마포구 북카페 다녀왔어요 ☕️",
                content = "조용히 재즈 음악 나오고 햇살 드는 통창 자리에서 커피 한 잔 마시면서 책 읽으니까 그동안 쌓인 스트레스가 싹 풀리네요. 주말에 가기 좋은 조용한 스팟 있으면 공유해 주세요!",
                authorTag = "책읽는고양이",
                timeAgo = "35분 전",
                likesCount = 28,
                viewsCount = 195,
                commentsCount = 2,
                comments = listOf(
                    Comment(
                        id = "c_seed_4",
                        postId = "post_seed_daily_1",
                        authorTag = "커피러버",
                        content = "혹시 상수동 쪽인가요? 저도 거기 분위기 너무 좋아해요!",
                        likesCount = 3,
                        timeAgo = "20분 전",
                        isAuthor = false,
                        authorId = "user_seed_3",
                        createdAt = now - (20 * 60 * 1000L)
                    ),
                    Comment(
                        id = "c_seed_5",
                        postId = "post_seed_daily_1",
                        authorTag = "책읽는고양이",
                        content = "네 맞아요! 상수역 근처 골목에 있는 곳인데 나중에 가보세요 강추입니다 ✨",
                        likesCount = 2,
                        timeAgo = "12분 전",
                        isAuthor = true,
                        authorId = "author_seed_2",
                        createdAt = now - (12 * 60 * 1000L)
                    )
                ),
                createdAt = now - (35 * 60 * 1000L)
            ),
            Post(
                id = "post_seed_worries_1",
                category = PostCategory.WORRIES,
                title = "소개팅이나 첫 만남에서 대화 정적 생기면 다들 어떻게 풀어요?",
                content = "성격이 극 I (내향형)이라 어색한 분위기 흐르면 식은땀부터 나요.. 공통 취미 찾으려고 질문을 너무 취조하듯이 물어보는 것 같아서 걱정인데 꿀팁 있을까요?",
                authorTag = "수줍은다람쥐",
                timeAgo = "1시간 전",
                likesCount = 34,
                viewsCount = 312,
                commentsCount = 2,
                comments = listOf(
                    Comment(
                        id = "c_seed_6",
                        postId = "post_seed_worries_1",
                        authorTag = "스몰토커",
                        content = "음식 이야기나 요즘 넷플릭스 뭐 보는지 가볍게 물어보는 게 가장 자연스러워요! 리액션만 잘해줘도 반은 성공입니다 👍",
                        likesCount = 11,
                        timeAgo = "40분 전",
                        isAuthor = false,
                        authorId = "user_seed_4",
                        createdAt = now - (40 * 60 * 1000L)
                    ),
                    Comment(
                        id = "c_seed_7",
                        postId = "post_seed_worries_1",
                        authorTag = "수줍은다람쥐",
                        content = "음식 얘기 메모할게요 감사합니다! 오늘 당장 써먹어봐야겠어요 ㅎㅎ",
                        likesCount = 4,
                        timeAgo = "15분 전",
                        isAuthor = true,
                        authorId = "author_seed_3",
                        createdAt = now - (15 * 60 * 1000L)
                    )
                ),
                createdAt = now - oneHour
            ),
            Post(
                id = "post_seed_balance_2",
                category = PostCategory.BALANCE_GAME,
                title = "연락 스타일: 1시간마다 단답 vs 하루 1번 몰아서 장문",
                content = "연애할 때 연락 빈도 중요하게 생각하는 편인데 친구들이랑 의견이 완전 갈리더라고요. 여러분의 취향은 어느 쪽인가요?",
                authorTag = "연애탐구생활",
                timeAgo = "3시간 전",
                likesCount = 56,
                viewsCount = 520,
                commentsCount = 2,
                balanceGame = BalanceGame(
                    optionA = "1시간마다 짧은 단답",
                    optionB = "퇴근 후 몰아서 장문",
                    votesA = 45,
                    votesB = 55,
                    userVotedOption = null
                ),
                comments = listOf(
                    Comment(
                        id = "c_seed_8",
                        postId = "post_seed_balance_2",
                        authorTag = "자유로운영혼",
                        content = "일할 땐 서로 바쁘니까 집중하고 저녁에 통화나 장문으로 오늘 하루 공유하는 게 더 애틋해요 ☺️",
                        likesCount = 8,
                        timeAgo = "2시간 전",
                        isAuthor = false,
                        authorId = "user_seed_5",
                        createdAt = now - (2 * oneHour)
                    )
                ),
                createdAt = now - (3 * oneHour)
            ),
            Post(
                id = "post_seed_daily_2",
                category = PostCategory.DAILY,
                title = "퇴근길 한강 러닝 5km 완주 인증 🏃‍♂️",
                content = "요즘 저녁 바람 선선해서 달리기 딱 좋은 날씨네요! 반포에서 여의도 쪽으로 뛰었는데 야경 보면서 땀 흘리니까 하루 피로가 싹 가십니다.",
                authorTag = "야간러너",
                timeAgo = "5시간 전",
                likesCount = 63,
                viewsCount = 410,
                commentsCount = 1,
                comments = listOf(
                    Comment(
                        id = "c_seed_9",
                        postId = "post_seed_daily_2",
                        authorTag = "운동초보",
                        content = "대단하세요! 저도 내일부터 퇴근하고 3km씩 시작해보려구요 파이팅!",
                        likesCount = 5,
                        timeAgo = "4시간 전",
                        isAuthor = false,
                        authorId = "user_seed_6",
                        createdAt = now - (4 * oneHour)
                    )
                ),
                createdAt = now - (5 * oneHour)
            )
        )
    }
}
