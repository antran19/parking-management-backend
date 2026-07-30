package com.smartparking.backend.security;

import com.smartparking.backend.entity.SystemSettings;
import com.smartparking.backend.repository.SystemSettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionGuardTest {

    @Mock
    private SystemSettingsRepository settingsRepository;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", "pw", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ---- pure logic ----
    @Test
    void allows_adminAlwaysTrue_evenWhenCsvEmpty() {
        assertTrue(PermissionGuard.allows("ADMIN", ""));
        assertTrue(PermissionGuard.allows("ADMIN", null));
    }

    @Test
    void allows_roleInCsv_true_notIn_false() {
        assertTrue(PermissionGuard.allows("SECURITY", "STAFF,SECURITY,MANAGER"));
        assertFalse(PermissionGuard.allows("STAFF", "SECURITY,MANAGER"));
    }

    @Test
    void allows_emptyCsv_nonAdmin_false() {
        assertFalse(PermissionGuard.allows("STAFF", ""));
        assertFalse(PermissionGuard.allows("MANAGER", null));
        assertFalse(PermissionGuard.allows(null, "STAFF"));
    }

    // ---- guard uses settings + security context ----
    @Test
    void canResolveIncident_respectsSettings() {
        var s = new SystemSettings();
        s.setIncidentResolverRoles("SECURITY,MANAGER");
        when(settingsRepository.findAll()).thenReturn(List.of(s));
        PermissionGuard guard = new PermissionGuard(settingsRepository);

        loginAs("MANAGER");
        assertTrue(guard.canResolveIncident());
        loginAs("STAFF");
        assertFalse(guard.canResolveIncident());
        loginAs("ADMIN");
        assertTrue(guard.canResolveIncident()); // admin luôn true
    }

    @Test
    void canManageBlacklist_respectsSettings() {
        var s = new SystemSettings();
        s.setBlacklistManagerRoles("SECURITY");
        when(settingsRepository.findAll()).thenReturn(List.of(s));
        PermissionGuard guard = new PermissionGuard(settingsRepository);

        loginAs("SECURITY");
        assertTrue(guard.canManageBlacklist());
        loginAs("MANAGER");
        assertFalse(guard.canManageBlacklist());
    }
}
