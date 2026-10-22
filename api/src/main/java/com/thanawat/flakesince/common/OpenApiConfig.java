package com.thanawat.flakesince.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI flakesinceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("FlakeSince API")
                .version("0.1.0")
                .description("Find flaky tests in CI and the commit they started at."));
    }
}