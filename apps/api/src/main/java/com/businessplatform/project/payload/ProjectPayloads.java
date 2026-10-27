package com.businessplatform.project.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.businessplatform.project.domain.ProjectRole;

/** Projects, members, tasks, comments and attachments. */
public final class ProjectPayloads {

    private ProjectPayloads() {
    }

    public record PersonRef(UUID id, String name, String avatarUrl) {
    }

    public record CreateProjectRequest(@NotBlank @Size(min = 2, max = 120) String name,
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$", message = "2–10 capital letters or digits, starting with a letter") String key,
            @Size(max = 5000) String description, @Pattern(regexp = "ORG|PRIVATE") String visibility,
            LocalDate startDate, LocalDate dueDate, List<MemberInput> members) {
    }

    public record UpdateProjectRequest(@Size(min = 2, max = 120) String name, @Size(max = 5000) String description,
            @Pattern(regexp = "ACTIVE|ON_HOLD|DONE|ARCHIVED") String status, @Pattern(regexp = "ORG|PRIVATE") String visibility,
            LocalDate startDate, LocalDate dueDate, Boolean clearDates) {
    }

    public record MemberInput(@NotNull UUID userId, @NotNull ProjectRole role) {
    }

    public record ProjectMemberResponse(PersonRef person, ProjectRole role) {
    }

    public record ProjectResponse(UUID id, String key, String name, String description, String status,
            String visibility, PersonRef lead, LocalDate startDate, LocalDate dueDate, Map<String, Long> taskCounts,
            int progress, ProjectRole myRole, boolean canEdit, boolean canManage, Instant updatedAt) {
    }

    public record CreateTaskRequest(@NotBlank @Size(max = 200) String title, @Size(max = 20000) String description,
            @Pattern(regexp = "TODO|IN_PROGRESS|IN_REVIEW|DONE") String status,
            @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority, UUID assigneeId, LocalDate startDate,
            LocalDate dueDate, UUID parentId) {
    }

    /** Only the fields sent change; {@code clear} lists fields to empty (assignee, dueDate, startDate, description). */
    public record UpdateTaskRequest(@Size(min = 1, max = 200) String title, @Size(max = 20000) String description,
            @Pattern(regexp = "TODO|IN_PROGRESS|IN_REVIEW|DONE") String status,
            @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority, UUID assigneeId, LocalDate startDate,
            LocalDate dueDate, List<String> clear) {
    }

    public record MoveTaskRequest(@NotNull @Pattern(regexp = "TODO|IN_PROGRESS|IN_REVIEW|DONE") String status,
            @NotNull Integer index) {
    }

    public record TaskResponse(UUID id, String key, int number, UUID projectId, String projectKey, String projectName,
            UUID parentId, String title, String description, String status, String priority, PersonRef assignee,
            PersonRef reporter, LocalDate startDate, LocalDate dueDate, int subtasks, int subtasksDone, int comments,
            Instant completedAt, Instant createdAt, Instant updatedAt) {
    }

    public record FileRef(UUID id, String name, String contentType, long size, Instant createdAt) {
    }

    public record TaskDetailResponse(TaskResponse task, List<TaskResponse> subtasks, List<FileRef> attachments,
            boolean canEdit) {
    }

    public record CommentRequest(@NotBlank @Size(max = 10000) String body, List<UUID> mentionIds) {
    }

    public record CommentResponse(UUID id, PersonRef author, String body, Instant createdAt, Instant editedAt) {
    }

    public record ActivityResponse(UUID id, PersonRef actor, String verb, Map<String, Object> data, Instant at) {
    }
}
