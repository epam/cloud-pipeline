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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.IssueVO;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolWithIssuesCount;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.issue.IssueManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.security.AuthManager;

public class ToolDaoTest extends AbstractSpringTest {

    private static final String ISSUE_NAME = "Issue name";
    private static final String ISSUE_NAME2 = "Issue name2";
    private static final String ISSUE_TEXT = "Issue text";
    private static final String AUTHOR = "author";

    private static final String TEST_FILE_NAME = "test";
    private static final String TEST_USER = TEST_FILE_NAME;
    private static final String TEST_ANOTHER_USER = "test2";
    private static final String TEST_IMAGE = "image";
    private static final String TEST_SOURCE_IMAGE = "library/image";
    private static final String TEST_SYMLINK_IMAGE = "user/image";
    private static final String TEST_CPU = "500m";
    private static final String TEST_RAM = "1Gi";
    private static final String TEST_REPO = "repository";
    private static final String TEST_REPO_2 = "repository2";
    private static final String TOOL_GROUP_NAME = "library";
    private static final String USER_GROUP_NAME = "user";
    private static final String DESCRIPTION = "description";
    private static final String SHORT_DESCRIPTION = "short description";
    private static final List<String> LABELS = Arrays.asList("label1", "label2");
    private static final List<String> ENDPOINTS = Arrays.asList("endpoint1", "endpoint2");
    private static final String DEFAULT_COMMAND = "default command";
    private static final Integer DISK = 100;
    private static final String INSTANCE_TYPE = "Instance type";
    private static final Long NO_LINK = null;

    private DockerRegistry firstRegistry;
    private DockerRegistry secondRegistry;
    private ToolGroup library1;
    private ToolGroup library2;
    private ToolGroup userGroup;

    @Autowired
    private ToolDao toolDao;
    @Autowired
    private DockerRegistryDao registryDao;
    @Autowired
    private ToolGroupDao toolGroupDao;
    @Autowired
    private IssueManager issueManager;

    @SpyBean
    private AuthManager authManager;
    @MockBean
    private NotificationManager notificationManager;

