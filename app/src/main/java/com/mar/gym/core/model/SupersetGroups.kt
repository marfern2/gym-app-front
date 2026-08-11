package com.mar.gym.core.model

const val MAX_SUPERSET_ORDINAL = 15

/** Converts draft-only group identities to the backend's local, normalized ordinals. */
fun normalizedSupersetOrdinals(localGroupIds: List<String?>): List<Int?> {
    val ordinals = linkedMapOf<String, Int>()
    return localGroupIds.map { localGroupId ->
        localGroupId?.let { ordinals.getOrPut(it) { ordinals.size + 1 } }
    }
}

/** Validates the canonical grouping invariants promised by the backend. */
fun hasValidCanonicalSupersetGroups(groups: List<Int?>): Boolean {
    if (groups.any { it != null && it !in 1..MAX_SUPERSET_ORDINAL }) return false
    val present = groups.filterNotNull().distinct()
    if (present != (1..present.size).toList()) return false
    return present.all { group ->
        val positions = groups.indices.filter { groups[it] == group }
        positions.size >= 2 && positions.zipWithNext().all { (first, second) -> second == first + 1 }
    }
}

/** Local draft identities have the same shape constraints, but no ordinal semantics. */
fun hasValidLocalSupersetGroups(groups: List<String?>): Boolean =
    groups.filterNotNull().distinct().all { group ->
        val positions = groups.indices.filter { groups[it] == group }
        positions.size >= 2 && positions.zipWithNext().all { (first, second) -> second == first + 1 }
    }

