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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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

    private static final String TEST_USER = "test";
    private static final String TEST_IMAGE = "image";
    private static final String TEST_CPU = "500m";
    private static final String TEST_RAM = "1Gi";
    private static final String TEST_REPO = "repository";
    private static final String TEST_REPO_2 = "repository2";
    private static final String TOOL_GROUP_NAME = "library";

    private DockerRegistry firstRegistry;
    private DockerRegistry secondRegistry;
    private ToolGroup library1;
    private ToolGroup library2;

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
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        EntityVO entityVO = new EntityVO(tool.getId(), AclClass.TOOL);
        IssueVO issueVO = getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO);
        issueManager.createIssue(issueVO);
        verify(notificationManager).notifyIssue(any(), any(), any());
        issueVO.setName(ISSUE_NAME2);
        issueManager.createIssue(issueVO);

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
        String testFileName = "test";
        long iconId = toolDao.updateIcon(tool.getId(), testFileName, randomBytes);

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
        Assert.assertEquals(testFileName, loadedImage.get().getLeft());
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
        toolDao.updateIcon(tool.getId(), testFileName, randomBytes);
        loaded = toolDao.loadTool(tool.getId());
        Assert.assertTrue(loaded.isHasIcon());

        toolDao.deleteToolIcon(tool.getId());
        toolDao.deleteTool(tool.getId());
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
