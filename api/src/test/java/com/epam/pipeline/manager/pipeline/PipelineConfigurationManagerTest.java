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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.acl.datastorage.DataStorageApiService;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.manager.security.PermissionsService;
import com.epam.pipeline.security.acl.AclPermission;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getNfsDataStorage;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getS3bucketDataStorage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PipelineConfigurationManagerTest {
    private static final String TEST_IMAGE = "image";
    private static final String TEST_REPO = "repository";
    private static final String TEST_INSTANCE_TYPE = "testInstanceType";
    private static final String OWNER1 = "testUser1";
    private static final String OWNER2 = "testUser2";
    private static final Long TEST_TIMEOUT = 11L;
    private static final Long NFS_ID_1 = 1L;
    private static final Long S3_ID_1 = 2L;
    private static final Long NFS_ID_2 = 3L;
    private static final Long S3_ID_2 = 4L;
    private static final String TEST_DOCKER_IMAGE = TEST_REPO + "/" + TEST_IMAGE;
    private static final boolean TEST_NON_PAUSE = false;
    private static final String TEST_INSTANCE_IMAGE = "instanceImage";
    private static final String TEST_PRETTY_URL = "prettyUrl";
    private static final String TEST_WORKER_CMD = "workerCmd";
    private static final Integer TEST_HDD_SIZE = 2;
    private static final String TEST_CMD_TEMPLATE = "cmdTemplate";
    private static final Integer TEST_NODE_COUNT = 1;
    private static final Boolean TEST_IS_SPOT = true;
    private static final Map<String, PipeConfValueVO> TEST_PARAMS = Collections.singletonMap("testParam",
            new PipeConfValueVO("testParamValue", "int", true));

    @Mock
    private PipelineVersionManager pipelineVersionManager;

    @Mock
    private DataStorageApiService dataStorageApiService;

    @Spy
    private PermissionsService permissionsService;

    @InjectMocks
    private final PipelineConfigurationManager pipelineConfigurationManager = new PipelineConfigurationManager();

    private final List<AbstractDataStorage> dataStorages = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        dataStorages.add(getNfsDataStorage(NFS_ID_1, "test/path1", "options1", "/some/path", OWNER1));
        dataStorages.add(getS3bucketDataStorage(S3_ID_1, "test/path2", OWNER1));
        dataStorages.add(getNfsDataStorage(NFS_ID_2, "test/path3", "options2", "", OWNER2));
        dataStorages.add(getS3bucketDataStorage(S3_ID_2, "test/path4", OWNER2));
    }

    @Test
    public void shouldGetUnregisteredPipelineConfiguration() {
        doReturn(TEST_DOCKER_IMAGE).when(pipelineVersionManager).getValidDockerImage(eq(TEST_IMAGE));
        doReturn(dataStorages).when(dataStorageApiService).getWritableStorages();

        final PipelineStart vo = getPipelineStartVO();
        final PipelineConfiguration config = pipelineConfigurationManager.getPipelineConfiguration(vo);
        assertFalse(config.getBuckets().isEmpty());
        assertFalse(config.getNfsMountOptions().isEmpty());

        String[] buckets = config.getBuckets().split(";");
        assertEquals(4, buckets.length);
        for (String bucket : buckets) {
            assertTrue(dataStorages.stream().anyMatch(ds -> bucket.equals(ds.getPathMask())));
        }

        String[] nfsOptions = deleteEmptyElements(config.getNfsMountOptions().split(";"));
        assertEquals(2, nfsOptions.length);
        for (String option : nfsOptions) {
            assertTrue(dataStorages.stream()
                    .filter(ds -> ds instanceof NFSDataStorage)
                    .anyMatch(ds -> {
                        NFSDataStorage nfsDs = (NFSDataStorage) ds;
                        return nfsDs.getMountOptions().equals(option) ||
                                option.equals(nfsDs.getMountOptions() + ",ro");
                    }));
        }

        String[] mountPoints = deleteEmptyElements(config.getMountPoints().split(";"));

        assertEquals(dataStorages.get(0).getMountPoint(), mountPoints[0]);
        assertFalse(config.isNonPause());
        assertEquals(TEST_INSTANCE_IMAGE, config.getInstanceImage());
        assertEquals(TEST_INSTANCE_TYPE, config.getInstanceType());
        assertEquals(TEST_PRETTY_URL, config.getPrettyUrl());
        assertEquals(TEST_WORKER_CMD, config.getWorkerCmd());
        assertEquals(TEST_CMD_TEMPLATE, config.getCmdTemplate());
        assertEquals(TEST_NODE_COUNT, config.getNodeCount());
        assertEquals(TEST_HDD_SIZE, Integer.valueOf(config.getInstanceDisk()));
        assertTrue(config.getIsSpot());
        assertEquals(TEST_PARAMS, config.getParameters());
        assertEquals(TEST_REPO + "/" + TEST_IMAGE, config.getDockerImage());
        assertEquals(TEST_TIMEOUT, config.getTimeout());

        verify(pipelineVersionManager).getValidDockerImage(eq(TEST_IMAGE));
        verify(dataStorageApiService).getWritableStorages();
        verify(permissionsService, times(2)).isMaskBitSet(
                eq(AbstractDataStorage.ALL_PERMISSIONS_MASK),
                eq(((AclPermission) AclPermission.WRITE).getSimpleMask()));
    }

    private PipelineStart getPipelineStartVO() {
        final PipelineStart vo = new PipelineStart();
        vo.setNonPause(TEST_NON_PAUSE);
        vo.setInstanceImage(TEST_INSTANCE_IMAGE);
        vo.setPrettyUrl(TEST_PRETTY_URL);
        vo.setWorkerCmd(TEST_WORKER_CMD);
        vo.setInstanceType(TEST_INSTANCE_TYPE);
        vo.setDockerImage(TEST_IMAGE);
        vo.setHddSize(TEST_HDD_SIZE);
        vo.setCmdTemplate(TEST_CMD_TEMPLATE);
        vo.setTimeout(TEST_TIMEOUT);
        vo.setNodeCount(TEST_NODE_COUNT);
        vo.setIsSpot(TEST_IS_SPOT);
        vo.setParams(TEST_PARAMS);
        return vo;
    }

    private String[] deleteEmptyElements(String[] array) {
        return Arrays.stream(array)
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);
    }
}
