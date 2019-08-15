package com.epam.pipeline.tesadapter;

import com.epam.pipeline.tesadapter.configuration.TesAdapterWebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({TesAdapterWebConfiguration.class})
public class TesAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(TesAdapterApplication.class, args);
    }
}
