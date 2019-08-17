package com.epam.pipeline.tesadapter;

import com.epam.pipeline.tesadapter.configuration.TesSwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({TesSwaggerConfig.class})
@ComponentScan({"com.epam.pipeline.tesadapter.controller", "com.epam.pipeline.tesadapter.service",
        "com.epam.pipeline.tesadapter.entity"})
public class TesAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(TesAdapterApplication.class, args);
    }
}
