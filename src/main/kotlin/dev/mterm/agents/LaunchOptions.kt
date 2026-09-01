package dev.mterm.agents

data class LaunchOptions(
    val model: String? = null,
    val effort: String? = null,
) {
    val isEmpty: Boolean get() = model == null && effort == null

    fun label(): String? = listOfNotNull(model, effort).joinToString(" · ").takeIf { it.isNotEmpty() }

    companion object {
        val NONE = LaunchOptions()

        fun of(model: String?, effort: String?): LaunchOptions = LaunchOptions(
            model = model?.trim()?.takeIf { it.isNotEmpty() },
            effort = effort?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}
