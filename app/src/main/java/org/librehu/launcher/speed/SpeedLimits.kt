package org.librehu.launcher.speed

import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A road of OpenStreetMap near the car: its tags and its points (lat, lon). */
class OsmWay(
    val id: Long,
    val tags: Map<String, String>,
    val points: List<Pair<Double, Double>>,
) {
    val oneway: Boolean get() = tags["oneway"] == "yes" || tags["highway"] == "motorway" || tags["junction"] == "roundabout"
    val name: String get() = tags["name"] ?: tags["ref"].orEmpty()
}

/** The road the car is on and its limit ([kmh] null = no limit known or none). */
data class RoadMatch(
    val way: OsmWay,
    val distanceM: Double,
    val forward: Boolean,
    val kmh: Int?,
)

/**
 * OpenStreetMap speed limits (wiki Key:maxspeed): `maxspeed`, `maxspeed:forward` / `:backward` by the direction of
 * travel, numbers in km/h or "NN mph", and implicit values (`FR:urban`…, also in `maxspeed:type`,
 * `source:maxspeed`, `zone:maxspeed`, `zone:traffic`) from the default limits of the OSM wiki ("Default speed
 * limits", "Key:zone:traffic"; France: urban 50, rural 80, motorway 130). `none`, `signals`, `variable` = unknown.
 */
object SpeedLimits {
    private val IMPLICIT =
        mapOf(
            "FR:urban" to 50,
            "FR:rural" to 80,
            "FR:motorway" to 130,
            "FR:zone30" to 30,
            "FR:zone20" to 20,
            "FR:walk" to 6,
            "BE:urban" to 50,
            "BE-VLG:rural" to 70,
            "BE-WAL:rural" to 90,
            "BE-BRU:rural" to 70,
            "BE:motorway" to 120,
            "BE:zone30" to 30,
            "CH:urban" to 50,
            "CH:rural" to 80,
            "CH:trunk" to 100,
            "CH:motorway" to 120,
            "LU:urban" to 50,
            "LU:rural" to 90,
            "LU:motorway" to 130,
            "DE:urban" to 50,
            "DE:rural" to 100,
            "DE:zone30" to 30,
            "DE:living_street" to 7,
            "IT:urban" to 50,
            "IT:rural" to 90,
            "IT:trunk" to 110,
            "IT:motorway" to 130,
            "ES:urban" to 50,
            "ES:rural" to 90,
            "ES:motorway" to 120,
            "NL:urban" to 50,
            "NL:rural" to 80,
            "NL:motorway" to 100,
            "GB:nsl_single" to 97,
            "GB:nsl_dual" to 113,
            "GB:motorway" to 113,
        )

    /** One value of a maxspeed tag → km/h, null when unknown or "none". */
    fun parse(value: String?): Int? {
        val v = value?.trim()?.substringBefore(';')?.trim().orEmpty()
        if (v.isEmpty()) return null
        IMPLICIT[v]?.let { return it }
        if (v.endsWith("zone30", ignoreCase = true)) return 30
        if (v.endsWith("zone20", ignoreCase = true)) return 20
        val mph = v.endsWith("mph")
        val n = v.removeSuffix("mph").trim().toDoubleOrNull() ?: return null
        if (n <= 0 || n > 200) return null
        return if (mph) (n * 1.609344).roundToInt() else n.roundToInt()
    }

    /** Limit of [way] for a car going [forward] (along its points) or back. */
    fun limit(
        way: OsmWay,
        forward: Boolean,
    ): Int? {
        val t = way.tags
        val directional = if (forward) t["maxspeed:forward"] else t["maxspeed:backward"]
        return parse(directional)
            ?: parse(t["maxspeed"])
            ?: parse(t["maxspeed:type"])
            ?: parse(t["source:maxspeed"])
            ?: parse(t["zone:maxspeed"])
            ?: parse(t["zone:traffic"])
    }

    /** Overpass API answer (`out tags geom`) → roads. */
    fun ways(json: String): List<OsmWay> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<OsmWay>()
        for (i in 0 until elements.length()) {
            val e = elements.getJSONObject(i)
            if (e.optString("type") != "way") continue
            val g = e.optJSONArray("geometry") ?: continue
            val pts = (0 until g.length()).map { g.getJSONObject(it).let { p -> p.getDouble("lat") to p.getDouble("lon") } }
            if (pts.size < 2) continue
            val tags = HashMap<String, String>()
            e.optJSONObject("tags")?.let { o -> o.keys().forEach { k -> tags[k] = o.getString(k) } }
            out += OsmWay(e.getLong("id"), tags, pts)
        }
        return out
    }

    /** Overpass query: drivable roads within [radiusM] of the point, with their tags and points. */
    fun query(
        lat: Double,
        lon: Double,
        radiusM: Int,
    ): String =
        "[out:json][timeout:20];way(around:$radiusM,$lat,$lon)[highway~\"^(motorway|trunk|primary|secondary|tertiary|" +
            "unclassified|residential|living_street|road|motorway_link|trunk_link|primary_link|secondary_link|" +
            "tertiary_link)$\"];out tags geom;"

    /**
     * The road under the car: the closest segment within [maxDistanceM], among those going the car's way when its
     * [bearing] (degrees, null when stopped) is known (60° at most off the segment, either direction unless one way).
     */
    fun match(
        ways: List<OsmWay>,
        lat: Double,
        lon: Double,
        bearing: Float?,
        maxDistanceM: Double,
    ): RoadMatch? {
        val kx = 111_320.0 * cos(lat * PI / 180)
        val ky = 110_540.0
        var best: RoadMatch? = null
        var bestScore = Double.MAX_VALUE
        for (w in ways) {
            for (i in 0 until w.points.size - 1) {
                val (alat, alon) = w.points[i]
                val (blat, blon) = w.points[i + 1]
                val ax = (alon - lon) * kx
                val ay = (alat - lat) * ky
                val bx = (blon - lon) * kx
                val by = (blat - lat) * ky
                val dx = bx - ax
                val dy = by - ay
                val len2 = dx * dx + dy * dy
                if (len2 < 0.01) continue
                val t = (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
                val px = ax + t * dx
                val py = ay + t * dy
                val dist = sqrt(px * px + py * py)
                if (dist > maxDistanceM) continue
                val segBearing = (atan2(dx, dy) * 180 / PI + 360) % 360
                var forward = true
                var penalty = 0.0
                if (bearing != null) {
                    val along = angle(bearing.toDouble(), segBearing)
                    val against = angle(bearing.toDouble(), (segBearing + 180) % 360)
                    forward = along <= against
                    val off = if (w.oneway) along else minOf(along, against)
                    if (off > 60) continue
                    penalty = off * 0.3
                }
                val score = dist + penalty
                if (score < bestScore) {
                    bestScore = score
                    best = RoadMatch(w, dist, forward, null)
                }
            }
        }
        return best?.let { it.copy(kmh = limit(it.way, it.forward)) }
    }

    private fun angle(
        a: Double,
        b: Double,
    ): Double {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }
}
