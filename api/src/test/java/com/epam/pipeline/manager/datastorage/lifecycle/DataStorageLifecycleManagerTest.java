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
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRuleTemplate;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRuleTransition;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleTransitionMethod;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleEntity;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleTemplateEntity;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.StorageProviderManager;
import com.epam.pipeline.mapper.datastorage.lifecycle.StorageLifecycleEntityMapper;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleRepository;
import com.epam.pipeline.repository.datastorage.lifecycle.DataStorageLifecycleRuleTemplateRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;

import static com.epam.pipeline.manager.ObjectCreatorUtils.createS3Bucket;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;

public class DataStorageLifecycleManagerTest {

    public static final long ID = 1L;
    public static final String ROOT = "/";
    public static final String STORAGE_CLASS = "Glacier";
    public static final String OBJECT_GLOB = "*.txt";
    public static final StorageLifecycleRuleTemplate RULE_TEMPLATE = StorageLifecycleRuleTemplate.builder()
            .datastorageId(ID)
            .enabled(true)
            .pathRoot(ROOT)
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
    public static final StorageLifecycleRuleTemplate RULE_TEMPLATE_WITH_ID = RULE_TEMPLATE.toBuilder().id(ID).build();

    public static final StorageLifecycleRule RULE = StorageLifecycleRule.builder()
            .datastorageId(ID)
            .pathRoot(ROOT)
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

    public static final String EMPTY_TRANSITIONS_JSON = "[]";
    public static final String EMPTY_NOTIFICATION_JSON = "{}";

    private final DataStorageManager storageManager = Mockito.mock(DataStorageManager.class);
    private final DataStorageLifecycleRuleRepository lifecycleRuleRepository =
            Mockito.mock(DataStorageLifecycleRuleRepository.class);
    private final DataStorageLifecycleRuleTemplateRepository lifecycleRuleTemplateRepository =
            Mockito.mock(DataStorageLifecycleRuleTemplateRepository.class);
    private final StorageLifecycleEntityMapper mapper = Mappers.getMapper(StorageLifecycleEntityMapper.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);
    private final StorageProviderManager providerManager = Mockito.mock(StorageProviderManager.class);


    private final DataStorageLifecycleManager lifecycleManager = new DataStorageLifecycleManager(
            messageHelper, mapper, lifecycleRuleTemplateRepository,
            lifecycleRuleRepository, storageManager, providerManager
    );

    @Before
    public void setUp() {
        Mockito.doReturn(createS3Bucket(ID, "bucket", "bucket", "TEST_USER"))
                .when(storageManager)
                .load(Mockito.anyLong());

        Mockito.doReturn(mapper.toEntity(RULE_TEMPLATE_WITH_ID))
                .when(lifecycleRuleTemplateRepository)
                .save(any(StorageLifecycleRuleTemplateEntity.class));
        Mockito.doReturn(mapper.toEntity(RULE_TEMPLATE_WITH_ID))
                .when(lifecycleRuleTemplateRepository)
                .findOne(RULE_TEMPLATE_WITH_ID.getId());

        Mockito.doReturn(mapper.toEntity(RULE_WITH_ID))
                .when(lifecycleRuleRepository)
                .save(any(StorageLifecycleRuleEntity.class));
        Mockito.doReturn(mapper.toEntity(RULE_WITH_ID))
                .when(lifecycleRuleRepository)
                .findOne(RULE_WITH_ID.getId());
    }

