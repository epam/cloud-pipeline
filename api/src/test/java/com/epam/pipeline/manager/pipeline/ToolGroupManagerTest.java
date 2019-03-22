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

import com.epam.pipeline.app.TestApplication;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.manager.AbstractManagerTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ContextConfiguration(classes = TestApplication.class)
public class ToolGroupManagerTest extends AbstractManagerTest {
    private static final String TEST_REPO = "repository";
    private static final String TEST_USER = "test";
    private static final String TEST_OTHER_USER = "otherUser";
    private static final String TEST_DESCRIPTION = "description";
    private static final String TEST_OTHER_DESCRIPTION = "description2";

    @Autowired
    private ToolGroupManager toolGroupManager;

    @Autowired
    private DockerRegistryDao registryDao;

    private DockerRegistry registry;

    @Before
    public void setUp() throws Exception {
        registry = new DockerRegistry();
        registry.setPath(TEST_REPO);
        registry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(registry);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testLoadByNameWIthRegistry() {
        ToolGroup group = createToolGroup();

        ToolGroup loadedGroup = toolGroupManager.loadByNameOrId(registry.getPath() + "/" + group.getName());
        Assert.assertEquals(group.getId(), loadedGroup.getId());
        Assert.assertEquals(group.getName(), loadedGroup.getName());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testChangeOwner() {
        ToolGroup group = createToolGroup();

        toolGroupManager.changeOwner(group.getId(), TEST_OTHER_USER);
        ToolGroup loaded = toolGroupManager.load(group.getId());

        Assert.assertEquals(TEST_OTHER_USER, loaded.getOwner());
    }

    private ToolGroup createToolGroup() {
        ToolGroup group = new ToolGroup();
        group.setName("test");
        group.setRegistryId(registry.getId());
        group.setDescription(TEST_DESCRIPTION);

        toolGroupManager.create(group);
        return group;
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testUpdateToolGroup() {
        ToolGroup group = createToolGroup();
        ToolGroup original = toolGroupManager.load(group.getId());

        group.setDescription(TEST_OTHER_DESCRIPTION);
        group.setName("foo");
        group.setOwner("bar");
        group.setRegistryId(0L);

        toolGroupManager.updateToolGroup(group);
        ToolGroup updated = toolGroupManager.load(group.getId());

        Assert.assertEquals(TEST_OTHER_DESCRIPTION, updated.getDescription());
        Assert.assertEquals(original.getName(), updated.getName());
        Assert.assertEquals(original.getOwner(), updated.getOwner());
        Assert.assertEquals(original.getRegistryId(), updated.getRegistryId());
    }
}
