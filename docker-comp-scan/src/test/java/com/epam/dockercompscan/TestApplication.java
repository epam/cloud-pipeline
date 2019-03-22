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

package com.epam.dockercompscan;

import com.epam.dockercompscan.util.LayerScanCache;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.TestPropertySource;


@SpringBootConfiguration
@ComponentScan(basePackages = {"com.epam.dockercompscan.dockerregistry", "com.epam.dockercompscan.owasp",
        "com.epam.dockercompscan.scan"})
@TestPropertySource(value={"classpath:test-application.properties"})
public class TestApplication {

    @Value("${expire.cached.scan.time:24}")
    private int expireCacheTime;

    @Value("${number.cached.scans:50}")
    private int numberOfCachedScans;

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public OkHttpClient dockerRegistryClient() {
        return new OkHttpClient();
    }

    @Bean
    public LayerScanCache layerScanCache() {
        return new LayerScanCache(expireCacheTime, numberOfCachedScans);
    }

}

