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

package com.epam.pipeline.test.acl;

import com.epam.pipeline.app.AclSecurityConfiguration;
import com.epam.pipeline.manager.contextual.handler.ContextualPreferenceHandler;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test annotation providing the following test ApplicationContext configuration:
 * - all ACL-related beans are loaded
 * - for all dependencies mock beans are provided
 * - AclAspect is enabled for testing
 * - GlobalMethodSecurity is enabled
 * - Property source is defined for testing
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ContextConfiguration(classes = {AclTestBeans.class, AclSecurityConfiguration.class})
@Import({ContextualPreferenceHandler.class})
@ComponentScan(basePackages = {"com.epam.pipeline.security.acl", "com.epam.pipeline.manager.security"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@TestPropertySource(value = {"classpath:test-application.properties"})
public @interface AclTestConfiguration {

}
