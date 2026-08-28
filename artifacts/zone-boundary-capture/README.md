# DIMA Now campus zone boundary evidence

The user finalized all three boundaries on 2026-08-27 with the one-time OpenStreetMap boundary tool in this directory. The tool is development-only and is not packaged in the Android app.

Approved source: `dima-now-campus-zones-v2.geojson`

SHA-256: `90A0D9E51129D2481B2945CC78800B9E34D24D42AE7B33536A0A06512D8EA094`

| Zone | Unique vertices | Center (lat, lon) | Area | Wake-up radius |
|---|---:|---|---:|---:|
| YEIN | 5 | 37.0609666, 127.3535671 | 252,466 m² | 550 m |
| MAIN | 6 | 37.0594160, 127.3585957 | 292,742 m² | 570 m |
| ONE_ROOM | 9 | 37.0558538, 127.3627537 | 453,601 m² | 680 m |

Each wake-up radius encloses its polygon's farthest vertex with approximately 107–110 m of margin. Runtime zone classification uses the bundled polygons; Android's circular geofences only wake the app for a fresh offline polygon classification.

Map data attribution: © OpenStreetMap contributors.
