package com.epam.pipeline.tesadapter;

import com.epam.pipeline.tesadapter.configuration.RestConfiguration;
import com.epam.pipeline.tesadapter.configuration.TesSwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ComponentScan("com.epam.pipeline.tesadapter.controller")
@Import({TesSwaggerConfig.class, RestConfiguration.class})
public class TesAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(TesAdapterApplication.class, args);
    }
}
