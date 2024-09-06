/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.bitbucketcloud.BitbucketCloudService;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

public class BitbucketCloudServiceTest extends AbstractSpringTest {

    private static final String BRANCH = "test";
    private static final String TAG = "test_tag10";
    private static final String MESSAGE = "message";
    private static final String COMMIT_ID = "d468da2";
    private static final String FILE_PATH = "/test2/test_file.txt";
    private static final String FOLDER_PATH = "test2";
    private static final String CONTENT = "content123";
    @Value("${bitbucket.cloud.repository.path}")
    private String repositoryPath;
    @Value("${bitbucket.cloud.token}")
    private String token;

    @Autowired
    private BitbucketCloudService service;

    @Ignore
    @Test
    public void testGetRepository() throws GitClientException {
        final GitProject project = service.getRepository(repositoryPath, token);
        Assert.assertNotNull(project);
    }

    @Ignore
    @Test
    public void testDeleteRepository() throws GitClientException {
        service.deleteRepository(getPipeline());
    }

    @Ignore
    @Test
    public void testGetBranches() throws GitClientException {
        final List<String> branches = service.getBranches(repositoryPath, token);
        Assert.assertTrue(branches.size() > 0);
    }

    @Ignore
    @Test
    public void testGetTags() throws GitClientException {
        final List<Revision> tags = service.getTags(getPipeline());
        Assert.assertTrue(tags.size() > 0);
    }

    @Ignore
    @Test
    public void testCreateTag() throws GitClientException {
        final Revision tag = service.createTag(getPipeline(), TAG, COMMIT_ID, MESSAGE, "");
        Assert.assertNotNull(tag);
    }

    @Ignore
    @Test
    public void testGetTag() throws GitClientException {
        final GitTagEntry tag = service.getTag(getPipeline(), TAG);
        Assert.assertNotNull(tag);
    }

    @Ignore
    @Test
    public void testGetCommit() throws GitClientException {
        final GitCommitEntry commit = service.getCommit(getPipeline(), COMMIT_ID);
        Assert.assertNotNull(commit);
    }

    @Ignore
    @Test
    public void testGetLastRevision() throws GitClientException {
        final Revision commit = service.getLastRevision(getPipeline(), "");
        Assert.assertNotNull(commit);
    }

    @Ignore
    @Test
    public void testCreateFile() throws GitClientException {
        service.createFile(getGitProject(), FILE_PATH, CONTENT, token, BRANCH);
    }

    @Ignore
    @Test
    public void testGetFile() throws GitClientException {
        final byte[] content = service.getFileContents(getGitProject(), FILE_PATH, COMMIT_ID, token);
        Assert.assertNotNull(content);
    }

    @Ignore
    @Test
    public void testGetFiles() throws GitClientException {
        final List<GitRepositoryEntry> contents = service.getRepositoryContents(getPipeline(),
                FOLDER_PATH, COMMIT_ID, true);
        Assert.assertTrue(contents.size() > 0);
    }

    private Pipeline getPipeline() {
        final Pipeline pipeline = new Pipeline();
        pipeline.setRepository(repositoryPath);
        pipeline.setRepositoryToken(token);
        pipeline.setBranch(BRANCH);
        return pipeline;
    }

    private GitProject getGitProject() {
        final GitProject project = new GitProject();
        project.setRepoUrl(repositoryPath);
        return project;
    }
}
