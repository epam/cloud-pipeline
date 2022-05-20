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

package com.epam.pipeline.mapper.git;

import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.bitbucket.BitbucketAuthor;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCloneEntry;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCloneHrefType;
import com.epam.pipeline.entity.git.bitbucket.BitbucketCommit;
import com.epam.pipeline.entity.git.bitbucket.BitbucketLinks;
import com.epam.pipeline.entity.git.bitbucket.BitbucketProject;
import com.epam.pipeline.entity.git.bitbucket.BitbucketRepository;
import com.epam.pipeline.entity.git.bitbucket.BitbucketTag;
import com.epam.pipeline.entity.pipeline.Revision;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import java.util.Arrays;
import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class BitbucketMapperTest {
    private static final String TEST_NAME = "name";
    private static final String TEST_PROJECT = "project";
    private static final String TEST_PROJECT_KEY = "prj";
    private static final String TEST_SSH_LINK = "ssh://git@test/prj/name.git";
    private static final String TEST_HTTP_LINK = "http://test/scm/prj/name.git";
    private static final String TEST_PATH = "prj/name";
    private static final Long TEST_REPOSITORY_ID = 1L;
    private static final Long TEST_PROJECT_ID = 2L;
    private static final String TEST_TAG_NAME = "test";
    private static final String TEST_COMMIT_ID = "test123commit";
    private static final String TEST_SHORT_COMMIT = "test12";
    private static final String TEST_MESSAGE = "message";
    private static final Date TEST_DATE = new Date();
    private static final Long TEST_TIMESTAMP = TEST_DATE.getTime();
    private static final String TEST_AUTHOR_NAME = "test_test";
    private static final String TEST_AUTHOR_EMAIL = "test@test.com";

    private final BitbucketMapper mapper = Mappers.getMapper(BitbucketMapper.class);

    @Test
    public void shouldMapBitbucketRepositoryToGitRepository() {
        final BitbucketCloneEntry sshLink = BitbucketCloneEntry.builder()
                .href(TEST_SSH_LINK)
                .name(BitbucketCloneHrefType.ssh)
                .build();
        final BitbucketCloneEntry httpsLink = BitbucketCloneEntry.builder()
                .href(TEST_HTTP_LINK)
                .name(BitbucketCloneHrefType.http)
                .build();
        final BitbucketLinks links = new BitbucketLinks();
        links.setClone(Arrays.asList(sshLink, httpsLink));
        final BitbucketProject bitbucketProject = BitbucketProject.builder()
                .id(TEST_PROJECT_ID)
                .key(TEST_PROJECT_KEY)
                .name(TEST_PROJECT)
                .build();
        final BitbucketRepository bitbucketRepository = BitbucketRepository.builder()
                .name(TEST_NAME)
                .links(links)
                .project(bitbucketProject)
                .isPublic(true)
                .id(TEST_REPOSITORY_ID)
                .build();

        final GitProject repository = mapper.toGitRepository(bitbucketRepository);
        assertThat(repository.getId(), is(TEST_REPOSITORY_ID));
        assertThat(repository.getName(), is(TEST_NAME));
        assertThat(repository.getPath(), is(TEST_PATH));
        assertThat(repository.getProjectId(), is(TEST_PROJECT_ID));
        assertThat(repository.getRepoSsh(), is(TEST_SSH_LINK));
        assertThat(repository.getRepoUrl(), is(TEST_HTTP_LINK));
    }

    @Test
    public void shouldMapBitbucketTagToRevision() {
        final BitbucketTag bitbucketTag = BitbucketTag.builder()
                .displayId(TEST_TAG_NAME)
                .latestCommit(TEST_COMMIT_ID)
                .commit(commit())
                .build();

        final Revision revision = mapper.tagToRevision(bitbucketTag);
        assertThat(revision.getCommitId(), is(TEST_COMMIT_ID));
        assertThat(revision.getName(), is(TEST_TAG_NAME));
        assertThat(revision.getAuthor(), is(TEST_AUTHOR_NAME));
        assertThat(revision.getAuthorEmail(), is(TEST_AUTHOR_EMAIL));
        assertThat(revision.getCreatedDate(), is(TEST_DATE));
    }

    @Test
    public void shouldMapBitbucketCommitToRevision() {
        final Revision revision = mapper.commitToRevision(commit());
        assertThat(revision.getCommitId(), is(TEST_COMMIT_ID));
        assertThat(revision.getName(), is(TEST_SHORT_COMMIT));
        assertThat(revision.getAuthor(), is(TEST_AUTHOR_NAME));
        assertThat(revision.getAuthorEmail(), is(TEST_AUTHOR_EMAIL));
        assertThat(revision.getMessage(), is(TEST_MESSAGE));
        assertThat(revision.getCreatedDate(), is(TEST_DATE));
    }

    private BitbucketCommit commit() {
        return BitbucketCommit.builder()
                .id(TEST_COMMIT_ID)
                .displayId(TEST_SHORT_COMMIT)
                .message(TEST_MESSAGE)
                .author(BitbucketAuthor.builder()
                        .displayName(TEST_AUTHOR_NAME)
                        .emailAddress(TEST_AUTHOR_EMAIL)
                        .build())
                .authorTimestamp(TEST_TIMESTAMP)
                .build();
    }
}
