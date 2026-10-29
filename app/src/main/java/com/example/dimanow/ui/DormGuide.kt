package com.example.dimanow.ui

import java.time.LocalTime

/**
 * 예인관 facts for the 기숙사 page (D-112), from the 2026-2 orientation slides and handout the user shared on
 * 2026-10-23, in the app's voice. Left out on purpose: the men's-wing Wi-Fi password rule and the door-lock default
 * code (security), the 자치회 recruitment (closed) and the LAN photos. The 점호 rules themselves live in
 * [com.example.dimanow.dorm.RollCallRules].
 */
internal object DormGuide {
    const val SOURCE = "2026-2학기 예인관 오리엔테이션 자료 기준이에요. 학교와 자치회 공지가 우선이에요."

    /** 학교 홈페이지 → 대학생활 → 예인관 → 수칙안내. */
    const val RULES_URL = "https://www.dima.ac.kr/?p=101"
    const val INSTAGRAM_URL = "https://www.instagram.com/dima_dorm/"

    const val ADDRESS = "경기도 안성시 삼죽면 동아예대길 47 동아방송예술대학교 예인관 "

    data class Facility(val name: String, val detail: String, val opens: LocalTime?, val closes: LocalTime?)

    /**
     * Opening times as published; where only the closing time is given the building's 06:00 opening is assumed.
     * A null [Facility.opens] means always open.
     */
    val facilities = listOf(
        Facility("지하 세탁실", "02:00까지", LocalTime.of(6, 0), LocalTime.of(2, 0)),
        Facility("4층 세탁실", "소음 때문에 23:00까지", LocalTime.of(6, 0), LocalTime.of(23, 0)),
        Facility("휴게실", "02:00까지 · 음식은 여기서", LocalTime.of(6, 0), LocalTime.of(2, 0)),
        Facility("체력단련장", "06:00~24:00 · 관생만, 실내화 착용", LocalTime.of(6, 0), LocalTime.MIDNIGHT),
        Facility("지하1층 편의점", "24시간 · 00:00~08:00 무인 운영", null, null),
    )

    const val MICROWAVES = "전자레인지는 남자 1·4층, 여자 3·5층에 있어요. 샤워실이 다 차면 다른 층을 써도 돼요."

    val delivery = listOf(
        "사진이 있는 신분증(학생증·주민등록증 등)을 보여 주고 수령 대장에 서명해요.",
        "동명이인과 비슷한 이름이 많아요. 운송장번호를 꼭 확인해 주세요.",
        "그날 찾아가 주세요. 운영실은 분실을 책임지지 않아요.",
    )
    val DELIVERY_OPENS: LocalTime = LocalTime.of(9, 0)
    val DELIVERY_CLOSES: LocalTime = LocalTime.of(2, 0)
    const val DELIVERY_HOURS = "09:00~02:00 · 00:00 이후는 쿠팡·한진·롯데·로젠만, CJ·우체국은 00:00까지"

    val mealTickets = listOf(
        "식권은 달마다 나와요(하루 2장).",
        "하루에 3장까지, 한 끼에 1장만 쓸 수 있어요.",
        "다른 사람에게 빌려주거나 받으면 안 돼요.",
        "식사는 PAYCO 식권으로 해요(식당 운영 업체 하림). PAYCO 식권 화면에서 사용하기를 누르면 나오는 바코드를 보여 주면 돼요.",
    )

    val pointRules = listOf(
        "5점" to "음주·흡연, 남녀 상호 무단출입, 절도·폭력, 시설 고의 파손, 사행 행위 등(정도가 심하면 퇴사)",
        "4점" to "비관생·외부인 무단 숙박, 열쇠 무단 복제·대여, 지도에 대한 불량한 태도",
        "3점" to "무단 외박, 문 닫힌 뒤 입사",
        "2점" to "점호 불참(문을 닫아 두어도 불참이에요)",
    )
    val meritWays = listOf(
        "오리엔테이션 참석 5점(불참하면 벌점 5점)",
        "소방훈련 참석 5점(학기마다 1번, 불참하면 벌점 5점)",
        "퇴사 때 만족도 조사 제출 5점",
    )

    val outingRules = listOf(
        "점호가 있는 날은 21:00 전까지, 02:00 이후에 들어오면 전날 23:59까지 신청해요.",
        "외박 신청은 마감(23:59)이 지나면 자동으로 다음 날 신청으로 넘어가요.",
        "신청하지 않고 방에 없거나, 신청하고 방에 있으면 벌점이에요.",
        "모바일 신청은 누락될 수 있어요. 신청한 뒤 목록에서 접수됐는지 확인해 주세요.",
    )

    val livingRules = listOf(
        "남녀 상호 생활관·호실 출입 금지(어기면 즉시 퇴사)",
        "생활관 안 음주·주류 반입 금지, 흡연은 지정 장소에서만",
        "호실 안 음식물 금지(식당·휴게실 이용)",
        "냉장고·전기장판 같은 위험 전자기기 반입 금지",
        "청소도구는 개인이 챙겨요(복도 청소도구함은 쓰지 않아요)",
        "기본 약품·건전지·비누는 운영실에 있어요(사용 대장 작성)",
        "에어컨 필터는 주기적으로 청소해 주세요",
    )
    val moveRules = listOf(
        "퇴사는 2026년 12월 18일(금)~20일(일) 예정이에요(확정은 따로 공지).",
        "퇴사 때 호실 점검을 받아야 보증금을 돌려받아요. 파손·분실은 보증금에서 빠져요.",
        "무단 퇴사하면 보증금 환불과 다음 학기 입사가 안 돼요.",
        "중도 퇴사 환불은 주 단위예요(첫 주 10만 원, 그 뒤 매주 9만 원, 15~16주차는 보증금만).",
    )
}
