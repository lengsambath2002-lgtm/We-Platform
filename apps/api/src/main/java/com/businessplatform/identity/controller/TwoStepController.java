package com.businessplatform.identity.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.security.CurrentUser;
import com.businessplatform.identity.payload.AccountPayloads.PasswordRequest;
import com.businessplatform.identity.payload.AccountPayloads.RecoveryCodes;
import com.businessplatform.identity.payload.AccountPayloads.TwoStepCodeRequest;
import com.businessplatform.identity.payload.AccountPayloads.TwoStepOffRequest;
import com.businessplatform.identity.payload.AccountPayloads.TwoStepSetup;
import com.businessplatform.identity.payload.AccountPayloads.TwoStepStatus;
import com.businessplatform.identity.service.TwoStepService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The signed-in person's two-step sign-in. */
@Tag(name = "Me")
@RestController
@RequestMapping("/api/me/two-step")
@RequiredArgsConstructor
public class TwoStepController {

    private final TwoStepService twoStep;

    @GetMapping
    public ResponseEntity<ApiResponse<TwoStepStatus>> status() {
        return ResponseEntity.ok(ApiResponse.ok(twoStep.status(CurrentUser.id())));
    }

    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<TwoStepSetup>> setup() {
        return ResponseEntity.ok(ApiResponse.ok(twoStep.setup(CurrentUser.id())));
    }

    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<RecoveryCodes>> enable(@Valid @RequestBody TwoStepCodeRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(new RecoveryCodes(twoStep.enable(CurrentUser.id(), in.code()))));
    }

    @PostMapping("/recovery-codes")
    public ResponseEntity<ApiResponse<RecoveryCodes>> recoveryCodes(@Valid @RequestBody PasswordRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(new RecoveryCodes(twoStep.regenerate(CurrentUser.id(), in.password()))));
    }

    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disable(@Valid @RequestBody TwoStepOffRequest in) {
        twoStep.disable(CurrentUser.id(), in.password(), in.code());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
