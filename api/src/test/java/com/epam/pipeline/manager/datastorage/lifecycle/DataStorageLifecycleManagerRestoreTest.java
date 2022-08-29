/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.lifecycle;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreAction;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionSearchFilter;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestorePath;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestorePathType;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreStatus;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.lifecycle.restore.StorageRestoreActionEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.mapper.datastorage.lifecycle.StorageLifecycleEntityMapper;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleExecutionRepository;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleRepository;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageRestoreActionRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mockito;
import org.springframework.data.util.Pair;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

public class DataStorageLifecycleManagerRestoreTest {

    private static final long ID = 1L;
    private static final String STORAGE_NAME = "storage-name";
    private static final String PATH_1 = "data/";
    public static final StorageRestoreActionEntity RUNNING_ACTION = StorageRestoreActionEntity.builder().id(ID)
            .datastorageId(ID).path(PATH_1)
            .status(StorageRestoreStatus.RUNNING)
            .days(50L)
            .started(DateUtils.nowUTC().minus(2L, ChronoUnit.DAYS)).build();
    public static final StorageRestoreActionEntity INITIATED_ACTION = StorageRestoreActionEntity.builder().id(ID)
            .datastorageId(ID).path(PATH_1)
            .status(StorageRestoreStatus.INITIATED)
            .days(10L)
            .started(DateUtils.nowUTC().minus(1L, ChronoUnit.DAYS)).build();

    public static final StorageRestoreActionEntity CANCELLED_ACTION = StorageRestoreActionEntity.builder().id(ID)
            .datastorageId(ID).path(PATH_1)
            .status(StorageRestoreStatus.CANCELLED)
            .days(10L)
            .started(DateUtils.nowUTC().minus(1L, ChronoUnit.DAYS)).build();
    private static final String PATH_2 = "data2/";
    public static final String STANDARD_RESTORE_MODE = "Standard";

    private final DataStorageManager storageManager = Mockito.mock(DataStorageManager.class);
    private final PreferenceManager preferenceManager = Mockito.mock(PreferenceManager.class);
    private final DataStorageLifecycleRuleRepository lifecycleRuleRepository =
            Mockito.mock(DataStorageLifecycleRuleRepository.class);
    private final DataStorageLifecycleRuleExecutionRepository lifecycleRuleExecutionRepository =
            Mockito.mock(DataStorageLifecycleRuleExecutionRepository.class);
    private final DataStorageRestoreActionRepository dataStoragePathRestoreActionRepository =
            Mockito.mock(DataStorageRestoreActionRepository.class);
    private final StorageLifecycleEntityMapper mapper = Mappers.getMapper(StorageLifecycleEntityMapper.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);
    private final StorageProviderManager providerManager = Mockito.mock(StorageProviderManager.class);
    private final UserManager userManager = Mockito.mock(UserManager.class);

    private final AbstractDataStorage dataStorage = new S3bucketDataStorage(ID, STORAGE_NAME, STORAGE_NAME);


    private final DataStorageLifecycleManager lifecycleManager = new DataStorageLifecycleManager(
            messageHelper, mapper, lifecycleRuleRepository, lifecycleRuleExecutionRepository,
            dataStoragePathRestoreActionRepository, storageManager, providerManager, preferenceManager, userManager
    );

    private final List<StorageRestoreActionEntity> filteredAsEffective = Arrays.asList(
            RUNNING_ACTION, INITIATED_ACTION
    );


    @Before
    public void setUp() {
        Mockito.doReturn(filteredAsEffective)
                .when(dataStoragePathRestoreActionRepository)
                .filterBy(
                        Mockito.eq(
                            StorageRestoreActionSearchFilter.builder()
                                    .searchType(StorageRestoreActionSearchFilter.SearchType.SEARCH_PARENT)
                                    .statuses(StorageRestoreStatus.ACTIVE_STATUSES)
                                    .datastorageId(ID)
                                    .path(new StorageRestorePath(PATH_1, StorageRestorePathType.FOLDER))
                                    .build()
                        )
                );

        Mockito.doReturn(dataStorage).when(storageManager).load(ID);
    }

