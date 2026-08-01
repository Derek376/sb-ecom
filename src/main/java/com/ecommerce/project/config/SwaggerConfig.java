package com.ecommerce.project.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        SecurityScheme cookieScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name("springBootEcom")
                .description("JWT stored in the HTTP-only authentication cookie");

        SecurityRequirement cookieRequirement = new SecurityRequirement()
                .addList("Cookie Authentication");

        return new OpenAPI()
                .info(new Info()
                        .title("sb-ecom API")
                        .version("1.0")
                        .description("REST API for a full-stack e-commerce platform. Supports user authentication, product catalog, shopping cart, shipping addresses, and order placement. Built with Spring Boot, Spring Security (JWT), JPA, and PostgreSQL.")
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html"))
                        .contact(new Contact()
                                .name("Derek")
                                .email("derek46534@gmail.com")
                                .url("https://github.com/Derek376")
                        )
                )
                .externalDocs(new ExternalDocumentation()
                        .description("Project Repository")
                        .url("https://github.com/Derek376/react-ecom")
                )
                .components(new Components()
                        .addSecuritySchemes("Cookie Authentication", cookieScheme))
                .addSecurityItem(cookieRequirement);
    }
}
