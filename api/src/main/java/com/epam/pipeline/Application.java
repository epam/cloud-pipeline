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

package com.epam.pipeline;

import com.epam.pipeline.app.AppConfiguration;
import com.epam.pipeline.app.AppMVCConfiguration;
import com.epam.pipeline.app.DBConfiguration;
import com.epam.pipeline.app.ElasticsearchConfig;
import com.epam.pipeline.app.MappersConfiguration;
import com.epam.pipeline.app.RestConfiguration;
import com.epam.pipeline.app.SecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({AppMVCConfiguration.class,
        AppConfiguration.class,
        DBConfiguration.class,
        ElasticsearchConfig.class,
        SecurityConfig.class,
        MappersConfiguration.class})
@ComponentScan(basePackages = {"com.epam.pipeline.app"},
        excludeFilters = {@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = RestConfiguration.class)})
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
