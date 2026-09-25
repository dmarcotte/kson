import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** the name of the package under test, as it appears in the messages [InstalledPackageCopy] reports */
private const val PACKAGE_NAME = "kson"

class InstalledPackageCopyTest {
    @Test
    fun copyMatchingItsSourceIsCurrent() {
        val (installed, source) = installedAndSource()
        writePackage(source, "index.mjs" to "export const x = 1", "lib/util.mjs" to "export const y = 2")
        writePackage(installed, "index.mjs" to "export const x = 1", "lib/util.mjs" to "export const y = 2")

        installedCopy(installed, source).verifyCurrent()
    }

    @Test
    fun copyWithDifferingFileIsStale() {
        val (installed, source) = installedAndSource()
        writePackage(source, "index.mjs" to "export const x = 2", "lib/util.mjs" to "export const y = 2")
        writePackage(installed, "index.mjs" to "export const x = 1", "lib/util.mjs" to "export const y = 2")

        val failure = assertFailsWith<GradleException> { installedCopy(installed, source).verifyCurrent() }

        assertEquals(
            "`$PACKAGE_NAME` installed at $installed does not match the package at $source. " +
                    "Files differing or missing from the package: index.mjs",
            failure.message
        )
    }

    @Test
    fun copyWithFileMissingFromSourceIsStale() {
        val (installed, source) = installedAndSource()
        writePackage(source, "index.mjs" to "export const x = 1")
        writePackage(installed, "index.mjs" to "export const x = 1", "lib/removed.mjs" to "export const y = 2")

        val failure = assertFailsWith<GradleException> { installedCopy(installed, source).verifyCurrent() }

        assertEquals(
            "`$PACKAGE_NAME` installed at $installed does not match the package at $source. " +
                    "Files differing or missing from the package: lib/removed.mjs",
            failure.message
        )
    }

    @Test
    fun missingCopyIsReported() {
        val (installed, source) = installedAndSource()
        writePackage(source, "index.mjs" to "export const x = 1")

        val failure = assertFailsWith<GradleException> { installedCopy(installed, source).verifyCurrent() }

        assertEquals("`$PACKAGE_NAME` is not installed at $installed", failure.message)
    }

    @Test
    fun packageManagersNestedNodeModulesIsNotCompared() {
        val (installed, source) = installedAndSource()
        writePackage(source, "index.mjs" to "export const x = 1")
        writePackage(
            installed,
            "index.mjs" to "export const x = 1",
            "node_modules/dep/index.js" to "module.exports = {}"
        )

        installedCopy(installed, source).verifyCurrent()
    }

    @Test
    fun removeDeletesTheCopy() {
        val (installed, source) = installedAndSource()
        writePackage(installed, "index.mjs" to "export const x = 1", "lib/util.mjs" to "export const y = 2")

        installedCopy(installed, source).remove()

        assertFalse(installed.exists(), "The installed copy should be gone")
    }

    @Test
    fun removeOfSymlinkedCopyLeavesItsTargetIntact() {
        val (installed, source) = installedAndSource()
        val linkTarget = File(installed.parentFile, ".pnpm/$PACKAGE_NAME")
        writePackage(linkTarget, "index.mjs" to "export const x = 1")
        Files.createSymbolicLink(installed.toPath(), linkTarget.toPath())

        installedCopy(installed, source).remove()

        assertFalse(Files.isSymbolicLink(installed.toPath()), "The link should be gone")
        assertTrue(File(linkTarget, "index.mjs").isFile, "The link target should be untouched")
    }

    @Test
    fun removeOfAbsentCopyIsANoOp() {
        val (installed, source) = installedAndSource()

        installedCopy(installed, source).remove()

        assertFalse(installed.exists())
    }

    private fun installedCopy(installed: File, source: File) = InstalledPackageCopy(PACKAGE_NAME, installed, source)

    /** An `installed` location inside a fresh `node_modules` and a `source` package directory, both not yet written */
    private fun installedAndSource(): Pair<File, File> {
        val tempDir = createTempDirectory("InstalledPackageCopyTest").toFile()
        return Pair(File(tempDir, "node_modules/$PACKAGE_NAME"), File(tempDir, "build/$PACKAGE_NAME"))
    }

    /** Write [files], each a path relative to [packageDir] and its content */
    private fun writePackage(packageDir: File, vararg files: Pair<String, String>) {
        for ((path, content) in files) {
            val file = File(packageDir, path)
            file.parentFile.mkdirs()
            file.writeText(content)
        }
    }
}
