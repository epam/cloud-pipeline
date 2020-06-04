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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

public class DockerRegistryDaoTest extends AbstractSpringTest {

    private static final String TEST_USER = "test";
    private static final String EMPTY = "";
    private static final String REGISTRY_PATH = "Awesome path";
    private static final String ANOTHER_REGISTRY_PATH = "Another awesome path";
    private static final String REGISTRY_EXTERNAL_URL = "External url";
    private static final String DESCRIPTION = "Awesome description";
    private static final String SHORT_DESCRIPTION = "short description";
    private static final List<String> LABELS = Arrays.asList("label1", "label2");
    private static final List<String> ENDPOINTS = Arrays.asList("endpoint1", "endpoint2");
    private static final String DEFAULT_COMMAND = "default command";
    private static final Integer DISK = 100;
    private static final String INSTANCE_TYPE = "Instance type";
    private static final int EXPECTED_TOOLS_TOTAL_COUNT = 2;

    private static final String TOOL_IMAGE = "Awesome tool";
    private static final String ANOTHER_TOOL_IMAGE = "Awesome tool2";
    private static final String CPU = "300Gi";
    private static final String RAM = "500m";

    private static final String TOOL_GROUP_NAME = "library";
    private static final String TOOL_GROUP_DESCRIPTION = "test description";
    private static final String REGISTRY_CERT = "cert";

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
    @Transactional
    public void loadAllRegistries() {
        final List<DockerRegistry> expectedRegistries = initTestHierarchy();

        final List<DockerRegistry> loadedRegistries = registryDao.loadAllDockerRegistry();

        assertRegistryTools(loadedRegistries, expectedRegistries);
    }

    @Test
    @Transactional
    public void loadAllRegistriesContent() {
        final List<DockerRegistry> expectedRegistries = initTestHierarchy();

        final List<DockerRegistry> loadedRegistries = registryDao.loadAllRegistriesContent();

        assertRegistryGroups(loadedRegistries, expectedRegistries);
    }

    @Test
    @Transactional
    public void loadAllRegistriesWithSecurityScanEnabled() {
        final List<DockerRegistry> expectedRegistries = initTestHierarchy();

        final List<DockerRegistry> loadedRegistries = registryDao.loadDockerRegistriesWithSecurityScanEnabled();

        assertRegistryTools(loadedRegistries, expectedRegistries.subList(1, 2));
    }

    @Test
    @Transactional
    public void listAllDockerRegistriesWithCerts() {
        final List<DockerRegistry> expectedRegistries = initTestHierarchy();

        final List<DockerRegistry> loadedRegistries = registryDao.listAllDockerRegistriesWithCerts();

        assertRegistryGroups(loadedRegistries, expectedRegistries.subList(1, 2));
    }

    @Test
    @Transactional
    public void loadDockerRegistryById() {
        final List<DockerRegistry> expectedRegistries = initTestHierarchy();

        final List<DockerRegistry> loadedRegistries = expectedRegistries.stream()
                .map(DockerRegistry::getId)
                .map(registryDao::loadDockerRegistry)
                .collect(Collectors.toList());

        assertRegistryTools(loadedRegistries, expectedRegistries);
    }

    @Test
    @Transactional
    public void loadDockerRegistryByPath() {
        final List<DockerRegistry> expectedRegistries = initTestHierarchy();

        final List<DockerRegistry> loadedRegistries = expectedRegistries.stream()
                .map(DockerRegistry::getPath)
                .map(registryDao::loadDockerRegistry)
                .collect(Collectors.toList());

        assertRegistryTools(loadedRegistries, expectedRegistries);
    }

    @Test
    @Transactional
    public void loadDockerRegistryTree() {
        final List<DockerRegistry> expectedRegistries = initTestHierarchy();

        final List<DockerRegistry> loadedRegistries = expectedRegistries.stream()
                .map(DockerRegistry::getId)
                .map(registryDao::loadDockerRegistryTree)
                .collect(Collectors.toList());

        assertRegistryGroups(loadedRegistries, expectedRegistries);
    }

    @Test
    @Transactional
    public void loadDockerRegistryByExternalUrl() {
        final List<DockerRegistry> expectedRegistries = initTestHierarchy();

        final List<DockerRegistry> loadedRegistries = expectedRegistries.stream()
                .map(DockerRegistry::getExternalUrl)
                .filter(Objects::nonNull)
                .map(registryDao::loadDockerRegistryByExternalUrl)
                .collect(Collectors.toList());

        assertRegistryTools(loadedRegistries, expectedRegistries.subList(1, 2));
    }

    // TODO 02.06.2020: Why is it ignored out?
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

    private List<DockerRegistry> initTestHierarchy() {
        DockerRegistry createdRegistry = new DockerRegistry();
        createdRegistry.setPath(REGISTRY_PATH);
        createdRegistry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(createdRegistry);

        DockerRegistry createdRegistry2 = new DockerRegistry();
        createdRegistry2.setPath(ANOTHER_REGISTRY_PATH);
        createdRegistry2.setOwner(TEST_USER);
        createdRegistry2.setSecurityScanEnabled(true);
        createdRegistry2.setExternalUrl(REGISTRY_EXTERNAL_URL);
        createdRegistry2.setCaCert(REGISTRY_CERT);
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
        createdRegistry2.setTools(Collections.singletonList(tool2));

        Tool symlink = createTool(TOOL_IMAGE, createdRegistry2.getId(), library2);
        symlink.setLink(tool.getId());
        toolDao.createTool(symlink);
        library2.setTools(Arrays.asList(tool2, symlink));
        createdRegistry2.setTools(Arrays.asList(tool2, symlink));

        return Arrays.asList(createdRegistry, createdRegistry2);
    }

