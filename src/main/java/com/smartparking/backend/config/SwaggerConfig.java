package com.smartparking.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI Configuration.
 *
 * Truy cập:
 *   - Swagger UI:  http://localhost:8080/swagger-ui.html
 *   - API Docs:    http://localhost:8080/v3/api-docs
 *
 * Cách dùng:
 *   1. Mở Swagger UI trên trình duyệt
 *   2. Gọi POST /api/v1/auth/login lấy token
 *   3. Bấm "Authorize" → paste token vào → test các API khác
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI smartParkingOpenAPI() {
        final String securitySchemeName = "Bearer JWT";

        return new OpenAPI()
                .info(new Info()
                        .title("SmartParking API v2")
                        .description("Hệ thống Quản lý Bãi xe Thông minh — 5 Role, Zone-based, 2 cổng, QR, Redis")
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("SmartParking Team")
                                .email("admin@parking.vn")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste JWT token vào đây (không cần prefix 'Bearer ')")));
    }
}
