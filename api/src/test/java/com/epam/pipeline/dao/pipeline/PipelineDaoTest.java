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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.EntityFilterVO;
import com.epam.pipeline.dao.metadata.MetadataDao;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.run.RunVisibilityPolicy;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PipelineDaoTest extends AbstractJdbcTest {

    private static final String TEST_USER = "Test";
    private static final String TEST_NAME = "Test";
    private static final String TEST_REPO = "///";
    private static final String TEST_REPO_SSH = "git@test";
    private static final String TEST_CODE_PATH = "/src";
    private static final String TEST_DOCS_PATH = "/docs";
    private static final String STRING = "string";
    private static final String KEY_1 = "key1";
    private static final String VALUE_1 = "value1";
    private static final String VALUE_2 = "value2";
    private static final String VALUE_3 = "value3";
    private static final String KEY_2 = "key2";

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private FolderDao folderDao;

    @Autowired
    private MetadataDao metadataDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCreatePipeline() {
        final Pipeline pipeline = getPipeline(TEST_NAME);
        pipeline.setVisibility(RunVisibilityPolicy.OWNER);
        pipeline.setCodePath(TEST_CODE_PATH);
        pipeline.setDocsPath(TEST_DOCS_PATH);
        pipelineDao.createPipeline(pipeline);
        List<Pipeline> result = pipelineDao.loadAllPipelines();
        assertEquals(1, result.size());
        final Pipeline resultPipeline = result.get(0);
        assertEquals(resultPipeline.getVisibility(), RunVisibilityPolicy.OWNER);
        assertEquals(resultPipeline.getCodePath(), TEST_CODE_PATH);
        assertEquals(resultPipeline.getDocsPath(), TEST_DOCS_PATH);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testMovePipeline() {
        Pipeline pipeline = getPipeline(TEST_NAME);
        pipelineDao.createPipeline(pipeline);
        assertNull(pipeline.getParentFolderId());

        Folder folder = getFolder();
        folderDao.createFolder(folder);

        pipeline.setParentFolderId(folder.getId());
        pipelineDao.updatePipeline(pipeline);
        assertEquals(folder.getId(), pipelineDao.loadPipeline(pipeline.getId()).getParentFolderId());

        pipeline.setParentFolderId(null);
        pipelineDao.updatePipeline(pipeline);
        assertNull(pipeline.getParentFolderId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testLoadAllPipelinesWithFolderTree() {
        Pipeline rootPipeline = getPipeline("ROOT_PIPELINE");
        pipelineDao.createPipeline(rootPipeline);
        assertNull(rootPipeline.getParentFolderId());

        Folder rootFolder = getFolder();
        folderDao.createFolder(rootFolder);

        Pipeline pipeline1 = getPipeline("PIPELINE1");
        pipeline1.setParentFolderId(rootFolder.getId());
        pipeline1.setParent(rootFolder);
        pipelineDao.createPipeline(pipeline1);

        Folder doNotConsider = new Folder();
        doNotConsider.setName("NOT_CONSIDER");
        doNotConsider.setOwner(TEST_USER);
        folderDao.createFolder(doNotConsider);

        Folder folder1 = getFolder();
        folder1.setParentId(rootFolder.getId());
        folder1.setParent(rootFolder);
        folderDao.createFolder(folder1);

        Folder folder2 = getFolder();
        folder2.setParentId(folder1.getId());
        folder2.setParent(folder1);
        folderDao.createFolder(folder2);

        Folder folder3 = getFolder();
        folder3.setParentId(folder2.getId());
        folder3.setParent(folder2);
        folderDao.createFolder(folder3);

        Pipeline pipeline2 = getPipeline("PIPELINE2");
        pipeline2.setParentFolderId(folder3.getId());
        pipeline2.setParent(folder3);
        pipelineDao.createPipeline(pipeline2);

        List<Pipeline> expected = Stream.of(rootPipeline, pipeline1, pipeline2).collect(Collectors.toList());
        assertPipelineWithParameters(expected, null, null);
        assertPipelineWithParameters(expected, 1, 3);

        expected = Stream.of(rootPipeline, pipeline1).collect(Collectors.toList());
        assertPipelineWithParameters(expected, 1, 2);

        expected = Stream.of(pipeline2).collect(Collectors.toList());
        assertPipelineWithParameters(expected, 2, 2);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldLoadPipelineWithFolders() {
        Folder root = buildFolder(null);
        root.setParentId(0L);
        Folder folder = buildFolder(root.getId());
        folder.setParent(root);
        Folder parent = buildFolder(folder.getId());
        parent.setParent(folder);

        Pipeline pipeline = getPipeline(TEST_NAME);
        pipeline.setParentFolderId(parent.getId());
        pipelineDao.createPipeline(pipeline);

        Pipeline loaded = pipelineDao.loadPipelineWithParents(pipeline.getId());
        verifyFolderTree(loaded.getParent(), parent);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldLoadPipelinesByTagsFilter() {
        createPipelineWithMetadata();
        createPipelineWithMetadata();
        pipelineDao.createPipeline(getPipeline(TEST_NAME));

        final EntityFilterVO filter = new EntityFilterVO();
        filter.setTags(Collections.singletonMap(KEY_1, Collections.singletonList(VALUE_1)));

        final List<Pipeline> actual = pipelineDao.loadAllPipelines(filter);
        assertThat(actual).hasSize(2);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldLoadPipelinesByTagsFilterWithMultipleMetadataValues() {
        final Pipeline pipeline1 = createPipelineWithMetadata(VALUE_1, VALUE_2);
        final Pipeline pipeline2 = createPipelineWithMetadata(VALUE_1, VALUE_3);
        createPipelineWithMetadata(VALUE_2, VALUE_3);

        final EntityFilterVO filter = new EntityFilterVO();
        filter.setTags(Collections.singletonMap(KEY_1, Collections.singletonList(VALUE_1)));

        final List<Pipeline> actual = pipelineDao.loadAllPipelines(filter);
        assertThat(actual).hasSize(2);
        assertThat(actual.stream().map(Pipeline::getId).collect(Collectors.toList()))
                .containsOnly(pipeline1.getId(), pipeline2.getId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldLoadPipelinesByTagsFilterWithMultipleFilterValues() {
        final Pipeline pipeline1 = createPipelineWithMetadata(VALUE_1, VALUE_2);
        createPipelineWithMetadata(VALUE_3, VALUE_2);
        final Pipeline pipeline3 = createPipelineWithMetadata(VALUE_2, VALUE_1);

        final EntityFilterVO filter = new EntityFilterVO();
        filter.setTags(Collections.singletonMap(KEY_1, Arrays.asList(VALUE_1, VALUE_2)));

        final List<Pipeline> actual = pipelineDao.loadAllPipelines(filter);
        assertThat(actual).hasSize(2);
        assertThat(actual.stream().map(Pipeline::getId).collect(Collectors.toList()))
                .containsOnly(pipeline1.getId(), pipeline3.getId());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldNotLoadPipelinesByTagsFilterIfNotSuchKey() {
        createPipelineWithMetadata();
        createPipelineWithMetadata();
        pipelineDao.createPipeline(getPipeline(TEST_NAME));

        final EntityFilterVO filter = new EntityFilterVO();
        filter.setTags(Collections.singletonMap(KEY_2, Collections.singletonList(VALUE_1)));

        final List<Pipeline> actual = pipelineDao.loadAllPipelines(filter);
        assertThat(actual).hasSize(0);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldNotLoadPipelinesByTagsFilterIfNotSuchValue() {
        createPipelineWithMetadata();
        createPipelineWithMetadata();
        pipelineDao.createPipeline(getPipeline(TEST_NAME));

        final EntityFilterVO filter = new EntityFilterVO();
        filter.setTags(Collections.singletonMap(KEY_1, Collections.singletonList(VALUE_2)));

        final List<Pipeline> actual = pipelineDao.loadAllPipelines(filter);
        assertThat(actual).hasSize(0);
    }

    private void assertPipelineWithParameters(List<Pipeline> expected, Integer pageNum, Integer pageSize) {
        Set<Pipeline> loaded = pipelineDao.loadAllPipelinesWithParents(pageNum, pageSize);
        assertEquals(expected.size(), loaded.size());
        Map<Long, Pipeline> expectedMap =
                expected.stream().collect(Collectors.toMap(Pipeline::getId, Function.identity()));
        loaded.forEach(pipeline -> {
            Pipeline expectedPipeline = expectedMap.get(pipeline.getId());
            assertEquals(expectedPipeline.getName(), pipeline.getName());
            Folder expectedParent = expectedPipeline.getParent();
            Folder actualParent = pipeline.getParent();
            while (expectedParent != null) {
                assertEquals(expectedParent.getId(), actualParent.getId());
                expectedParent = expectedParent.getParent();
                actualParent = actualParent.getParent();
            }
        });
    }

    private Pipeline getPipeline(String name) {
        Pipeline pipeline = new Pipeline();
        pipeline.setName(name);
        pipeline.setRepository(TEST_REPO);
        pipeline.setRepositorySsh(TEST_REPO_SSH);
        pipeline.setOwner(TEST_USER);
        return pipeline;
    }

    private Folder getFolder() {
        Folder folder = new Folder();
        folder.setName(TEST_NAME);
        folder.setOwner(TEST_USER);
        return folder;
    }

    private Folder buildFolder(final Long parentId) {
        Folder folder = getFolder();
        folder.setParentId(parentId);
        folderDao.createFolder(folder);
        return folder;
    }

    private void verifyFolderTree(final Folder expected, final Folder actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getParentId(), actual.getParentId());
        if (expected.getParent() != null) {
            verifyFolderTree(expected.getParent(), actual.getParent());
        }
    }

    private void createPipelineWithMetadata() {
        final Pipeline pipeline = getPipeline(TEST_NAME);
        pipelineDao.createPipeline(pipeline);
        final MetadataEntry metadata = new MetadataEntry();
        metadata.setEntity(new EntityVO(pipeline.getId(), AclClass.PIPELINE));
        metadata.setData(Collections.singletonMap(KEY_1, new PipeConfValue(STRING, VALUE_1)));
        metadataDao.registerMetadataItem(metadata);
    }

    private Pipeline createPipelineWithMetadata(final String value1, final String value2) {
        final Pipeline pipeline = getPipeline(TEST_NAME);
        pipelineDao.createPipeline(pipeline);
        final MetadataEntry metadata = new MetadataEntry();
        metadata.setEntity(new EntityVO(pipeline.getId(), AclClass.PIPELINE));
        metadata.setData(new HashMap<String, PipeConfValue>() {{
                put(KEY_1, new PipeConfValue(STRING, value1));
                put(KEY_2, new PipeConfValue(STRING, value2));
            }});
        metadataDao.registerMetadataItem(metadata);

        return pipeline;
    }
}
