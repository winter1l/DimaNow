package com.example.dimanow.domain

object DefaultCampusZones {
    const val VERSION = "CAMPUS_ZONES_V2_USER_2026_08_27"

    val all: List<CampusZone> = listOf(
        CampusZone(
            CampusZoneId.YEIN,
            GeoPoint(37.0609666, 127.3535671),
            250,
            ZoneGeometry.Polygon(
                version = VERSION,
                vertices = listOf(
                    GeoPoint(37.0636703410925, 127.35157370567323),
                    GeoPoint(37.057959773064354, 127.35134840011598),
                    GeoPoint(37.05704364493188, 127.3542881011963),
                    GeoPoint(37.06186389603846, 127.35631585121156),
                    GeoPoint(37.06429530969742, 127.35430955886842),
                ),
                wakeRadiusMeters = 550,
            ),
        ),
        CampusZone(
            CampusZoneId.MAIN,
            GeoPoint(37.0594160, 127.3585957),
            250,
            ZoneGeometry.Polygon(
                version = VERSION,
                vertices = listOf(
                    GeoPoint(37.06185319422157, 127.35633462667468),
                    GeoPoint(37.062120739190675, 127.35869765281679),
                    GeoPoint(37.06156852533837, 127.35989928245546),
                    GeoPoint(37.05798545874042, 127.36126184463502),
                    GeoPoint(37.05593057717724, 127.36106336116792),
                    GeoPoint(37.057037223433966, 127.35431760549547),
                ),
                wakeRadiusMeters = 570,
            ),
        ),
        CampusZone(
            CampusZoneId.ONE_ROOM,
            GeoPoint(37.0558538, 127.3627537),
            250,
            ZoneGeometry.Polygon(
                version = VERSION,
                vertices = listOf(
                    GeoPoint(37.05797903732221, 127.36129134893419),
                    GeoPoint(37.059783709917404, 127.36156225204469),
                    GeoPoint(37.05824243840848, 127.36826777458192),
                    GeoPoint(37.05588764603305, 127.36702322959901),
                    GeoPoint(37.051383925255784, 127.36590743064882),
                    GeoPoint(37.052629807185085, 127.36044108867647),
                    GeoPoint(37.054796087471864, 127.35963106155397),
                    GeoPoint(37.05607209310532, 127.35957473516466),
                    GeoPoint(37.05590917186811, 127.36108481884004),
                ),
                wakeRadiusMeters = 680,
            ),
        ),
    )

    fun evidence(zone: CampusZoneId): String = when (zone) {
        CampusZoneId.YEIN -> "사용자 승인 고정 polygon · 예인관"
        CampusZoneId.MAIN -> "사용자 승인 고정 polygon · 본관"
        CampusZoneId.ONE_ROOM -> "사용자 승인 고정 polygon · 원룸촌"
        CampusZoneId.OUTSIDE -> "고정 좌표 없음"
    }
}
