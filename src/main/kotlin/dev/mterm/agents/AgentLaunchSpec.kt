package dev.mterm.agents

class AgentLaunchSpec private constructor(
    val binary: String,
    val modelPresets: List<String>,
    val effortPresets: List<String>,
    private val modelFlag: String,
    private val effortFlag: String,
) {

    fun apply(command: String, options: LaunchOptions): String {
        val parts = mutableListOf(command.trim())
        options.model?.let { parts += modelFlag + quote(it) }
        options.effort?.let { parts += effortFlag + quote(it) }
        return parts.joinToString(" ")
    }

    private fun quote(value: String): String =
        if (SAFE_VALUE.matches(value)) value else "'" + value.replace("'", "'\\''") + "'"

    companion object {
        private val SAFE_VALUE = Regex("[A-Za-z0-9._:/-]+")
        private val WHITESPACE = Regex("\\s+")

        private val SPECS = listOf(
            AgentLaunchSpec(
                binary = "claude",
                modelPresets = listOf("fable", "opus", "sonnet", "haiku"),
                effortPresets = listOf("low", "medium", "high", "xhigh", "max"),
                modelFlag = "--model ",
                effortFlag = "--effort ",
            ),
            AgentLaunchSpec(
                binary = "codex",
                modelPresets = listOf("gpt-5.6-terra", "gpt-5.6-luna", "gpt-5.5", "gpt-5.4-mini"),
                effortPresets = listOf("minimal", "low", "medium", "high", "xhigh"),
                modelFlag = "--model ",
                effortFlag = "-c model_reasoning_effort=",
            ),
            AgentLaunchSpec(
                binary = "grok",
                modelPresets = listOf("grok-4.5"),
                effortPresets = listOf("low", "high"),
                modelFlag = "--model ",
                effortFlag = "--reasoning-effort ",
            ),
        )

        fun forCommand(command: String?): AgentLaunchSpec? {
            val trimmed = command?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val binary = trimmed.split(WHITESPACE).first().substringAfterLast('/')
            return SPECS.firstOrNull { it.binary == binary }
        }

        fun forProfile(profile: AgentProfile): AgentLaunchSpec? = forCommand(profile.command)
    }
}
