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

package com.epam.pipeline.dao.datastorage;

import static org.assertj.core.api.Assertions.assertThat;

import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.StorageQuotaAction;
import com.epam.pipeline.entity.datastorage.StorageQuotaType;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationEntry;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationRecipient;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaTrigger;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StorageQuotaTriggersDaoTest extends AbstractJdbcTest {

    private static final String OWNER = "owner";

    @Autowired
    private StorageQuotaTriggersDao storageQuotaTriggersDao;

    @Autowired
    private DataStorageDao dataStorageDao;

    @Transactional
    @Test
    public void testCRUD() {
        final Long storageId1 = createNfsStorage().getId();
        final Long storageId2 = createNfsStorage().getId();
        final NFSQuotaTrigger triggerEntry1 = createQuotaTrigger(storageId1, 10.0, StorageQuotaType.PERCENTS,
                                                                 StorageQuotaAction.EMAIL);
        final NFSQuotaTrigger triggerEntry2 = createQuotaTrigger(storageId2, 50.0, StorageQuotaType.GIGABYTES,
                                                                 StorageQuotaAction.DISABLE, StorageQuotaAction.EMAIL);

        assertThat(storageQuotaTriggersDao.loadAll()).isEmpty();

        storageQuotaTriggersDao.create(triggerEntry1);
        assertThat(storageQuotaTriggersDao.loadAll()).hasSameElementsAs(Collections.singletonList(triggerEntry1));
        assertThat(storageQuotaTriggersDao.find(storageId1)).isEqualTo(Optional.of(triggerEntry1));
        assertThat(storageQuotaTriggersDao.find(storageId2)).isEqualTo(Optional.empty());

        storageQuotaTriggersDao.create(triggerEntry2);
        assertThat(storageQuotaTriggersDao.loadAll()).hasSameElementsAs(Arrays.asList(triggerEntry1, triggerEntry2));
        assertThat(storageQuotaTriggersDao.find(storageId2)).isEqualTo(Optional.of(triggerEntry2));

        dataStorageDao.deleteDataStorage(storageId1);
        assertThat(storageQuotaTriggersDao.loadAll()).hasSameElementsAs(Collections.singletonList(triggerEntry2));
        assertThat(storageQuotaTriggersDao.find(storageId1)).isEqualTo(Optional.empty());
    }

    private NFSDataStorage createNfsStorage() {
        final NFSDataStorage storage = DatastorageCreatorUtils.getNfsDataStorage(NFSStorageMountStatus.ACTIVE, OWNER);
        dataStorageDao.createDataStorage(storage);
        return storage;
    }

    private NFSQuotaTrigger createQuotaTrigger(final Long storageId, final Double value, final StorageQuotaType type,
                                               final StorageQuotaAction... actions) {
        final NFSQuotaNotificationEntry quota =
            new NFSQuotaNotificationEntry(value, type, Stream.of(actions).collect(Collectors.toSet()));
        return new NFSQuotaTrigger(storageId, quota,
                                   Collections.singletonList(new NFSQuotaNotificationRecipient(true, OWNER)),
                                   DateUtils.nowUTC());
    }
}
