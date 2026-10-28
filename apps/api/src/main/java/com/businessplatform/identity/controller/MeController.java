package com.businessplatform.identity.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.security.CurrentUser;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.payload.AuthPayloads.UpdateMeRequest;
import com.businessplatform.identity.payload.AuthPayloads.UserResponse;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.identity.service.AuthService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The signed-in person's own account. */
@Tag(name = "Me")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository users;

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok(AuthService.toUser(load())));
    }

    @PatchMapping
    @Transactional
    public ResponseEntity<ApiResponse<UserResponse>> update(@Valid @RequestBody UpdateMeRequest in) {
        AppUser u = load();
        if (in.name() != null) {
            u.setName(in.name().strip());
        }
        if (in.locale() != null) {
            u.setLocale(in.locale());
        }
        return ResponseEntity.ok(ApiResponse.ok(AuthService.toUser(users.save(u))));
    }

    private AppUser load() {
        return users.findById(CurrentUser.id()).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
