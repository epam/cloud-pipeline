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

package com.epam.pipeline.dts;

import com.epam.pipeline.dts.configuration.CommonConfiguration;
import com.epam.pipeline.dts.configuration.DataSourceConfiguration;
import com.epam.pipeline.dts.configuration.RestConfiguration;
import com.epam.pipeline.dts.listing.ListingConfiguration;
import com.epam.pipeline.dts.security.JWTSecurityConfiguration;
import com.epam.pipeline.dts.submission.SubmissionConfiguration;
import com.epam.pipeline.dts.transfer.TransferConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication(
        exclude = {ThymeleafAutoConfiguration.class})
@Import({DataSourceConfiguration.class,
        RestConfiguration.class,
        CommonConfiguration.class,
        JWTSecurityConfiguration.class,
        ListingConfiguration.class,
        TransferConfiguration.class,
        SubmissionConfiguration.class})
@ComponentScan(basePackages = "com.epam.pipeline.dts.configuration")
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class DTSApplication {

    public static void main(String[] args) {
        SpringApplication.run(DTSApplication.class, args);
    }
}
