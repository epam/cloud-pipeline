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

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LONG;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.NFS_MASK;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.S3_MASK;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getNfsDataStorage;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getS3bucketDataStorage;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineStart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PipelineConfigurationManagerTest {
    private static final String TEST_IMAGE = "image";
    private static final String TEST_REPO = "repository";
    private static final String OWNER1 = "testUser1";
    private static final String OWNER2 = "testUser2";
    private static final Long NFS_ID_1 = 1L;
    private static final Long S3_ID_1 = 2L;
    private static final Long NFS_ID_2 = 3L;
    private static final Long S3_ID_2 = 4L;
    private static final String TEST_DOCKER_IMAGE = TEST_REPO + "/" + TEST_IMAGE;
    private static final Map<String, PipeConfValueVO> TEST_PARAMS = Collections.singletonMap("testParam",
            new PipeConfValueVO("testParamValue", "int", true));
    private static final String TEST_MOUNT_POINT = "/some/path";
    private static final String TEST_OPTIONS_1 = "options1";
    private static final String TEST_OPTIONS_2 = "options2";
    private static final String TEST_PATH_1 = "test/path1";
    private static final String TEST_PATH_2 = "test/path2";
    private static final String TEST_PATH_3 = "test/path3";
    private static final String TEST_PATH_4 = "test/path4";

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
        dataStorages.add(getNfsDataStorage(NFS_ID_1, TEST_PATH_1, TEST_OPTIONS_1, TEST_MOUNT_POINT, OWNER1));
        dataStorages.add(getS3bucketDataStorage(S3_ID_1, TEST_PATH_2, OWNER1));
        dataStorages.add(getNfsDataStorage(NFS_ID_2, TEST_PATH_3, TEST_OPTIONS_2, "", OWNER2));
        dataStorages.add(getS3bucketDataStorage(S3_ID_2, TEST_PATH_4, OWNER2));
    }

    @Test
    public void shouldGetUnregisteredPipelineConfiguration() {
        doReturn(TEST_DOCKER_IMAGE).when(pipelineVersionManager).getValidDockerImage(eq(TEST_IMAGE));
        doReturn(dataStorages).when(dataStorageApiService).getWritableStorages();

        final PipelineStart runVO = getPipelineStart(TEST_PARAMS, TEST_IMAGE);
        final PipelineConfiguration config = pipelineConfigurationManager.getPipelineConfiguration(runVO);
        assertFalse(config.getBuckets().isEmpty());
        assertFalse(config.getNfsMountOptions().isEmpty());

        final String[] buckets = config.getBuckets().split(";");
        assertThat(buckets)
                .hasSize(4)
                .contains(NFS_MASK + TEST_PATH_1, S3_MASK + TEST_PATH_2, NFS_MASK + TEST_PATH_3, S3_MASK + TEST_PATH_4);

        final String[] nfsOptions = deleteEmptyElements(config.getNfsMountOptions().split(";"));
        assertThat(nfsOptions)
                .hasSize(2)
                .contains(TEST_OPTIONS_1, TEST_OPTIONS_2);

        final String[] mountPoints = config.getMountPoints().split(";");
        assertThat(mountPoints)
                .hasSize(1)
                .contains(TEST_MOUNT_POINT);

        assertThat(config.isNonPause()).isFalse();
        assertThat(config.getInstanceImage()).isEqualTo(TEST_STRING);
        assertThat(config.getInstanceType()).isEqualTo(TEST_STRING);
        assertThat(config.getPrettyUrl()).isEqualTo(TEST_STRING);
        assertThat(config.getWorkerCmd()).isEqualTo(TEST_STRING);
        assertThat(config.getCmdTemplate()).isEqualTo(TEST_STRING);
        assertThat(config.getNodeCount()).isEqualTo(TEST_INT);
        assertThat(Integer.valueOf(config.getInstanceDisk())).isEqualTo(TEST_INT);
        assertThat(config.getIsSpot()).isTrue();
        assertThat(config.getParameters()).isEqualTo(TEST_PARAMS);
        assertThat(config.getDockerImage()).isEqualTo(TEST_REPO + "/" + TEST_IMAGE);
        assertThat(config.getTimeout()).isEqualTo(TEST_LONG);

        verify(pipelineVersionManager).getValidDockerImage(eq(TEST_IMAGE));
        verify(dataStorageApiService).getWritableStorages();
        verify(permissionsService, times(2)).isMaskBitSet(
                eq(AbstractDataStorage.ALL_PERMISSIONS_MASK),
                eq(((AclPermission) AclPermission.WRITE).getSimpleMask()));
    }

    private String[] deleteEmptyElements(final String[] array) {
        return Arrays.stream(array)
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);
    }
}
