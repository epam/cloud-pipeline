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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.dao.tool.ToolVersionDao;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.manager.AbstractManagerTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Date;
import java.util.Optional;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class ToolVersionManagerTest extends AbstractManagerTest {
    private static final String TEST_DIGEST = "sha256:aaa";
    private static final String TEST_DIGEST_2 = "sha256:bbb";
    private static final Long TEST_SIZE = 123L;
    private static final Date TEST_LAST_MODIFIED_DATE = new Date();
    private static final String TEST_VERSION = "latest";
    private static final String TEST_IMAGE = "image";
    private static final Long TEST_TOOL_ID = 1L;

    @MockBean
    private ToolVersionDao toolVersionDao;
    @Autowired
    private ToolVersionManager toolVersionManager;
    @Mock
    private DockerRegistry dockerRegistry;
    @Mock
    private DockerClient dockerClient;

    private ToolVersion toolVersion;

    @Before
    public void setUp() {
        toolVersion = ToolVersion
                .builder()
                .digest(TEST_DIGEST)
                .size(TEST_SIZE)
                .version(TEST_VERSION)
                .modificationDate(TEST_LAST_MODIFIED_DATE)
                .toolId(1L)
                .build();
    }

    @Test
    public void shouldCreateToolVersion() {
        when(toolVersionDao.loadToolVersion(TEST_TOOL_ID, TEST_VERSION)).thenReturn(Optional.empty());
        when(dockerClient.getVersionAttributes(any(DockerRegistry.class), anyString(), anyString()))
                .thenReturn(this.toolVersion);
        doNothing().when(toolVersionDao).createToolVersion(isA(ToolVersion.class));
        doThrow(getThrowable()).when(toolVersionDao).updateToolVersion(any(ToolVersion.class));
        toolVersionManager
                .updateOrCreateToolVersion(TEST_TOOL_ID, TEST_VERSION, TEST_IMAGE, dockerRegistry, dockerClient);
        verify(toolVersionDao).createToolVersion(this.toolVersion);
    }

    @Test
    public void shouldUpdateToolVersion() {
        ToolVersion toolVersionWithSameVersion = ToolVersion
                .builder()
                .digest(TEST_DIGEST_2)
                .size(TEST_SIZE)
                .version(TEST_VERSION)
                .modificationDate(TEST_LAST_MODIFIED_DATE)
                .toolId(1L)
                .build();
        Optional<ToolVersion> toolVersion = Optional.ofNullable(this.toolVersion);
        when(toolVersionDao.loadToolVersion(TEST_TOOL_ID, TEST_VERSION)).thenReturn(toolVersion);
        when(dockerClient.getVersionAttributes(any(DockerRegistry.class), anyString(), anyString()))
                .thenReturn(toolVersionWithSameVersion);
        doNothing().when(toolVersionDao).updateToolVersion(isA(ToolVersion.class));
        doThrow(getThrowable()).when(toolVersionDao).createToolVersion(any(ToolVersion.class));
        toolVersionManager
                .updateOrCreateToolVersion(TEST_TOOL_ID, TEST_VERSION, TEST_IMAGE, dockerRegistry, dockerClient);
        verify(toolVersionDao).updateToolVersion(toolVersionWithSameVersion);
    }

    private static Throwable getThrowable() {
        return new IllegalArgumentException("This method should not be called.");
    }
}
