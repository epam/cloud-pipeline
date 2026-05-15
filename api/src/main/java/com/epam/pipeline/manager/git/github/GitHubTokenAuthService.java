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

package com.epam.pipeline.manager.git.github;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.git.github.GitHubInstallation;
import com.epam.pipeline.entity.git.github.GitHubRepository;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.exception.git.GitClientException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * {@link RepositoryType#GITHUB}: use only the stored repository token (PAT). Installation tokens are not used.
 */
@Component
@RequiredArgsConstructor
public class GitHubTokenAuthService implements GitHubAuthInnerService {

    private final MessageHelper messageHelper;

    @Override
    public String resolveToken(final String repositoryPath, final String explicitToken) {
        return requireRepositoryToken(explicitToken);
    }

    @Override
    public String resolveCloneToken(final Pipeline pipeline) {
        return requireRepositoryToken(pipeline.getRepositoryToken());
    }

    @Override
    public List<GitHubInstallation> getAllowedNamespaces() {
        return Collections.emptyList();
    }

    @Override
    public List<GitHubRepository> getNamespaceRepositories(final String installationId) {
        return Collections.emptyList();
    }

    private String requireRepositoryToken(final String token) {
        if (StringUtils.isBlank(token)) {
            throw new GitClientException(messageHelper.getMessage(
                    MessageConstants.ERROR_REPOSITORY_TOKEN_NOT_FOUND, RepositoryType.GITHUB));
        }
        return token;
    }
}
