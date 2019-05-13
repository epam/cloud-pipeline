/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.PipelineVO;
import com.epam.pipeline.entity.git.GitProject;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.security.AuthManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@SuppressWarnings("PMD.TooManyStaticImports")
public class PipelineManagerTest {

    private static final String REPOSITORY_NAME = "repository";
    private static final String REPOSITORY_HTTPS = "https://example.com:repository/repository.git";
    private static final String REPOSITORY_SSH = "git@example.com:repository/repository.git";
    private static final String REPOSITORY_TOKEN = "token";

    @Mock
    private GitManager gitManager;

    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    private MessageHelper messageHelper;

    @Mock
    @SuppressWarnings("PMD.UnusedPrivateField")
    private AuthManager securityManager;

    @Mock
    private PipelineCRUDManager crudManager;

    @InjectMocks
    private PipelineManager pipelineManager = new PipelineManager();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final GitProject gitProject = new GitProject();
        gitProject.setRepoUrl(REPOSITORY_HTTPS);
        gitProject.setRepoSsh(REPOSITORY_SSH);
        when(gitManager.createRepository(any(), eq(REPOSITORY_NAME), any())).thenReturn(gitProject);
        when(gitManager.createRepository(any(), any(), eq(REPOSITORY_HTTPS), eq(REPOSITORY_TOKEN)))
                .thenReturn(gitProject);
        when(gitManager.getRepository(eq(REPOSITORY_HTTPS), eq(REPOSITORY_TOKEN))).thenReturn(gitProject);
        when(gitManager.checkProjectExists(eq(REPOSITORY_NAME))).thenReturn(false);
        when(crudManager.save(any())).thenAnswer(invocation -> invocation.getArguments()[0]);
    }

    @Test
    public void createShouldCreateRepositoryInDefaultGitlabIfThereIsNoRepositoryUrlInVO() throws GitClientException {
        final PipelineVO pipelineVO = new PipelineVO();
        pipelineVO.setName(REPOSITORY_NAME);

        pipelineManager.create(pipelineVO);

        verify(gitManager).createRepository(any(), eq(REPOSITORY_NAME), any());
    }

    @Test
    public void createShouldSetRepositoryUrlAndSshIfThereIsNoRepositoryUrlInVO() throws GitClientException {
        final PipelineVO pipelineVO = new PipelineVO();
        pipelineVO.setName(REPOSITORY_NAME);

        final Pipeline pipeline = pipelineManager.create(pipelineVO);

        assertThat(pipeline.getRepository(), is(REPOSITORY_HTTPS));
        assertThat(pipeline.getRepositorySsh(), is(REPOSITORY_SSH));
    }

    @Test
    public void createShouldCreateRepositoryInExternalGitlabIfItDoesNotExists() throws GitClientException {
        final PipelineVO pipelineVO = new PipelineVO();
        pipelineVO.setName(REPOSITORY_NAME);
        pipelineVO.setRepository(REPOSITORY_HTTPS);
        pipelineVO.setRepositoryToken(REPOSITORY_TOKEN);
        when(gitManager.getPipelineRevisions(any(), any())).thenThrow(new IllegalArgumentException(REPOSITORY_NAME));

        pipelineManager.create(pipelineVO);

        verify(gitManager).createRepository(any(), any(), eq(REPOSITORY_HTTPS), eq(REPOSITORY_TOKEN));
    }

    @Test
    public void createShouldSetRepositoryUrlAndSshFromExternalGitlabIfItDoesNotExists() throws GitClientException {
        final PipelineVO pipelineVO = new PipelineVO();
        pipelineVO.setName(REPOSITORY_NAME);
        pipelineVO.setRepository(REPOSITORY_HTTPS);
        pipelineVO.setRepositoryToken(REPOSITORY_TOKEN);
        when(gitManager.getPipelineRevisions(any(), any())).thenThrow(new IllegalArgumentException(REPOSITORY_NAME));

        final Pipeline pipeline = pipelineManager.create(pipelineVO);

        assertThat(pipeline.getRepository(), is(REPOSITORY_HTTPS));
        assertThat(pipeline.getRepositorySsh(), is(REPOSITORY_SSH));
    }

    @Test
    public void createShouldNotCreateRepositoryInExternalGitlabIfItAlreadyExists() throws GitClientException {
        final PipelineVO pipelineVO = new PipelineVO();
        pipelineVO.setName(REPOSITORY_NAME);
        pipelineVO.setRepository(REPOSITORY_HTTPS);
        pipelineVO.setRepositoryToken(REPOSITORY_TOKEN);
        when(gitManager.getPipelineRevisions(any(), any())).thenReturn(Collections.emptyList());

        pipelineManager.create(pipelineVO);

        verify(gitManager, times(0)).createRepository(any(), any(), eq(REPOSITORY_HTTPS), eq(REPOSITORY_TOKEN));
    }

    @Test
    public void createShouldSetRepositoryUrlAndSshFromExternalGitlabIfItAlreadyExists() throws GitClientException {
        final PipelineVO pipelineVO = new PipelineVO();
        pipelineVO.setName(REPOSITORY_NAME);
        pipelineVO.setRepository(REPOSITORY_HTTPS);
        pipelineVO.setRepositoryToken(REPOSITORY_TOKEN);
        when(gitManager.getPipelineRevisions(any(), any())).thenReturn(Collections.emptyList());

        final Pipeline pipeline = pipelineManager.create(pipelineVO);

        assertThat(pipeline.getRepository(), is(REPOSITORY_HTTPS));
        assertThat(pipeline.getRepositorySsh(), is(REPOSITORY_SSH));
    }
}
