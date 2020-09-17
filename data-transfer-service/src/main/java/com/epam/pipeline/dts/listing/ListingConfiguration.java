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

package com.epam.pipeline.dts.listing;

import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.cmd.ImpersonatingCmdExecutor;
import com.epam.pipeline.cmd.PlainCmdExecutor;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dts.listing.configuration.ListingRestConfiguration;
import com.epam.pipeline.dts.listing.service.ListingService;
import com.epam.pipeline.dts.listing.service.impl.ImpersonatingLocalListingService;
import com.epam.pipeline.dts.security.service.SecurityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Controller;

@SpringBootConfiguration
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ListingRestConfiguration.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Controller.class)})
public class ListingConfiguration {

    @Bean
    public JsonMapper jsonMapper() {
        return new JsonMapper();
    }
    
    @Bean
    public CmdExecutor listingCmdExecutor() {
        return new ImpersonatingCmdExecutor(new PlainCmdExecutor());
    }
    
    @Bean
    public ListingService listingService(final SecurityService securityService,
                                         final CmdExecutor listingCmdExecutor,
                                         @Value("${dts.listing.listTemplate}")
                                         final String listTemplate) {
        return new ImpersonatingLocalListingService(securityService, listingCmdExecutor, listTemplate);
    }
}
