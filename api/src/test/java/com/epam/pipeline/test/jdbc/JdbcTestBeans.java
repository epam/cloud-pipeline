/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.test.jdbc;

import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.configuration.RunConfigurationManager;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.issue.AttachmentFileManager;
import com.epam.pipeline.manager.issue.IssueManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.pipeline.PipelineCRUDManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RestartRunManager;
import com.epam.pipeline.manager.pipeline.RunScheduleManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.pipeline.runner.ConfigurationProviderManager;
import com.epam.pipeline.manager.scheduling.RunScheduler;
import com.epam.pipeline.manager.scheduling.ScheduleProviderManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.mapper.AbstractRunConfigurationMapper;
import com.epam.pipeline.mapper.IssueMapper;
import com.epam.pipeline.security.jwt.JwtTokenGenerator;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.spy;

@Configuration
public class JdbcTestBeans {

    @MockBean
    protected JwtTokenGenerator mockJwtTokenGenerator;

    @MockBean
    protected EntityManager mockEntityManager;

    @MockBean
    protected AttachmentFileManager mockAttachmentFileManager;

    @MockBean
    protected ToolManager mockToolManager;

    @MockBean
    protected PipelineCRUDManager mockCrudManager;

    @MockBean
    protected GitManager mockGitManager;

    @MockBean
    protected FolderManager mockFolderManager;

    @MockBean
    protected MetadataManager mockMetadataManager;

    @MockBean
    protected NotificationManager mockNotificationManager;

    @MockBean
    protected RunScheduler mockRunScheduler;

    @MockBean
    protected ScheduleProviderManager mockScheduleProviderManager;

    @MockBean
    protected PipelineRunManager mockPipelineRunManager;

    @MockBean
    protected AbstractRunConfigurationMapper mockAbstractRunConfigurationMapper;

    @MockBean
    protected ConfigurationProviderManager mockConfigurationProviderManager;

    @MockBean
    protected RestartRunManager spyRestartRunManager;

    @MockBean
    protected RunStatusManager spyRunStatusManager;

    @SpyBean
    protected PipelineManager spyPipelineManager;

    @SpyBean
    protected IssueManager spyIssueManager;

    @SpyBean
    protected AuthManager spyAuthManager;

    @SpyBean
    protected RunScheduleManager spyRunScheduleManager;

    @SpyBean
    protected RunConfigurationManager spyRunConfigurationManager;

    @Bean
    protected IssueMapper spyIssueMapper() {
        return spy(Mappers.getMapper(IssueMapper.class));
    }
}
