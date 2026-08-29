package dev.mterm.changes.logs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class JsonlTailTest {

    @Test
    fun `only complete lines are consumed`() {
        val file = Files.createTempFile("mterm-tail", ".jsonl")
        try {
            Files.writeString(file, "{\"n\":1}\n{\"n\":2}\n{\"n\":3")
            val tail = JsonlTail(file)

            assertEquals(listOf(1, 2), collect(tail))

            append(file, "}\n{\"n\":4}\n")

            assertEquals(listOf(3, 4), collect(tail))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `previously read lines are not repeated`() {
        val file = Files.createTempFile("mterm-tail", ".jsonl")
        try {
            Files.writeString(file, "{\"n\":1}\n")
            val tail = JsonlTail(file)

            assertEquals(listOf(1), collect(tail))
            assertEquals(emptyList<Int>(), collect(tail))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `a truncated file is read from the start again`() {
        val file = Files.createTempFile("mterm-tail", ".jsonl")
        try {
            Files.writeString(file, "{\"n\":1}\n{\"n\":2}\n")
            val tail = JsonlTail(file)
            collect(tail)

            Files.writeString(file, "{\"n\":9}\n")

            assertEquals(listOf(9), collect(tail))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `broken lines and blanks are skipped`() {
        val file = Files.createTempFile("mterm-tail", ".jsonl")
        try {
            Files.writeString(file, "not json\n\n{\"n\":7}\n[1,2]\n")

            assertEquals(listOf(7), collect(JsonlTail(file)))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `a missing file yields nothing`() {
        val missing = Path.of(System.getProperty("java.io.tmpdir"), "mterm-missing-${System.nanoTime()}.jsonl")

        assertEquals(emptyList<Int>(), collect(JsonlTail(missing)))
    }

    @Test
    fun `multi byte characters keep the offset aligned`() {
        val file = Files.createTempFile("mterm-tail", ".jsonl")
        try {
            Files.writeString(file, "{\"n\":1,\"t\":\"zażółć gęślą jaźń\"}\n")
            val tail = JsonlTail(file)
            assertEquals(listOf(1), collect(tail))

            append(file, "{\"n\":2,\"t\":\"ćma\"}\n")

            assertEquals(listOf(2), collect(tail))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private fun collect(tail: JsonlTail): List<Int> {
        val seen = mutableListOf<Int>()
        tail.readNew { entry -> entry.get("n")?.asInt?.let { seen += it } }
        return seen
    }

    private fun append(file: Path, text: String) {
        Files.writeString(file, text, StandardOpenOption.APPEND)
    }
}
