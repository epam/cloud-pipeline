/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.datastorage.FileShareMountDao;
import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.entity.datastorage.MountType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;
import java.util.Optional;

public class FileShareMountManagerTest extends AbstractSpringTest {

    @Autowired
    private FileShareMountManager shareMountManager;

    @MockBean
    FileShareMountDao shareMountDao;


    @Before
    public void setup() {
        Mockito.doReturn(Optional.of(azureShareMount())).when(shareMountDao).loadById(0L);
        Mockito.doReturn(Collections.singletonList(azureShareMount())).when(shareMountDao).loadAllByRegionId(0L);
        Mockito.doReturn(Optional.of(awsShareMount())).when(shareMountDao).loadById(1L);
        Mockito.doReturn(Collections.singletonList(awsShareMount())).when(shareMountDao).loadAllByRegionId(1L);
    }

    @Test
    public void load() {
        Assert.assertEquals(azureShareMount(), shareMountManager.load(azureShareMount().getId()));
        Assert.assertEquals(awsShareMount(), shareMountManager.load(awsShareMount().getId()));
    }

    @Test
    public void loadByRegionId() {
        Assert.assertArrayEquals(Collections.singletonList(azureShareMount()).toArray(new FileShareMount[0]),
                shareMountManager.loadByRegionId(azureShareMount().getRegionId()).toArray(new FileShareMount[0]));
        Assert.assertArrayEquals(Collections.singletonList(awsShareMount()).toArray(new FileShareMount[0]),
                shareMountManager.loadByRegionId(awsShareMount().getRegionId()).toArray(new FileShareMount[0]));
    }

    @Test(expected = IllegalArgumentException.class)
    public void saveThrowIfRegionIdIsNull() {
        FileShareMount fileShareMount = new FileShareMount();
        fileShareMount.setMountType(MountType.NFS);
        shareMountManager.save(fileShareMount);
    }

    @Test(expected = IllegalArgumentException.class)
    public void saveThrowIfMountTypeIsNull() {
        FileShareMount fileShareMount = new FileShareMount();
        fileShareMount.setRegionId(0L);
        shareMountManager.save(fileShareMount);
    }

    @Test(expected = IllegalArgumentException.class)
    public void saveThrowIfLustreMountRootIsInvalid() {
        final FileShareMount fileShareMount = new FileShareMount();
        fileShareMount.setRegionId(0L);
        fileShareMount.setMountType(MountType.LUSTRE);
        fileShareMount.setMountRoot("host");
        shareMountManager.save(fileShareMount);
    }

    private FileShareMount azureShareMount() {
        FileShareMount fileShareMount = new FileShareMount();
        fileShareMount.setId(0L);
        fileShareMount.setMountType(MountType.SMB);
        fileShareMount.setRegionId(0L);
        return fileShareMount;
    }

    private FileShareMount awsShareMount() {
        FileShareMount fileShareMount = new FileShareMount();
        fileShareMount.setId(1L);
        fileShareMount.setMountType(MountType.NFS);
        fileShareMount.setRegionId(1L);
        return fileShareMount;
    }
}