package com.smartparking.backend.controller;

import com.smartparking.backend.dto.request.SecurityExceptionRequest;
import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.entity.ExceptionLog;
import com.smartparking.backend.service.SecurityExceptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
public class SecurityController {

    private final SecurityExceptionService securityExceptionService;

    public SecurityController(SecurityExceptionService securityExceptionService) {
        this.securityExceptionService = securityExceptionService;
    }

    @PostMapping("/exceptions")
    @PreAuthorize("hasAnyRole('SECURITY', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExceptionLog>> logException(
            @Valid @RequestBody SecurityExceptionRequest request) {
        ExceptionLog exceptionLog = securityExceptionService.logException(request);
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận ngoại lệ bảo vệ xử lý", exceptionLog));
    }
}
