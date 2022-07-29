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
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRuleTransition;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleTransitionMethod;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleEntity;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.mapper.datastorage.lifecycle.StorageLifecycleEntityMapper;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mockito;

import java.util.Collections;

import static com.epam.pipeline.manager.ObjectCreatorUtils.createS3Bucket;
import static org.mockito.Matchers.any;

public class DataStorageLifecycleManagerTest {

    public static final long ID = 1L;
    public static final String ROOT = "/";
    public static final String STORAGE_CLASS = "Glacier";
    public static final String OBJECT_GLOB = "*.txt";
    public static final StorageLifecycleRule RULE = StorageLifecycleRule.builder()
            .datastorageId(ID)
            .pathGlob(ROOT)
            .objectGlob(OBJECT_GLOB)
            .transitionMethod(StorageLifecycleTransitionMethod.EARLIEST_FILE)
            .transitions(
                    Collections.singletonList(
                            StorageLifecycleRuleTransition.builder()
                                    .storageClass(STORAGE_CLASS)
                                    .transitionAfterDays(2L)
                                    .build()
                    )
            ).build();
    public static final StorageLifecycleRule RULE_WITH_ID = RULE.toBuilder().id(ID).build();

    private final DataStorageManager storageManager = Mockito.mock(DataStorageManager.class);
    private final PreferenceManager preferenceManager = Mockito.mock(PreferenceManager.class);
    private final DataStorageLifecycleRuleRepository lifecycleRuleRepository =
            Mockito.mock(DataStorageLifecycleRuleRepository.class);
    private final StorageLifecycleEntityMapper mapper = Mappers.getMapper(StorageLifecycleEntityMapper.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);
    private final StorageProviderManager providerManager = Mockito.mock(StorageProviderManager.class);


    private final DataStorageLifecycleManager lifecycleManager = new DataStorageLifecycleManager(
            messageHelper, mapper, lifecycleRuleRepository, storageManager, providerManager, preferenceManager
    );

    @Before
    public void setUp() {
        Mockito.doReturn(createS3Bucket(ID, "bucket", "bucket", "TEST_USER"))
                .when(storageManager)
                .load(Mockito.anyLong());

        Mockito.doReturn(mapper.toEntity(RULE_WITH_ID))
                .when(lifecycleRuleRepository)
                .save(any(StorageLifecycleRuleEntity.class));
        Mockito.doReturn(mapper.toEntity(RULE_WITH_ID))
                .when(lifecycleRuleRepository)
                .findOne(RULE_WITH_ID.getId());
    }

    @Test
    public void shouldSuccessfullyCreateLifecycleRule() {
        final StorageLifecycleRule created = lifecycleManager.createStorageLifecyclePolicyRule(ID, RULE);
        verifyRule(created, RULE_WITH_ID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfLifecycleRuleMalformedPathRoot() {
        final StorageLifecycleRule rule = StorageLifecycleRule.builder()
                .datastorageId(ID)
                .objectGlob(OBJECT_GLOB)
                .transitionMethod(StorageLifecycleTransitionMethod.EARLIEST_FILE)
                .transitions(
                        Collections.singletonList(
                                StorageLifecycleRuleTransition.builder()
                                        .storageClass(STORAGE_CLASS)
                                        .transitionAfterDays(2L)
                                        .build()
                        )
                ).build();
        lifecycleManager.createStorageLifecyclePolicyRule(ID, rule);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfLifecycleRuleMalformedTransitions() {
        final StorageLifecycleRule rule = StorageLifecycleRule.builder()
                .datastorageId(ID)
                .pathGlob(ROOT)
                .objectGlob(OBJECT_GLOB)
                .transitionMethod(StorageLifecycleTransitionMethod.EARLIEST_FILE)
                .build();
        lifecycleManager.createStorageLifecyclePolicyRule(ID, rule);
    }

    private void verifyRule(final StorageLifecycleRule created, final StorageLifecycleRule ruleWithId) {
        Assert.assertNotNull(created.getId());
        Assert.assertEquals(ruleWithId.getPathGlob(), created.getPathGlob());
        Assert.assertEquals(ruleWithId.getObjectGlob(), created.getObjectGlob());
        Assert.assertEquals(ruleWithId.getTransitionMethod(), created.getTransitionMethod());
        Assert.assertEquals(ruleWithId.getTransitions().size(), created.getTransitions().size());
        Assert.assertEquals(ruleWithId.getNotification(), created.getNotification());
    }

}