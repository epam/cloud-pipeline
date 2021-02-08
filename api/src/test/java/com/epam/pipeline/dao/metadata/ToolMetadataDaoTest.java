/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.dao.metadata;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.issue.IssueDao;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

public class ToolMetadataDaoTest extends AbstractJdbcTest {


    private static final String ISSUE_NAME = "Issue name";
    private static final String ISSUE_TEXT = "Issue text";
    private static final String AUTHOR = "author";

    private static final String TEST_USER = "user";
    private static final String TEST_SOURCE_IMAGE = "library/image";
    private static final String TEST_SYMLINK_IMAGE = "user/image";
    private static final String TEST_CPU = "500m";
    private static final String TEST_RAM = "1Gi";
    private static final String TEST_REPO = "repository";
    private static final String TOOL_GROUP_NAME = "library";
    private static final String USER_GROUP_NAME = "user";

    private DockerRegistry firstRegistry;
    private DockerRegistry secondRegistry;
    private ToolGroup library;
    private ToolGroup firstUserGroup;
    private ToolGroup secondUserGroup;
    private Tool tool;
    private Tool symlink;
    private Tool secondSymlink;

    @Autowired
    private ToolDao toolDao;
    @Autowired
    private DockerRegistryDao registryDao;
    @Autowired
    private ToolGroupDao toolGroupDao;
    @Autowired
    private MetadataDao metadataDao;
    @Autowired
    private IssueDao issueDao;
    @Autowired
    private AuthManager authManager;

    @Before
    public void setUp() {
        firstRegistry = new DockerRegistry();
        firstRegistry.setPath(TEST_REPO);
        firstRegistry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(firstRegistry);

        library = new ToolGroup();
        library.setName(TOOL_GROUP_NAME);
        library.setRegistryId(firstRegistry.getId());
        library.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(library);

        firstUserGroup = new ToolGroup();
        firstUserGroup.setName(USER_GROUP_NAME);
        firstUserGroup.setRegistryId(firstRegistry.getId());
        firstUserGroup.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(firstUserGroup);

        tool = new Tool();
        tool.setImage(TEST_SOURCE_IMAGE);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);
        tool.setOwner(TEST_USER);
        tool.setRegistryId(firstRegistry.getId());
        tool.setToolGroupId(library.getId());
        toolDao.createTool(tool);
        
        symlink = new Tool();
        symlink.setImage(TEST_SYMLINK_IMAGE);
        symlink.setRam(TEST_RAM);
        symlink.setCpu(TEST_CPU);
        symlink.setOwner(TEST_USER);
        symlink.setRegistryId(firstRegistry.getId());
        symlink.setToolGroupId(firstUserGroup.getId());
        symlink.setLink(tool.getId());
        toolDao.createTool(symlink);

        secondRegistry = new DockerRegistry();
        secondRegistry.setPath(TEST_REPO);
        secondRegistry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(secondRegistry);

        secondUserGroup = new ToolGroup();
        secondUserGroup.setName(TOOL_GROUP_NAME);
        secondUserGroup.setRegistryId(secondRegistry.getId());
        secondUserGroup.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(secondUserGroup);

