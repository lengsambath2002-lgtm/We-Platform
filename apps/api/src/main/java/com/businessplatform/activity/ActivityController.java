package com.businessplatform.activity;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.api.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** The organisation's audit log (permission {@code activity.view}: Owners and Admins). */
@Tag(name = "Audit log")
@RestController
@RequestMapping("/api/orgs/{org}/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activity;

    /**
     * Newest first. Filters: {@code actorId}; {@code module}, {@code recordType}, {@code verb} (a prefix such as
     * {@code approval.}); {@code from} / {@code to} (days, inclusive, in the organisation's time zone).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ActivityService.AuditEntry>>> list(@PathVariable String org,
            @RequestParam(required = false) UUID actorId, @RequestParam(required = false) String module,
            @RequestParam(required = false) String recordType, @RequestParam(required = false) String verb,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(activity.audit(org,
                new ActivityService.AuditFilter(actorId, module, recordType, verb, from, to), page, size)));
    }
}
