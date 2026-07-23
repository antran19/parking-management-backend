package com.smartparking.backend.controller;

import com.smartparking.backend.dto.response.ApiResponse;
import com.smartparking.backend.security.PermissionGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trả về các quyền thao tác của user đang đăng nhập, để frontend ẩn/hiện nút.
 * GET /api/v1/me/permissions
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final PermissionGuard permissionGuard;

    public MeController(PermissionGuard permissionGuard) {
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myPermissions() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("canResolveIncident", permissionGuard.canResolveIncident());
        data.put("canManageBlacklist", permissionGuard.canManageBlacklist());
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }
}