    private static Tool createTool(String image, long registryId, ToolGroup group) {
        Tool tool = new Tool();
        tool.setImage(image);
        tool.setCpu(CPU);
        tool.setRam(RAM);
        tool.setRegistryId(registryId);
        tool.setOwner(TEST_USER);
        tool.setToolGroupId(group.getId());
        tool.setDescription(DESCRIPTION);
        tool.setShortDescription(SHORT_DESCRIPTION);
        tool.setLabels(LABELS);
        tool.setEndpoints(ENDPOINTS);
        tool.setDefaultCommand(DEFAULT_COMMAND);
        tool.setDisk(DISK);
        tool.setInstanceType(INSTANCE_TYPE);
        return tool;
    }

    private void assertRegistryTools(final List<DockerRegistry> actualRegistries,
                                     final List<DockerRegistry> expectedRegistries) {
        actualRegistries.sort(Comparator.comparing(DockerRegistry::getId));
        expectedRegistries.sort(Comparator.comparing(DockerRegistry::getId));
        assertThat(actualRegistries.size(), is(expectedRegistries.size()));
        assertThat(actualRegistries.size(), greaterThan(0));
        for (int i = 0; i < actualRegistries.size(); i++) {
            final DockerRegistry dockerRegistry = actualRegistries.get(i);
            final DockerRegistry expectedRegistry = expectedRegistries.get(i);
            final List<Tool> actualTools = dockerRegistry.getTools();
            final List<Tool> expectedTools = expectedRegistry.getTools();
            assertTools(actualTools, expectedTools);
        }
    }

    private void assertRegistryGroups(final List<DockerRegistry> actualRegistries,
                                      final List<DockerRegistry> expectedRegistries) {
        actualRegistries.sort(Comparator.comparing(DockerRegistry::getId));
        expectedRegistries.sort(Comparator.comparing(DockerRegistry::getId));
        assertThat(actualRegistries.size(), is(expectedRegistries.size()));
        assertThat(actualRegistries.size(), greaterThan(0));
        for (int i = 0; i < actualRegistries.size(); i++) {
            final DockerRegistry actualRegistry = actualRegistries.get(i);
            final DockerRegistry expectedRegistry = expectedRegistries.get(i);
            final List<ToolGroup> actualGroups = actualRegistry.getGroups();
            final List<ToolGroup> expectedGroups = expectedRegistry.getGroups();
            assertGroups(actualGroups, expectedGroups);
        }
    }

    private void assertGroups(final List<ToolGroup> actualGroups, final List<ToolGroup> expectedGroups) {
        actualGroups.sort(Comparator.comparing(ToolGroup::getId));
        expectedGroups.sort(Comparator.comparing(ToolGroup::getId));
        assertThat(actualGroups.size(), is(expectedGroups.size()));
        assertThat(actualGroups.size(), greaterThan(0));
        for (int i = 0; i < actualGroups.size(); i++) {
            final ToolGroup actualGroup = actualGroups.get(i);
            final ToolGroup expectedGroup = expectedGroups.get(i);
            final List<Tool> actualTools = actualGroup.getTools();
            final List<Tool> expectedTools = expectedGroup.getTools();
            assertTools(actualTools, expectedTools);
        }
    }

    private void assertTools(final List<Tool> actualTools, final List<Tool> expectedTools) {
        actualTools.sort(Comparator.comparing(Tool::getId));
        expectedTools.sort(Comparator.comparing(Tool::getId));
        assertThat(actualTools.size(), is(expectedTools.size()));
        assertThat(actualTools.size(), greaterThan(0));
        for (int i = 0; i < actualTools.size(); i++) {
            final Tool actualTool = actualTools.get(i);
            final Tool expectedTool = expectedTools.get(i);
            assertTools(actualTool, expectedTool);
        }
    }

    private void assertTools(final Tool actualTool, final Tool expectedTool) {
        assertThat(actualTool.getId(), is(expectedTool.getId()));
        assertThat(actualTool.getImage(), is(expectedTool.getImage()));
        assertThat(actualTool.getLink(), is(expectedTool.getLink()));
        assertThat(actualTool.getCpu(), is(expectedTool.getCpu()));
        assertThat(actualTool.getRam(), is(expectedTool.getRam()));
        assertThat(actualTool.getDefaultCommand(), is(expectedTool.getDefaultCommand()));
        assertThat(actualTool.getLabels(), is(expectedTool.getLabels()));
        assertThat(actualTool.getEndpoints(), is(expectedTool.getEndpoints()));
        assertThat(actualTool.getShortDescription(), is(expectedTool.getShortDescription()));
        assertThat(actualTool.getIconId(), is(expectedTool.getIconId()));
    }
}
