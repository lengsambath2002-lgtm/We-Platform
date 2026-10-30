package com.businessplatform.org.service;

import java.util.List;
import java.util.UUID;

/**
 * Published (inside the transaction) when units, positions or placements change, so modules that copy from the
 * structure (HR's department and default manager) bring themselves up to date. {@code placed}: the users whose
 * own placements changed (empty for a rename or a move).
 */
public record StructureChanged(UUID orgId, List<UUID> placed) {
}
