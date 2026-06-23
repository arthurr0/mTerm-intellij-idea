package dev.mterm.sound

enum class AgentSound(val displayName: String) {
    CHIME("Chime"),
    POP("Pop"),
    DROP("Drop"),
    SUCCESS("Success"),
    SYSTEM("System beep");

    companion object {
        fun fromId(id: String?): AgentSound = entries.firstOrNull { it.name == id } ?: CHIME
    }
}
