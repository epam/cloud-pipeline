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
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecutionStatus;
import com.epam.pipeline.dto.datastorage.lifecycle.transition.StorageLifecycleRuleTransition;
import com.epam.pipeline.dto.datastorage.lifecycle.transition.StorageLifecycleTransitionMethod;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleEntity;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleExecutionEntity;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.mapper.datastorage.lifecycle.StorageLifecycleEntityMapper;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleExecutionRepository;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleRepository;
import com.google.common.collect.Iterables;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.dto.datastorage.lifecycle.transition.StorageLifecycleTransitionCriterion.*;
import static com.epam.pipeline.manager.ObjectCreatorUtils.createS3Bucket;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

public class DataStorageLifecycleManagerTest {

    public static final long ID = 1L;
    public static final String ROOT = "/data/**/dataset*";

    public static final String ROOT_2 = "/root/**/dataset*";

    public static final String STORAGE_CLASS = "Glacier";
    public static final String OBJECT_GLOB = "*.txt";
    public static final StorageLifecycleRule RULE = StorageLifecycleRule.builder()
            .datastorageId(ID)
            .pathGlob(ROOT)
            .objectGlob(OBJECT_GLOB)
            .transitionCriterion(builder()
                    .type(StorageLifecycleTransitionCriterionType.DEFAULT).build())
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
    private final DataStorageLifecycleRuleExecutionRepository lifecycleRuleExecutionRepository =
            Mockito.mock(DataStorageLifecycleRuleExecutionRepository.class);
    private final StorageLifecycleEntityMapper mapper = Mappers.getMapper(StorageLifecycleEntityMapper.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);
    private final StorageProviderManager providerManager = Mockito.mock(StorageProviderManager.class);

    private final UserManager userManager = Mockito.mock(UserManager.class);


    private final DataStorageLifecycleManager lifecycleManager = new DataStorageLifecycleManager(
            messageHelper, mapper, lifecycleRuleRepository, lifecycleRuleExecutionRepository,
            storageManager, providerManager, preferenceManager, userManager
    );

