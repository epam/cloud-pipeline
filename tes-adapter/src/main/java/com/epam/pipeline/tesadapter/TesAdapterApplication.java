package com.epam.pipeline.tesadapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({
        "com.epam.pipeline.tesadapter.configuration",
        "com.epam.pipeline.tesadapter.controller",
        "com.epam.pipeline.tesadapter.service"})
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class})
public class TesAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(TesAdapterApplication.class, args);
    }
}
