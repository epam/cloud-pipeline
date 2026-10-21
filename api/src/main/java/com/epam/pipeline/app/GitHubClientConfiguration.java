/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.app;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.manager.git.GitClientService;
import com.epam.pipeline.manager.git.github.GitHubAppAuthService;
import com.epam.pipeline.manager.git.github.GitHubTokenAuthService;
import com.epam.pipeline.manager.git.github.GitHubService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.mapper.git.GitHubMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This configuration implemented to distinguish GITHUB and GITHUB_APP repository types.
 */
@Configuration
public class GitHubClientConfiguration {

    @Bean
    public GitClientService gitHubClientService(
            final GitHubTokenAuthService gitHubTokenAuthService,
            final GitHubMapper mapper,
            final MessageHelper messageHelper,
            final PreferenceManager preferenceManager) {
        return new GitHubService(
                RepositoryType.GITHUB,
                gitHubTokenAuthService,
                mapper,
                messageHelper,
                preferenceManager);
    }

    @Bean
    public GitClientService gitHubAppClientService(
            final GitHubAppAuthService gitHubAppAuthService,
            final GitHubMapper mapper,
            final MessageHelper messageHelper,
            final PreferenceManager preferenceManager) {
        return new GitHubService(
                RepositoryType.GITHUB_APP,
                gitHubAppAuthService,
                mapper,
                messageHelper,
                preferenceManager);
    }
}
