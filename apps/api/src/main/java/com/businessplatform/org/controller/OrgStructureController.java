package com.businessplatform.org.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.org.payload.StructurePayloads.*;
import com.businessplatform.org.service.OrgStructureService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The organisation structure: units, positions and members' places in them. */
@Tag(name = "Organisation structure")
@RestController
@RequestMapping("/api/orgs/{org}")
@RequiredArgsConstructor
public class OrgStructureController {

    private final OrgStructureService structure;

    @GetMapping("/structure")
    public ResponseEntity<ApiResponse<StructureView>> view(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(structure.view(org)));
    }

    @PostMapping("/structure/units")
    public ResponseEntity<ApiResponse<UnitView>> createUnit(@PathVariable String org, @Valid @RequestBody UnitRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(structure.saveUnit(org, null, in)));
    }

    @PutMapping("/structure/units/{id}")
    public ResponseEntity<ApiResponse<UnitView>> updateUnit(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody UnitRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(structure.saveUnit(org, id, in)));
    }

    @PostMapping("/structure/units/{id}/archive")
    public ResponseEntity<ApiResponse<UnitView>> archiveUnit(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(structure.archiveUnit(org, id, true)));
    }

    @PostMapping("/structure/units/{id}/restore")
    public ResponseEntity<ApiResponse<UnitView>> restoreUnit(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(structure.archiveUnit(org, id, false)));
    }

    @DeleteMapping("/structure/units/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUnit(@PathVariable String org, @PathVariable UUID id) {
        structure.deleteUnit(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/structure/positions")
    public ResponseEntity<ApiResponse<PositionView>> createPosition(@PathVariable String org,
            @Valid @RequestBody PositionRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(structure.savePosition(org, null, in)));
    }

    @PutMapping("/structure/positions/{id}")
    public ResponseEntity<ApiResponse<PositionView>> updatePosition(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody PositionRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(structure.savePosition(org, id, in)));
    }

    @PostMapping("/structure/positions/{id}/archive")
    public ResponseEntity<ApiResponse<PositionView>> archivePosition(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(structure.archivePosition(org, id, true)));
    }

    @PostMapping("/structure/positions/{id}/restore")
    public ResponseEntity<ApiResponse<PositionView>> restorePosition(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(structure.archivePosition(org, id, false)));
    }

    @DeleteMapping("/structure/positions/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePosition(@PathVariable String org, @PathVariable UUID id) {
        structure.deletePosition(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/members/{membershipId}/placements")
    public ResponseEntity<ApiResponse<List<AssignmentView>>> placements(@PathVariable String org,
            @PathVariable UUID membershipId) {
        return ResponseEntity.ok(ApiResponse.ok(structure.placements(org, membershipId)));
    }

    @PutMapping("/members/{membershipId}/placements")
    public ResponseEntity<ApiResponse<List<AssignmentView>>> place(@PathVariable String org,
            @PathVariable UUID membershipId, @Valid @RequestBody PlacementsRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(structure.place(org, membershipId, in)));
    }
}
