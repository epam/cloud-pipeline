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

package com.epam.pipeline.dao.tool;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DockerRegistryDaoTest extends AbstractSpringTest {

    private static final String TEST_USER = "test";
    private static final String EMPTY = "";
    private static final String REGISTRY_PATH = "Awesome path";
    private static final String ANOTHER_REGISTRY_PATH = "Another awesome path";
    private static final String DESCRIPTION = "Awesome description";
    private static final int EXPECTED_TOOLS_TOTAL_COUNT = 2;

    private static final String TOOL_IMAGE = "Awesome tool";
    private static final String ANOTHER_TOOL_IMAGE = "Awesome tool2";
    private static final String CPU = "300Gi";
    private static final String RAM = "500m";

    private static final String TOOL_GROUP_NAME = "library";
    private static final String TOOL_GROUP_DESCRIPTION = "test description";

    @Autowired
    private DockerRegistryDao registryDao;

    @Autowired
    private ToolDao toolDao;

    @Autowired
    private ToolGroupDao toolGroupDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createDockerRegistry() throws Exception {
        DockerRegistry created = getDockerRegistry();
        registryDao.createDockerRegistry(created);

        DockerRegistry loaded = registryDao.loadDockerRegistry(created.getId());

        Assert.assertEquals(created.getId(), loaded.getId());
        Assert.assertEquals(created.getDescription(), loaded.getDescription());
        Assert.assertEquals(created.getPath(), loaded.getPath());

        ToolGroup library = createToolGroup(created);
        toolGroupDao.createToolGroup(library);

        Tool tool = createTool(TOOL_IMAGE, loaded.getId(), library);
        toolDao.createTool(tool);

        Tool tool2 = createTool(ANOTHER_TOOL_IMAGE, loaded.getId(), library);
        toolDao.createTool(tool2);

        loaded = registryDao.loadDockerRegistry(created.getId());
        Assert.assertEquals(loaded.getTools().size(), EXPECTED_TOOLS_TOTAL_COUNT);
        Tool loadedTool = loaded.getTools().get(0);
        Tool loadedTool2 = loaded.getTools().get(1);

        Assert.assertNotEquals(loadedTool.getImage(), loadedTool2.getImage());
        Assert.assertEquals(loaded.getPath(), loadedTool.getRegistry());
        Assert.assertEquals(loaded.getPath(), loadedTool2.getRegistry());
    }

    private ToolGroup createToolGroup(DockerRegistry created) {
        ToolGroup group = new ToolGroup();
        group.setRegistryId(created.getId());
        group.setParent(created);
        group.setOwner(TEST_USER);
        group.setName(TOOL_GROUP_NAME);
        group.setDescription(TOOL_GROUP_DESCRIPTION);
        return group;
    }

    static DockerRegistry getDockerRegistry() {
        DockerRegistry created = new DockerRegistry();
        created.setDescription(DESCRIPTION);
        created.setPath(REGISTRY_PATH);
        created.setOwner(TEST_USER);
        return created;
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateDockerRegistry() throws Exception {
        DockerRegistry created = getDockerRegistry();
        registryDao.createDockerRegistry(created);

        created.setDescription(EMPTY);
        registryDao.updateDockerRegistry(created);

        DockerRegistry loaded = registryDao.loadDockerRegistry(created.getId());
        Assert.assertEquals(EMPTY, loaded.getDescription());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteDockerRegistry() throws Exception {
        DockerRegistry created = getDockerRegistry();
        registryDao.createDockerRegistry(created);
        registryDao.deleteDockerRegistry(created.getId());

        DockerRegistry loaded = registryDao.loadDockerRegistry(created.getId());
        Assert.assertNull(loaded);
    }

    @Test(expected = DataIntegrityViolationException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteDockerRegistryWithToolShouldThrowsException() throws Exception {
        DockerRegistry created = getDockerRegistry();
        registryDao.createDockerRegistry(created);

        ToolGroup library = createToolGroup(created);
        Tool tool = createTool(TOOL_IMAGE, created.getId(), library);
        toolDao.createTool(tool);

        registryDao.deleteDockerRegistry(created.getId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteDockerRegistryAfterDeletingToolShouldNotThrowsException() throws Exception {
        DockerRegistry created = getDockerRegistry();
        registryDao.createDockerRegistry(created);

        ToolGroup library = createToolGroup(created);
        toolGroupDao.createToolGroup(library);

        Tool tool = createTool(TOOL_IMAGE, created.getId(), library);
        toolDao.createTool(tool);
        toolDao.deleteTool(tool.getId());

        toolGroupDao.deleteToolGroup(library.getId());
        registryDao.deleteDockerRegistry(created.getId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void loadAllDockerRegistriesWithTools() throws Exception {
        initTestHierarchy();

        List<DockerRegistry> dockerRegistries = registryDao.loadAllDockerRegistry();
        Assert.assertFalse(dockerRegistries.isEmpty());

        Assert.assertTrue(
                dockerRegistries
                        .stream()
                        .filter(dockerRegistry -> !dockerRegistry.getTools().isEmpty())
                        .allMatch(dockerRegistry ->
                                dockerRegistry.getTools().size() == 1
                                && dockerRegistry.getTools().get(0).getImage().equals(TOOL_IMAGE)
                                || dockerRegistry.getTools().get(0).getImage().equals(ANOTHER_TOOL_IMAGE)
                        )
        );
    }

    private List<DockerRegistry> initTestHierarchy() {
        DockerRegistry createdRegistry = new DockerRegistry();
        createdRegistry.setPath(REGISTRY_PATH);
        createdRegistry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(createdRegistry);

        DockerRegistry createdRegistry2 = new DockerRegistry();
        createdRegistry2.setPath(ANOTHER_REGISTRY_PATH);
        createdRegistry2.setOwner(TEST_USER);
        registryDao.createDockerRegistry(createdRegistry2);

        ToolGroup library = createToolGroup(createdRegistry);
        ToolGroup library2 = createToolGroup(createdRegistry2);
        toolGroupDao.createToolGroup(library);
        toolGroupDao.createToolGroup(library2);

        createdRegistry.setGroups(Collections.singletonList(library));
        createdRegistry2.setGroups(Collections.singletonList(library2));

        Tool tool = createTool(TOOL_IMAGE, createdRegistry.getId(), library);
        toolDao.createTool(tool);
        library.setTools(Collections.singletonList(tool));
        createdRegistry.setTools(Collections.singletonList(tool));

        Tool tool2 = createTool(ANOTHER_TOOL_IMAGE, createdRegistry2.getId(), library2);
        toolDao.createTool(tool2);
        library2.setTools(Collections.singletonList(tool2));

        return Arrays.asList(createdRegistry, createdRegistry2);
    }

    @Test
    @Ignore
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testLoadAllRegistriesContent() {
        List<DockerRegistry> created = initTestHierarchy();
        Set<Long> createdRegistryIds = created.stream().map(BaseEntity::getId).collect(Collectors.toSet());

        List<DockerRegistry> dockerRegistries = registryDao.loadAllRegistriesContent();
        Assert.assertFalse(dockerRegistries.isEmpty());
        for (int i = 0; i < dockerRegistries.size(); i++) {
            DockerRegistry loadedRegistry = dockerRegistries.get(i);

            if (createdRegistryIds.contains(loadedRegistry.getId())) {
                Assert.assertFalse(loadedRegistry.getGroups().isEmpty());
                Assert.assertEquals(loadedRegistry.getGroups().get(0).getName(),
                                    created.get(i).getGroups().get(0).getName());
                Assert.assertEquals(loadedRegistry.getGroups().get(0).getDescription(),
                                    created.get(i).getGroups().get(0).getDescription());
                /*Assert.assertEquals(loadedRegistry.getGroups().get(0).getId(),
                                    created.get(i).getGroups().get(0).getId());*/

                for (int j = 0; j < created.get(i).getGroups().get(0).getTools().size(); j++) {
                    Tool tool = created.get(i).getGroups().get(0).getTools().get(j);
                    Tool loadedTool = loadedRegistry.getGroups().get(0).getTools().get(j);
                    Assert.assertEquals(tool.getId(), loadedTool.getId());
                    Assert.assertEquals(tool.getImage(), loadedTool.getImage());
                    Assert.assertEquals(tool.getCpu(), loadedTool.getCpu());
                }
            }
        }
    }

    private static Tool createTool(String image, long registryId, ToolGroup group) {
        Tool tool = new Tool();
        tool.setImage(image);
        tool.setCpu(CPU);
        tool.setRam(RAM);
        tool.setRegistryId(registryId);
        tool.setOwner(TEST_USER);
        tool.setToolGroupId(group.getId());
        return tool;
    }
}
