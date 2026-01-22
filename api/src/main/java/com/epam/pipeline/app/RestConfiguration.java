package com.epam.pipeline.app;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

@Configuration
/*@EnableWebMvc
TODO: if enabled swagger fails with Please indicate a valid Swagger or OpenAPI version field.
 Supported version fields are swagger: "2.0" and those that match openapi: 3.x.y (for example, openapi: 3.1.0).
 */
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
        // You can also add other elements like servers, security, etc. here.
)
public class RestConfiguration {}
