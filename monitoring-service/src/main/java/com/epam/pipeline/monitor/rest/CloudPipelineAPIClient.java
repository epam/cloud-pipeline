/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.monitor.rest;

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.client.pipeline.CloudPipelineApiExecutor;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.user.PipelineUser;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class CloudPipelineAPIClient {
    private final CloudPipelineAPI cloudPipelineAPI;
    private final CloudPipelineApiExecutor executor;

    public CloudPipelineAPIClient(@Value("${cloud.pipeline.host}") final String cloudPipelineHostUrl,
                                  @Value("${cloud.pipeline.token}") final String cloudPipelineToken,
                                  final CloudPipelineApiExecutor cloudPipelineApiExecutor) {
        this.cloudPipelineAPI =
                new CloudPipelineApiBuilder(0, 0, cloudPipelineHostUrl, cloudPipelineToken)
                        .buildClient();
        this.executor = cloudPipelineApiExecutor;
    }

    public List<PipelineUser> loadOnlineUsers() {
        return executor.execute(cloudPipelineAPI.loadAllOnlineUsers());
    }

    public Integer getIntPreference(final String preferenceName) {
        final Preference preference = executor.execute(cloudPipelineAPI.loadPreference(preferenceName));
        if (Objects.isNull(preference) || StringUtils.isBlank(preference.getValue())) {
            return null;
        }
        return Integer.parseInt(preference.getValue());
    }

    public boolean getBooleanPreference(final String preferenceName) {
        final Preference preference = executor.execute(cloudPipelineAPI.loadPreference(preferenceName));
        if (Objects.isNull(preference) || StringUtils.isBlank(preference.getValue())) {
            return false;
        }
        return Boolean.parseBoolean(preference.getValue());
    }
}
