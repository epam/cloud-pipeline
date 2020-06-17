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

import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.tool.ToolVersionDao;
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.tool.ToolSymlinkRequest;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.util.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
@Transactional
public class ToolVersionManagerTest extends AbstractManagerTest {
    private static final String TEST_DIGEST = "sha256:aaa";
    private static final String TEST_DIGEST_2 = "sha256:bbb";
    private static final Long TEST_SIZE = 123L;
    private static final Date TEST_LAST_MODIFIED_DATE = new Date();
    private static final String TEST_VERSION = "latest";
    private static final String TEST_IMAGE = "library/image";
    private static final String TEST_USER = "test";
    private static final String TEST_CPU = "500m";
    private static final String TEST_RAM = "1Gi";
    private static final String TOOL_GROUP = "library";
    private static final String ANOTHER_TOOL_GROUP = "user";
    private static final String TEST_REPO = "repository";

    @MockBean
    private ToolVersionDao toolVersionDao;
    @Autowired
    private ToolManager toolManager;
    @Autowired
    private ToolVersionManager toolVersionManager;
    @Autowired
    private DockerRegistryDao registryDao;
    @Autowired
    private ToolGroupManager toolGroupManager;
    @Mock
    private DockerRegistry dockerRegistry;
    @Mock
    private DockerClient dockerClient;
    @MockBean
    private DockerClientFactory dockerClientFactory;

    private ToolVersion toolVersion;
    private Tool tool;
    private Tool symlink;

    @Before
    public void setUp() {
        TestUtils.configureDockerClientMock(dockerClient, dockerClientFactory);
        
        final DockerRegistry registry = new DockerRegistry();
        registry.setPath(TEST_REPO);
        registry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(registry);

        final ToolGroup firstGroup = new ToolGroup();
        firstGroup.setName(TOOL_GROUP);
        firstGroup.setRegistryId(registry.getId());
        toolGroupManager.create(firstGroup);

        final ToolGroup secondGroup = new ToolGroup();
        secondGroup.setName(ANOTHER_TOOL_GROUP);
        secondGroup.setRegistryId(registry.getId());
        toolGroupManager.create(secondGroup);

        tool = new Tool();
        tool.setImage(TEST_IMAGE);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);
        tool.setOwner(TEST_USER);
        tool.setToolGroup(TOOL_GROUP);
        tool.setToolGroupId(firstGroup.getId());
        tool.setRegistryId(registry.getId());
        toolManager.create(tool, false);
        
        symlink = toolManager.symlink(new ToolSymlinkRequest(tool.getId(), secondGroup.getId()));
        
        toolVersion = ToolVersion
                .builder()
                .digest(TEST_DIGEST)
                .size(TEST_SIZE)
                .version(TEST_VERSION)
                .modificationDate(TEST_LAST_MODIFIED_DATE)
                .toolId(tool.getId())
                .build();
    }

    @Test
    public void shouldCreateToolVersion() {
        when(toolVersionDao.loadToolVersion(tool.getId(), TEST_VERSION)).thenReturn(Optional.empty());
        when(dockerClient.getVersionAttributes(any(DockerRegistry.class), anyString(), anyString()))
                .thenReturn(this.toolVersion);
        doNothing().when(toolVersionDao).createToolVersion(isA(ToolVersion.class));
        doThrow(getThrowable()).when(toolVersionDao).updateToolVersion(any(ToolVersion.class));
        toolVersionManager
                .updateOrCreateToolVersion(tool.getId(), TEST_VERSION, TEST_IMAGE, dockerRegistry, dockerClient);
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
        when(toolVersionDao.loadToolVersion(tool.getId(), TEST_VERSION)).thenReturn(toolVersion);
        when(dockerClient.getVersionAttributes(any(DockerRegistry.class), anyString(), anyString()))
                .thenReturn(toolVersionWithSameVersion);
        doNothing().when(toolVersionDao).updateToolVersion(isA(ToolVersion.class));
        doThrow(getThrowable()).when(toolVersionDao).createToolVersion(any(ToolVersion.class));
        toolVersionManager
                .updateOrCreateToolVersion(tool.getId(), TEST_VERSION, TEST_IMAGE, dockerRegistry, dockerClient);
        verify(toolVersionDao).updateToolVersion(toolVersionWithSameVersion);
    }

    @Test
    public void testSymlinkModificationFails() {
        assertThrows(IllegalArgumentException.class, () -> 
                toolVersionManager.createToolVersionSettings(symlink.getId(), TEST_VERSION, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> 
                toolVersionManager.deleteToolVersion(symlink.getId(), TEST_VERSION));
        assertThrows(IllegalArgumentException.class, () -> 
                toolVersionManager.deleteToolVersions(symlink.getId()));
        assertThrows(IllegalArgumentException.class, () -> 
                toolVersionManager.updateOrCreateToolVersion(symlink.getId(), TEST_VERSION, TEST_IMAGE, dockerRegistry, 
                        dockerClient));
    }
    
    private static Throwable getThrowable() {
        return new IllegalArgumentException("This method should not be called.");
    }
}
