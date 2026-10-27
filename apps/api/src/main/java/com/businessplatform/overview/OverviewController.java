package com.businessplatform.overview;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Overview")
@RestController
@RequiredArgsConstructor
public class OverviewController {

    private final OverviewService overview;

    @GetMapping("/api/orgs/{org}/overview")
    public ResponseEntity<ApiResponse<OverviewService.Overview>> get(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(overview.get(org)));
    }
}