    @Before
    public void setUp() {
        firstRegistry = new DockerRegistry();
        firstRegistry.setPath(TEST_REPO);
        firstRegistry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(firstRegistry);

        library1 = new ToolGroup();
        library1.setName(TOOL_GROUP_NAME);
        library1.setRegistryId(firstRegistry.getId());
        library1.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(library1);

        secondRegistry = new DockerRegistry();
        secondRegistry.setPath(TEST_REPO_2);
        secondRegistry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(secondRegistry);

        library2 = new ToolGroup();
        library2.setName(TOOL_GROUP_NAME);
        library2.setRegistryId(secondRegistry.getId());
        library2.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(library2);
        
        userGroup = new ToolGroup();
        userGroup.setName(USER_GROUP_NAME);
        userGroup.setRegistryId(secondRegistry.getId());
        userGroup.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(userGroup);

        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testSaveDelete() {
        Tool tool = generateTool();
        tool.setRegistryId(firstRegistry.getId());
        tool.setToolGroupId(library1.getId());
        toolDao.createTool(tool);

        Tool loaded = toolDao.loadTool(tool.getId());
        checkLoadedTool(loaded, firstRegistry.getId(), TEST_REPO);

        List<Tool> tools = toolDao.loadAllTools();
        Assert.assertEquals(1, tools.size());

        loaded = toolDao.loadTool(firstRegistry.getId(), tool.getImage());
        checkLoadedTool(loaded, firstRegistry.getId(), TEST_REPO);

        toolDao.deleteTool(loaded.getId());
        Assert.assertTrue(toolDao.loadAllTools().isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testLoadToolsWithIssuesCount() {
        //create tool
        Tool tool = generateTool();
        tool.setRegistryId(firstRegistry.getId());
        tool.setToolGroupId(library1.getId());
        toolDao.createTool(tool);
        //create issues
        createIssue(tool, ISSUE_NAME);
        createIssue(tool, ISSUE_NAME2);
        verify(notificationManager, times(2)).notifyIssue(any(), any(), any());

        List<ToolWithIssuesCount> loaded = toolDao.loadToolsWithIssuesCountByGroup(library1.getId());
        Assert.assertEquals(1, loaded.size());
        Assert.assertEquals(2, loaded.get(0).getIssuesCount());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void sameToolsInDifferentRegistryShouldBeWorksCorrectly() {
        Tool tool = generateTool();

        tool.setRegistryId(firstRegistry.getId());
        tool.setToolGroupId(library1.getId());
        toolDao.createTool(tool);

        tool.setRegistryId(secondRegistry.getId());
        tool.setToolGroupId(library2.getId());
        toolDao.createTool(tool);

        List<Tool> tools = toolDao.loadAllTools();
        Assert.assertEquals(2, tools.size());

        Tool first = toolDao.loadTool(firstRegistry.getId(), tool.getImage());
        checkLoadedTool(first, firstRegistry.getId(), TEST_REPO);

        Tool second = toolDao.loadTool(secondRegistry.getId(), tool.getImage());
        checkLoadedTool(second, secondRegistry.getId(), TEST_REPO_2);

        toolDao.deleteTool(second.getId());
        Assert.assertEquals(1, toolDao.loadAllTools().size());

        toolDao.deleteTool(first.getId());
        Assert.assertTrue(toolDao.loadAllTools().isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testSaveIcon() throws IOException {
        Tool tool = generateTool();

        tool.setRegistryId(firstRegistry.getId());
        tool.setToolGroupId(library1.getId());
        toolDao.createTool(tool);

        byte[] randomBytes = new byte[10];
        new Random().nextBytes(randomBytes);
        long iconId = toolDao.updateIcon(tool.getId(), TEST_FILE_NAME, randomBytes);

        Tool loaded = toolDao.loadTool(tool.getId());
        Assert.assertTrue(loaded.isHasIcon());
        Assert.assertEquals(iconId, loaded.getIconId().longValue());

        List<DockerRegistry> registries = registryDao.loadAllRegistriesContent();
        DockerRegistry registry = registries.stream()
            .filter(r -> r.getId().equals(firstRegistry.getId())).findFirst().get();
        ToolGroup group = registry.getGroups().stream()
            .filter(g -> g.getId().equals(library1.getId())).findFirst().get();
        loaded = group.getTools().stream().filter(t -> t.getId().equals(tool.getId())).findFirst().get();
        Assert.assertTrue(loaded.isHasIcon());
        Assert.assertEquals(iconId, loaded.getIconId().longValue());

        Optional<Pair<String, InputStream>> loadedImage = toolDao.loadIcon(tool.getId());
        Assert.assertEquals(TEST_FILE_NAME, loadedImage.get().getLeft());
        try (InputStream imageStream = loadedImage.get().getRight()) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            IOUtils.copy(imageStream, os);
            byte[] loadedBytes = os.toByteArray();
            for (int i = 0; i < loadedBytes.length; i++) {
                Assert.assertEquals(randomBytes[i], loadedBytes[i]);
            }
        }

        // update again
        new Random().nextBytes(randomBytes);
        toolDao.updateIcon(tool.getId(), TEST_FILE_NAME, randomBytes);
        loaded = toolDao.loadTool(tool.getId());
        Assert.assertTrue(loaded.isHasIcon());

        deleteTool(loaded);
    }
    
    @Test
    @Transactional
    public void testToolCanBeLoadedWithAnyAvailableMethod() {
        final Tool tool = createTool();

        assertToolCanBeLoaded(tool);
    }

    @Test
    @Transactional
    public void testSymlinkCanBeLoadedWithAnyAvailableMethod() {
        final Tool tool = createTool();
        final Tool symlink = createSymlink(tool);

        assertSymlinkCanBeLoaded(tool, symlink);
    }

    @Test(expected = DataIntegrityViolationException.class)
    @Transactional
    public void testToolCannotBeDeletedIfThereAreSymlinksForIt() {
        final Tool tool = createTool();
        createSymlink(tool);

        deleteTool(tool);
    }

    @Test
    @Transactional
    public void testToolIsNotAffectedAfterSymlinkIsDeleted() {
        final Tool tool = createTool();
        final Tool symlink = createSymlink(tool);
        
        assertToolCanBeLoaded(tool);
        deleteSymlink(symlink);
        assertToolCanBeLoaded(tool);
        assertToolCannotBeLoaded(symlink);
    }
    
    @Test
    @Transactional
    public void testSymlinkWithIssuesCountCanBeLoaded() {
        final Tool tool = createTool();
        final Tool symlink = createSymlink(tool);
        createIssue(tool, ISSUE_NAME);
        createIssue(tool, ISSUE_NAME2);

        final ToolWithIssuesCount loadedSymlink = loadToolWithIssuesCount(symlink);
        assertThat(loadedSymlink.getIssuesCount(), is(2L));
    }

    private Tool createTool() {
        final Tool tool = generateToolWithAllFields();
        toolDao.createTool(tool);
        final long iconId = updateToolIcon(tool);
        tool.setIconId(iconId);
        return tool;
    }

    private Tool createSymlink(final Tool tool) {
        final Tool symlink = generateSymlink(tool);
        toolDao.createTool(symlink);
        return symlink;
    }

    private Tool generateToolWithAllFields() {
        final Tool tool = new Tool();
        tool.setImage(TEST_SOURCE_IMAGE);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);
        tool.setOwner(TEST_USER);
        tool.setRegistryId(firstRegistry.getId());
        tool.setToolGroupId(library1.getId());
        tool.setDescription(DESCRIPTION);
        tool.setShortDescription(SHORT_DESCRIPTION);
        tool.setLabels(LABELS);
        tool.setEndpoints(ENDPOINTS);
        tool.setDefaultCommand(DEFAULT_COMMAND);
        tool.setDisk(DISK);
        tool.setInstanceType(INSTANCE_TYPE);
        return tool;
    }

    private Tool generateSymlink(final Tool tool) {
        final Tool symlink = new Tool();
        symlink.setImage(TEST_SYMLINK_IMAGE);
        symlink.setOwner(TEST_ANOTHER_USER);
        symlink.setRam(tool.getRam());
        symlink.setCpu(tool.getCpu());
        symlink.setLink(tool.getId());
        symlink.setRegistryId(secondRegistry.getId());
        symlink.setToolGroupId(userGroup.getId());
        return symlink;
    }

    private void createIssue(final Tool tool, final String issueName) {
        final EntityVO entityVO = new EntityVO(tool.getId(), AclClass.TOOL);
        final IssueVO issueVO = getIssueVO(issueName, ISSUE_TEXT, entityVO);
        issueManager.createIssue(issueVO);
    }

    private long updateToolIcon(final Tool tool) {
        byte[] randomBytes = new byte[10];
        new Random().nextBytes(randomBytes);
        return toolDao.updateIcon(tool.getId(), TEST_FILE_NAME, randomBytes);
    }

    private void assertToolCannotBeLoaded(final Tool tool) {
        assertNull(toolDao.loadTool(tool.getId()));    
    }

    private void assertToolCanBeLoaded(final Tool tool) {
        loadToolWithAllTheAvailableMethods(tool.getId(), firstRegistry, library1, TEST_SOURCE_IMAGE)
                .forEach(loadedTool -> assertToolFields(tool.getId(), loadedTool, library1, firstRegistry,
                        TEST_USER, TEST_SOURCE_IMAGE, NO_LINK, tool.getIconId()));
    }

    private void assertSymlinkCanBeLoaded(final Tool tool, final Tool symlink) {
        loadToolWithAllTheAvailableMethods(symlink.getId(), secondRegistry, userGroup, TEST_SYMLINK_IMAGE)
                .forEach(loadedSymlink -> assertToolFields(symlink.getId(), loadedSymlink, userGroup, secondRegistry,
                        TEST_ANOTHER_USER, TEST_SYMLINK_IMAGE, tool.getId(), tool.getIconId()));
    }

    private List<Tool> loadToolWithAllTheAvailableMethods(final Long toolId, final DockerRegistry registry, 
                                                          final ToolGroup group, final String image) {
        return Arrays.asList(
                toolDao.loadTool(toolId),
                toolDao.loadTool(registry.getId(), image),
                toolDao.loadToolByGroupAndImage(group.getId(), image)
                        .orElseThrow(RuntimeException::new),
                selectMatching(toolDao.loadToolsWithIssuesCountByGroup(group.getId()), toolId),
                selectMatching(toolDao.loadWithSameImageNameFromOtherRegistries(image, registry.getPath() + "salt"), toolId),
                selectMatching(toolDao.loadToolsByGroup(group.getId()), toolId),
                selectMatching(toolDao.loadAllTools(), toolId),
                selectMatching(toolDao.loadAllTools(registry.getId()), toolId),
                selectMatching(toolDao.loadAllTools(LABELS), toolId),
                selectMatching(toolDao.loadAllTools(registry.getId(), LABELS), toolId)
        );
    }

    private Tool selectMatching(final List<? extends Tool> tools, final Long id) {
        return tools.stream()
                .filter(it -> Objects.equals(it.getId(), id))
                .findFirst()
                .orElseThrow(RuntimeException::new);
    }

    private void assertSymlinkIssuesCountCanBeLoaded(final Tool tool, final Tool symlink) {
        final ToolWithIssuesCount loadedTool = loadToolWithIssuesCount(tool);
        final ToolWithIssuesCount loadedSymlink = loadToolWithIssuesCount(symlink);
        assertThat(loadedSymlink.getIssuesCount(), is(loadedTool.getIssuesCount()));
    }

    private ToolWithIssuesCount loadToolWithIssuesCount(final Tool symlink) {
        return toolDao.loadToolsWithIssuesCountByGroup(symlink.getToolGroupId())
                .stream()
                .filter(it -> Objects.equals(it.getId(), symlink.getId()))
                .findFirst()
                .orElseThrow(RuntimeException::new);
    }

    private void assertToolFields(final Long toolId, final Tool tool, final ToolGroup group,
                                  final DockerRegistry registry, final String user, final String image,
                                  final Long linkId, final Long iconId) {
        assertThat(tool.getId(), is(toolId));
        assertThat(tool.getImage(), is(image));
        assertThat(tool.getRegistryId(), is(registry.getId()));
        assertThat(tool.getToolGroupId(), is(group.getId()));
        assertThat(tool.getOwner(), is(user));
        assertThat(tool.getLink(), is(linkId));
        assertThat(tool.getRegistry(), is(registry.getPath()));
        assertThat(tool.getSecretName(), is(registry.getSecretName()));
        assertThat(tool.getDescription(), is(DESCRIPTION));
        assertThat(tool.getShortDescription(), is(SHORT_DESCRIPTION));
        assertThat(tool.getCpu(), is(TEST_CPU));
        assertThat(tool.getRam(), is(TEST_RAM));
        assertThat(tool.getDefaultCommand(), is(DEFAULT_COMMAND));
        assertThat(tool.getLabels(), is(LABELS));
        assertThat(tool.getEndpoints(), is(ENDPOINTS));
        assertThat(tool.getDisk(), is(DISK));
        assertThat(tool.getInstanceType(), is(INSTANCE_TYPE));
        assertThat(tool.getIconId(), is(iconId));
    }

    private void deleteTool(final Tool tool) {
        if (tool.isHasIcon()) {
            toolDao.deleteToolIcon(tool.getId());
        }
        toolDao.deleteTool(tool.getId());
    }

    private void deleteSymlink(final Tool symlink) {
        toolDao.deleteTool(symlink.getId());
    }

    public static Tool generateTool() {
        Tool tool = new Tool();
        tool.setImage(TEST_IMAGE);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);
        tool.setOwner(TEST_USER);
        return tool;
    }

    private void checkLoadedTool(Tool tool, Long registryId, String toolRegistry) {
        Assert.assertEquals(TEST_IMAGE, tool.getImage());
        Assert.assertEquals(TEST_CPU, tool.getCpu());
        Assert.assertEquals(TEST_RAM, tool.getRam());
        Assert.assertEquals(registryId, tool.getRegistryId());
        Assert.assertEquals(toolRegistry, tool.getRegistry());
    }

    private IssueVO getIssueVO(String name, String text, EntityVO entity) {
        IssueVO issueVO = new IssueVO();
        issueVO.setName(name);
        issueVO.setEntity(entity);
        issueVO.setText(text);
        return issueVO;
    }
}
