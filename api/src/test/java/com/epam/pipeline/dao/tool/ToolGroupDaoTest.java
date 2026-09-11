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

package com.epam.pipeline.dao.tool;

import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ToolGroupDaoTest extends AbstractJdbcTest {
    private static final String TEST_GROUP_NAME = "TestGroup";
    private static final String TEST_REPO = "repository";
    private static final String TEST_REPO2 = "repository2";
    private static final String TEST_USER = "test";
    private static final String TEST_USER2 = "admin";
    private static final String TEST_DESCRIPTION = "Description";
    private static final String TEST_OTHER_DESCRIPTION = "OtherDescription";

    @Autowired
    private DockerRegistryDao registryDao;

    @Autowired
    private ToolGroupDao toolGroupDao;

    private DockerRegistry registry;
    private DockerRegistry registry2;

    @BeforeEach    public void setUp() throws Exception {
        registry = new DockerRegistry();
        registry.setPath(TEST_REPO);
        registry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(registry);

        registry2 = new DockerRegistry();
        registry2.setPath(TEST_REPO2);
        registry2.setOwner(TEST_USER);
        registryDao.createDockerRegistry(registry2);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createToolGroup() {
        ToolGroup group = saveToolGroup(TEST_GROUP_NAME, registry);

        assertNotNull(group.getId());
    }

    private ToolGroup saveToolGroup(String name, DockerRegistry dockerRegistry) {
        ToolGroup group = getToolGroup(name, dockerRegistry);

        toolGroupDao.createToolGroup(group);
        return group;
    }

    static ToolGroup getToolGroup(String name, DockerRegistry dockerRegistry) {
        ToolGroup group = new ToolGroup();
        group.setName(name);
        group.setRegistryId(dockerRegistry.getId());
        group.setOwner(TEST_USER);
        group.setDescription(TEST_DESCRIPTION);
        return group;
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadToolGroups() {
        for (int i = 0; i < 10; i++) {
            saveToolGroup(TEST_GROUP_NAME + i, registry);
        }

        List<ToolGroup> groups = toolGroupDao.loadToolGroups();
        assertFalse(groups.isEmpty());
        assertTrue(groups.size() >= 10);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadToolGroupsByNameAndRegistryName() {
        saveToolGroup(TEST_GROUP_NAME, registry);
        saveToolGroup(TEST_GROUP_NAME, registry2);

        assertEquals(1,
                            toolGroupDao.loadToolGroupsByNameAndRegistryName(TEST_GROUP_NAME, registry.getPath())
                                .size());
        assertEquals(1,
                            toolGroupDao.loadToolGroupsByNameAndRegistryName(TEST_GROUP_NAME, registry2.getPath())
                                .size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadToolGroups1() {
        saveToolGroup(TEST_GROUP_NAME, registry);
        saveToolGroup(TEST_GROUP_NAME, registry2);

        assertEquals(1, toolGroupDao.loadToolGroups(registry.getId()).size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadToolGroup() {
        ToolGroup group = saveToolGroup(TEST_GROUP_NAME, registry);

        Optional<ToolGroup> loaded = toolGroupDao.loadToolGroup(group.getId());
        checkFields(group, loaded);
    }

    private void checkFields(ToolGroup group, Optional<ToolGroup> loaded) {
        assertTrue(loaded.isPresent());

        ToolGroup loadedGroup = loaded.get();

        assertEquals(group.getId(), loadedGroup.getId());
        assertEquals(group.getName(), loadedGroup.getName());
        assertEquals(group.getRegistryId(), loadedGroup.getRegistryId());
        assertEquals(group.getOwner(), loadedGroup.getOwner());
        assertEquals(group.getDescription(), loadedGroup.getDescription());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadToolGroup1() {
        ToolGroup group = saveToolGroup(TEST_GROUP_NAME, registry);

        Optional<ToolGroup> loaded = toolGroupDao.loadToolGroup(group.getName(), group.getRegistryId());
        checkFields(group, loaded);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void deleteToolGroup() {
        ToolGroup group = saveToolGroup(TEST_GROUP_NAME, registry);

        toolGroupDao.deleteToolGroup(group.getId());

        assertTrue(toolGroupDao.loadToolGroups().stream().noneMatch(g -> g.getId().equals(group.getId())));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testUpdateToolGroup() {
        ToolGroup group = saveToolGroup(TEST_GROUP_NAME, registry);

        group.setDescription(TEST_OTHER_DESCRIPTION);
        group.setOwner(TEST_USER2);
        toolGroupDao.updateToolGroup(group);

        ToolGroup loaded = toolGroupDao.loadToolGroup(group.getId()).get();
        assertEquals(TEST_USER2, loaded.getOwner());
        assertEquals(TEST_OTHER_DESCRIPTION, loaded.getDescription());
    }
}
