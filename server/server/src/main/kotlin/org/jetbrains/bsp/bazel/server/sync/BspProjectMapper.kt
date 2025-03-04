package org.jetbrains.bsp.bazel.server.sync

import ch.epfl.scala.bsp4j.BuildTarget
import ch.epfl.scala.bsp4j.BuildTargetCapabilities
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.CompileProvider
import ch.epfl.scala.bsp4j.CppOptionsItem
import ch.epfl.scala.bsp4j.CppOptionsParams
import ch.epfl.scala.bsp4j.CppOptionsResult
import ch.epfl.scala.bsp4j.DependencyModule
import ch.epfl.scala.bsp4j.DependencyModuleDataKind
import ch.epfl.scala.bsp4j.DependencyModulesItem
import ch.epfl.scala.bsp4j.DependencyModulesParams
import ch.epfl.scala.bsp4j.DependencyModulesResult
import ch.epfl.scala.bsp4j.DependencySourcesItem
import ch.epfl.scala.bsp4j.DependencySourcesParams
import ch.epfl.scala.bsp4j.DependencySourcesResult
import ch.epfl.scala.bsp4j.InitializeBuildResult
import ch.epfl.scala.bsp4j.InverseSourcesParams
import ch.epfl.scala.bsp4j.InverseSourcesResult
import ch.epfl.scala.bsp4j.JavacOptionsItem
import ch.epfl.scala.bsp4j.JavacOptionsParams
import ch.epfl.scala.bsp4j.JavacOptionsResult
import ch.epfl.scala.bsp4j.JvmCompileClasspathItem
import ch.epfl.scala.bsp4j.JvmCompileClasspathParams
import ch.epfl.scala.bsp4j.JvmCompileClasspathResult
import ch.epfl.scala.bsp4j.JvmEnvironmentItem
import ch.epfl.scala.bsp4j.JvmMainClass
import ch.epfl.scala.bsp4j.JvmRunEnvironmentParams
import ch.epfl.scala.bsp4j.JvmRunEnvironmentResult
import ch.epfl.scala.bsp4j.JvmTestEnvironmentParams
import ch.epfl.scala.bsp4j.JvmTestEnvironmentResult
import ch.epfl.scala.bsp4j.OutputPathItem
import ch.epfl.scala.bsp4j.OutputPathItemKind
import ch.epfl.scala.bsp4j.OutputPathsItem
import ch.epfl.scala.bsp4j.OutputPathsParams
import ch.epfl.scala.bsp4j.OutputPathsResult
import ch.epfl.scala.bsp4j.PythonOptionsItem
import ch.epfl.scala.bsp4j.PythonOptionsParams
import ch.epfl.scala.bsp4j.PythonOptionsResult
import ch.epfl.scala.bsp4j.ResourcesItem
import ch.epfl.scala.bsp4j.ResourcesParams
import ch.epfl.scala.bsp4j.ResourcesResult
import ch.epfl.scala.bsp4j.RunProvider
import ch.epfl.scala.bsp4j.RustWorkspaceParams
import ch.epfl.scala.bsp4j.RustWorkspaceResult
import ch.epfl.scala.bsp4j.ScalaMainClassesParams
import ch.epfl.scala.bsp4j.ScalaMainClassesResult
import ch.epfl.scala.bsp4j.ScalaTestClassesParams
import ch.epfl.scala.bsp4j.ScalaTestClassesResult
import ch.epfl.scala.bsp4j.ScalacOptionsItem
import ch.epfl.scala.bsp4j.ScalacOptionsParams
import ch.epfl.scala.bsp4j.ScalacOptionsResult
import ch.epfl.scala.bsp4j.SourceItemKind
import ch.epfl.scala.bsp4j.SourcesItem
import ch.epfl.scala.bsp4j.SourcesParams
import ch.epfl.scala.bsp4j.SourcesResult
import ch.epfl.scala.bsp4j.TestProvider
import ch.epfl.scala.bsp4j.WorkspaceBuildTargetsResult
import org.eclipse.lsp4j.jsonrpc.CancelChecker
import org.jetbrains.bazel.commons.constants.Constants
import org.jetbrains.bazel.commons.label.Label
import org.jetbrains.bazel.commons.label.label
import org.jetbrains.bazel.commons.label.toBspIdentifier
import org.jetbrains.bsp.bazel.bazelrunner.BazelRunner
import org.jetbrains.bsp.bazel.server.bsp.info.BspInfo
import org.jetbrains.bsp.bazel.server.bzlmod.BzlmodRepoMapping
import org.jetbrains.bsp.bazel.server.bzlmod.RepoMappingDisabled
import org.jetbrains.bsp.bazel.server.model.AspectSyncProject
import org.jetbrains.bsp.bazel.server.model.BspMappings
import org.jetbrains.bsp.bazel.server.model.Language
import org.jetbrains.bsp.bazel.server.model.Module
import org.jetbrains.bsp.bazel.server.model.NonModuleTarget
import org.jetbrains.bsp.bazel.server.model.Project
import org.jetbrains.bsp.bazel.server.model.Tag
import org.jetbrains.bsp.bazel.server.paths.BazelPathsResolver
import org.jetbrains.bsp.bazel.server.sync.languages.LanguagePluginsService
import org.jetbrains.bsp.bazel.server.sync.languages.java.IdeClasspathResolver
import org.jetbrains.bsp.bazel.server.sync.languages.java.JavaModule
import org.jetbrains.bsp.bazel.server.sync.languages.jvm.javaModule
import org.jetbrains.bsp.bazel.server.sync.languages.scala.ScalaModule
import org.jetbrains.bsp.bazel.workspacecontext.WorkspaceContextProvider
import org.jetbrains.bsp.protocol.BazelBuildServerCapabilities
import org.jetbrains.bsp.protocol.BazelResolveLocalToRemoteParams
import org.jetbrains.bsp.protocol.BazelResolveLocalToRemoteResult
import org.jetbrains.bsp.protocol.BazelResolveRemoteToLocalParams
import org.jetbrains.bsp.protocol.BazelResolveRemoteToLocalResult
import org.jetbrains.bsp.protocol.DirectoryItem
import org.jetbrains.bsp.protocol.EnhancedSourceItem
import org.jetbrains.bsp.protocol.GoLibraryItem
import org.jetbrains.bsp.protocol.JvmBinaryJarsItem
import org.jetbrains.bsp.protocol.JvmBinaryJarsParams
import org.jetbrains.bsp.protocol.JvmBinaryJarsResult
import org.jetbrains.bsp.protocol.LibraryItem
import org.jetbrains.bsp.protocol.NonModuleTargetsResult
import org.jetbrains.bsp.protocol.WorkspaceBazelRepoMappingResult
import org.jetbrains.bsp.protocol.WorkspaceDirectoriesResult
import org.jetbrains.bsp.protocol.WorkspaceGoLibrariesResult
import org.jetbrains.bsp.protocol.WorkspaceInvalidTargetsResult
import org.jetbrains.bsp.protocol.WorkspaceLibrariesResult
import java.io.IOException
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.relativeToOrNull
import kotlin.io.path.toPath

