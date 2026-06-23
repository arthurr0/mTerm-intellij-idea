package dev.mterm.session

import com.intellij.openapi.fileTypes.FileType
import com.intellij.testFramework.LightVirtualFile
import dev.mterm.AgentKind
import java.util.concurrent.atomic.AtomicInteger

class MTermSessionFile(
    val agent: AgentKind,
    val workingDirectory: String?,
) : LightVirtualFile("${agent.displayName} ${counter.incrementAndGet()}", MTermSessionFileType, "") {

    init {
        isWritable = false
    }

    override fun getFileType(): FileType = MTermSessionFileType

    companion object {
        private val counter = AtomicInteger(0)
    }
}
