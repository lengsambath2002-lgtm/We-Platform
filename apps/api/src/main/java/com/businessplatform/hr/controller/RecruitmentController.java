package com.businessplatform.hr.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.file.FileService;
import com.businessplatform.file.StoredFile;
import com.businessplatform.hr.payload.RecruitmentPayloads.*;
import com.businessplatform.hr.service.RecruitmentService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** HR & Workforce: job openings, candidates, interviews, hiring and onboarding. */
@RestController
@RequestMapping("/api/orgs/{org}/hr/recruitment")
@RequiredArgsConstructor
@Tag(name = "HR recruitment")
public class RecruitmentController {

    private final RecruitmentService recruitment;
    private final FileService files;

    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<List<JobView>>> jobs(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.jobs(org)));
    }

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<JobView>> createJob(@PathVariable String org, @Valid @RequestBody JobRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(recruitment.saveJob(org, null, in)));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<JobView>> job(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.job(org, id)));
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<JobView>> updateJob(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody JobRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.saveJob(org, id, in)));
    }

    @GetMapping("/jobs/{id}/candidates")
    public ResponseEntity<ApiResponse<List<CandidateSummary>>> candidates(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.candidates(org, id)));
    }

    @PostMapping("/jobs/{id}/candidates")
    public ResponseEntity<ApiResponse<CandidateDetail>> addCandidate(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CandidateRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(recruitment.addCandidate(org, id, in)));
    }

    @GetMapping("/candidates/{id}")
    public ResponseEntity<ApiResponse<CandidateDetail>> candidate(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.candidate(org, id)));
    }

    @PutMapping("/candidates/{id}")
    public ResponseEntity<ApiResponse<CandidateDetail>> updateCandidate(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CandidateRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.updateCandidate(org, id, in)));
    }

    @DeleteMapping("/candidates/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCandidate(@PathVariable String org, @PathVariable UUID id) {
        recruitment.deleteCandidate(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/candidates/{id}/stage")
    public ResponseEntity<ApiResponse<CandidateDetail>> move(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody StageRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.move(org, id, in)));
    }

    @PostMapping("/candidates/{id}/hire")
    public ResponseEntity<ApiResponse<CandidateDetail>> hire(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody HireRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.hire(org, id, in)));
    }

    @PostMapping(value = "/candidates/{id}/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CandidateDetail>> attachCv(@PathVariable String org, @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.attachCv(org, id, file)));
    }

    @GetMapping("/candidates/{id}/cv")
    public ResponseEntity<byte[]> cv(@PathVariable String org, @PathVariable UUID id) {
        StoredFile f = recruitment.cv(org, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(files.read(f));
    }

    @PostMapping("/candidates/{id}/interviews")
    public ResponseEntity<ApiResponse<CandidateDetail>> schedule(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody InterviewRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.schedule(org, id, in)));
    }

    @DeleteMapping("/interviews/{id}")
    public ResponseEntity<ApiResponse<CandidateDetail>> cancelInterview(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.cancelInterview(org, id)));
    }

    @PutMapping("/interviews/{id}/feedback")
    public ResponseEntity<ApiResponse<CandidateDetail>> feedback(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody FeedbackRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.feedback(org, id, in)));
    }

    @GetMapping("/interviews/mine")
    public ResponseEntity<ApiResponse<List<InterviewView>>> myInterviews(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.myInterviews(org)));
    }

    @GetMapping("/hires")
    public ResponseEntity<ApiResponse<List<HireView>>> hires(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.hires(org)));
    }

    @PostMapping("/candidates/{id}/onboarding")
    public ResponseEntity<ApiResponse<CandidateDetail>> addItem(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody OnboardingItemRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.addItem(org, id, in)));
    }

    public record DoneRequest(boolean done) {
    }

    @PutMapping("/onboarding/{id}")
    public ResponseEntity<ApiResponse<CandidateDetail>> setDone(@PathVariable String org, @PathVariable UUID id,
            @RequestBody DoneRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.setDone(org, id, in.done())));
    }

    @DeleteMapping("/onboarding/{id}")
    public ResponseEntity<ApiResponse<CandidateDetail>> removeItem(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.removeItem(org, id)));
    }

    @GetMapping("/onboarding-template")
    public ResponseEntity<ApiResponse<TemplateView>> template(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.templateView(org)));
    }

    @PutMapping("/onboarding-template")
    public ResponseEntity<ApiResponse<TemplateView>> saveTemplate(@PathVariable String org,
            @Valid @RequestBody TemplateRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(recruitment.saveTemplate(org, in)));
    }
}
