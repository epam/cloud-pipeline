package com.epam.pipeline.test.web;

import com.epam.pipeline.app.AppMVCConfiguration;
import com.epam.pipeline.app.JWTSecurityConfiguration;
import com.epam.pipeline.app.RestConfiguration;
import com.epam.pipeline.test.CommonTestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test annotation providing the following test ApplicationContext configuration:
 *  - all controller beans are loaded
 *  - for all dependencies mock beans are provided
 *  - Spring MockMVC is configured
 *  - JWT Security is enabled
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ContextConfiguration(classes = {
        CommonTestConfiguration.class,
        WebTestBeans.class,
        RestConfiguration.class,
        AppMVCConfiguration.class,
        JWTSecurityConfiguration.class})
@WebAppConfiguration
@AutoConfigureMockMvc
@TestPropertySource(value={"classpath:test-application.properties"})
public @interface WebTestConfiguration {
}
