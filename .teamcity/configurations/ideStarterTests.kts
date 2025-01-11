package configurations

import jetbrains.buildServer.configs.kotlin.v2019_2.Requirements
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.BazelStep
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.bazel
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

sealed class IdeStarterTests(
  vcsRoot: GitVcsRoot,
  targets: String,
  requirements: (Requirements.() -> Unit)? = null,
  name: String
) : BaseConfiguration.BaseBuildType(
  name = "[ide-starter] $name",
  vcsRoot = vcsRoot,
  requirements = requirements,
  artifactRules = Utils.CommonParams.BazelTestlogsArtifactRules,
  steps = {
    bazel {
      val reportErrors = "--jvmopt=\"-DDO_NOT_REPORT_ERRORS=true\""
      val cachePath = "--jvmopt=\"-Dbazel.ide.starter.test.cache.directory=%system.teamcity.build.tempDir%\""
      val memArg = "--jvmopt=\"-Xmx12g\""
      val sandboxArg = "--sandbox_writable_path=/"
      val actionEnvArg = "--action_env=PATH"

      val sysArgs = "$reportErrors $cachePath $memArg $sandboxArg $actionEnvArg"

      this.name = "run $targets"
      id = "run_${targets.replace(":", "_")}"
      command = "test"
      this.targets = targets
      arguments = "$sysArgs ${Utils.CommonParams.BazelCiSpecificArgs}"
      toolPath = "/usr/local/bin"
      logging = BazelStep.Verbosity.Diagnostic
      Utils.DockerParams.get().forEach { (key, value) ->
        param(key, value)
      }
    }
    script {
      this.name = "copy test logs"
      id = "copy_test_logs"
      scriptContent =
        """
          #!/bin/bash
          set -euxo
          
          cp -R /home/teamcity/agent/system/.persistent_cache/bazel/_bazel_hirschuser/*/execroot/_main/bazel-out/k8-fastbuild/testlogs .
          """.trimIndent()
    }
  }
)

sealed class HotswapTest(
  vcsRoot: GitVcsRoot,
  requirements: (Requirements.() -> Unit)? = null
) : IdeStarterTests(
  name = "Hotswap test",
  vcsRoot = vcsRoot,
  targets = "//plugin-bazel/src/test/kotlin/org/jetbrains/bazel/hotswap",
  requirements = requirements
)

sealed class ExternalRepoResolveTest(
  vcsRoot: GitVcsRoot,
  requirements: (Requirements.() -> Unit)? = null
) : IdeStarterTests(
  name = "External repo resolve test",
  vcsRoot = vcsRoot,
  targets = "//plugin-bazel/src/test/kotlin/org/jetbrains/bazel/languages/starlark/references:ExternalRepoResolveTest",
  requirements = requirements
)

object HotswapTestGitHub : HotswapTest(
  vcsRoot = BaseConfiguration.GitHubVcs,
  requirements = {
    endsWith("cloud.amazon.agent-name-prefix", "Ubuntu-22.04-Large")
    equals("container.engine.osType", "linux")
  }
)

object HotswapTestSpace : HotswapTest(
  vcsRoot = BaseConfiguration.SpaceVcs,
)

object ExternalRepoResolveTestGitHub : ExternalRepoResolveTest(
  vcsRoot = BaseConfiguration.GitHubVcs,
  requirements = {
    endsWith("cloud.amazon.agent-name-prefix", "Ubuntu-22.04-Large")
    equals("container.engine.osType", "linux")
  }
)

object ExternalRepoResolveTestSpace : ExternalRepoResolveTest(
  vcsRoot = BaseConfiguration.SpaceVcs,
)