class BspProjectMapper(
  private val languagePluginsService: LanguagePluginsService,
  private val workspaceContextProvider: WorkspaceContextProvider,
  private val bazelPathsResolver: BazelPathsResolver,
  private val bazelRunner: BazelRunner,
  private val bspInfo: BspInfo,
) {
  fun initializeServer(supportedLanguages: Set<Language>): InitializeBuildResult {
    val languageNames = supportedLanguages.map { it.id }
    val capabilities =
      BazelBuildServerCapabilities(
        compileProvider = CompileProvider(languageNames),
        runProvider = RunProvider(languageNames),
        testProvider = TestProvider(languageNames),
        outputPathsProvider = true,
        dependencySourcesProvider = true,
        dependencyModulesProvider = true,
        inverseSourcesProvider = true,
        resourcesProvider = true,
        jvmRunEnvironmentProvider = true,
        jvmTestEnvironmentProvider = true,
        workspaceLibrariesProvider = true,
        goDebuggerDataProvider = true,
        workspaceDirectoriesProvider = true,
        workspaceNonModuleTargetsProvider = true,
        workspaceInvalidTargetsProvider = true,
        runWithDebugProvider = true,
        testWithDebugProvider = true,
        jvmBinaryJarsProvider = true,
        jvmCompileClasspathProvider = true,
        bazelRepoMappingProvider = true,
      )
    return InitializeBuildResult(
      Constants.NAME,
      Constants.VERSION,
      Constants.BSP_VERSION,
      capabilities,
    )
  }

  fun workspaceTargets(project: AspectSyncProject): WorkspaceBuildTargetsResult {
    val buildTargets = project.modules.map { it.toBuildTarget() }
    return WorkspaceBuildTargetsResult(buildTargets)
  }

  fun workspaceInvalidTargets(project: AspectSyncProject): WorkspaceInvalidTargetsResult =
    WorkspaceInvalidTargetsResult(project.invalidTargets.map { BuildTargetIdentifier(it.toString()) })

  fun workspaceLibraries(project: AspectSyncProject): WorkspaceLibrariesResult {
    val libraries =
      project.libraries.values.map {
        LibraryItem(
          id = BuildTargetIdentifier(it.label.toString()),
          dependencies = it.dependencies.map { dep -> BuildTargetIdentifier(dep.toString()) },
          ijars = it.interfaceJars.map { uri -> uri.toString() },
          jars = it.outputs.map { uri -> uri.toString() },
          sourceJars = it.sources.map { uri -> uri.toString() },
          mavenCoordinates = it.mavenCoordinates,
        )
      }
    return WorkspaceLibrariesResult(libraries)
  }

  fun workspaceGoLibraries(project: AspectSyncProject): WorkspaceGoLibrariesResult {
    val libraries =
      project.goLibraries.values.map {
        GoLibraryItem(
          id = BuildTargetIdentifier(it.label.toString()),
          goImportPath = it.goImportPath,
          goRoot = it.goRoot,
        )
      }
    return WorkspaceGoLibrariesResult(libraries)
  }

  fun workspaceNonModuleTargets(project: AspectSyncProject): NonModuleTargetsResult {
    val nonModuleTargets =
      project.nonModuleTargets.map {
        it.toBuildTarget()
      }
    return NonModuleTargetsResult(nonModuleTargets)
  }

  fun workspaceDirectories(project: Project): WorkspaceDirectoriesResult {
    val workspaceContext = workspaceContextProvider.currentWorkspaceContext()
    val directoriesSection = workspaceContext.directories

    val symlinksToExclude = computeSymlinksToExclude(project.workspaceRoot)
    val additionalDirectoriesToExclude = computeAdditionalDirectoriesToExclude()
    val directoriesToExclude = directoriesSection.excludedValues + symlinksToExclude + additionalDirectoriesToExclude

    return WorkspaceDirectoriesResult(
      includedDirectories = directoriesSection.values.map { it.toDirectoryItem() },
      excludedDirectories = directoriesToExclude.map { it.toDirectoryItem() },
    )
  }

  fun workspaceBazelRepoMapping(project: Project): WorkspaceBazelRepoMappingResult {
    val repoMapping = project.repoMapping
    return when (repoMapping) {
      is RepoMappingDisabled -> WorkspaceBazelRepoMappingResult(emptyMap(), emptyMap())
      is BzlmodRepoMapping ->
        WorkspaceBazelRepoMappingResult(
          apparentRepoNameToCanonicalName = repoMapping.apparentRepoNameToCanonicalName,
          canonicalRepoNameToPath =
            repoMapping.canonicalRepoNameToPath.mapValues { (_, path) ->
              path.toUri().toString()
            },
        )
    }
  }

  private fun computeSymlinksToExclude(workspaceRoot: URI): List<Path> {
    val stableSymlinkNames = setOf("bazel-out", "bazel-testlogs", "bazel-bin")
    val workspaceRootPath = workspaceRoot.toPath()
    val workspaceSymlinkNames = setOf("bazel-${workspaceRootPath.name}")

    val symlinks = (stableSymlinkNames + workspaceSymlinkNames).map { workspaceRootPath.resolve(it) }
    val realPaths =
      symlinks.mapNotNull {
        try {
          it.toRealPath()
        } catch (e: IOException) {
          null
        }
      }
    return symlinks + realPaths
  }

  private fun computeAdditionalDirectoriesToExclude(): List<Path> = listOf(bspInfo.bazelBspDir())

  private fun Path.toDirectoryItem() =
    DirectoryItem(
      uri = this.toUri().toString(),
    )

  private fun NonModuleTarget.toBuildTarget(): BuildTarget {
    val languages = languages.flatMap(Language::allNames).distinct()
    val capabilities = inferCapabilities(tags)
    val tags = tags.mapNotNull(BspMappings::toBspTag)
    val baseDirectory = BspMappings.toBspUri(baseDirectory)
    val buildTarget =
      BuildTarget(
        label.toBspIdentifier(),
        tags,
        languages,
        emptyList(),
        capabilities,
      )
    buildTarget.displayName = label.toString()
    buildTarget.baseDirectory = baseDirectory
    return buildTarget
  }

  private fun Module.toBuildTarget(): BuildTarget {
    val label = BspMappings.toBspId(this)
    val dependencies =
      directDependencies.map { it.toBspIdentifier() }
    val languages = languages.flatMap(Language::allNames).distinct()
    val capabilities = inferCapabilities(tags)
    val tags = tags.mapNotNull(BspMappings::toBspTag)
    val baseDirectory = BspMappings.toBspUri(baseDirectory)
    val buildTarget =
      BuildTarget(
        label,
        tags,
        languages,
        dependencies,
        capabilities,
      )
    buildTarget.displayName = label.uri
    buildTarget.baseDirectory = baseDirectory
    applyLanguageData(this, buildTarget)
    return buildTarget
  }

  private fun inferCapabilities(tags: Set<Tag>): BuildTargetCapabilities {
    val canCompile = !tags.contains(Tag.NO_BUILD)
    val canTest = tags.contains(Tag.TEST)
    val canRun = tags.contains(Tag.APPLICATION)
    // Native-BSP debug is not supported with Bazel.
    // It simply means that the `debugSession/start` method should not be called on any Bazel target.
    // Enabling client-side debugging (for example, for JVM targets via JDWP) is up to the client.
    val canDebug = false
    return BuildTargetCapabilities().also {
      it.canCompile = canCompile
      it.canTest = canTest
      it.canRun = canRun
      it.canDebug = canDebug
    }
  }

  private fun applyLanguageData(module: Module, buildTarget: BuildTarget) {
    val plugin = languagePluginsService.getPlugin(module.languages)
    module.languageData?.let { plugin.setModuleData(it, buildTarget) }
  }

  fun sources(project: AspectSyncProject, sourcesParams: SourcesParams): SourcesResult {
    fun toSourcesItem(module: Module): SourcesItem {
      val sourceSet = module.sourceSet
      val sourceItems =
        sourceSet.sources.map {
          EnhancedSourceItem(
            uri = it.source.toString(),
            kind = SourceItemKind.FILE,
            generated = false,
            data = it.data,
          )
        }
      val generatedSourceItems =
        sourceSet.generatedSources.map {
          EnhancedSourceItem(
            uri = it.source.toString(),
            kind = SourceItemKind.FILE,
            generated = true,
            data = it.data,
          )
        }
      val sourceRoots = sourceSet.sourceRoots.map(BspMappings::toBspUri)
      val sourcesItem = SourcesItem(BspMappings.toBspId(module), sourceItems + generatedSourceItems)
      sourcesItem.roots = sourceRoots
      return sourcesItem
    }

    fun emptySourcesItem(label: Label): SourcesItem = SourcesItem(label.toBspIdentifier(), emptyList())

    val labels = BspMappings.toLabels(sourcesParams.targets)
    val sourcesItems =
      labels.map {
        project.findModule(it)?.let(::toSourcesItem) ?: emptySourcesItem(it)
      }
    return SourcesResult(sourcesItems)
  }

  fun resources(project: AspectSyncProject, resourcesParams: ResourcesParams): ResourcesResult {
    fun toResourcesItem(module: Module): ResourcesItem {
      val resources = module.resources.map(BspMappings::toBspUri)
      return ResourcesItem(BspMappings.toBspId(module), resources)
    }

    fun emptyResourcesItem(label: Label): ResourcesItem = ResourcesItem(label.toBspIdentifier(), emptyList())

    val labels = BspMappings.toLabels(resourcesParams.targets)
    val resourcesItems =
      labels.map {
        project.findModule(it)?.let(::toResourcesItem) ?: emptyResourcesItem(it)
      }
    return ResourcesResult(resourcesItems)
  }

  fun inverseSources(
    project: AspectSyncProject,
    inverseSourcesParams: InverseSourcesParams,
    cancelChecker: CancelChecker,
  ): InverseSourcesResult {
    val documentUri = BspMappings.toUri(inverseSourcesParams.textDocument)
    val documentRelativePath =
      documentUri
        .toPath()
        .relativeToOrNull(project.workspaceRoot.toPath()) ?: throw RuntimeException("File path outside of project root")
    return InverseSourcesQuery.inverseSourcesQuery(documentRelativePath, bazelRunner, project.bazelRelease, cancelChecker)
  }

  fun dependencySources(project: AspectSyncProject, dependencySourcesParams: DependencySourcesParams): DependencySourcesResult {
    fun getDependencySourcesItem(label: Label): DependencySourcesItem {
      val sources =
        project
          .findModule(label)
          ?.sourceDependencies
          ?.map(BspMappings::toBspUri)
          .orEmpty()
      return DependencySourcesItem(label.toBspIdentifier(), sources)
    }

    val labels = BspMappings.toLabels(dependencySourcesParams.targets)
    val items = labels.map(::getDependencySourcesItem)
    return DependencySourcesResult(items)
  }

  fun outputPaths(project: AspectSyncProject, params: OutputPathsParams): OutputPathsResult {
    fun getItem(label: Label): OutputPathsItem {
      val items =
        project
          .findModule(label)
          ?.let { module ->
            module.outputs.map { OutputPathItem(BspMappings.toBspUri(it), OutputPathItemKind.DIRECTORY) }
          }.orEmpty()
      return OutputPathsItem(label.toBspIdentifier(), items)
    }

    val labels = BspMappings.toLabels(params.targets)
    val items = labels.map(::getItem)
    return OutputPathsResult(items)
  }

  fun jvmRunEnvironment(
    project: AspectSyncProject,
    params: JvmRunEnvironmentParams,
    cancelChecker: CancelChecker,
  ): JvmRunEnvironmentResult {
    val targets = params.targets
    val result = getJvmEnvironmentItems(project, targets, cancelChecker)
    return JvmRunEnvironmentResult(result)
  }

  fun jvmTestEnvironment(
    project: AspectSyncProject,
    params: JvmTestEnvironmentParams,
    cancelChecker: CancelChecker,
  ): JvmTestEnvironmentResult {
    val targets = params.targets
    val result = getJvmEnvironmentItems(project, targets, cancelChecker)
    return JvmTestEnvironmentResult(result)
  }

  fun jvmCompileClasspath(
    project: AspectSyncProject,
    params: JvmCompileClasspathParams,
    cancelChecker: CancelChecker,
  ): JvmCompileClasspathResult {
    val items =
      params.targets.collectClasspathForTargetsAndApply(project, true, cancelChecker) { module, ideClasspath ->
        JvmCompileClasspathItem(BspMappings.toBspId(module), ideClasspath.map { it.toString() })
      }
    return JvmCompileClasspathResult(items)
  }

  private fun getJvmEnvironmentItems(
    project: AspectSyncProject,
    targets: List<BuildTargetIdentifier>,
    cancelChecker: CancelChecker,
  ): List<JvmEnvironmentItem> {
    fun extractJvmEnvironmentItem(module: Module, runtimeClasspath: List<URI>): JvmEnvironmentItem? =
      module.javaModule?.let { javaModule ->
        JvmEnvironmentItem(
          BspMappings.toBspId(module),
          runtimeClasspath.map { it.toString() },
          javaModule.jvmOps.toList(),
          bazelPathsResolver.unresolvedWorkspaceRoot().toString(),
          module.environmentVariables,
        ).apply {
          mainClasses = javaModule.mainClass?.let { listOf(JvmMainClass(it, javaModule.args)) }.orEmpty()
        }
      }

    return targets.mapNotNull {
      val label = it.label()
      val module = project.findModule(label)
      val cqueryResult = ClasspathQuery.classPathQuery(label, cancelChecker, bspInfo, bazelRunner).runtime_classpath
      val resolvedClasspath = resolveClasspath(cqueryResult)
      module?.let { extractJvmEnvironmentItem(module, resolvedClasspath) }
    }
  }

  fun jvmBinaryJars(project: AspectSyncProject, params: JvmBinaryJarsParams): JvmBinaryJarsResult {
    fun toJvmBinaryJarsItem(module: Module): JvmBinaryJarsItem? =
      module.javaModule?.let { javaModule ->
        val jars = javaModule.binaryOutputs.map { it.toString() }
        JvmBinaryJarsItem(BspMappings.toBspId(module), jars)
      }

    val jvmBinaryJarsItems =
      params.targets.mapNotNull { target ->
        val label = Label.parse(target.uri)
        val module = project.findModule(label)
        module?.let { toJvmBinaryJarsItem(it) }
      }
    return JvmBinaryJarsResult(jvmBinaryJarsItems)
  }

  fun buildTargetJavacOptions(
    project: AspectSyncProject,
    params: JavacOptionsParams,
    includeClasspath: Boolean,
    cancelChecker: CancelChecker,
  ): JavacOptionsResult {
    val items =
      params.targets.collectClasspathForTargetsAndApply(project, includeClasspath, cancelChecker) { module, ideClasspath ->
        module.javaModule?.let { toJavacOptionsItem(module, it, ideClasspath) }
      }
    return JavacOptionsResult(items)
  }

  fun buildTargetCppOptions(project: AspectSyncProject, params: CppOptionsParams): CppOptionsResult {
    fun extractCppOptionsItem(module: Module): CppOptionsItem? =
      languagePluginsService.extractCppModule(module)?.let {
        languagePluginsService.cppLanguagePlugin.toCppOptionsItem(module, it)
      }

    val modules = BspMappings.getModules(project, params.targets)
    val items = modules.mapNotNull(::extractCppOptionsItem)
    return CppOptionsResult(items)
  }

  fun buildTargetPythonOptions(project: AspectSyncProject, params: PythonOptionsParams): PythonOptionsResult {
    val modules = BspMappings.getModules(project, params.targets)
    val items = modules.mapNotNull(::extractPythonOptionsItem)
    return PythonOptionsResult(items)
  }

  private fun extractPythonOptionsItem(module: Module): PythonOptionsItem? =
    languagePluginsService.extractPythonModule(module)?.let {
      languagePluginsService.pythonLanguagePlugin.toPythonOptionsItem(module, it)
    }

  fun buildTargetScalacOptions(
    project: AspectSyncProject,
    params: ScalacOptionsParams,
    includeClasspath: Boolean,
    cancelChecker: CancelChecker,
  ): ScalacOptionsResult {
    val items =
      params.targets.collectClasspathForTargetsAndApply(project, includeClasspath, cancelChecker) { module, ideClasspath ->
        toScalacOptionsItem(module, ideClasspath)
      }
    return ScalacOptionsResult(items)
  }

  private fun <T> List<BuildTargetIdentifier>.collectClasspathForTargetsAndApply(
    project: AspectSyncProject,
    includeClasspath: Boolean,
    cancelChecker: CancelChecker,
    mapper: (Module, List<URI>) -> T?,
  ): List<T> =
    this
      .mapNotNull { project.findModule(Label.parse(it.uri)) }
      .mapNotNull {
        val classpath = if (includeClasspath) readIdeClasspath(it.label, cancelChecker) else emptyList()
        mapper(it, classpath)
      }

  private fun readIdeClasspath(targetLabel: Label, cancelChecker: CancelChecker): List<URI> {
    val classPathFromQuery = ClasspathQuery.classPathQuery(targetLabel, cancelChecker, bspInfo, bazelRunner)
    val ideClasspath =
      IdeClasspathResolver.resolveIdeClasspath(
        label = targetLabel,
        runtimeClasspath = resolveClasspath(classPathFromQuery.runtime_classpath),
        compileClasspath = resolveClasspath(classPathFromQuery.compile_classpath),
      )
    return ideClasspath
  }

  private fun resolveClasspath(cqueryResult: List<String>) =
    cqueryResult
      .map { bazelPathsResolver.resolveOutput(Paths.get(it)) }
      .filter { it.toFile().exists() } // I'm surprised this is needed, but we literally test it in e2e tests
      .map { it.toUri() }

  private fun toScalacOptionsItem(module: Module, ideClasspath: List<URI>): ScalacOptionsItem? =
    (module.languageData as? ScalaModule)?.let { scalaModule ->
      scalaModule.javaModule?.let { javaModule ->
        val javacOptions = toJavacOptionsItem(module, javaModule, ideClasspath)
        ScalacOptionsItem(
          javacOptions.target,
          scalaModule.scalacOpts,
          javacOptions.classpath,
          javacOptions.classDirectory,
        )
      }
    }

  private fun toJavacOptionsItem(
    module: Module,
    javaModule: JavaModule,
    ideClasspath: List<URI>,
  ): JavacOptionsItem =
    JavacOptionsItem(
      BspMappings.toBspId(module),
      javaModule.javacOpts.toList(),
      ideClasspath.map { it.toString() },
      javaModule.mainOutput.toString(),
    )

  fun buildTargetScalaTestClasses(project: AspectSyncProject, params: ScalaTestClassesParams): ScalaTestClassesResult {
    val modules = BspMappings.getModules(project, params.targets)
    val scalaLanguagePlugin = languagePluginsService.scalaLanguagePlugin
    val items = modules.mapNotNull(scalaLanguagePlugin::toScalaTestClassesItem)
    return ScalaTestClassesResult(items)
  }

  fun buildTargetScalaMainClasses(project: AspectSyncProject, params: ScalaMainClassesParams): ScalaMainClassesResult {
    val modules = BspMappings.getModules(project, params.targets)
    val scalaLanguagePlugin = languagePluginsService.scalaLanguagePlugin
    val items = modules.mapNotNull(scalaLanguagePlugin::toScalaMainClassesItem)
    return ScalaMainClassesResult(items)
  }

  fun buildDependencyModules(project: AspectSyncProject, params: DependencyModulesParams): DependencyModulesResult =
    buildDependencyModulesStatic(project, params)

  fun rustWorkspace(project: AspectSyncProject, params: RustWorkspaceParams): RustWorkspaceResult {
    val allRustModules = project.modules.filter { Language.RUST in it.languages }
    val requestedModules =
      BspMappings
        .getModules(project, params.targets)
        .filter { Language.RUST in it.languages }
    val toRustWorkspaceResult = languagePluginsService.rustLanguagePlugin::toRustWorkspaceResult

    return toRustWorkspaceResult(requestedModules, allRustModules)
  }

  fun resolveLocalToRemote(cancelChecker: CancelChecker, params: BazelResolveLocalToRemoteParams): BazelResolveLocalToRemoteResult {
    val resolve = languagePluginsService.goLanguagePlugin::resolveLocalToRemote
    return resolve(params)
  }

  fun resolveRemoteToLocal(cancelChecker: CancelChecker, params: BazelResolveRemoteToLocalParams): BazelResolveRemoteToLocalResult {
    val resolve = languagePluginsService.goLanguagePlugin::resolveRemoteToLocal
    return resolve(params)
  }

  companion object {
    @JvmStatic
    fun buildDependencyModulesStatic(project: AspectSyncProject, params: DependencyModulesParams): DependencyModulesResult {
      val targetSet = params.targets.toSet()
      val cache = mutableMapOf<String, List<DependencyModule>>()
      val dependencyModulesItems =
        project.modules.filter { targetSet.contains(BuildTargetIdentifier(it.label.toString())) }.map { module ->
          val buildTargetId = BuildTargetIdentifier(module.label.toString())
          val moduleDependencies = DependencyMapper.allModuleDependencies(project, module)
          // moduleDependencies are sorted here to have a deterministic output (used in tests) and not strictly necessary.
          val moduleItems =
            moduleDependencies.sortedBy { it.label.toString() }.flatMap { libraryDep ->
              cache.getOrPut(libraryDep.label.toString()) {
                if (libraryDep.outputs.isNotEmpty()) {
                  val mavenDependencyModule = DependencyMapper.extractMavenDependencyInfo(libraryDep)
                  val dependencyModule = DependencyModule(libraryDep.label.toString(), mavenDependencyModule?.version ?: "")
                  if (mavenDependencyModule != null) {
                    dependencyModule.data = mavenDependencyModule
                    dependencyModule.dataKind = DependencyModuleDataKind.MAVEN
                  }
                  listOf(dependencyModule)
                } else {
                  emptyList()
                }
              }
            }
          DependencyModulesItem(buildTargetId, moduleItems)
        }
      return DependencyModulesResult(dependencyModulesItems)
    }
  }
}
