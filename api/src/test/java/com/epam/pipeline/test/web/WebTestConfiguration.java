/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.test.web;

import com.epam.pipeline.app.AppMVCConfiguration;
import com.epam.pipeline.app.JWTSecurityConfiguration;
import com.epam.pipeline.app.RestConfiguration;
import com.epam.pipeline.test.CommonTestContext;
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
        CommonTestContext.class,
        ControllerTestBeans.class,
        RestConfiguration.class,
        AppMVCConfiguration.class,
        JWTSecurityConfiguration.class})
@WebAppConfiguration
@AutoConfigureMockMvc
@TestPropertySource(value={"classpath:test-application.properties"})
public @interface WebTestConfiguration {
}
