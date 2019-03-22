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

package com.epam.pipeline.dao.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class PipelineDaoTest extends AbstractSpringTest {

    private static final String TEST_USER = "Test";
    private static final String TEST_NAME = "Test";
    private static final String TEST_REPO = "///";

    @Autowired
    private PipelineDao pipelineDao;

    @Autowired
    private FolderDao folderDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCreatePipeline() {
        Pipeline pipeline = getPipeline(TEST_NAME);
        pipelineDao.createPipeline(pipeline);
        List<Pipeline> result = pipelineDao.loadAllPipelines();
        assertEquals(1, result.size());
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
}
