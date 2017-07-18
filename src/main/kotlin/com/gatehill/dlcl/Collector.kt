package com.gatehill.dlcl

import com.gatehill.dlcl.model.UniqueFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.function.BiPredicate
import java.util.stream.Collectors
import javax.xml.bind.DatatypeConverter

/**
 * Collects downloaded dependencies from the repository.
 *
 * @author pete
 */
class Collector(repoBaseDir: String) {
    private val repoDir: Path = Paths.get(repoBaseDir).toAbsolutePath()

    fun clearCollected() {
        println("Clearing repo: $repoDir")
        repoDir.toFile().takeIf { it.exists() }?.deleteRecursively()
    }

    fun collectJars(): List<UniqueFile> {
        println("Collecting JARs")

        return Files
                .find(repoDir, 10, BiPredicate { path, _ -> path.fileName.toString().endsWith(".jar") })
                .parallel()
                .map { UniqueFile(it, checksum(it)) }
                .distinct()
                .collect(Collectors.toList())
    }

    private fun checksum(file: Path): String {
        Files.newInputStream(file).use { stream ->
            val digest = MessageDigest.getInstance("MD5")
            val block = ByteArray(4096)

            while (true) stream.read(block).takeIf { it > 0 }
                    ?.let { length -> digest.update(block, 0, length) }
                    ?: break

            return DatatypeConverter.printHexBinary(digest.digest())
        }
    }
}
