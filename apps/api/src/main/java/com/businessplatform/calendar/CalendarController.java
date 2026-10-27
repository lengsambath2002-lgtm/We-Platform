package com.businessplatform.calendar;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Calendar: events and every module's deadlines in a date range. */
@Tag(name = "Calendar")
@RestController
@RequestMapping("/api/orgs/{org}")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendar;

    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<CalendarService.CalendarView>> range(@PathVariable String org,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean mine) {
        return ResponseEntity.ok(ApiResponse.ok(calendar.range(org, from, to, mine)));
    }

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<CalendarService.EventView>> create(@PathVariable String org,
            @Valid @RequestBody CalendarService.EventRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(calendar.create(org, in)));
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<ApiResponse<CalendarService.EventView>> update(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CalendarService.EventRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(calendar.update(org, id, in)));
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String org, @PathVariable UUID id) {
        calendar.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
