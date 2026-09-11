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

package com.epam.pipeline.dao.configuration;

import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RunConfigurationDaoTest extends AbstractJdbcTest {
    
    private static final String TEST_NAME = "test";
    private static final String TEST_NAME_1 = "test2";
    private static final String TEST_NAME_2 = "test3";
    private static final String TEST_DESCRIPTION = "test";
    private static final String TEST_CONFIG_NAME = "configuration1";
    private static final String TEST_CMD_TEMPLATE = "sleep infinity";
    private static final String TEST_DOCKER = "centos";
    private static final String TEST_INSTANCE = "m4.xlarge";
    private static final String TEST_DISK = "10";
    private static final String TEST_OWNER = "test_user";

    @Autowired
    private RunConfigurationDao runConfigurationDao;
    @Autowired
    private FolderDao folderDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCRUD() {

        //create
        RunConfigurationEntry entry =
                ObjectCreatorUtils.createConfigEntry(TEST_CONFIG_NAME, true, getTestConfig());

        RunConfiguration configuration =
                ObjectCreatorUtils.createConfiguration(TEST_NAME, TEST_DESCRIPTION, null,
                        TEST_OWNER, Collections.singletonList(entry));

        RunConfiguration created = runConfigurationDao.create(configuration);
        verifyRunConfiguration(configuration, created);

        //load
        RunConfiguration loaded = runConfigurationDao.load(created.getId());
        verifyRunConfiguration(configuration, loaded);

        //loadAll
        List<RunConfiguration> configurations = runConfigurationDao.loadAll();
        assertEquals(1, configurations.size());
        verifyRunConfiguration(configuration, configurations.get(0));

        //loadRoot
        configurations = runConfigurationDao.loadRootEntities();
        assertEquals(1, configurations.size());
        verifyRunConfiguration(configuration, configurations.get(0));

        //load with folders
        loaded = runConfigurationDao.loadConfigurationWithParents(created.getId());
        verifyRunConfiguration(configuration, loaded);

        //update
        created.setDescription(TEST_DESCRIPTION + TEST_DESCRIPTION);
        created.setEntries(Arrays.asList(entry, entry));
        runConfigurationDao.update(created);
        verifyRunConfiguration(created, runConfigurationDao.load(created.getId()));

        //delete
        runConfigurationDao.delete(created.getId());
        assertTrue(runConfigurationDao.loadAll().isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldLoadRunConfigurationWithFolderTree() {
        Folder root = buildFolder(TEST_NAME, null);
        root.setParentId(0L);
        Folder folder = buildFolder(TEST_NAME_1, root.getId());
        folder.setParent(root);
        Folder parent = buildFolder(TEST_NAME_2, folder.getId());
        parent.setParent(folder);

        RunConfigurationEntry entry =
                ObjectCreatorUtils.createConfigEntry(TEST_CONFIG_NAME, true, getTestConfig());

        RunConfiguration configuration =
                ObjectCreatorUtils.createConfiguration(TEST_NAME, TEST_DESCRIPTION, parent.getId(),
                        TEST_OWNER, Collections.singletonList(entry));

        RunConfiguration created = runConfigurationDao.create(configuration);
        verifyRunConfiguration(configuration, created);

        //load with folders
        RunConfiguration loaded = runConfigurationDao.loadConfigurationWithParents(created.getId());
        verifyRunConfiguration(configuration, loaded);
        verifyFolderTree(parent, loaded.getParent());
    }

    private void verifyRunConfiguration(RunConfiguration expected, RunConfiguration actual) {
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getEntries().size(), actual.getEntries().size());
        verifyRunConfigurationEntry((RunConfigurationEntry) expected.getEntries().get(0),
                (RunConfigurationEntry) actual.getEntries().get(0));
    }

    private void verifyRunConfigurationEntry(RunConfigurationEntry expected, RunConfigurationEntry actual) {
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getDefaultConfiguration(), actual.getDefaultConfiguration());
        verifyPipeConfiguration(expected.getConfiguration(), actual.getConfiguration());
    }

    private void verifyPipeConfiguration(PipelineConfiguration expected, PipelineConfiguration actual) {
        assertEquals(expected.getCmdTemplate(), actual.getCmdTemplate());
        assertEquals(expected.getDockerImage(), actual.getDockerImage());
        assertEquals(expected.getInstanceType(), actual.getInstanceType());
        assertEquals(expected.getInstanceDisk(), actual.getInstanceDisk());
    }

    private void verifyFolderTree(final Folder expected, final Folder actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getParentId(), actual.getParentId());
        if (expected.getParent() != null) {
            verifyFolderTree(expected.getParent(), actual.getParent());
        }
    }

    public PipelineConfiguration getTestConfig() {
        PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setCmdTemplate(TEST_CMD_TEMPLATE);
        configuration.setDockerImage(TEST_DOCKER);
        configuration.setInstanceDisk(TEST_DISK);
        configuration.setInstanceType(TEST_INSTANCE);
        return configuration;
    }

    private Folder buildFolder(final String name, final Long parentId) {
        Folder folder = ObjectCreatorUtils.createFolder(name, parentId);
        folder.setOwner(TEST_OWNER);
        folderDao.createFolder(folder);
        return folder;
    }
}
