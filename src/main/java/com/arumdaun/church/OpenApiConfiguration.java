package com.arumdaun.church;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@Configuration
@SecurityScheme(
        name = "adminBasicAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "basic",
        description = "Admin HTTP Basic credentials")
public class OpenApiConfiguration {
}
