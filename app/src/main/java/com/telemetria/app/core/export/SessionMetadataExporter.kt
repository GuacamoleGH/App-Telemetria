package com.telemetria.app.core.export

import com.telemetria.app.core.database.entity.SessionEntity
import com.telemetria.app.core.util.FormatUtils

object SessionMetadataExporter {

    fun generateSessionJson(
        session: SessionEntity,
        pointCount: Long
    ): String {
        val syncOffsetMs = if (session.videoStartMonotonicNanos != null && session.startMonotonicNanos > 0) {
            (session.videoStartMonotonicNanos - session.startMonotonicNanos) / 1_000_000L
        } else {
            0L
        }

        val hasVideo = session.videoFilePath != null

        return buildString {
            append("{\n")
            append("  \"schema_version\": \"1.0.0\",\n")
            append("  \"generator\": \"Telemetria App Native Android\",\n")
            append("  \"session\": {\n")
            append("    \"id\": \"${session.id}\",\n")
            append("    \"title\": \"${escapeJson(session.title)}\",\n")
            append("    \"status\": \"${session.status}\",\n")
            append("    \"start_time_iso\": \"${FormatUtils.formatIsoUtc(session.startTimeMillis)}\",\n")
            append("    \"start_time_utc_ms\": ${session.startTimeMillis},\n")
            append("    \"end_time_utc_ms\": ${session.endTimeMillis ?: session.startTimeMillis},\n")
            append("    \"start_monotonic_nanos\": ${session.startMonotonicNanos},\n")
            append("    \"duration_seconds\": ${session.durationSeconds},\n")
            append("    \"total_distance_meters\": ${session.totalDistanceMeters},\n")
            append("    \"max_speed_kmh\": ${session.maxSpeedKmh},\n")
            append("    \"avg_speed_kmh\": ${session.avgSpeedKmh},\n")
            append("    \"point_count\": $pointCount\n")
            append("  },\n")
            append("  \"video_synchronization\": {\n")
            append("    \"has_video\": $hasVideo,\n")
            append("    \"video_filename\": ${if (hasVideo) "\"video.mp4\"" else "null"},\n")
            append("    \"video_start_monotonic_nanos\": ${session.videoStartMonotonicNanos ?: "null"},\n")
            append("    \"video_start_epoch_ms\": ${session.videoStartEpochMillis ?: "null"},\n")
            append("    \"telemetry_to_video_offset_ms\": $syncOffsetMs,\n")
            append("    \"sync_formula\": \"video_frame_time_sec = (sample_monotonic_nanos - video_start_monotonic_nanos) / 1_000_000_000\"\n")
            append("  },\n")
            append("  \"signals\": {\n")
            append("    \"time_offset_video_sec\": {\"unit\": \"s\", \"source\": \"Monotonic Clock delta\", \"desc\": \"Segundos relativos al primer frame de video (t=0.000 es inicio de video)\"},\n")
            append("    \"latitude\": {\"unit\": \"deg\", \"source\": \"GPS Fused Location\"},\n")
            append("    \"longitude\": {\"unit\": \"deg\", \"source\": \"GPS Fused Location\"},\n")
            append("    \"altitude_m\": {\"unit\": \"m\", \"source\": \"GPS WGS84\"},\n")
            append("    \"gps_speed_kmh\": {\"unit\": \"km/h\", \"source\": \"GPS Doppler / Fused Location\"},\n")
            append("    \"lateral_g\": {\"unit\": \"G\", \"source\": \"IMU Linear Accelerometer (Vehicle lateral axis, 1G=9.80665 m/s^2)\"},\n")
            append("    \"longitudinal_g\": {\"unit\": \"G\", \"source\": \"IMU Linear Accelerometer (Vehicle forward axis)\"},\n")
            append("    \"engine_rpm\": {\"unit\": \"RPM\", \"source\": \"OBD-II PID 010C\"},\n")
            append("    \"throttle_pct\": {\"unit\": \"%\", \"source\": \"OBD-II PID 0111 / 0149\"},\n")
            append("    \"coolant_temp_c\": {\"unit\": \"degC\", \"source\": \"OBD-II PID 0105\"}\n")
            append("  }\n")
            append("}\n")
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

