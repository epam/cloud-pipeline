package com.epam.pipeline.tesadapter.configuration;

import com.epam.pipeline.tesadapter.controller.TesTokenInterceptor;
import com.epam.pipeline.tesadapter.entity.TesTokenHolder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;

@Configuration
public class RestConfiguration implements WebMvcConfigurer {
    private TesTokenInterceptor tesTokenInterceptor;

    @Bean
    public HttpMessageConverters customConverters() {
        MappingJackson2HttpMessageConverter addition = new MappingJackson2HttpMessageConverter();
        addition.setObjectMapper(new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL));
        return new HttpMessageConverters(false, Collections.singletonList(addition));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tesTokenInterceptor);
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public TesTokenHolder getTesTokenHolder() {
        return new TesTokenHolder();
    }

    @Autowired
    public void setTesTokenInterceptor(TesTokenInterceptor tesTokenInterceptor) {
        this.tesTokenInterceptor = tesTokenInterceptor;
    }
}