    @Test
    public void shouldSuccessfullyCreateLifecycleRuleTemplate() {
        final StorageLifecycleRuleTemplate created = lifecycleManager
                .createOrUpdateStorageLifecycleRuleTemplate(RULE_TEMPLATE_WITH_ID);
        verifyRuleTemplate(created, RULE_TEMPLATE_WITH_ID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfLifecycleRuleTemplateMalformedStorageId() {
        final StorageLifecycleRuleTemplate ruleTemplate = StorageLifecycleRuleTemplate.builder()
                .enabled(true)
                .pathRoot(ROOT)
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
        lifecycleManager.createOrUpdateStorageLifecycleRuleTemplate(ruleTemplate);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfLifecycleRuleTemplateMalformedPathRoot() {
        final StorageLifecycleRuleTemplate ruleTemplate = StorageLifecycleRuleTemplate.builder()
                .enabled(true)
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
        lifecycleManager.createOrUpdateStorageLifecycleRuleTemplate(ruleTemplate);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfLifecycleRuleTemplateMalformedTransitions() {
        final StorageLifecycleRuleTemplate ruleTemplate = StorageLifecycleRuleTemplate.builder()
                .enabled(true)
                .datastorageId(ID)
                .pathRoot(ROOT)
                .objectGlob(OBJECT_GLOB)
                .transitionMethod(StorageLifecycleTransitionMethod.EARLIEST_FILE)
                .build();
        lifecycleManager.createOrUpdateStorageLifecycleRuleTemplate(ruleTemplate);
    }

    @Test
    public void shouldSuccessfullyCreateLifecycleRuleFromTemplate() {
        lifecycleManager
                .createStorageLifecyclePolicyRule(
                        StorageLifecycleRule.builder()
                            .datastorageId(ID)
                            .pathRoot("/data")
                            .templateId(RULE_TEMPLATE_WITH_ID.getId())
                            .build()
                );
        final ArgumentCaptor<StorageLifecycleRuleEntity> rule = ArgumentCaptor
                .forClass(StorageLifecycleRuleEntity.class);
        Mockito.verify(lifecycleRuleRepository).save(rule.capture());
        verifyRuleFromTemplate(mapper.toDto(rule.getValue()), RULE_TEMPLATE_WITH_ID);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotBeAbleToDeleteTemplateIfThereIsARuleCreatedFromIt() {
        Mockito.doReturn(Collections.singletonList(StorageLifecycleRuleEntity
                .builder()
                .id(ID)
                .transitionsJson(EMPTY_TRANSITIONS_JSON)
                .notificationJson(EMPTY_NOTIFICATION_JSON).build()))
                .when(lifecycleRuleRepository).findByTemplateId(anyLong());
        lifecycleManager.deleteStorageLifecycleRuleTemplate(ID);
    }

    @Test
    public void shouldSuccessfullyCreateLifecycleRule() {
        final StorageLifecycleRule created = lifecycleManager
                .createStorageLifecyclePolicyRule(RULE);
        verifyRule(created, RULE_WITH_ID);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfLifecycleRuleMalformedStorageId() {
        final StorageLifecycleRule rule = StorageLifecycleRule.builder()
                .pathRoot(ROOT)
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
        lifecycleManager.createStorageLifecyclePolicyRule(rule);
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
        lifecycleManager.createStorageLifecyclePolicyRule(rule);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowIfLifecycleRuleMalformedTransitions() {
        final StorageLifecycleRule rule = StorageLifecycleRule.builder()
                .datastorageId(ID)
                .pathRoot(ROOT)
                .objectGlob(OBJECT_GLOB)
                .transitionMethod(StorageLifecycleTransitionMethod.EARLIEST_FILE)
                .build();
        lifecycleManager.createStorageLifecyclePolicyRule(rule);
    }

    @Test
    public void shouldSuccessfullyCreateLifecycleRuleFrom() {
        lifecycleManager
                .createStorageLifecyclePolicyRule(
                        StorageLifecycleRule.builder()
                                .datastorageId(ID)
                                .pathRoot("/data")
                                .templateId(RULE_TEMPLATE_WITH_ID.getId())
                                .build()
                );
        final ArgumentCaptor<StorageLifecycleRuleEntity> rule = ArgumentCaptor
                .forClass(StorageLifecycleRuleEntity.class);
        Mockito.verify(lifecycleRuleRepository).save(rule.capture());
        verifyRuleFromTemplate(mapper.toDto(rule.getValue()), RULE_TEMPLATE_WITH_ID);
    }

    private void verifyRule(final StorageLifecycleRule created, final StorageLifecycleRule ruleWithId) {
        Assert.assertNotNull(created.getId());
        Assert.assertEquals(ruleWithId.getPathRoot(), created.getPathRoot());
        Assert.assertEquals(ruleWithId.getObjectGlob(), created.getObjectGlob());
        Assert.assertEquals(ruleWithId.getTransitionMethod(), created.getTransitionMethod());
        Assert.assertEquals(ruleWithId.getTransitions().size(), created.getTransitions().size());
        Assert.assertEquals(ruleWithId.getNotification(), created.getNotification());
    }

    private void verifyRuleFromTemplate(final StorageLifecycleRule created,
                                        final StorageLifecycleRuleTemplate ruleTemplateWithId) {
        Assert.assertEquals(ruleTemplateWithId.getObjectGlob(), created.getObjectGlob());
        Assert.assertEquals(ruleTemplateWithId.getTransitionMethod(), created.getTransitionMethod());
        Assert.assertEquals(ruleTemplateWithId.getTransitions().size(), created.getTransitions().size());
        Assert.assertEquals(ruleTemplateWithId.getNotification(), created.getNotification());
    }

    private void verifyRuleTemplate(final StorageLifecycleRuleTemplate created,
                                    final StorageLifecycleRuleTemplate ruleTemplateWithId) {
        Assert.assertNotNull(created.getId());
        Assert.assertEquals(ruleTemplateWithId.getPathRoot(), created.getPathRoot());
        Assert.assertEquals(ruleTemplateWithId.getObjectGlob(), created.getObjectGlob());
        Assert.assertEquals(ruleTemplateWithId.getEnabled(), created.getEnabled());
        Assert.assertEquals(ruleTemplateWithId.getTransitionMethod(), created.getTransitionMethod());
        Assert.assertEquals(ruleTemplateWithId.getTransitions().size(), created.getTransitions().size());
        Assert.assertEquals(ruleTemplateWithId.getNotification(), created.getNotification());
    }


}