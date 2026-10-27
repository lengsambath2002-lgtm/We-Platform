package com.businessplatform.project.controller;

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
import com.businessplatform.project.payload.ProjectPayloads.*;
import com.businessplatform.project.service.ProjectService;
import com.businessplatform.project.service.TaskService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Projects & Tasks (module {@code projects}). */
@Tag(name = "Projects & Tasks")
@RestController
@RequestMapping("/api/orgs/{org}")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projects;
    private final TaskService tasks;
    private final FileService files;

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> list(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(projects.list(org)));
    }

    @PostMapping("/projects")
    public ResponseEntity<ApiResponse<ProjectResponse>> create(@PathVariable String org,
            @Valid @RequestBody CreateProjectRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(projects.create(org, in)));
    }

    @GetMapping("/projects/{key}")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(@PathVariable String org, @PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.ok(projects.get(org, key)));
    }

    @PatchMapping("/projects/{key}")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(@PathVariable String org, @PathVariable String key,
            @Valid @RequestBody UpdateProjectRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(projects.update(org, key, in)));
    }

    @DeleteMapping("/projects/{key}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String org, @PathVariable String key) {
        projects.delete(org, key);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/projects/{key}/members")
    public ResponseEntity<ApiResponse<List<ProjectMemberResponse>>> members(@PathVariable String org,
            @PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.ok(projects.members(org, key)));
    }

    @PutMapping("/projects/{key}/members")
    public ResponseEntity<ApiResponse<List<ProjectMemberResponse>>> setMembers(@PathVariable String org,
            @PathVariable String key, @Valid @RequestBody List<@Valid MemberInput> in) {
        return ResponseEntity.ok(ApiResponse.ok(projects.setMembers(org, key, in)));
    }

    @GetMapping("/projects/{key}/tasks")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> tasks(@PathVariable String org, @PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.ok(tasks.list(org, key)));
    }

    @PostMapping("/projects/{key}/tasks")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(@PathVariable String org, @PathVariable String key,
            @Valid @RequestBody CreateTaskRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(tasks.create(org, key, in)));
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> myTasks(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(tasks.myTasks(org)));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<TaskDetailResponse>> task(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(tasks.get(org, id)));
    }

    @PatchMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(tasks.update(org, id, in)));
    }

    @PostMapping("/tasks/{id}/move")
    public ResponseEntity<ApiResponse<TaskResponse>> move(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody MoveTaskRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(tasks.move(org, id, in)));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable String org, @PathVariable UUID id) {
        tasks.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/tasks/{id}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> comments(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(tasks.comments(org, id)));
    }

    @PostMapping("/tasks/{id}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> comment(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CommentRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(tasks.comment(org, id, in)));
    }

    @GetMapping("/tasks/{id}/activity")
    public ResponseEntity<ApiResponse<List<ActivityResponse>>> history(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(tasks.history(org, id)));
    }

    @PostMapping(value = "/tasks/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileRef>> attach(@PathVariable String org, @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(201).body(ApiResponse.ok(tasks.attach(org, id, file)));
    }

    @DeleteMapping("/tasks/{id}/attachments/{fileId}")
    public ResponseEntity<ApiResponse<Void>> detach(@PathVariable String org, @PathVariable UUID id,
            @PathVariable UUID fileId) {
        tasks.detach(org, id, fileId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** The file itself; only for people who can see the task. */
    @GetMapping("/tasks/{id}/attachments/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable String org, @PathVariable UUID id, @PathVariable UUID fileId) {
        StoredFile f = tasks.attachment(org, id, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(files.read(f));
    }
}
