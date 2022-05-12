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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryUrl;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCommit;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCommits;
import com.epam.pipeline.entity.git.bitbucket.BitbucketRepository;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTag;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTags;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.RepositoryType;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.GitClientService;
import com.epam.pipeline.mapper.git.BitbucketMapper;
import com.epam.pipeline.utils.AuthorizationUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BitbucketService implements GitClientService {
    private static final String REPOSITORY_NAME = "repository name";
    private static final String PROJECT_NAME = "project name";
    private static final String INITIAL_COMMIT = "Initial commit";

    private final BitbucketMapper mapper;
    private final MessageHelper messageHelper;

    @Override
    public RepositoryType getType() {
        return RepositoryType.BITBUCKET;
    }

    @Override
    public GitProject getRepository(final String repositoryPath, final String token) {
        final BitbucketRepository repository = getClient(repositoryPath, token).getRepository();
        return mapper.toGitRepository(repository);
    }

    @Override
    public GitProject createRepository(final String description, final String path, final String token) {
        final BitbucketRepository bitbucketRepository = BitbucketRepository.builder()
                .isPublic(false)
                .description(description)
                .build();
        final BitbucketRepository repository = getClient(path, token).createRepository(bitbucketRepository);
        return mapper.toGitRepository(repository);
    }

    @Override
    public void handleHooks(final GitProject project, final String token) {
        // not supported
    }

    @Override
    public void createFile(final GitProject repository, final String path, final String content, final String token) {
        getClient(repository.getRepoUrl(), token).createFile(path, content, INITIAL_COMMIT);
    }

    @Override
    public byte[] getFileContents(final GitProject repository, final String path, final String revision,
                                  final String token) {
        return getClient(repository.getRepoUrl(), token).getFileContent(revision, path);
    }

    @Override
    public List<Revision> getTags(final Pipeline pipeline) {
        final BitbucketClient client = getClient(pipeline.getRepository(),
                pipeline.getRepositoryToken());

        final BitbucketTags tags = client.getTags();
        return Optional.ofNullable(tags)
                .map(values -> ListUtils.emptyIfNull(values.getValues()).stream()
                        .map(tag -> fillCommitInfo(tag, client))
                        .map(mapper::tagToRevision)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public Revision getLastRevision(final Pipeline pipeline) {
        final BitbucketCommits commits = getClient(pipeline.getRepository(), pipeline.getRepositoryToken())
                .getCommits();
        return Optional.ofNullable(commits)
                .flatMap(value -> ListUtils.emptyIfNull(value.getValues()).stream()
                        .findFirst()
                        .map(mapper::commitToRevision))
                .orElse(null);
    }

    private BitbucketClient getClient(final String repositoryPath, final String token) {
        return buildClient(repositoryPath, token);
    }

    private BitbucketClient buildClient(final String repositoryPath, final String token) {
        final GitRepositoryUrl repositoryUrl = GitRepositoryUrl.fromBitbucket(repositoryPath);
        final String projectName = repositoryUrl.getNamespace().orElseThrow(() -> buildUrlParseError(PROJECT_NAME));
        final String repositoryName = repositoryUrl.getProject().orElseThrow(() -> buildUrlParseError(REPOSITORY_NAME));
        final String protocol = repositoryUrl.getProtocol();
        final String host = repositoryUrl.getHost();
        final String bitbucketHost = protocol + host;

        Assert.isTrue(StringUtils.isNotBlank(token), messageHelper
                .getMessage(MessageConstants.ERROR_BITBUCKET_TOKEN_NOT_FOUND));
        final String credentials = AuthorizationUtils.BEARER_AUTH + token;

        return new BitbucketClient(bitbucketHost, credentials, null, projectName, repositoryName);
    }

    private GitClientException buildUrlParseError(final String urlPart) {
        return new GitClientException(messageHelper.getMessage(
                MessageConstants.ERROR_PARSE_BITBUCKET_REPOSITORY_PATH, urlPart));
    }

    private BitbucketTag fillCommitInfo(final BitbucketTag tag, final BitbucketClient client) {
        final BitbucketCommit commit = client.getCommit(tag.getLatestCommit());
        tag.setCommit(commit);
        return tag;
    }
}
