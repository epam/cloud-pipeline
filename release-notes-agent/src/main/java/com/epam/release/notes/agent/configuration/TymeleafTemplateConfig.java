/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.release.notes.agent.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;

@Configuration
public class TymeleafTemplateConfig {

    private static final String EMAIL_TEMPLATE_ENCODING = "UTF-8";
    private static final String DOT = ".";

    private final String resolverPrefix;
    private final String resolverSuffix;

    public TymeleafTemplateConfig(
            @Value("${release.notes.agent.path.to.folder.with.templates}") final String resolverPrefix,
            @Value("${release.notes.agent.type.of.template.files}") final String resolverSuffix) {
        this.resolverPrefix = resolverPrefix;
        this.resolverSuffix = resolverSuffix;
    }

    @Bean
    public TemplateEngine emailTemplateEngine() {
        final TemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.addTemplateResolver(fileTemplateResolver());
        return templateEngine;
    }

    @Bean
    public AbstractConfigurableTemplateResolver fileTemplateResolver() {
        final AbstractConfigurableTemplateResolver templateResolver = new FileTemplateResolver();
        templateResolver.setPrefix(resolverPrefix);
        templateResolver.setSuffix(DOT + resolverSuffix);
        templateResolver.setTemplateMode(TemplateMode.parse(resolverSuffix));
        templateResolver.setCharacterEncoding(EMAIL_TEMPLATE_ENCODING);
        return templateResolver;
    }

}
