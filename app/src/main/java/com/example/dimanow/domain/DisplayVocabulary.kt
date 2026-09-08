package com.example.dimanow.domain

object DisplayVocabulary {
    fun zoneName(zone: CampusZoneId): String = when (zone) {
        CampusZoneId.YEIN -> "예인관"
        CampusZoneId.MAIN -> "본관"
        CampusZoneId.ONE_ROOM -> "원룸촌"
        CampusZoneId.OUTSIDE -> "캠퍼스 밖"
    }

    fun originName(zone: CampusZoneId): String = when (zone) {
        CampusZoneId.YEIN -> "엔터관"
        CampusZoneId.MAIN -> "본관"
        CampusZoneId.ONE_ROOM -> "원룸촌"
        CampusZoneId.OUTSIDE -> "캠퍼스 밖"
    }

    /** 홈 화면 캡슐과 나우바 칩이 함께 쓰는 목적지 표기 ("본관행"). */
    fun destinationName(zone: CampusZoneId): String = "${originName(zone)}행"
}
