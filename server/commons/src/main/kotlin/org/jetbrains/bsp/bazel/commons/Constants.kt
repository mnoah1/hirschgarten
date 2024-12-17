package org.jetbrains.bsp.bazel.commons

object Constants {
  const val NAME: String = "bazelbsp"
  const val VERSION: String = "3.2.0"
  const val BSP_VERSION: String = "2.1.0"
  const val SCALA: String = "scala"
  const val JAVA: String = "java"
  const val KOTLIN: String = "kotlin"
  const val CPP: String = "cpp"
  val SUPPORTED_LANGUAGES: List<String> = listOf(SCALA, JAVA, KOTLIN)
  const val BAZEL_BUILD_COMMAND: String = "build"
  const val BAZEL_TEST_COMMAND: String = "test"
  const val BAZEL_COVERAGE_COMMAND: String = "coverage"
  const val BUILD_FILE_NAME: String = "BUILD"
  const val WORKSPACE_FILE_NAME: String = "WORKSPACE"
  const val ASPECT_REPOSITORY: String = "bazelbsp_aspect"
  const val ASPECTS_ROOT: String = "aspects"
  const val CORE_BZL: String = "core.bzl"
  const val EXTENSIONS_BZL: String = "extensions.bzl"
  const val TEMPLATE_EXTENSION: String = ".template"
  const val DOT_BAZELBSP_DIR_NAME: String = ".bazelbsp"
  const val DOT_BSP_DIR_NAME: String = ".bsp"
  const val BAZELBSP_JSON_FILE_NAME: String = "bazelbsp.json"
  const val SERVER_CLASS_NAME: String = "org.jetbrains.bsp.bazel.server.ServerInitializer"
  const val CLASSPATH_FLAG: String = "-classpath"
  const val BAZELBSP_TRACE_JSON_FILE_NAME: String = "bazelbsp.trace.json"
}
