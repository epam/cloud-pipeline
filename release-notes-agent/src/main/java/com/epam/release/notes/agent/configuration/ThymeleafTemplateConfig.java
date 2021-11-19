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

import com.epam.pipeline.utils.URLUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;

@Configuration
public class ThymeleafTemplateConfig {

    private static final String EMAIL_TEMPLATE_ENCODING = "UTF-8";
    private static final String CLASS_LOADER_RESOLVER_PREFIX = "mail/";

    private final String fileResolverPrefix;

    public ThymeleafTemplateConfig(@Value("${release.notes.agent.templates.dir}")
                                   final String fileResolverPrefix) {
        this.fileResolverPrefix = fileResolverPrefix;
    }

    @Bean
    public TemplateEngine emailTemplateEngine() {
        final TemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.addTemplateResolver(fileTemplateResolver());
        return templateEngine;
    }

    @Bean
    public AbstractConfigurableTemplateResolver fileTemplateResolver() {
        final AbstractConfigurableTemplateResolver templateResolver;
        if(StringUtils.isEmpty(fileResolverPrefix)) {
            templateResolver = new ClassLoaderTemplateResolver();
            templateResolver.setPrefix(CLASS_LOADER_RESOLVER_PREFIX);
        } else {
            templateResolver = new FileTemplateResolver();
            templateResolver.setPrefix(URLUtils.normalizeUrl(fileResolverPrefix));
        }
        templateResolver.setCharacterEncoding(EMAIL_TEMPLATE_ENCODING);
        return templateResolver;
    }

}
