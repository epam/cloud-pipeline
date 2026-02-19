package com.epam.pipeline.app;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Template REST API", // Corresponds to ApiInfo's title
                description = "Some custom description of API.", // Corresponds to ApiInfo's description
                version = "API TOS", // Often used for version number, similar to ApiInfo's version/TOS
                contact = @Contact(
                        name = "dev",
                        url = "url",
                        email = "email"
                ),
                license = @License(
                        name = "License of API",
                        url = "API license URL"
                )
        )
)
public class RestConfiguration {}