    @Before
    public void setUp() {
        Mockito.doReturn(createS3Bucket(ID, "bucket", "bucket", "TEST_USER"))
                .when(storageManager)
                .load(Mockito.anyLong());

        Mockito.doReturn(Iterables.concat())
                .when(lifecycleRuleRepository)
                .findByDatastorageId(eq(ID));

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
    public void shouldFailCreateLifecycleRuleIfExists() {
        Mockito.doReturn(Collections.singletonList(mapper.toEntity(RULE_WITH_ID)))
                .when(lifecycleRuleRepository)
                .findByDatastorageId(eq(ID));
        lifecycleManager.createStorageLifecyclePolicyRule(ID, RULE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailCreateLifecycleRuleIfExistsButCriterionEmpty() {
        Mockito.doReturn(Collections.singletonList(mapper.toEntity(RULE_WITH_ID)))
                .when(lifecycleRuleRepository)
                .findByDatastorageId(eq(ID));
        final StorageLifecycleRule ruleWithoutCriterion = RULE.toBuilder().transitionCriterion(null).build();
        lifecycleManager.createStorageLifecyclePolicyRule(ID, ruleWithoutCriterion);
    }

    @Test
    public void shouldFailCreateLifecycleRuleIfPathNotFromRoot() {
        final StorageLifecycleRule rule = RULE.toBuilder().pathGlob("path/not/from/root").build();
        final StorageLifecycleRule created = lifecycleManager.createStorageLifecyclePolicyRule(ID, rule);
        Assert.assertTrue(created.getPathGlob().startsWith("/"));
    }

    @Test
    public void shouldSuccessfullyCreateLifecycleRuleIfRuleWithAnotherSettingsExists() {
        Mockito.doReturn(Collections.singletonList(mapper.toEntity(RULE_WITH_ID)))
                .when(lifecycleRuleRepository)
                .findByDatastorageId(eq(ID));
        final StorageLifecycleRule anotherRule = RULE.toBuilder().objectGlob(null).build();
        final StorageLifecycleRule created = lifecycleManager.createStorageLifecyclePolicyRule(ID, anotherRule);
        verifyRule(created, RULE_WITH_ID);
    }

    @Test
    public void shouldSuccessfullyCreateLifecycleRuleIfRuleWithAnotherCriterionExists() {
        Mockito.doReturn(Collections.singletonList(mapper.toEntity(RULE_WITH_ID)))
                .when(lifecycleRuleRepository)
                .findByDatastorageId(eq(ID));
        final StorageLifecycleRule ruleWithAnotherCriterion = RULE.toBuilder()
                .transitionCriterion(
                        builder().type(StorageLifecycleTransitionCriterionType.MATCHING_FILES)
                                .value(OBJECT_GLOB).build()
                ).build();
        final StorageLifecycleRule created =
                lifecycleManager.createStorageLifecyclePolicyRule(ID, ruleWithAnotherCriterion);
        verifyRule(created, RULE_WITH_ID);
    }

    @Test
    public void shouldListAllRulesIfPathNotProvided() {
        final StorageLifecycleRule anotherRule = RULE.toBuilder().id(ID + 1).pathGlob(ROOT_2).build();
        Mockito.doReturn(Arrays.asList(mapper.toEntity(RULE_WITH_ID), mapper.toEntity(anotherRule)))
                .when(lifecycleRuleRepository)
                .findByDatastorageId(eq(ID));
        final List<StorageLifecycleRule> listed = lifecycleManager.listStorageLifecyclePolicyRules(ID, null);
        Assert.assertEquals(2, listed.size());
    }

    @Test
    public void shouldListOnlyMatchingRulesIfPathProvided() {
        final StorageLifecycleRule anotherRule = RULE.toBuilder().id(ID + 1).pathGlob(ROOT_2).build();
        Mockito.doReturn(Arrays.asList(mapper.toEntity(RULE_WITH_ID), mapper.toEntity(anotherRule)))
                .when(lifecycleRuleRepository)
                .findByDatastorageId(eq(ID));
        final List<StorageLifecycleRule> listed =
                lifecycleManager.listStorageLifecyclePolicyRules(ID, "/root/data_folder/dataset1");
        Assert.assertEquals(1, listed.size());
    }

    @Test
    public void shouldSuccessfullyCreateLifecycleRuleExecution() {
        final StorageLifecycleRuleExecution execution = StorageLifecycleRuleExecution.builder()
                .ruleId(ID).path("/data/1/dataset1")
                .storageClass(STORAGE_CLASS)
                .status(StorageLifecycleRuleExecutionStatus.RUNNING)
                .build();
        final StorageLifecycleRuleExecutionEntity saved = StorageLifecycleRuleExecutionEntity.builder()
                .ruleId(ID).path("/data/1/dataset1")
                .storageClass(STORAGE_CLASS)
                .status(StorageLifecycleRuleExecutionStatus.RUNNING)
                .build();
        Mockito.doReturn(saved).when(lifecycleRuleExecutionRepository)
                .save(any(StorageLifecycleRuleExecutionEntity.class));
        lifecycleManager.createStorageLifecycleRuleExecution(ID, execution);
        Mockito.verify(lifecycleRuleExecutionRepository).save(Mockito.any(StorageLifecycleRuleExecutionEntity.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailCreateLifecycleRuleExecutionIfPathNotMatch() {
        lifecycleManager.createStorageLifecycleRuleExecution(ID,
                StorageLifecycleRuleExecution.builder()
                        .ruleId(ID).path("data/1/dataset2")
                        .storageClass(STORAGE_CLASS)
                        .status(StorageLifecycleRuleExecutionStatus.RUNNING)
                        .build());
    }

    @Test
    public void shouldSuccessfullyUpdateStatusForLifecycleRuleExecution() {
        final LocalDateTime startPoint = DateUtils.nowUTC().minus(1, ChronoUnit.SECONDS);
        final StorageLifecycleRuleExecutionEntity execution = StorageLifecycleRuleExecutionEntity.builder()
                .ruleId(ID).path("/data/1/dataset1")
                .storageClass(STORAGE_CLASS)
                .status(StorageLifecycleRuleExecutionStatus.RUNNING)
                .updated(DateUtils.nowUTC())
                .build();
        Mockito.doReturn(execution).when(lifecycleRuleExecutionRepository).findOne(eq(ID));
        Mockito.doReturn(execution).when(lifecycleRuleExecutionRepository)
                .save(any(StorageLifecycleRuleExecutionEntity.class));

        lifecycleManager.updateStorageLifecycleRuleExecutionStatus(ID, StorageLifecycleRuleExecutionStatus.SUCCESS);
        final ArgumentCaptor<StorageLifecycleRuleExecutionEntity> updated =
                ArgumentCaptor.forClass(StorageLifecycleRuleExecutionEntity.class);
        Mockito.verify(lifecycleRuleExecutionRepository).save(updated.capture());
        Assert.assertTrue(updated.getValue().getUpdated().isAfter(startPoint));
        Assert.assertEquals(updated.getValue().getStatus(), StorageLifecycleRuleExecutionStatus.SUCCESS);
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