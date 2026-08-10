package dev.mterm.session

import com.intellij.openapi.fileTypes.FileType
import com.intellij.testFramework.LightVirtualFile
import dev.mterm.agents.AgentProfile
import java.util.concurrent.atomic.AtomicInteger

class MTermSessionFile(
    val profile: AgentProfile,
    val workingDirectory: String?,
) : LightVirtualFile("${profile.displayName} ${counter.incrementAndGet()}", MTermSessionFileType, "") {

    init {
        isWritable = false
    }

    override fun getFileType(): FileType = MTermSessionFileType

    companion object {
        private val counter = AtomicInteger(0)
    }
}
