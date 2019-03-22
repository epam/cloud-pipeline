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

package com.epam.pipeline.manager.region;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.AwsRegionVO;
import com.epam.pipeline.dao.region.AwsRegionDao;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.mapper.AwsRegionMapper;
import org.junit.Before;
import org.junit.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static com.epam.pipeline.util.CustomMatchers.isEmpty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"PMD.TooManyStaticImports"})
public class AwsRegionManagerTest {

    private static final long ID = 1L;
    private static final String REGION_NAME = "name";
    private static final String VALID_REGION_ID = "us-east-1";
    private static final String INVALID_REGION_ID = "invalid";
    private static final String EMPTY_POLICY = "{}";
    private static final String EMPTY_CORS_RULES = "[]";
    private static final String KMS_KEY_ID = "kmsKeyId";
    private static final String KMS_KEY_ARN = "kmsKeyArn";
    private static final String INVALID_POLICY = "invalidPolicy";
    private static final String INVALID_CORS_RULES = "invalidCorsRules";
    private final AwsRegionMapper awsRegionMapper = Mappers.getMapper(AwsRegionMapper.class);
    private final AwsRegionDao awsRegionDao = mock(AwsRegionDao.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final AuthManager authManager = mock(AuthManager.class);
    private final AwsRegionManager awsRegionManager =
            new AwsRegionManager(awsRegionDao, awsRegionMapper, messageHelper, preferenceManager, authManager);

    @Before
    public void initJsonMapper() {
        new JsonMapper().init();
    }

    @Test
    public void createShouldSaveEntityInDao() {
        final AwsRegionVO regionVO = getAwsRegionVoBuilder()
                .awsRegionName(VALID_REGION_ID)
                .corsRules(EMPTY_CORS_RULES)
                .policy(EMPTY_POLICY)
                .kmsKeyId(KMS_KEY_ID)
                .kmsKeyArn(KMS_KEY_ARN)
                .isDefault(false)
                .build();

        awsRegionManager.create(regionVO);

        verify(awsRegionDao).create(any());
    }

    @Test
    public void createShouldThrowIfNameIsMissing() {
        assertThrows(IllegalArgumentException.class,
            () -> awsRegionManager.create(AwsRegionVO.builder().awsRegionName(VALID_REGION_ID).build()));
    }

    @Test
    public void createShouldThrowIfRegionIdIsMissing() {
        assertThrows(IllegalArgumentException.class,
            () -> awsRegionManager.create(AwsRegionVO.builder().name(REGION_NAME).build()));
    }

    @Test
    public void createShouldThrowIfRegionIdIsInvalid() {
        assertThrows(AwsRegionException.class,
            () -> awsRegionManager.create(AwsRegionVO.builder().name(REGION_NAME)
                    .awsRegionName(INVALID_REGION_ID).build()));
    }

    @Test
    public void createShouldSaveRegionAsIsIfAllOptionalFieldsIsAlreadySet() {
        final AwsRegionVO regionVO = getAwsRegionVoBuilder()
                .corsRules(EMPTY_CORS_RULES)
                .policy(EMPTY_POLICY)
                .kmsKeyId(KMS_KEY_ID)

                .kmsKeyArn(KMS_KEY_ARN)
                .awsRegionName(VALID_REGION_ID)
                .isDefault(false)
                .build();
        final AwsRegion expectedRegion = awsRegionMapper.toAwsRegion(regionVO);

        awsRegionManager.create(regionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(awsRegionDao).create(regionCaptor.capture());
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertRegionEquals(expectedRegion, actualRegion);
    }

    @Test
    public void createShouldSetPolicyToDefaultValueIfMissing() {
        final String defaultPolicyAsString =
                "{" +
                    "\"key1\":\"value1\"," +
                    "\"key2\":\"value2\"" +
                "}";
        Preference policy = new Preference();
        policy.setValue(defaultPolicyAsString);
        doReturn(Optional.of(policy))
                .when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_POLICY.getKey());
        final AwsRegionVO regionVO = getAwsRegionVoBuilder()
                .corsRules(EMPTY_CORS_RULES)
                .kmsKeyId(KMS_KEY_ID)
                .kmsKeyArn(KMS_KEY_ARN)
                .awsRegionName(VALID_REGION_ID)
                .isDefault(false)
                .build();

        awsRegionManager.create(regionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(awsRegionDao).create(regionCaptor.capture());
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getPolicy(), is(defaultPolicyAsString));
    }

    @Test
    public void createShouldSetPolicyToNullIfDefaultValueIsNull() {
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_CORS_POLICY.getKey());
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_POLICY.getKey());
        final AwsRegionVO regionVO = getAwsRegionVoBuilder()
                .corsRules(EMPTY_CORS_RULES)
                .kmsKeyId(KMS_KEY_ID)
                .kmsKeyArn(KMS_KEY_ARN)
                .awsRegionName(VALID_REGION_ID)
                .isDefault(false)
                .build();

