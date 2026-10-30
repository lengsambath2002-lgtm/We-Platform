package com.businessplatform.hr.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.hr.payload.DevelopmentPayloads.*;
import com.businessplatform.hr.service.ReviewService;
import com.businessplatform.hr.service.TrainingService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** HR & Workforce: performance reviews and training. */
@RestController
@RequestMapping("/api/orgs/{org}/hr")
@RequiredArgsConstructor
@Tag(name = "HR reviews and training")
public class DevelopmentController {

    private final ReviewService reviews;
    private final TrainingService training;

    // ── Review cycles (HR managers) ──────────────────────────────────────────────────

    @GetMapping("/review-cycles")
    public ResponseEntity<ApiResponse<List<CycleView>>> cycles(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.cycles(org)));
    }

    @PostMapping("/review-cycles")
    public ResponseEntity<ApiResponse<CycleView>> createCycle(@PathVariable String org, @Valid @RequestBody CycleRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(reviews.saveCycle(org, null, in)));
    }

    @GetMapping("/review-cycles/{id}")
    public ResponseEntity<ApiResponse<CycleDetail>> cycle(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.cycle(org, id)));
    }

    @PutMapping("/review-cycles/{id}")
    public ResponseEntity<ApiResponse<CycleView>> updateCycle(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CycleRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.saveCycle(org, id, in)));
    }

    @PostMapping("/review-cycles/{id}/launch")
    public ResponseEntity<ApiResponse<CycleDetail>> launch(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.launch(org, id)));
    }

    @PostMapping("/review-cycles/{id}/close")
    public ResponseEntity<ApiResponse<CycleView>> close(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.close(org, id)));
    }

    // ── Reviews ──────────────────────────────────────────────────────────────────────

    @GetMapping("/reviews/mine")
    public ResponseEntity<ApiResponse<List<ReviewRow>>> mine(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.mine(org)));
    }

    @GetMapping("/reviews/to-review")
    public ResponseEntity<ApiResponse<List<ReviewRow>>> toReview(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.toReview(org)));
    }

    @GetMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<ReviewView>> review(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.get(org, id)));
    }

    @PutMapping("/reviews/{id}/self")
    public ResponseEntity<ApiResponse<ReviewView>> self(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody AnswersRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.saveSelf(org, id, in)));
    }

    @PutMapping("/reviews/{id}/review")
    public ResponseEntity<ApiResponse<ReviewView>> review(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody AnswersRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.saveReview(org, id, in)));
    }

    @PostMapping("/reviews/{id}/acknowledge")
    public ResponseEntity<ApiResponse<ReviewView>> acknowledge(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.acknowledge(org, id)));
    }

    @PutMapping("/reviews/{id}/reviewer")
    public ResponseEntity<ApiResponse<ReviewView>> reviewer(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody ReviewerRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(reviews.changeReviewer(org, id, in)));
    }

    // ── Training ─────────────────────────────────────────────────────────────────────

    @GetMapping("/training/courses")
    public ResponseEntity<ApiResponse<List<CourseView>>> courses(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(training.courses(org)));
    }

    @PostMapping("/training/courses")
    public ResponseEntity<ApiResponse<CourseDetail>> createCourse(@PathVariable String org, @Valid @RequestBody CourseRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(training.save(org, null, in)));
    }

    @GetMapping("/training/courses/{id}")
    public ResponseEntity<ApiResponse<CourseDetail>> course(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(training.course(org, id)));
    }

    @PutMapping("/training/courses/{id}")
    public ResponseEntity<ApiResponse<CourseDetail>> updateCourse(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CourseRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(training.save(org, id, in)));
    }

    @PostMapping("/training/courses/{id}/enrol")
    public ResponseEntity<ApiResponse<CourseDetail>> enrolMe(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(training.enrolMe(org, id)));
    }

    @PostMapping("/training/courses/{id}/enrolments")
    public ResponseEntity<ApiResponse<CourseDetail>> enrol(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody EnrolRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(training.enrolPeople(org, id, in)));
    }

    @PutMapping("/training/enrolments/{id}")
    public ResponseEntity<ApiResponse<CourseDetail>> setStatus(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody EnrolmentStatusRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(training.setStatus(org, id, in)));
    }

    @GetMapping("/training/mine")
    public ResponseEntity<ApiResponse<List<MyTraining>>> myTraining(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(training.mine(org)));
    }
}
