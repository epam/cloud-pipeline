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

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.dao.util.AclTestDao;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.security.acl.AclPermission;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class PipelineConfigurationManagerTest extends AbstractManagerTest {
    private static final String TEST_USER = "test";
    private static final String TEST_IMAGE = "image";
    private static final String TEST_CPU = "500m";
    private static final String TEST_RAM = "1Gi";
    private static final String TEST_REPO = "repository";
    private static final String TOOL_GROUP_NAME = "library";
    private static final String TEST_INSTANCE_TYPE = "testInstanceType";
    private static final String TEST_OWNER1 = "testUser1";
    private static final String TEST_OWNER2 = "testUser2";
    private static final Long TEST_TIMEOUT = 11L;

    @Autowired
    private PipelineConfigurationManager pipelineConfigurationManager;

    @Autowired
    private ToolDao toolDao;

    @Autowired
    private ToolGroupDao toolGroupDao;

    @Autowired
    private DockerRegistryDao dockerRegistryDao;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Autowired
    private AclTestDao aclTestDao;

    private DockerRegistry registry;
    private ToolGroup library;
    private Tool tool;
    private List<AbstractDataStorage> dataStorages = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        registry = new DockerRegistry();
        registry.setPath(TEST_REPO);
        registry.setOwner(TEST_USER);
        dockerRegistryDao.createDockerRegistry(registry);

        library = new ToolGroup();
        library.setName(TOOL_GROUP_NAME);
        library.setRegistryId(registry.getId());
        library.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(library);

        tool = new Tool();
        tool.setImage(TEST_IMAGE);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);
        tool.setOwner(TEST_USER);

        tool.setRegistryId(registry.getId());
        tool.setToolGroupId(library.getId());
        toolDao.createTool(tool);

        // Data storages of user 1
        NFSDataStorage dataStorage = new NFSDataStorage(dataStorageDao.createDataStorageId(), "testNFS",
                                                        "test/path1");
        dataStorage.setMountOptions("testMountOptions1");
        dataStorage.setMountPoint("/some/other/path");
        dataStorage.setOwner(TEST_OWNER1);
        dataStorageDao.createDataStorage(dataStorage);
        dataStorages.add(dataStorage);

        S3bucketDataStorage bucketDataStorage = new S3bucketDataStorage(dataStorageDao.createDataStorageId(),
                                                                        "testBucket", "test/path2");
        bucketDataStorage.setOwner(TEST_OWNER1);
        dataStorageDao.createDataStorage(bucketDataStorage);
        dataStorages.add(bucketDataStorage);

        // Data storages of user 2
        dataStorage = new NFSDataStorage(dataStorageDao.createDataStorageId(), "testNFS2", "test/path3");
        dataStorage.setMountOptions("testMountOptions2");
        dataStorage.setOwner(TEST_OWNER2);
        dataStorageDao.createDataStorage(dataStorage);
        dataStorages.add(dataStorage);

        bucketDataStorage = new S3bucketDataStorage(dataStorageDao.createDataStorageId(),
                                                                        "testBucket2", "test/path4");
        bucketDataStorage.setOwner(TEST_OWNER2);
        dataStorageDao.createDataStorage(bucketDataStorage);
        dataStorages.add(bucketDataStorage);

        dataStorages.forEach(ds -> aclTestDao.createAclForObject(ds));
        aclTestDao.grantPermissions(dataStorage, TEST_OWNER1,
                                    Collections.singletonList((AclPermission) AclPermission.READ));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_OWNER1)
    public void testGetUnregisteredPipelineConfiguration() {
        PipelineStart vo = getPipelineStartVO();
        PipelineConfiguration config = pipelineConfigurationManager.getPipelineConfiguration(vo);
        Assert.assertFalse(config.getBuckets().isEmpty());
        Assert.assertFalse(config.getNfsMountOptions().isEmpty());

        String[] buckets = config.getBuckets().split(";");

        Assert.assertEquals(2, buckets.length);
        for (String bucket : buckets) {
            Assert.assertTrue(dataStorages.stream().anyMatch(ds -> bucket.equals(ds.getPathMask())));
        }


        String[] nfsOptions = config.getNfsMountOptions().split(";");

        Assert.assertEquals(1, nfsOptions.length);
        for (String option : nfsOptions) {
            if (StringUtils.isNotBlank(option)) {
                Assert.assertTrue(dataStorages.stream()
                                      .filter(ds -> ds instanceof NFSDataStorage)
                                      .anyMatch(ds -> {
                                          NFSDataStorage nfsDs = (NFSDataStorage) ds;
                                          return nfsDs.getMountOptions().equals(option) ||
                                                 option.equals(nfsDs.getMountOptions() + ",ro");
                                      }));
            }
        }

        String[] mountPoints = config.getMountPoints().split(";");
        for (String mountPoint : mountPoints) {
            if (StringUtils.isNotBlank(mountPoint)) {
                Assert.assertTrue(dataStorages.stream().anyMatch(ds -> mountPoint.equals(ds.getMountPoint())));
            }
        }
        //Assert.assertTrue(Arrays.stream(nfsOptions).anyMatch(o -> o.endsWith(",ro")));
    }

    private PipelineStart getPipelineStartVO() {
        PipelineStart vo = new PipelineStart();
        vo.setInstanceType(TEST_INSTANCE_TYPE);
        vo.setDockerImage(tool.getImage());
        vo.setHddSize(1);
        vo.setCmdTemplate("template");
        vo.setTimeout(TEST_TIMEOUT);
        vo.setNodeCount(1);
        vo.setIsSpot(true);
        vo.setParams(Collections.singletonMap("testParam", new PipeConfValueVO("testParamValue", "int", true)));
        return vo;
    }
}