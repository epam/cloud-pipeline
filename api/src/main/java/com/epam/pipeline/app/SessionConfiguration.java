/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.app;

import com.epam.pipeline.security.converter.PipelineDeserializingConverter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.SerializingConverter;

/**
 * Spring Session in jdbc mode stores the sessions in db tables. JdbcIndexedSessionRepository is responsible for that,
 * and uses GenericConversionService as a default for serialization and deserialization of user data into/from tables.
 * To redefine it and inject this class instead of the default conversion service, we are defining our own bean
 * with the serializer and deserializer we need.
 */
@Configuration
public class SessionConfiguration implements BeanClassLoaderAware {

    private ClassLoader classLoader;

    /**
     * To inject ClassLoader, we need to implement the BeanClassLoaderAware interface.
     */
    @Override
    public void setBeanClassLoader(@NotNull final ClassLoader beanClassLoader) {
        this.classLoader = beanClassLoader;
    }

    @Bean
    public GenericConversionService springSessionConversionService() {
        final GenericConversionService conversionService = new GenericConversionService();
        conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
        conversionService.addConverter(byte[].class, Object.class, new PipelineDeserializingConverter(classLoader));
        return conversionService;
    }
}
