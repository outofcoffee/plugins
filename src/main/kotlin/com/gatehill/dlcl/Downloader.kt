package com.gatehill.dlcl

import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader
import org.apache.maven.repository.internal.DefaultVersionRangeResolver
import org.apache.maven.repository.internal.DefaultVersionResolver
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.Exclusion
import org.eclipse.aether.impl.ArtifactDescriptorReader
import org.eclipse.aether.impl.DefaultServiceLocator
import org.eclipse.aether.impl.VersionRangeResolver
import org.eclipse.aether.impl.VersionResolver
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.artifact.JavaScopes
import org.eclipse.aether.util.filter.DependencyFilterUtils
import org.eclipse.aether.util.filter.ExclusionsDependencyFilter
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Downloads dependencies from Maven repositories.
 *
 * @author pete
 */
class Downloader(repoBaseDir: String,
                 private val root: String,
                 private val excludes: List<Exclusion> = emptyList(),
                 private val repositories: List<Pair<String, String>> = listOf(mavenCentral)) {

    private val repoDir: Path = Paths.get(repoBaseDir).toAbsolutePath()
    private val system = newRepositorySystem()
    private val session = newRepositorySystemSession(system, repoDir.toString())
    private val artifactCache = mutableListOf<Artifact>()

    fun download(coordinates: String = root, scope: String = JavaScopes.RUNTIME) =
            download(DefaultArtifact(coordinates), scope)

    fun download(artifact: Artifact, scope: String = JavaScopes.RUNTIME) {
        if (artifactCache.contains(artifact)) {
            println("Already downloaded: $artifact")
            return
        } else {
            artifactCache += artifact
        }
        println("Downloading: $artifact")

        val classpathFilter = DependencyFilterUtils.andFilter(
                DependencyFilterUtils.classpathFilter(scope),
                ExclusionsDependencyFilter(excludes.map { "${it.groupId}:${it.artifactId}" })
        )

        val collectRequest = CollectRequest()
        collectRequest.root = Dependency(artifact, scope)
        collectRequest.repositories = newRepositories()

        val dependencyRequest = DependencyRequest(collectRequest, classpathFilter)
        val dependencyResult = system.resolveDependencies(session, dependencyRequest)

        dependencyResult.artifactResults.forEach {
            println("Resolved: ${it.artifact} to: ${it.artifact.file}")
        }

        // ignore the excluded children of this dependency, as well as those explicitly specified
        val allExclusions = excludes.toMutableList().apply {
            addAll(dependencyResult.root.dependency.exclusions)
        }

        dependencyResult.root.children
                .filterNot { child -> allExclusions.any { child.artifact.groupId == it.groupId && child.artifact.artifactId == it.artifactId } }
                .filter { scope == it.dependency.scope }
                .forEach { child -> download(child.artifact) }
    }

    private fun newRepositorySystem(): RepositorySystem {
        val locator = DefaultServiceLocator()
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(VersionResolver::class.java, DefaultVersionResolver::class.java)
        locator.addService(VersionRangeResolver::class.java, DefaultVersionRangeResolver::class.java)
        locator.addService(ArtifactDescriptorReader::class.java, DefaultArtifactDescriptorReader::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        locator.addService(TransporterFactory::class.java, FileTransporterFactory::class.java)
        return locator.getService(RepositorySystem::class.java)
    }

    private fun newRepositorySystemSession(system: RepositorySystem, repoBaseDir: String): DefaultRepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession()

        val localRepo = LocalRepository(repoBaseDir)
        session.localRepositoryManager = system.newLocalRepositoryManager(session, localRepo)

        return session
    }

    private fun newRepositories() = repositories.map { (id, url) -> RemoteRepository.Builder(id, "default", url).build() }

    fun clearRepo() {
        println("Clearing repo: $repoDir")
        repoDir.toFile().takeIf { it.exists() }?.deleteRecursively()
    }
}
