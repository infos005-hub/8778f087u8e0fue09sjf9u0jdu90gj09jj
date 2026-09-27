package com.example.core.util

import kotlin.random.Random

object AnonymousNameGenerator {

    private val adjectives = listOf(
        "행복한", "우울한", "배고픈", "졸린", "당당한",
        "신난", "심심한", "설레는", "따뜻한", "용감한",
        "호기심많은", "엉뚱한", "느긋한", "고민많은", "다정한",
        "솔직한", "차분한", "활기찬", "빛나는", "감성적인",
        "사랑스러운", "소심한", "열정적인", "산뜻한", "포근한",
        "귀여운", "반짝이는", "친절한", "새벽감성", "조용한",
        "피곤한", "긍정적인", "유쾌한", "낭만적인", "상냥한",
        "단호한", "풋풋한", "털털한", "야심찬", "센스있는"
    )

    /**
     * 글 작성자용 랜덤 닉네임 생성
     * 예: "배고픈 글쓰니", "행복한 글쓰니", "감성적인 글쓰니"
     */
    fun generatePostAuthorName(): String {
        val adj = adjectives.random()
        return "$adj 글쓰니"
    }

    /**
     * 댓글 작성자용 랜덤 닉네임 생성
     * 만약 글 작성자 본인이 댓글을 다는 경우 "(글쓴이)" 표시 또는 일관성 있게 식별 가능
     * 예: "신난 댓쓰니", "따뜻한 댓쓰니", "솔직한 댓쓰니"
     */
    fun generateCommentAuthorName(isAuthor: Boolean = false): String {
        if (isAuthor) {
            return "글쓰니"
        }
        val adj = adjectives.random()
        return "$adj 댓쓰니"
    }
}
