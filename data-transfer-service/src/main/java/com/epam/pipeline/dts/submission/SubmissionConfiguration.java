/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.submission;

import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.cmd.PlainCmdExecutor;
import com.epam.pipeline.cmd.ImpersonatingCmdExecutor;
import com.epam.pipeline.dts.configuration.CommonConfiguration;
import com.epam.pipeline.dts.security.JWTSecurityConfiguration;
import com.epam.pipeline.dts.submission.configuration.SubmissionRestConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Controller;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * Module for work with SGE jobs
 */
@SpringBootConfiguration
@EntityScan(basePackages = {"com.epam.pipeline.dts.submission.model"})
@EnableJpaRepositories(basePackages = {"com.epam.pipeline.dts.submission.repository"})
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = SubmissionRestConfiguration.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Controller.class)})
@Import({JWTSecurityConfiguration.class, CommonConfiguration.class})
@EnableScheduling
public class SubmissionConfiguration {

    @Bean
    public CmdExecutor submissionCmdExecutor() {
        return new PlainCmdExecutor();
    }

    @Bean
    public TemplateEngine templateEngine() {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(fileTemplateResolver());
        return templateEngine;
    }

    private ITemplateResolver fileTemplateResolver() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setCacheable(false);
        resolver.setTemplateMode(TemplateMode.TEXT);
        return resolver;
    }
}
