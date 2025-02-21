package org.jetbrains.bazel.flow.open

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.bazel.assets.BazelPluginIcons
import org.jetbrains.bazel.config.BazelPluginConstants
import org.jetbrains.bazel.projectAware.ProjectAwareExtension
import javax.swing.Icon

class BazelBspProjectAwareExtension : ProjectAwareExtension {
  override fun getProjectId(projectPath: VirtualFile): ExternalSystemProjectId =
    ExternalSystemProjectId(BazelPluginConstants.SYSTEM_ID, projectPath.path)

  override val eligibleConfigFileNames: List<String> =
    BazelPluginConstants.SUPPORTED_CONFIG_FILE_NAMES

  override val eligibleConfigFileExtensions: List<String> =
    BazelPluginConstants.SUPPORTED_EXTENSIONS
}

class BazelExternalSystemIconProvider : ExternalSystemIconProvider {
  override val reloadIcon: Icon
    get() = BazelPluginIcons.bazelReload
}
