package org.jetbrains.bsp.testkit.client.bazel

import org.jetbrains.bazel.commons.utils.OsFamily
import java.nio.file.Path

class BazelJsonTransformer(
  private val workspacePath: Path,
  private val bazelCache: Path,
  private val bazelOutputBase: Path,
) {
  fun transformJson(s: String): String =
    s
      .replace("\$WORKSPACE", workspacePath.toString())
      .replace("\$BAZEL_CACHE", bazelCache.toString())
      .replace("\$BAZEL_OUTPUT_BASE_PATH", bazelOutputBase.toString())
      .replace("\$OS", osFamily)

  companion object {
    private val osFamily: String =
      when (OsFamily.inferFromSystem()) {
        OsFamily.WINDOWS -> "win"
        OsFamily.MACOS -> "macos"
        else -> "linux"
      }
  }
}
