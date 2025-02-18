package org.jetbrains.bsp.bazel.server.dependencygraph

import org.jetbrains.bazel.label.Label
import org.jetbrains.bsp.bazel.info.BspTargetInfo.Dependency
import org.jetbrains.bsp.bazel.info.BspTargetInfo.TargetInfo
import org.jetbrains.bsp.bazel.server.label.label

class DependencyGraph(private val rootTargets: Set<Label> = emptySet(), private val idToTargetInfo: Map<Label, TargetInfo> = emptyMap()) {
  private val idToDirectDependenciesIds: Map<Label, Set<Label>>
  private val idToLazyTransitiveDependencies: Map<Label, Lazy<Set<TargetInfo>>>

  init {
    idToDirectDependenciesIds =
      idToTargetInfo.entries.associate { (id, target) ->
        Pair(
          id,
          getDependencies(target),
        )
      }
    idToLazyTransitiveDependencies = createIdToLazyTransitiveDependenciesMap(idToTargetInfo)
  }

  private fun createIdToLazyTransitiveDependenciesMap(idToTargetInfo: Map<Label, TargetInfo>): Map<Label, Lazy<Set<TargetInfo>>> =
    idToTargetInfo.mapValues { (_, targetInfo) ->
      calculateLazyTransitiveDependenciesForTarget(targetInfo)
    }

  private fun calculateLazyTransitiveDependenciesForTarget(targetInfo: TargetInfo): Lazy<Set<TargetInfo>> =
    lazy { calculateTransitiveDependenciesForTarget(targetInfo) }

  private fun calculateTransitiveDependenciesForTarget(targetInfo: TargetInfo): Set<TargetInfo> {
    val dependencies = getDependencies(targetInfo)
    val strictlyTransitiveDependencies = calculateStrictlyTransitiveDependencies(dependencies)
    val directDependencies = idsToTargetInfo(dependencies)
    return strictlyTransitiveDependencies + directDependencies
  }

  private fun calculateStrictlyTransitiveDependencies(dependencies: Set<Label>): Set<TargetInfo> =
    dependencies
      .flatMap {
        idToLazyTransitiveDependencies[it]?.value.orEmpty()
      }.toSet()

  private fun idsToTargetInfo(dependencies: Set<Label>): Set<TargetInfo> = dependencies.mapNotNull(idToTargetInfo::get).toSet()

  private fun directDependenciesIds(targetIds: Set<Label>) =
    targetIds
      .flatMap {
        idToDirectDependenciesIds[it].orEmpty()
      }.toSet()

  fun allTargetsAtDepth(depth: Int, targets: Set<Label>): Set<TargetInfo> {
    if (depth < 0) {
      return idsToTargetInfo(targets) + calculateStrictlyTransitiveDependencies(targets)
    }

    var currentDepth = depth
    val searched: MutableSet<TargetInfo> = mutableSetOf()
    var currentTargets = targets

    while (currentDepth > 0) {
      searched.addAll(idsToTargetInfo(currentTargets))
      currentTargets = directDependenciesIds(currentTargets)
      currentDepth--
    }

    searched.addAll(idsToTargetInfo(currentTargets))
    return searched
  }

  fun transitiveDependenciesWithoutRootTargets(targetId: Label): Set<TargetInfo> =
    idToTargetInfo[targetId]
      ?.let(::getDependencies)
      .orEmpty()
      .filter(::isNotARootTarget)
      .flatMap(::collectTransitiveDependenciesAndAddTarget)
      .toSet()

  private fun getDependencies(target: TargetInfo): Set<Label> =
    target.dependenciesList
      .map(Dependency::getId)
      .map(Label::parse)
      .toSet()

  private fun isNotARootTarget(targetId: Label): Boolean = !rootTargets.contains(targetId)

  private fun collectTransitiveDependenciesAndAddTarget(targetId: Label): Set<TargetInfo> {
    val target = idToTargetInfo[targetId]?.let(::setOf).orEmpty()
    val dependencies =
      idToLazyTransitiveDependencies[targetId]
        ?.let(::setOf)
        .orEmpty()
        .map(Lazy<Set<TargetInfo>>::value)
        .flatten()
        .toSet()
    return dependencies + target
  }

  fun filterUsedLibraries(libraries: Map<Label, TargetInfo>, targets: Sequence<TargetInfo>): Map<Label, TargetInfo> {
    val visited = hashSetOf<Label>()
    val queue = ArrayDeque<Label>()
    targets.map { it.label() }.forEach {
      queue.addLast(it)
      visited.add(it)
    }
    while (queue.isNotEmpty()) {
      val label = queue.removeFirst()
      val dependencies = idToDirectDependenciesIds[label] ?: continue
      for (dependency in dependencies) {
        if (!visited.add(dependency)) continue
        queue.addLast(dependency)
      }
    }
    return libraries.filterKeys { it in visited }
  }
}
