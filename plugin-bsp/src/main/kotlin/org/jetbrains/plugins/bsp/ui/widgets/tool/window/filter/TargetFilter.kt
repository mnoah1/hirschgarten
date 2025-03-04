package org.jetbrains.plugins.bsp.ui.widgets.tool.window.filter

import org.jetbrains.plugins.bsp.target.TargetUtils
import org.jetbrains.plugins.bsp.workspacemodel.entities.BuildTargetInfo

public class TargetFilter(private val onFilterChange: () -> Unit) {
  public var currentFilter: FILTER = FILTER.OFF
    set(value) {
      if (field != value) {
        field = value
        onFilterChange()
      }
    }

  public fun isFilterOn(): Boolean = currentFilter != FILTER.OFF

  public fun getMatchingLoadedTargets(xd: TargetUtils): List<BuildTargetInfo> =
    xd.allTargetIds().mapNotNull { xd.getBuildTargetInfoForId(it) }.filterTargets()

  private fun List<BuildTargetInfo>.filterTargets(): List<BuildTargetInfo> = this.filter(currentFilter.predicate)

  public enum class FILTER(public val predicate: (BuildTargetInfo) -> Boolean) {
    OFF({ true }),
    CAN_RUN({ it.capabilities.canRun }),
    CAN_TEST({ it.capabilities.canTest }),
  }
}
