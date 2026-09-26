package com.asms.config;

import com.asms.dto.common.ClientInfo;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI specification settings. The generated spec is the API contract the frontend uses to generate its client
 * (Orval).
 *
 * @author MinhTien
 * @version 1.1.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    static {
        // Resolved from the request by ClientInfoArgumentResolver, not sent by clients
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(ClientInfo.class);
    }

    @Bean
    OpenAPI asmsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ASMS API")
                        .description("Assignment Management System - REST API")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
