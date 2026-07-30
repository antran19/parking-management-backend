package com.smartparking.backend.security;

import com.smartparking.backend.entity.SystemSettings;
import com.smartparking.backend.repository.SystemSettingsRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Kiểm soát quyền động cho thao tác an ninh dựa trên cấu hình trong SystemSettings.
 * - ADMIN luôn có quyền (không phụ thuộc cấu hình).
 * - Các role STAFF/SECURITY/MANAGER được admin bật/tắt qua danh sách CSV trong settings.
 * Dùng trong @PreAuthorize: @PreAuthorize("@permissionGuard.canResolveIncident()").
 */
@Component("permissionGuard")
public class PermissionGuard {

    private final SystemSettingsRepository settingsRepository;

    public PermissionGuard(SystemSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public boolean canResolveIncident() {
        return allows(currentRole(), settings().getIncidentResolverRoles());
    }

    public boolean canManageBlacklist() {
        return allows(currentRole(), settings().getBlacklistManagerRoles());
    }

    /** Pure logic: ADMIN luôn true; role null hoặc không có trong CSV → false. */
    static boolean allows(String role, String csvRoles) {
        if (role == null) return false;
        if ("ADMIN".equalsIgnoreCase(role)) return true;
        if (csvRoles == null || csvRoles.isBlank()) return false;
        return Arrays.stream(csvRoles.split(","))
                .map(String::trim)
                .anyMatch(r -> r.equalsIgnoreCase(role));
    }

    private String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return null;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst().orElse(null);
    }

    private SystemSettings settings() {
        return settingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> SystemSettings.builder().build());
    }
}
