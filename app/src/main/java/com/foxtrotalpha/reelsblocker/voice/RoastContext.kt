package com.foxtrotalpha.reelsblocker.voice

internal data class RoastContext(
    val appLabel: String,
    val reason: String,
    val blocksToday: Int,
    val unproductiveMsToday: Long,
    val stepsToday: Long?,
    val sleepMsLastNight: Long?,
    val remainingEventsToday: Int,
    val minutesUntilNextEvent: Long?,
    val recentLines: List<String>,
) {
    fun toUserPrompt(): String {
        val lines = buildList {
            add("Blocked $appLabel short-form video. Reason: $reason.")
            add("Blocks today: $blocksToday.")
            add("Unproductive screen time today: ${formatDuration(unproductiveMsToday)}.")
            stepsToday?.let { add("Steps today: $it.") }
            sleepMsLastNight?.let { add("Sleep last night: ${formatDuration(it)}.") }
            add("Calendar events still remaining today: $remainingEventsToday.")
            minutesUntilNextEvent?.let { add("Minutes until next event: $it.") }
            if (recentLines.isNotEmpty()) {
                add("Do not repeat these recent lines: ${recentLines.joinToString(" | ")}")
            }
        }
        return lines.joinToString(" ")
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = (ms / 60_000L).coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "$hours hours $minutes minutes"
            else -> "$minutes minutes"
        }
    }
}
