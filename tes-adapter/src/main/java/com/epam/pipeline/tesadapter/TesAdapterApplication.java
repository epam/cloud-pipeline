package com.epam.pipeline.tesadapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({
        "com.epam.pipeline.tesadapter.configuration",
        "com.epam.pipeline.tesadapter.controller",
        "com.epam.pipeline.tesadapter.service"})
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class TesAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(TesAdapterApplication.class, args);
    }
}