        awsRegionManager.create(regionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(awsRegionDao).create(regionCaptor.capture());
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getPolicy(), isEmptyOrNullString());
    }

    @Test
    public void createShouldSetCorsRulesToDefaultValueIfMissing() {
        final String corsRulesAsString =
                "[{" +
                    "\"id\":\"id1\"," +
                    "\"allowedOrigins\":[\"origin1\",\"origin2\"]," +
                    "\"maxAgeSeconds\":0" +
                "},{" +
                    "\"id\":\"id2\"," +
                    "\"maxAgeSeconds\":0," +
                    "\"allowedHeaders\":[\"header1\"]" +
                "}]";
        Preference corsPolicy = new Preference();
        corsPolicy.setValue(corsRulesAsString);
        doReturn(Optional.of(corsPolicy))
                .when(preferenceManager)
                .load(eq(SystemPreferences.DATA_STORAGE_CORS_POLICY.getKey()));
        final AwsRegionVO regionVO = getAwsRegionVoBuilder()
                .policy(EMPTY_POLICY)
                .kmsKeyId(KMS_KEY_ID)
                .kmsKeyArn(KMS_KEY_ARN)
                .awsRegionName(VALID_REGION_ID)
                .isDefault(false)
                .build();

        awsRegionManager.create(regionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(awsRegionDao).create(regionCaptor.capture());
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getCorsRules(), is(corsRulesAsString));
    }

    @Test
    public void createShouldSetCorsRulesToNullIfDefaultValueIsNull() {
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_CORS_POLICY.getKey());
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_POLICY.getKey());
        final AwsRegionVO regionVO = getAwsRegionVoBuilder()
                .policy(EMPTY_POLICY)
                .kmsKeyId(KMS_KEY_ID)
                .kmsKeyArn(KMS_KEY_ARN)
                .awsRegionName(VALID_REGION_ID)
                .isDefault(false)
                .build();

        awsRegionManager.create(regionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(awsRegionDao).create(regionCaptor.capture());
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getCorsRules(), isEmptyOrNullString());
    }

    @Test
    public void createShouldSetKmsKeyIdToDefaultValueIfMissing() {
        final String defaultKeyId = KMS_KEY_ID;
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SECURITY_KEY_ID))
                .thenReturn(defaultKeyId);
        final AwsRegionVO regionVO = getAwsRegionVoBuilder()
                .kmsKeyArn(KMS_KEY_ARN)
                .policy(EMPTY_POLICY)
                .corsRules(EMPTY_CORS_RULES)
                .awsRegionName(VALID_REGION_ID)
                .isDefault(false)
                .build();

        awsRegionManager.create(regionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(awsRegionDao).create(regionCaptor.capture());
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getKmsKeyId(), is(defaultKeyId));
    }

    @Test
    public void createShouldSetKmsKeyArnToDefaultValueIfMissing() {
        final String defaultKeyArn = KMS_KEY_ARN;
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SECURITY_KEY_ARN))
                .thenReturn(defaultKeyArn);
        final AwsRegionVO regionVO = getAwsRegionVoBuilder()
                .kmsKeyId(KMS_KEY_ID)
                .policy(EMPTY_POLICY)
                .corsRules(EMPTY_CORS_RULES)
                .awsRegionName(VALID_REGION_ID)
                .isDefault(false)
                .build();

        awsRegionManager.create(regionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(awsRegionDao).create(regionCaptor.capture());
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getKmsKeyArn(), is(defaultKeyArn));
    }

    @Test
    public void createShouldSetCreatedDateForAwsRegion() {
        final AwsRegionVO awsRegionVO = getAwsRegionVoBuilder().build();
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_CORS_POLICY.getKey());
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_POLICY.getKey());
        awsRegionManager.create(awsRegionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(awsRegionDao).create(regionCaptor.capture());
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertNotNull(actualRegion.getCreatedDate());
    }

    @Test
    public void createShouldSetOwnerForAwsRegion() {
        final String ownerUserName = "ownerUserName";
        final AwsRegionVO awsRegionVO = getAwsRegionVoBuilder().build();
        when(authManager.getAuthorizedUser()).thenReturn(ownerUserName);
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_CORS_POLICY.getKey());
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_POLICY.getKey());
        awsRegionManager.create(awsRegionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(awsRegionDao).create(regionCaptor.capture());
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getOwner(), is(ownerUserName));
    }

    @Test
    public void createShouldThrowIfSpecifiedPolicyIsInvalid() {
        final AwsRegionVO awsRegionVO = getAwsRegionVoBuilder()
                .policy(INVALID_POLICY)
                .build();

        assertThrows(AwsRegionException.class,
            () -> awsRegionManager.create(awsRegionVO));
    }

    @Test
    public void createShouldThrowIfSpecifiedCorsRulesAreInvalid() {
        final AwsRegionVO awsRegionVO = getAwsRegionVoBuilder()
                .corsRules(INVALID_CORS_RULES)
                .build();

        assertThrows(AwsRegionException.class,
            () -> awsRegionManager.create(awsRegionVO));
    }

    @Test
    public void loadByIdShouldRetrieveRegionFromDao() {
        final AwsRegion expectedRegion = getCommonRegion();
        when(awsRegionDao.loadById(eq(ID))).thenReturn(Optional.of(expectedRegion));

        final AwsRegion actualRegion = awsRegionManager.load(ID);

        assertRegionEquals(expectedRegion, actualRegion);
    }

    @Test
    public void loadByIdShouldThrowIfThereIsNoRegionInDao() {
        when(awsRegionDao.loadById(eq(ID))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> awsRegionManager.load(ID));
    }

    @Test
    public void loadAllShouldReturnEmptyListIfThereIsNoEntitiesInDao() {
        when(awsRegionDao.loadAll()).thenReturn(Collections.emptyList());

        assertThat(awsRegionManager.loadAll(), isEmpty());
    }

    @Test
    public void loadAllShouldReturnAllEntitiesFromDao() {
        final AwsRegion firstRegion = getCommonRegion();
        final AwsRegion secondRegion = getCommonRegion();
        when(awsRegionDao.loadAll()).thenReturn(Arrays.asList(firstRegion, secondRegion));

        assertThat(awsRegionManager.loadAll(), containsInAnyOrder(firstRegion, secondRegion));
    }

    @Test
    public void updateShouldThrowIfEntityDoesNotExist() {
        when(awsRegionDao.loadById(ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> awsRegionManager.update(ID, getAwsRegionVoBuilder().build()));
    }

    @Test
    public void updateShouldUpdateEntityInDao() {
        final AwsRegion originalRegion = getCommonRegion();
        originalRegion.setId(ID);
        originalRegion.setCorsRules(EMPTY_CORS_RULES);
        originalRegion.setPolicy(EMPTY_POLICY);
        originalRegion.setKmsKeyId(KMS_KEY_ID);
        originalRegion.setKmsKeyArn(KMS_KEY_ARN);
        final AwsRegion updatedRegion = getCommonRegion();
        updatedRegion.setId(ID);
        updatedRegion.setName("anotherName");
        updatedRegion.setCorsRules(EMPTY_CORS_RULES);
        updatedRegion.setPolicy(EMPTY_POLICY);
        updatedRegion.setKmsKeyId(KMS_KEY_ID);
        updatedRegion.setKmsKeyArn(KMS_KEY_ARN);

        final AwsRegionVO updatedRegionVO = awsRegionMapper.toAwsRegionVO(updatedRegion);
        when(awsRegionDao.loadById(ID)).thenReturn(Optional.of(originalRegion));

        awsRegionManager.update(ID, updatedRegionVO);

        verify(awsRegionDao).update(eq(updatedRegion));
    }

    @Test
    public void updateShouldSaveCreatedDateFromTheOldValue() {
        final Date createdDate = new Date();
        final AwsRegionVO awsRegionVO = getAwsRegionVoBuilder().build();
        final AwsRegion originalRegion = getCommonRegion();
        originalRegion.setCreatedDate(createdDate);
        when(awsRegionDao.loadById(ID)).thenReturn(Optional.of(originalRegion));
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_CORS_POLICY.getKey());
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_POLICY.getKey());
        awsRegionManager.update(ID, awsRegionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(awsRegionDao).update(regionCaptor.capture());
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getCreatedDate(), is(createdDate));
    }

    @Test
    public void updateShouldSaveOwnerFromTheOldValue() {
        final String ownerUserName = "ownerUserName";
        final AwsRegionVO awsRegionVO = getAwsRegionVoBuilder().build();
        final AwsRegion originalRegion = getCommonRegion();
        originalRegion.setOwner(ownerUserName);
        when(awsRegionDao.loadById(ID)).thenReturn(Optional.of(originalRegion));
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_CORS_POLICY.getKey());
        doReturn(Optional.ofNullable(null)).when(preferenceManager)
                .load(SystemPreferences.DATA_STORAGE_POLICY.getKey());
        awsRegionManager.update(ID, awsRegionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(awsRegionDao).update(regionCaptor.capture());
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getOwner(), is(ownerUserName));
    }

    @Test
    public void deleteShouldThrowIfEntityDoesNotExist() {
        when(awsRegionDao.loadById(ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> awsRegionManager.delete(ID));
    }

    @Test
    public void deleteShouldRemoveEntityFromDao() {
        final AwsRegion awsRegion = getCommonRegion();
        when(awsRegionDao.loadById(ID)).thenReturn(Optional.of(awsRegion));

        awsRegionManager.delete(ID);

        verify(awsRegionDao).delete(ID);
    }

    private AwsRegion getCommonRegion() {
        final AwsRegion awsRegion = new AwsRegion();
        awsRegion.setName(REGION_NAME);
        awsRegion.setAwsRegionName(VALID_REGION_ID);
        awsRegion.setOwner("owner");
        awsRegion.setCreatedDate(new Date());
        return awsRegion;
    }

    /**
     * Ignores {@link AwsRegion#getId()} field.
     */
    private void assertRegionEquals(final AwsRegion expectedRegion, final AwsRegion actualRegion) {
        assertThat(expectedRegion.getAwsRegionName(), is(actualRegion.getAwsRegionName()));
        assertThat(expectedRegion.getName(), is(actualRegion.getName()));
        assertThat(expectedRegion.getPolicy(), is(actualRegion.getPolicy()));
        assertThat(expectedRegion.getKmsKeyId(), is(actualRegion.getKmsKeyId()));
        assertThat(expectedRegion.getKmsKeyArn(), is(actualRegion.getKmsKeyArn()));
        assertThat(expectedRegion.getCorsRules(), is(actualRegion.getCorsRules()));
    }

    private AwsRegionVO.AwsRegionVOBuilder getAwsRegionVoBuilder() {
        return AwsRegionVO.builder()
                .name(REGION_NAME)
                .awsRegionName(VALID_REGION_ID);
    }
}
