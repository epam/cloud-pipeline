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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.epam.pipeline.controller.vo.docker.DockerRegistryVO;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.entity.docker.ImageDescription;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.util.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class DockerRegistryManagerTest extends AbstractManagerTest {

    private static final String PATH = "registry:5000";
    private static final String ANOTHER_PATH = "anotherRegistry:5000";
    private static final String DESCRIPTION = "description";
    private static final String TEST_IMAGE = "image";
    private static final String TEST_USER = "test";
    private static final String TEST_CPU = "500m";
    private static final String TEST_RAM = "1Gi";
    private static final String TEST_TAG = "tag";
    private static final String TEST_GROUP_NAME = "test";

    @Autowired
    private DockerRegistryDao registryDao;

    @Autowired
    private ToolGroupDao toolGroupDao;

    @InjectMocks
    @Autowired
    private ToolManager toolManager;

    @InjectMocks
    @Autowired
    private DockerRegistryManager dockerRegistryManager;

    @MockBean
    private DockerClientFactory dockerClientFactoryMock;

    @Mock
    private DockerClient dockerClient;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        TestUtils.configureDockerClientMock(dockerClient, dockerClientFactoryMock);

        dockerClientFactoryMock.setObjectMapper(new ObjectMapper());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createRegistryShouldRegisterNonExistingRegistry() {
        when(dockerClientFactoryMock.getDockerClient(any(DockerRegistry.class)))
                .thenReturn(new EmptyDockerClient());
        DockerRegistryVO dockerRegistry = new DockerRegistryVO();
        dockerRegistry.setPath(PATH);
        dockerRegistry.setDescription(DESCRIPTION);
        dockerRegistryManager.create(dockerRegistry);
        Assert.assertNotNull(dockerRegistryManager.loadByNameOrId(dockerRegistry.getPath()));
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createRegistryShouldThrowExceptionIfRegistryAlreadyExists() {
        when(dockerClientFactoryMock.getDockerClient(any(DockerRegistry.class)))
                .thenReturn(new EmptyDockerClient());
        DockerRegistryVO dockerRegistry = new DockerRegistryVO();
        dockerRegistry.setPath(PATH);
        dockerRegistry.setDescription(DESCRIPTION);
        dockerRegistryManager.create(dockerRegistry);
        dockerRegistryManager.create(dockerRegistry);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testLoadingImageDescription() {
        DockerRegistry registry = generateRegistry();
        Date date = new Date();
        registryDao.createDockerRegistry(registry);

        ToolGroup group = generateToolGroup(registry);
        toolGroupDao.createToolGroup(group);

        Tool tool = generateTool(group);

        toolManager.create(tool, true);
        DockerClient mockClient = Mockito.mock(DockerClient.class);
        when(dockerClientFactoryMock.getDockerClient(any(DockerRegistry.class), any()))
                .thenReturn(mockClient);
        ImageDescription expected = new ImageDescription(1L, TEST_IMAGE, TEST_TAG, date);
        when(mockClient.getImageDescription(registry, tool.getName(), TEST_TAG)).thenReturn(expected);
        Assert.assertEquals(expected, dockerRegistryManager.getImageDescription(registry, tool.getName(), TEST_TAG));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testLoadingImageTags() {
        DockerRegistry registry = generateRegistry();
        registryDao.createDockerRegistry(registry);

        ToolGroup group = generateToolGroup(registry);
        toolGroupDao.createToolGroup(group);

        Tool tool = generateTool(group);

        toolManager.create(tool, true);
        DockerClient mockClient = Mockito.mock(DockerClient.class);
        when(dockerClientFactoryMock.getDockerClient(any(DockerRegistry.class), any()))
                .thenReturn(mockClient);
        List<String> expected = Arrays.asList("TAG1", "TAG2");
        when(mockClient.getImageTags(ANOTHER_PATH, TEST_IMAGE)).thenReturn(expected);
        Assert.assertEquals(expected, dockerRegistryManager.loadImageTags(registry, tool));
    }

    @Test
    @Ignore
    public void testListing() {
        Set<String> entries = dockerRegistryManager.getRegistryEntries(null);
        Assert.assertNotNull(entries);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testDelete() {
        TestUtils.configureDockerClientMock(Mockito.mock(DockerClient.class), dockerClientFactoryMock);

        DockerRegistry registry = generateRegistry();
        registryDao.createDockerRegistry(registry);

        ToolGroup group = generateToolGroup(registry);
        toolGroupDao.createToolGroup(group);

        Tool tool = generateTool(group);

        toolManager.create(tool, true);

        dockerRegistryManager.delete(registry.getId(), true);
    }

    private DockerRegistry generateRegistry() {
        DockerRegistry registry = new DockerRegistry();
        registry.setPath(ANOTHER_PATH);
        registry.setOwner(TEST_USER);
        return registry;
    }

    private ToolGroup generateToolGroup(DockerRegistry registry) {
        ToolGroup group = new ToolGroup();
        group.setName(TEST_GROUP_NAME);
        group.setRegistryId(registry.getId());
        group.setOwner(TEST_USER);
        return group;
    }

    private Tool generateTool(ToolGroup group) {
        Tool tool = new Tool();
        tool.setImage(TEST_IMAGE);
        tool.setRegistry(ANOTHER_PATH);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);
        tool.setOwner(TEST_USER);
        tool.setToolGroupId(group.getId());
        return tool;
    }
}