        secondSymlink = new Tool();
        secondSymlink.setImage(TEST_SYMLINK_IMAGE);
        secondSymlink.setRam(TEST_RAM);
        secondSymlink.setCpu(TEST_CPU);
        secondSymlink.setOwner(TEST_USER);
        secondSymlink.setRegistryId(secondRegistry.getId());
        secondSymlink.setToolGroupId(secondUserGroup.getId());
        secondSymlink.setLink(tool.getId());
        toolDao.createTool(secondSymlink);

        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
    }

    private static final String DATA_KEY_1 = "tag";
    private static final String DATA_TYPE_1 = "string";
    private static final String DATA_VALUE_1 = "OWNER";

    @Test
    @Transactional
    public void testLoadMetadataItemForTool() {
        final MetadataEntry createdMetadata = createMetadata(tool);

        final MetadataEntry actualMetadata = metadataDao.loadMetadataItem(entity(tool));

        assertMetadata(actualMetadata, createdMetadata);
    }

    @Test
    @Transactional
    public void testLoadMetadataItemForSymlink() {
        final MetadataEntry createdMetadata = createMetadata(tool);

        final MetadataEntry actualMetadata = metadataDao.loadMetadataItem(entity(symlink));

        assertMetadata(actualMetadata, metadata(symlink, createdMetadata.getData()));
    }

    @Test
    @Transactional
    public void testLoadMetadataItemsForTool() {
        final MetadataEntry createdMetadata = createMetadata(tool);

        final List<MetadataEntry> actualMetadata = metadataDao.loadMetadataItems(
                Collections.singletonList(entity(tool)));

        assertThat(actualMetadata.size(), is(1));
        assertMetadata(actualMetadata.get(0), createdMetadata);
    }

    @Test
    @Transactional
    public void testLoadMetadataItemsForSymlink() {
        final MetadataEntry createdMetadata = createMetadata(tool);

        final List<MetadataEntry> actualMetadata = metadataDao.loadMetadataItems(
                Collections.singletonList(entity(symlink)));

        assertThat(actualMetadata.size(), is(1));
        assertMetadata(actualMetadata.get(0), metadata(symlink, createdMetadata.getData()));
    }

    @Test
    @Transactional
    public void testLoadMetadataItemsForSeveralItems() {
        final MetadataEntry createdMetadata = createMetadata(tool);

        final List<MetadataEntry> actualMetadata = metadataDao.loadMetadataItems(
                Arrays.asList(entity(symlink), entity(secondSymlink), entity(tool)));

        assertThat(actualMetadata.size(), is(3));
        
        final List<MetadataEntry> expectedMetadata = Arrays.asList(
                metadata(tool, createdMetadata.getData()),
                metadata(symlink, createdMetadata.getData()),
                metadata(secondSymlink, createdMetadata.getData()));

        assertMetadata(actualMetadata, expectedMetadata);
    }

    @Test
    @Transactional
    public void testLoadMetadataItemsWithIssuesForTool() {
        final MetadataEntryWithIssuesCount createdMetadata = createMetadataWithIssues(tool);

        final List<MetadataEntryWithIssuesCount> actualMetadata = metadataDao.loadMetadataItemsWithIssues(
                Collections.singletonList(entity(tool)));

        assertThat(actualMetadata.size(), is(1));
        assertMetadata(actualMetadata.get(0), createdMetadata);
    }

    @Test
    @Transactional
    public void testLoadMetadataItemsWithIssuesForSymlink() {
        final MetadataEntryWithIssuesCount createdMetadata = createMetadataWithIssues(tool);

        final List<MetadataEntryWithIssuesCount> actualMetadata = metadataDao.loadMetadataItemsWithIssues(
                Collections.singletonList(entity(symlink)));

        assertThat(actualMetadata.size(), is(1));
        assertMetadata(actualMetadata.get(0), metadataWithIssues(symlink, createdMetadata.getData()));
    }

    @Test
    @Transactional
    public void testLoadMetadataItemsWithIssuesForSeveralEntities() {
        final MetadataEntryWithIssuesCount createdMetadata = createMetadataWithIssues(tool);

        final List<MetadataEntryWithIssuesCount> actualMetadata = metadataDao.loadMetadataItemsWithIssues(
                Arrays.asList(entity(symlink), entity(secondSymlink), entity(tool)));

        final List<MetadataEntryWithIssuesCount> expectedMetadata = Arrays.asList(
                metadataWithIssues(tool, createdMetadata.getData()),
                metadataWithIssues(symlink, createdMetadata.getData()),
                metadataWithIssues(secondSymlink, createdMetadata.getData()));
        assertThat(actualMetadata.size(), is(3));
        assertMetadataWithIssues(actualMetadata, expectedMetadata);
    }
    
    private MetadataEntry createMetadata(final Tool tool) {
        final MetadataEntry metadata = metadata(entity(tool), data());
        metadataDao.registerMetadataItem(metadata);
        return metadata;
    }

    private MetadataEntryWithIssuesCount createMetadataWithIssues(final Tool tool) {
        final MetadataEntry metadata = createMetadata(tool);
        issueDao.createIssue(issue(metadata));
        return metadataWithIssues(metadata);
    }

    private MetadataEntryWithIssuesCount metadataWithIssues(final MetadataEntry metadata) {
        final MetadataEntryWithIssuesCount metadataWithIssues = new MetadataEntryWithIssuesCount();
        metadataWithIssues.setEntity(metadata.getEntity());
        metadataWithIssues.setData(metadata.getData());
        metadataWithIssues.setIssuesCount(1);
        return metadataWithIssues;
    }

    private MetadataEntryWithIssuesCount metadataWithIssues(final Tool tool, final Map<String, PipeConfValue> data) {
        return metadataWithIssues(metadata(tool, data));
    }

    private Issue issue(final MetadataEntry metadata) {
        final Issue issue = new Issue();
        issue.setEntity(metadata.getEntity());
        issue.setName(ISSUE_NAME);
        issue.setText(ISSUE_TEXT);
        issue.setAuthor(AUTHOR);
        return issue;
    }

    private MetadataEntry metadata(final Tool tool, final Map<String, PipeConfValue> data) {
        final MetadataEntry metadata = new MetadataEntry();
        metadata.setEntity(entity(tool));
        metadata.setData(data);
        return metadata;
    }

    private MetadataEntry metadata(final EntityVO entityVO, final Map<String, PipeConfValue> data) {
        final MetadataEntry metadataToSave = new MetadataEntry();
        metadataToSave.setEntity(entityVO);
        metadataToSave.setData(data);
        return metadataToSave;
    }

    private EntityVO entity(final Tool tool) {
        return new EntityVO(tool.getId(), AclClass.TOOL);
    }

    private Map<String, PipeConfValue> data() {
        final Map<String, PipeConfValue> data = new HashMap<>();
        data.put(DATA_KEY_1, new PipeConfValue(DATA_TYPE_1, DATA_VALUE_1));
        return data;
    }

    private void assertMetadataWithIssues(final List<MetadataEntryWithIssuesCount> actualMetadata,
                                final List<MetadataEntryWithIssuesCount> expectedMetadata) {
        assertThat(actualMetadata.size(), is(expectedMetadata.size()));
        actualMetadata.sort(Comparator.comparing(it -> it.getEntity().getEntityId()));
        expectedMetadata.sort(Comparator.comparing(it -> it.getEntity().getEntityId()));
        for (int i = 0; i < expectedMetadata.size(); i++) {
            assertMetadata(actualMetadata.get(i), expectedMetadata.get(i));
        }
    }

    private void assertMetadata(final List<MetadataEntry> actualMetadata,
                                final List<MetadataEntry> expectedMetadata) {
        assertThat(actualMetadata.size(), is(expectedMetadata.size()));
        actualMetadata.sort(Comparator.comparing(it -> it.getEntity().getEntityId()));
        expectedMetadata.sort(Comparator.comparing(it -> it.getEntity().getEntityId()));
        for (int i = 0; i < expectedMetadata.size(); i++) {
            assertMetadata(actualMetadata.get(i), expectedMetadata.get(i));
        }
    }

    private void assertMetadata(final MetadataEntry actualMetadata, final MetadataEntry expectedMetadata) {
        assertThat(actualMetadata.getEntity(), is(expectedMetadata.getEntity()));
        assertThat(actualMetadata.getData(), is(expectedMetadata.getData()));
    }

    private void assertMetadata(final MetadataEntryWithIssuesCount actualMetadata, 
                                final MetadataEntryWithIssuesCount expectedMetadata) {
        assertMetadata((MetadataEntry) actualMetadata, expectedMetadata);
        assertThat(actualMetadata.getIssuesCount(), is(expectedMetadata.getIssuesCount()));
    }
}
