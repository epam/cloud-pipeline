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

package com.epam.pipeline.dts.configuration;

import com.epam.pipeline.dts.common.json.JsonMapper;
import com.epam.pipeline.dts.listing.configuration.ListingRestConfiguration;
import com.epam.pipeline.dts.submission.configuration.SubmissionRestConfiguration;
import com.epam.pipeline.dts.transfer.configuration.TransferRestConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.paths.RelativePathProvider;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.servlet.ServletContext;
import java.util.List;

@Configuration
@EnableSwagger2
public class RestConfiguration implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper());
        converters.add(converter);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        UrlPathHelper urlPathHelper = new UrlPathHelper();
        urlPathHelper.setRemoveSemicolonContent(false);
        configurer.setUrlPathHelper(urlPathHelper);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new JsonMapper();
    }

    @Bean
    public Docket api(ServletContext servletContext) {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.any())
                .build()
                .apiInfo(apiInfo())
                .pathProvider(new RelativePathProvider(servletContext) {
                    @Override
                    protected String applicationPath() {
                        return servletContext.getContextPath() + "/restapi";
                    }
                    @Override
                    protected String getDocumentationPath() {
                        return "/";
                    }});
    }

    @Bean
    public ServletRegistrationBean dispatcherRegistration(){
        DispatcherServlet dispatcherServlet = new DispatcherServlet();
        ServletRegistrationBean<DispatcherServlet> bean =
                new ServletRegistrationBean<>(dispatcherServlet, "/restapi/*");
        bean.setAsyncSupported(true);
        bean.setName("dts");
        bean.setLoadOnStartup(1);
        AnnotationConfigWebApplicationContext applicationContext = new AnnotationConfigWebApplicationContext();
        applicationContext.register(
                ListingRestConfiguration.class,
                TransferRestConfiguration.class,
                SubmissionRestConfiguration.class);
        dispatcherServlet.setApplicationContext(applicationContext);
        return bean;
    }

    private ApiInfo apiInfo() {
        return new ApiInfo(
                "Template REST API",
                "Some custom description of API.",
                "API TOS",
                "Terms of service",
                new Contact("dev", "url", "email"),
                "License of API",
                "API license URL");
    }
}
