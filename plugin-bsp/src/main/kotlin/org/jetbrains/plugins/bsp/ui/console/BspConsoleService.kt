package org.jetbrains.plugins.bsp.ui.console

import com.intellij.build.BuildViewManager
import com.intellij.build.SyncViewManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.bsp.config.ProjectPropertiesService

@Service(Service.Level.PROJECT)
public class BspConsoleService(project: Project) {

  public val bspBuildConsole: TaskConsole

  public val bspSyncConsole: TaskConsole

  public val bspTestConsole: BspTargetTestConsole = BspTargetTestConsole()

  public val bspRunConsole: BspTargetRunConsole = BspTargetRunConsole()

  init {
    val projectProperties = ProjectPropertiesService.getInstance(project).value
    val basePath = projectProperties.projectRootDir.path

    bspBuildConsole = TaskConsole(project.getService(BuildViewManager::class.java), basePath)
    bspSyncConsole = TaskConsole(project.getService(SyncViewManager::class.java), basePath)
  }

  public companion object {
    public fun getInstance(project: Project): BspConsoleService =
      project.getService(BspConsoleService::class.java)
  }
}
