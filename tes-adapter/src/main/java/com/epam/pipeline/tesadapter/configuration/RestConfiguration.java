package com.epam.pipeline.tesadapter.configuration;

import com.epam.pipeline.tesadapter.entity.TesTokenHolder;
import com.epam.pipeline.tesadapter.controller.TesTokenInterceptor;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;

@Configuration
public class RestConfiguration implements WebMvcConfigurer {

    @Bean
    public HttpMessageConverters customConverters() {
        HttpMessageConverter<Object> addition = new MappingJackson2HttpMessageConverter();
        return new HttpMessageConverters(false, Collections.singletonList(addition));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(getTesTokenInterceptor());
    }

    @Bean
    public TesTokenInterceptor getTesTokenInterceptor() {
        return new TesTokenInterceptor(getTesTokenHolder());
    }

    @Bean
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
    public TesTokenHolder getTesTokenHolder() {
        return new TesTokenHolder();
    }
}
