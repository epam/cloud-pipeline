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

package com.epam.pipeline.manager.git.bitbucketcloud;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudPagedResponse;
import com.epam.pipeline.entity.git.bitbucketcloud.BitbucketCloudSource;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.mapper.git.BitbucketCloudMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BitbucketCloudServicePagingTest {

    private static final String VERSION = "abc123";
    private static final String PATH = "src";
    private static final int MAX_DEPTH = 20;
    private static final String FILE_A = "src/A.java";
    private static final String FILE_B = "src/B.java";

    @Mock
    private BitbucketCloudMapper mapper;
    @Mock
    private MessageHelper messageHelper;
    @Mock
    private PreferenceManager preferenceManager;
    @Mock
    private BitbucketCloudClient mockClient;

    private BitbucketCloudService service;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        service = new BitbucketCloudService(mapper, messageHelper, preferenceManager) {
            @Override
            protected BitbucketCloudClient buildClient(final String repositoryPath, final String token) {
                return mockClient;
            }
        };
    }

    @Test
    public void shouldReturnSinglePageResultsWhenNextIsNull() {
        when(mockClient.getFiles(PATH, VERSION, null, MAX_DEPTH))
                .thenReturn(page(Arrays.asList(file(FILE_A), file(FILE_B)), null));

        final List<GitRepositoryEntry> result = service.getRepositoryContents(
                pipeline(), PATH, VERSION, true, false);

        Assert.assertEquals(2, result.size());
        verify(mockClient, times(1)).getFiles(any(), any(), any(), any());
    }

    @Test
    public void shouldFollowPaginationWhenNextUrlHasSinglePageParam() {
        when(mockClient.getFiles(PATH, VERSION, null, MAX_DEPTH))
                .thenReturn(page(Collections.singletonList(file(FILE_A)),
                        "https://api.bitbucket.org/2.0/repos/ws/r/src?page=cursor2"));
        when(mockClient.getFiles(PATH, VERSION, "cursor2", MAX_DEPTH))
                .thenReturn(page(Collections.singletonList(file(FILE_B)), null));

        final List<GitRepositoryEntry> result = service.getRepositoryContents(
                pipeline(), PATH, VERSION, true, false);

        Assert.assertEquals(2, result.size());
        verify(mockClient).getFiles(PATH, VERSION, "cursor2", MAX_DEPTH);
    }

    @Test
    public void shouldExtractOnlyPageTokenWhenNextUrlHasMultipleQueryParams() {
        // Regression: splitting on "page=" would produce "cursor123&foo=bar" instead of "cursor123"
        when(mockClient.getFiles(PATH, VERSION, null, MAX_DEPTH))
                .thenReturn(page(Collections.singletonList(file(FILE_A)),
                        "https://api.bitbucket.org/2.0/repos/ws/r/src?maxdepth=20&page=cursor123&foo=bar"));
        when(mockClient.getFiles(PATH, VERSION, "cursor123", MAX_DEPTH))
                .thenReturn(page(Collections.singletonList(file(FILE_B)), null));

        final List<GitRepositoryEntry> result = service.getRepositoryContents(
                pipeline(), PATH, VERSION, true, false);

        Assert.assertEquals(2, result.size());
        verify(mockClient).getFiles(PATH, VERSION, "cursor123", MAX_DEPTH);
    }

    @Test
    public void shouldStopPaginationWhenNextUrlHasNoPageParam() {
        when(mockClient.getFiles(PATH, VERSION, null, MAX_DEPTH))
                .thenReturn(page(Collections.singletonList(file(FILE_A)),
                        "https://api.bitbucket.org/2.0/repos/ws/r/src?maxdepth=20"));

        final List<GitRepositoryEntry> result = service.getRepositoryContents(
                pipeline(), PATH, VERSION, true, false);

        Assert.assertEquals(1, result.size());
        verify(mockClient, times(1)).getFiles(any(), any(), any(), any());
    }

    private Pipeline pipeline() {
        final Pipeline p = new Pipeline();
        p.setRepository("https://user@bitbucket.org/workspace/repo.git");
        p.setRepositoryToken("token");
        p.setBranch("main");
        return p;
    }

    private static BitbucketCloudSource file(final String path) {
        return BitbucketCloudSource.builder().path(path).type("commit_file").build();
    }

    private static BitbucketCloudPagedResponse<BitbucketCloudSource> page(
            final List<BitbucketCloudSource> values, final String next) {
        final BitbucketCloudPagedResponse<BitbucketCloudSource> response = new BitbucketCloudPagedResponse<>();
        response.setValues(new ArrayList<>(values));
        response.setNext(next);
        return response;
    }
}
