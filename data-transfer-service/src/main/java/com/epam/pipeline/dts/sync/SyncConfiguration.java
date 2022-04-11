/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.sync;

import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.cmd.EnvironmentCmdExecutor;
import com.epam.pipeline.cmd.PipelineCLI;
import com.epam.pipeline.cmd.PipelineCLIImpl;
import com.epam.pipeline.cmd.PlainCmdExecutor;
import com.epam.pipeline.dts.configuration.CommonConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import java.util.HashMap;
import java.util.Map;

@SpringBootConfiguration
@ComponentScan(basePackages = "com.epam.pipeline.dts.sync")
@Import({CommonConfiguration.class})
public class SyncConfiguration {

    private static final String API_ENV_VAR = "API";
    private static final String API_TOKEN_ENV_VAR = "API_TOKEN";

    @Bean
    public PipelineCLI tunnelPipelineCLI(@Value("${dts.api.url}")
                                         final String apiUrl,
                                         @Value("${dts.api.token}")
                                         final String apiToken,
                                         @Value("${dts.transfer.pipe.executable}")
                                         final String pipelineCliExecutable) {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(API_ENV_VAR, apiUrl);
        envVars.put(API_TOKEN_ENV_VAR, apiToken);
        final CmdExecutor cmdExecutor = new EnvironmentCmdExecutor(new PlainCmdExecutor(), envVars);
        return new PipelineCLIImpl(pipelineCliExecutable, "", false, 1, cmdExecutor);
    }
}
