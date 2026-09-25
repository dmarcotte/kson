import org.gradle.api.GradleException
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * A package we build ourselves that a Node/TS project installs as a `file:` dependency on [packageDirectory].
 *
 * pnpm hardlinks a `file:` dependency into `node_modules` once and then leaves it alone. That copy goes stale
 * as soon as [packageDirectory] is rebuilt into new files (a `clean`, say); reimporting it is what
 * [PixiExecTask.reinstallLocallyBuiltPackages] does around an install task.
 */
class LocallyBuiltPackage(
    /** the name the package is installed under in `node_modules` */
    val name: String,
    /** the directory the `file:` dependency points at, relative to the repository root */
    val packageDirectory: String,
    /** the path of the Gradle task which builds [packageDirectory] */
    val buildTask: String
) {
    /** The copy of this package installed under [nodeModules], for a repository checked out at [rootDir] */
    fun installedUnder(nodeModules: File, rootDir: File) =
        InstalledPackageCopy(name, nodeModules.resolve(name), rootDir.resolve(packageDirectory))

    companion object {
        /** The Kotlin/JS build of the core library */
        val KSON = LocallyBuiltPackage(
            "kson",
            "kson-lib/build/dist/js/productionLibrary",
            ":kson-lib:jsNodeProductionLibraryDistribution"
        )

        /** The Kotlin/JS build of the tooling library */
        val KSON_TOOLING = LocallyBuiltPackage(
            "kson-tooling",
            "kson-tooling-lib/build/dist/js/productionLibrary",
            ":kson-tooling-lib:jsNodeProductionLibraryDistribution"
        )

        /** The language server, whose package directory is its whole project, compiled into `out/` */
        val KSON_LANGUAGE_SERVER = LocallyBuiltPackage(
            "kson-language-server",
            "tooling/language-server-protocol",
            ":tooling:language-server-protocol:npm_run_compile"
        )
    }
}

/**
 * The copy of the package named [name] which an install imports into [installed] from [source]
 */
class InstalledPackageCopy(private val name: String, private val installed: File, private val source: File) {
    /**
     * Remove the installed copy, so that the next install imports the package afresh from [source].
     *
     * Only the copy is removed: a symlinked [installed] is unlinked rather than followed, and nothing
     * under [source] is touched.
     */
    fun remove() {
        if (!Files.exists(installed.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return
        }

        Files.walkFileTree(installed.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                exc?.let { throw it }
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    /**
     * Check that the installed copy matches [source]: every file in it must exist in [source] with the same
     * bytes. A nested `node_modules` belongs to the package manager, not the package, and is skipped.
     *
     * @throws GradleException naming the package, the copy and the source, if the copy is missing or stale
     */
    fun verifyCurrent() {
        if (!installed.isDirectory) {
            throw GradleException("`$name` is not installed at $installed")
        }

        val staleFiles = installedFiles().filter { !matchesSource(it) }
        if (staleFiles.isNotEmpty()) {
            throw GradleException(
                "`$name` installed at $installed does not match the package at $source. " +
                        "Files differing or missing from the package: ${staleFiles.joinToString(", ")}"
            )
        }
    }

    /** The files of the installed copy, as paths relative to [installed], in a stable order */
    private fun installedFiles(): List<String> {
        val files = mutableListOf<String>()
        val root = installed.toPath()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir != root && dir.fileName.toString() == NODE_MODULES) {
                    FileVisitResult.SKIP_SUBTREE
                } else {
                    FileVisitResult.CONTINUE
                }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                files.add(root.relativize(file).toString())
                return FileVisitResult.CONTINUE
            }
        })
        return files.sorted()
    }

    private fun matchesSource(relativePath: String): Boolean {
        val sourceFile = source.resolve(relativePath)
        return sourceFile.isFile && Files.mismatch(installed.resolve(relativePath).toPath(), sourceFile.toPath()) == -1L
    }

    private companion object {
        const val NODE_MODULES = "node_modules"
    }
}

/**
 * Make this install task reinstall [packages] fresh from their current builds on every run, failing if any
 * installed copy doesn't end up matching its build.
 */
fun PixiExecTask.reinstallLocallyBuiltPackages(vararg packages: LocallyBuiltPackage) {
    packages.forEach { dependsOn(it.buildTask) }

    fun installedCopies(): List<InstalledPackageCopy> {
        val nodeModules = workingDirectory.orNull?.asFile?.resolve("node_modules")
            ?: project.file("node_modules")
        return packages.map { it.installedUnder(nodeModules, project.rootDir) }
    }

    doFirst { installedCopies().forEach { it.remove() } }
    doLast { installedCopies().forEach { it.verifyCurrent() } }
}
