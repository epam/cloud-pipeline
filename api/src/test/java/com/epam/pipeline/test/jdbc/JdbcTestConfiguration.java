package com.epam.pipeline.test.jdbc;

import com.epam.pipeline.app.DBConfiguration;
import com.epam.pipeline.test.CommonTestContext;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ContextConfiguration(classes = {DBConfiguration.class, CommonTestContext.class})
@ComponentScan(basePackages = {"com.epam.pipeline.dao"})
@AutoConfigureJdbc
@TestPropertySource(value={"classpath:test-application.properties"})
@Transactional
public @interface JdbcTestConfiguration {

}