    @Test
    public void effectiveActionFilteredCorrectlyTest() {
        final StorageRestoreAction storageRestoreAction =
                lifecycleManager.loadEffectiveRestoreStorageActionByPath(
                        ID, StorageRestorePath.builder().path(PATH_1).type(StorageRestorePathType.FOLDER).build());
        Assert.assertEquals(StorageRestoreStatus.INITIATED, storageRestoreAction.getStatus());
    }

    @Test(expected = IllegalStateException.class)
    public void failToInitiateRestoreIfAlreadyExistAndIsntForcedTest() {
        Mockito.doReturn(Pair.of(true, "")).when(providerManager).isRestoreActionEligible(Mockito.any(), Mockito.any());
        lifecycleManager.initiateStoragePathRestore(dataStorage, StorageRestorePath.builder().path(PATH_1).build(),
                STANDARD_RESTORE_MODE, 10L, false);
    }

    @Test
    public void succeedToInitiateRestoreIfAlreadyExistAndIsForcedTest() {
        Mockito.doReturn(Pair.of(true, "")).when(providerManager).isRestoreActionEligible(Mockito.any(), Mockito.any());
        Mockito.doReturn(StorageRestoreActionEntity.builder().id(ID).status(StorageRestoreStatus.INITIATED).build())
                .when(dataStoragePathRestoreActionRepository)
                .save(Mockito.any(StorageRestoreActionEntity.class));
        final StorageRestoreAction storageRestoreAction =
                lifecycleManager.initiateStoragePathRestore(dataStorage,
                        StorageRestorePath.builder().path(PATH_1).type(StorageRestorePathType.FOLDER).build(),
                        STANDARD_RESTORE_MODE, 10L, true);
        Assert.assertNotNull(storageRestoreAction);
        Assert.assertEquals(StorageRestoreStatus.INITIATED, storageRestoreAction.getStatus());
    }

    @Test
    public void succeedToInitiateRestoreIfIsntAlreadyExistAndIsntForcedTest() {
        Mockito.doReturn(Pair.of(true, "")).when(providerManager).isRestoreActionEligible(Mockito.any(), Mockito.any());
        Mockito.doReturn(INITIATED_ACTION.toBuilder().build())
                .when(dataStoragePathRestoreActionRepository)
                .save(Mockito.any(StorageRestoreActionEntity.class));
        final StorageRestoreAction storageRestoreAction =
                lifecycleManager.initiateStoragePathRestore(dataStorage,
                        StorageRestorePath.builder().path(PATH_2).type(StorageRestorePathType.FOLDER).build(),
                        STANDARD_RESTORE_MODE, 10L, false);
        Assert.assertNotNull(storageRestoreAction);
        Assert.assertEquals(StorageRestoreStatus.INITIATED, storageRestoreAction.getStatus());
    }

    @Test
    public void succeedToChangeStatusForInitiated() {
        Mockito.doReturn(INITIATED_ACTION.toBuilder().build())
                .when(dataStoragePathRestoreActionRepository)
                .findOne(ID);
        lifecycleManager.updateStorageRestoreAction(
                StorageRestoreAction.builder().id(ID).status(StorageRestoreStatus.FAILED).build()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void failedToChangeStatusForNonExisting() {
        lifecycleManager.updateStorageRestoreAction(
                StorageRestoreAction.builder().id(ID).status(StorageRestoreStatus.RUNNING).build()
        );
    }

    @Test(expected = IllegalStateException.class)
    public void failedToChangeStatusForTerminal() {
        Mockito.doReturn(CANCELLED_ACTION.toBuilder().build())
                .when(dataStoragePathRestoreActionRepository)
                .findOne(ID);
        lifecycleManager.updateStorageRestoreAction(
                StorageRestoreAction.builder().id(ID).status(StorageRestoreStatus.SUCCEEDED).build()
        );
    }
}