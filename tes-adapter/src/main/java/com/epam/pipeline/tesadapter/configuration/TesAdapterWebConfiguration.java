package com.epam.pipeline.tesadapter.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

@EnableWebMvc
@Configuration
public class TesAdapterWebConfiguration extends WebMvcConfigurerAdapter {
}
