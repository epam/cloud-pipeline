/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.git;

import com.epam.pipeline.entity.git.GitCommitsFilter;
import com.epam.pipeline.entity.git.gitreader.GitReaderLogRequestFilter;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class GitReaderClientTest {

    @Test
    public void testMapEmptyCommitFilters() {
        GitReaderLogRequestFilter mapped = GitReaderClient.toGitReaderRequestFilter(
                GitCommitsFilter.builder().build(), Collections.emptyList());

        Assert.assertTrue(CollectionUtils.isEmpty(mapped.getPathMasks()));
        Assert.assertNull(mapped.getAuthors());
        Assert.assertNull(mapped.getRef());
        Assert.assertNull(mapped.getDateFrom());
        Assert.assertNull(mapped.getDateTo());
    }

    @Test
    public void testMapCommitFiltersExtension() {
        GitReaderLogRequestFilter mapped = GitReaderClient.toGitReaderRequestFilter(
                GitCommitsFilter.builder()
                        .extensions(Collections.singletonList("js"))
                        .build(), Collections.emptyList());

        Assert.assertArrayEquals(mapped.getPathMasks().toArray(), Collections.singletonList("*.js").toArray());
    }

    @Test
    public void testMapCommitFiltersSeveralExtensions() {
        GitReaderLogRequestFilter mapped = GitReaderClient.toGitReaderRequestFilter(
                GitCommitsFilter.builder()
                        .extensions(Arrays.asList("js", "py"))
                        .build(), Collections.emptyList());

        Assert.assertArrayEquals(mapped.getPathMasks().toArray(), new String[]{"*.js", "*.py"});
    }

    @Test
    public void testMapCommitFiltersSeveralExtensionsWithPath() {
        GitReaderLogRequestFilter mapped = GitReaderClient.toGitReaderRequestFilter(
                GitCommitsFilter.builder()
                        .path("path/")
                        .extensions(Arrays.asList("js", "py"))
                        .build(), Collections.emptyList());

        Assert.assertArrayEquals(mapped.getPathMasks().toArray(), new String[]{"path/*.js", "path/*.py"});
    }

    @Test
    public void testMapCommitFiltersWithPath() {
        GitReaderLogRequestFilter mapped = GitReaderClient.toGitReaderRequestFilter(
                GitCommitsFilter.builder()
                        .path("path/")
                        .build(), Collections.emptyList());

        Assert.assertArrayEquals(mapped.getPathMasks().toArray(), new String[]{"path/"});
    }

}
