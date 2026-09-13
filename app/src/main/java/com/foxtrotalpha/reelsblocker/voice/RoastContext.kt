package com.foxtrotalpha.reelsblocker.voice

internal data class RoastContext(
    val appLabel: String,
    val reason: String,
    val blocksToday: Int,
    val unproductiveMsToday: Long,
    val blockedAppMsToday: Long,
    val instagramMsToday: Long,
    val youtubeMsToday: Long,
    val stepsToday: Long?,
    val sleepMsLastNight: Long?,
    val remainingEventsToday: Int,
    val minutesUntilNextEvent: Long?,
    val focus: RoastFocus,
    val recentLines: List<String>,
) {
    fun toUserPrompt(): String {
        val lines = buildList {
            add("Blocked $appLabel short-form video. Reason: $reason.")
            add("Blocks today: $blocksToday.")
            add("Unproductive screen time today: ${formatDuration(unproductiveMsToday)}.")
            add("$appLabel screen time today: ${formatDuration(blockedAppMsToday)}.")
            add("Instagram screen time today: ${formatDuration(instagramMsToday)}.")
            add("YouTube screen time today: ${formatDuration(youtubeMsToday)}.")
            stepsToday?.let { add("Steps today: $it.") }
            sleepMsLastNight?.let { add("Sleep last night: ${formatDuration(it)}.") }
            add("Calendar events still remaining today: $remainingEventsToday.")
            minutesUntilNextEvent?.let { add("Minutes until next event: $it.") }
            add(focus.instruction)
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

internal enum class RoastFocus(val instruction: String) {
    BLOCKS(
        "Roast angle for this line: total blocks today. Do not mention steps, walking, or sleep.",
    ),
    SLEEP(
        "Roast angle for this line: last night's sleep. Do not mention steps or walking.",
    ),
    APP_SCREEN_TIME(
        "Roast angle for this line: screen time in the app just blocked, or Instagram vs YouTube. " +
            "Do not mention steps or walking.",
    ),
    UNPRODUCTIVE(
        "Roast angle for this line: total unproductive screen time today. Do not mention steps or walking.",
    ),
    STEPS(
        "Roast angle for this line: step count. You may tell them to walk more. Do not talk about sleep.",
    ),
}
