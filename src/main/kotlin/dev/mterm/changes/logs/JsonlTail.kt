package dev.mterm.changes.logs

import com.intellij.openapi.diagnostic.thisLogger
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

internal class JsonlTail(private val file: Path) {

    private var offset = 0L

    fun readNew(consumer: (JsonObject) -> Unit) {
        if (!Files.isRegularFile(file)) return
        val size = runCatching { Files.size(file) }.getOrDefault(0L)
        if (size < offset) offset = 0
        if (size == offset) return
        runCatching {
            Files.newByteChannel(file).use { channel ->
                channel.position(offset)
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = java.nio.ByteBuffer.allocate(BUFFER_SIZE)
                while (channel.read(buffer) > 0) {
                    buffer.flip()
                    bytes.write(buffer.array(), 0, buffer.limit())
                    buffer.clear()
                }
                val text = bytes.toString(StandardCharsets.UTF_8)
                val complete = text.substringBeforeLast('\n', "")
                if (complete.isEmpty()) return@use
                offset += complete.toByteArray(StandardCharsets.UTF_8).size.toLong() + 1
                for (line in complete.lineSequence()) {
                    if (line.isBlank()) continue
                    val element = runCatching { JsonParser.parseString(line) }.getOrNull() ?: continue
                    if (element.isJsonObject) consumer(element.asJsonObject)
                }
            }
        }.onFailure { thisLogger().warn("mTerm: cannot read agent log $file", it) }
    }

    companion object {
        private const val BUFFER_SIZE = 1 shl 16

        fun epochMillis(raw: String?): Long? =
            raw?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    }
}

internal fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString

internal fun JsonObject.obj(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

internal fun JsonObject.array(name: String): com.google.gson.JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray
