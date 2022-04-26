/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git.bibucket;

import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.bitbucket.BitbucketRepository;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.GitClientService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.git.BitbucketRepositoryMapper;
import com.epam.pipeline.utils.AuthorizationUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BitbucketService implements GitClientService {

    private final PreferenceManager preferenceManager;
    private final BitbucketRepositoryMapper mapper;

    @Override
    public RepositoryType getType() {
        return RepositoryType.BITBUCKET;
    }

    @Override
    public GitProject getRepository(final String path, final String token) {
        final String namespace = preferenceManager.getPreference(SystemPreferences.BITBUCKET_USER_NAME);
        final BitbucketRepository repository = getClient(token).getRepository(path, namespace);
        return mapper.toGit(repository);
    }

    @Override
    public boolean checkRepositoryExists(final String name) throws GitClientException {
        final String namespace = preferenceManager.getPreference(SystemPreferences.BITBUCKET_USER_NAME);
        return getDefaultClient().checkProjectExists(namespace, name);
    }

    @Override
    public GitProject createEmptyRepository(final String name, final String description) {
        final String namespace = preferenceManager.getPreference(SystemPreferences.BITBUCKET_USER_NAME);
        final BitbucketRepository bitbucketRepository = new BitbucketRepository();
        bitbucketRepository.setPrivate(true);
        bitbucketRepository.setDescription(description);
        final BitbucketRepository repository = getDefaultClient()
                .createRepository(namespace, name, bitbucketRepository);
        return mapper.toGit(repository);
    }

    @Override
    public void handleHooks(final GitProject project) {
        final boolean indexingEnabled = preferenceManager
                .getPreference(SystemPreferences.BITBUCKET_REPOSITORY_INDEXING_ENABLED);
        if (indexingEnabled) {
            throw new UnsupportedOperationException("Bitbucket hooks are not supported for now");
        }
    }

    @Override
    public void createFile(final GitProject project, final String path, final String content) {
        final String namespace = preferenceManager.getPreference(SystemPreferences.BITBUCKET_USER_NAME);
        getDefaultClient().createFile(namespace, project.getName(), path, content);
    }

    @Override
    public byte[] getFileContents(final GitProject repository, final String path, final String revision) {
        final String namespace = preferenceManager.getPreference(SystemPreferences.BITBUCKET_USER_NAME);
        return getDefaultClient().getFileContent(namespace, repository.getName(), revision, path);
    }

    private BitbucketClient getClient(final String token) {
        return getClient(null, token);
    }

    private BitbucketClient getDefaultClient() {
        return getClient(null, null);
    }

    private BitbucketClient getClient(final String host, final String token) {
        final String bitbucketHost = StringUtils.isBlank(host)
                ? preferenceManager.getPreference(SystemPreferences.BITBUCKET_HOST)
                : host;
        final String credentials = StringUtils.isBlank(token)
                ? buildDefaultCredentials()
                : token;
        return new BitbucketClient(bitbucketHost, credentials);
    }

    private String buildDefaultCredentials() {
        return AuthorizationUtils.buildBasicAuth(
                preferenceManager.getPreference(SystemPreferences.BITBUCKET_USER_NAME),
                preferenceManager.getPreference(SystemPreferences.BITBUCKET_APP_PASS));
    }
}
