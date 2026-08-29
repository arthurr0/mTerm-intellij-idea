package dev.mterm

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal object TestGit {

    fun initRepository(): Path {
        val repo = Files.createTempDirectory("mterm-test-repo")
        run(repo, "init", "--initial-branch=main")
        run(repo, "config", "user.email", "mterm@example.com")
        run(repo, "config", "user.name", "mTerm Test")
        run(repo, "config", "commit.gpgsign", "false")
        return repo
    }

    fun write(repo: Path, relative: String, content: String) {
        val file = repo.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    fun read(repo: Path, relative: String): String = Files.readString(repo.resolve(relative))

    fun commitAll(repo: Path, message: String) {
        run(repo, "add", "-A")
        run(repo, "commit", "-m", message)
    }

    fun run(repo: Path, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git") + arguments)
            .directory(repo.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readAllBytes().decodeToString()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "git ${arguments.joinToString(" ")} timed out" }
        check(process.exitValue() == 0) { "git ${arguments.joinToString(" ")} failed: $output" }
        return output
    }

    fun delete(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.delete(it) } }
        }
    }
}
