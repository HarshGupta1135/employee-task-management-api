package com.harsh.taskmanager.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI taskManagerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Employee Task Management API")
                        .version("v1")
                        .description("""
                                Manage employee tasks behind JWT authentication with ADMIN and USER roles.

                                1. Call `POST /api/auth/login` (demo admin: `admin@taskmanager.com` / `Admin@123`).
                                2. Click **Authorize** and paste the `accessToken` from the response.
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
