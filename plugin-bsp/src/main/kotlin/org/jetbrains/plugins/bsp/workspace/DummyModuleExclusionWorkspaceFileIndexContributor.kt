package org.jetbrains.plugins.bsp.workspace

import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import org.jetbrains.plugins.bsp.utils.isSourceFile
import org.jetbrains.plugins.bsp.workspacemodel.entities.BspDummyEntitySource

/**
 * If a source file is added to a dummy module and is not associated with any other module, it should not be indexed or analyzed.
 * This functionality is currently disabled due to this [UX issue](https://youtrack.jetbrains.com/issue/BAZEL-1653).
 * For this contributor to work correctly, [AssignFileToModuleListener] must always precede it.
 * However, this order is currently not guaranteed,
 * as when a new file is created,
 * it is initially added to the umbrella dummy module -> this contributor is invoked -> [AssignFileToModuleListener]
 * runs in the background at some point -> this contributor is invoked again.
 * The file handling mechanism needs to be reconsidered to ensure proper coordination between contributors.
 */
class DummyModuleExclusionWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<ModuleEntity> {
  override val entityClass: Class<ModuleEntity>
    get() = ModuleEntity::class.java

  override fun registerFileSets(
    entity: ModuleEntity,
    registrar: WorkspaceFileSetRegistrar,
    storage: EntityStorage,
  ) {
    if (entity.entitySource != BspDummyEntitySource) return
    // Since we register the exclusion at contentRootUrl,
    // it will be overridden if we add a file as a file-based source root at a subdirectory of contentRootUrl.
    entity.contentRoots.map { contentRoot ->
      val contentRootUrl = contentRoot.url
      registrar.registerExclusionCondition(
        root = contentRootUrl,
        condition = { it.isSourceFile() },
        entity = entity,
      )
    }
  }
}
