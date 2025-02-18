package org.jetbrains.bsp.bazel.bazelrunner

import com.intellij.execution.configurations.GeneralCommandLine
import org.apache.logging.log4j.LogManager
import org.jetbrains.bazel.commons.constants.Constants
import org.jetbrains.bazel.label.Label
import org.jetbrains.bsp.bazel.bazelrunner.params.BazelFlag.enableWorkspace
import org.jetbrains.bsp.bazel.bazelrunner.params.BazelFlag.overrideRepository
import org.jetbrains.bsp.bazel.bazelrunner.utils.BazelInfo
import org.jetbrains.bsp.bazel.logger.BspClientLogger
import org.jetbrains.bsp.bazel.workspacecontext.WorkspaceContextProvider
import org.jetbrains.bsp.bazel.workspacecontext.extraFlags
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.pathString

class BazelRunner(
  val workspaceContextProvider: WorkspaceContextProvider,
  private val bspClientLogger: BspClientLogger?,
  val workspaceRoot: Path?,
  var bazelInfo: BazelInfo? = null,
) {
  companion object {
    private val LOGGER = LogManager.getLogger(BazelRunner::class.java)
  }

  inner class CommandBuilder {
    private val workspaceContext = workspaceContextProvider.currentWorkspaceContext()
    private val bazelBinary = workspaceContext.bazelBinary.value.pathString
    var inheritWorkspaceOptions = false

    fun clean(builder: BazelCommand.Clean.() -> Unit = {}) = BazelCommand.Clean(bazelBinary).apply { builder() }

    fun shutDown(builder: BazelCommand.ShutDown.() -> Unit = {}) = BazelCommand.ShutDown(bazelBinary).apply { builder() }

    fun info(builder: BazelCommand.Info.() -> Unit = {}) = BazelCommand.Info(bazelBinary).apply { builder() }

    fun version(builder: BazelCommand.Version.() -> Unit = {}) = BazelCommand.Version(bazelBinary).apply { builder() }

    fun run(target: Label, builder: BazelCommand.Run.() -> Unit = {}) =
      BazelCommand.Run(bazelBinary, target).apply { builder() }.also { inheritWorkspaceOptions = true }

    fun graph(builder: BazelCommand.ModGraph.() -> Unit = {}) = BazelCommand.ModGraph(bazelBinary).apply { builder() }

    fun path(builder: BazelCommand.ModPath.() -> Unit = {}) = BazelCommand.ModPath(bazelBinary).apply { builder() }

    fun showRepo(builder: BazelCommand.ModShowRepo.() -> Unit = {}) = BazelCommand.ModShowRepo(bazelBinary).apply { builder() }

    fun dumpRepoMapping(
      builder: BazelCommand.ModDumpRepoMapping.() -> Unit = {
      },
    ) = BazelCommand.ModDumpRepoMapping(bazelBinary).apply { builder() }

    fun query(allowManualTargetsSync: Boolean = true, builder: BazelCommand.Query.() -> Unit = {}) =
      BazelCommand.Query(bazelBinary, allowManualTargetsSync).apply { builder() }

    /** Special version of `query` for asking Bazel about a file instead of a target */
    fun fileQuery(filePath: Path, builder: BazelCommand.FileQuery.() -> Unit = {}) =
      BazelCommand.FileQuery(bazelBinary, filePath.toString()).apply { builder() }

    fun cquery(builder: BazelCommand.CQuery.() -> Unit = {}) =
      BazelCommand.CQuery(bazelBinary).apply { builder() }.also { inheritWorkspaceOptions = true }

    fun build(builder: BazelCommand.Build.() -> Unit = {}) =
      BazelCommand
        .Build(bazelInfo, bazelBinary)
        .apply { builder() }
        .also { inheritWorkspaceOptions = true }

    fun mobileInstall(target: Label, builder: BazelCommand.MobileInstall.() -> Unit = {}) =
      BazelCommand
        .MobileInstall(bazelBinary, target)
        .apply {
          // --tool_tag is not supported by mobile-install
          this.options.clear()
          builder()
        }.also { inheritWorkspaceOptions = true }

    fun test(builder: BazelCommand.Test.() -> Unit = {}) =
      BazelCommand.Test(bazelBinary).apply { builder() }.also { inheritWorkspaceOptions = true }

    fun coverage(builder: BazelCommand.Coverage.() -> Unit = {}) =
      BazelCommand.Coverage(bazelBinary).apply { builder() }.also { inheritWorkspaceOptions = true }
  }

  fun buildBazelCommand(inheritProjectviewOptionsOverride: Boolean? = null, doBuild: CommandBuilder.() -> BazelCommand): BazelCommand {
    val commandBuilder = CommandBuilder()
    val command = doBuild(commandBuilder)
    val workspaceContext = workspaceContextProvider.currentWorkspaceContext()
    val relativeDotBspFolderPath = workspaceContext.dotBazelBspDirPath.value

    command.options.add(
      overrideRepository(
        Constants.ASPECT_REPOSITORY,
        relativeDotBspFolderPath.pathString,
        bazelInfo?.shouldUseInjectRepository() == true,
      ),
    )

    // this is a fallback solution for Bazel version 7.x.x that has the flag `--noenable_workspace`;
    // it will help the plugin not failing, at the cost of potentially invalidating the analysis cache.
    // see https://bazel.build/reference/command-line-reference#flag--enable_workspace
    if (bazelInfo?.isWorkspaceEnabled == false &&
      bazelInfo?.shouldUseInjectRepository() == false
    ) {
      command.options.add(enableWorkspace())
    }

    // These options are the same as in Google's Bazel plugin for IntelliJ
    // They make the output suitable for display in the console
    command.options.addAll(
      listOf(
        "--curses=no",
        "--color=yes",
        "--noprogress_in_terminal_title",
      ),
    )

    val workspaceBazelOptions = workspaceContext.buildFlags.values + workspaceContext.extraFlags

    inheritProjectviewOptionsOverride?.let {
      commandBuilder.inheritWorkspaceOptions = it
    }

    if (commandBuilder.inheritWorkspaceOptions) {
      command.options.addAll(workspaceBazelOptions)
    }

    return command
  }

  fun runBazelCommand(
    command: BazelCommand,
    originId: String? = null,
    logProcessOutput: Boolean = true,
    serverPidFuture: CompletableFuture<Long>?,
    shouldLogInvocation: Boolean = true,
  ): BazelProcess {
    val executionDescriptor = command.buildExecutionDescriptor()
    val finishCallback = executionDescriptor.finishCallback
    val processArgs = executionDescriptor.command

    var commandLine = GeneralCommandLine(processArgs).withWorkDirectory(workspaceRoot?.toFile())

    // Run needs to be handled separately because the resulting process is not run in the sandbox
    if (command is BazelCommand.Run) {
      command.workingDirectory?.also { commandLine = commandLine.withWorkDirectory(it.toFile()) }
      commandLine = commandLine.withEnvironment(command.environment)
      logInvocation(processArgs, command.environment, command.workingDirectory, originId, shouldLogInvocation = shouldLogInvocation)
    } else {
      logInvocation(processArgs, null, null, originId, shouldLogInvocation = shouldLogInvocation)
    }

    val process = commandLine.createProcess()
    val outputLogger = bspClientLogger.takeIf { logProcessOutput }?.copy(originId = originId)

    return BazelProcess(
      process,
      outputLogger,
      serverPidFuture,
      finishCallback,
    )
  }

  private fun envToString(environment: Map<String, String>): String = environment.entries.joinToString(" ") { "${it.key}=${it.value}" }

  private fun logInvocation(
    processArgs: List<String>,
    processEnv: Map<String, String>?,
    directory: Path?,
    originId: String?,
    shouldLogInvocation: Boolean,
  ) {
    if (!shouldLogInvocation) return
    val envString = processEnv?.let { envToString(it) }
    val directoryString = directory?.let { "cd $it &&" }
    val processArgsString = processArgs.joinToString("' '", "'", "'")
    listOfNotNull("Invoking:", envString, directoryString, processArgsString)
      .joinToString(" ")
      .also { LOGGER.info(it) }
      .also { bspClientLogger?.copy(originId = originId)?.message(it) }
  }
}
