package com.example.dimanow.domain

/**
 * A two- or three-syllable short name for a course, for the Now Bar chip (D-109): the first syllable of each word,
 * as students say 방송시스템전기 → 방시전. Course names are usually written without spaces, so a run of Hangul is
 * split by the longest course word that starts at each position; an unknown stretch counts as a two-syllable word
 * (most Sino-Korean words are). Connectors such as 및 are skipped. Toss's es-hangul has no word segmentation
 * (checked 2026-10-23), so this small vocabulary is the app's own.
 */
object CourseAbbreviation {
    private const val MAX_SYLLABLES = 3

    fun of(name: String): String {
        val words = words(name)
        val syllables = words.sumOf { it.length }
        return when {
            // A name of three syllables or fewer (연기, 연기론) is already short.
            words.isNotEmpty() && syllables <= MAX_SYLLABLES && words.all { isHangul(it.first()) } -> words.joinToString("")
            words.size == 1 && words.single().length <= MAX_SYLLABLES -> words.single()
            // A Hangul word gives its first syllable; a Latin acronym such as UX keeps two letters.
            words.size >= 2 -> words.take(MAX_SYLLABLES).joinToString("") { if (isHangul(it.first())) it.take(1) else it.take(2) }
            words.size == 1 -> words.single().take(2)
            else -> name.trim().take(MAX_SYLLABLES)
        }
    }

    /** The words of [name]: Hangul runs split by the vocabulary, Latin runs (such as UX) as one word each. */
    internal fun words(name: String): List<String> {
        val cleaned = name.replace(BRACKETED, " ")
        return RUNS.findAll(cleaned).flatMap { run ->
            val text = run.value
            if (isHangul(text.first())) split(text) else sequenceOf(text.uppercase())
        }.toList()
    }

    private fun split(run: String): Sequence<String> = sequence {
        var index = 0
        while (index < run.length) {
            val word = (minOf(LONGEST, run.length - index) downTo 2).asSequence()
                .map { run.substring(index, index + it) }
                .firstOrNull { it in VOCABULARY }
            when {
                word != null -> {
                    yield(word)
                    index += word.length
                }
                run[index] in CONNECTORS -> index += 1
                else -> {
                    val end = minOf(index + 2, run.length)
                    yield(run.substring(index, end))
                    index = end
                }
            }
        }
    }

    private fun isHangul(char: Char) = char in '가'..'힣'

    private val BRACKETED = Regex("""[(\[{（][^)\]}）]*[)\]}）]""")
    private val RUNS = Regex("""[가-힣]+|[A-Za-z]+""")
    private val CONNECTORS = setOf('및', '의', '와', '과')

    /** Words that recur in DIMA course names. Compounds are left out on purpose: 실용음악 is 실용 + 음악. */
    private val VOCABULARY = setOf(
        "방송", "시스템", "전기", "전자", "조명", "기초", "실습", "스튜디오", "영상", "제작", "음향", "연출", "촬영",
        "편집", "디자인", "디지털", "콘텐츠", "컨텐츠", "미디어", "뉴미디어", "연기", "공연", "무대", "미술", "기획",
        "이론", "심화", "응용", "사운드", "녹음", "믹싱", "그래픽", "모션", "애니메이션", "작곡", "화성학", "화성",
        "보컬", "실용", "음악", "엔터테인먼트", "경영", "마케팅", "광고", "크리에이티브", "패션", "스타일리스트",
        "스타일링", "극작", "시나리오", "영화", "드라마", "뮤지컬", "댄스", "발성", "캡스톤", "졸업", "작품",
        "포트폴리오", "프로젝트", "세미나", "워크숍", "현장", "실무", "입문", "개론", "분석", "이해", "표현", "창작",
        "연구", "글쓰기", "영어", "교양", "기술", "공학", "카메라", "프로그래밍", "컴퓨터", "데이터", "인공지능",
        "콘서트", "라이브", "레코딩", "엔지니어링", "마스터링", "사진", "특수", "효과", "시각", "비주얼", "인터랙티브",
        "게임", "웹툰", "일러스트", "매체", "언론", "저널리즘", "아나운서", "구성", "작가", "예술", "문화", "산업",
        "비즈니스", "매니지먼트", "커뮤니케이션", "스피치", "리더십", "진로", "취업", "창업", "인성", "봉사", "체육",
        "건강", "역사", "철학", "심리", "사회", "중계", "송출", "네트워크", "기기", "장비", "운용", "조작", "신호",
        "처리", "회로", "전파", "통신", "오디오", "비디오", "세트", "분장", "의상", "소품", "공간", "설계", "연습",
        "앙상블", "합주", "악기", "피아노", "드럼", "베이스", "리듬", "청음", "시창", "편곡", "대중", "퍼포먼스",
        "안무", "트레이닝", "오디션", "화법", "즉흥", "장면", "캐릭터", "미학", "비평", "서사", "스토리텔링", "콘티",
        "가상", "증강", "현실", "실감", "인터넷", "온라인", "모바일", "플랫폼", "브랜드", "브랜딩", "홍보", "유튜브",
        "크리에이터", "매니저", "엔터", "케이팝", "실기", "전공", "종합", "특강", "탐색",
    )
    private val LONGEST = VOCABULARY.maxOf { it.length }
}
