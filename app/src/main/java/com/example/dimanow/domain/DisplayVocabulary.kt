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
}
