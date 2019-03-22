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

import com.epam.pipeline.controller.vo.CloudRegionVO;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@SuppressWarnings({"PMD.TooManyStaticImports"})
public class AwsCloudRegionManagerTest extends AbstractCloudRegionManagerTest {

    private static final String PROFILE = "profile";
    private static final String ANOTHER_PROFILE = "anotherProfile";
    private static final String POLICY = "{}";
    private static final String CORS_RULES = "[]";
    private static final String EMPTY_POLICY = "{}";
    private static final String EMPTY_CORS_RULES = "[]";
    private static final String KMS_KEY_ID = "kmsKeyId";
    private static final String KMS_KEY_ARN = "kmsKeyArn";
    private static final String INVALID_POLICY = "invalidPolicy";
    private static final String INVALID_CORS_RULES = "invalidCorsRules";

    @Test
    public void createShouldSaveEntityInDao() {
        cloudRegionManager.create(createRegionBuilder()
                .regionCode(validRegionId())
                .corsRules(EMPTY_CORS_RULES)
                .policy(EMPTY_POLICY)
                .kmsKeyId(KMS_KEY_ID)
                .kmsKeyArn(KMS_KEY_ARN)
                .isDefault(false)
                .build());

        verify(cloudRegionDao).create(any(), eq(credentials()));
    }

    @Test
    public void createShouldSaveRegionAsIsIfAllOptionalFieldsAreAlreadySet() {
        final CloudRegionVO regionVO = createRegionBuilder()
                .regionCode(validRegionId())
                .corsRules(EMPTY_CORS_RULES)
                .policy(EMPTY_POLICY)
                .kmsKeyId(KMS_KEY_ID)
                .kmsKeyArn(KMS_KEY_ARN)
                .isDefault(false)
                .build();
        final AwsRegion expectedRegion = cloudRegionMapper.toAwsRegion(regionVO);

        cloudRegionManager.create(regionVO);

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(cloudRegionDao).create(regionCaptor.capture(), eq(credentials()));
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertRegionEquals(expectedRegion, actualRegion);
    }


    @Test
    public void createShouldThrowIfSpecifiedPolicyIsInvalid() {
        assertThrows(IllegalArgumentException.class,
            () -> cloudRegionManager.create(createRegionBuilder().policy(INVALID_POLICY).build()));
    }

    @Test
    public void createShouldThrowIfSpecifiedCorsRulesAreInvalid() {
        assertThrows(IllegalArgumentException.class,
            () -> cloudRegionManager.create(createRegionBuilder().corsRules(INVALID_CORS_RULES).build()));
    }

    @Test
    public void updateShouldUpdateEntityInDao() {
        final AwsRegion originalRegion = commonRegion();
        originalRegion.setId(ID);
        originalRegion.setCorsRules(EMPTY_CORS_RULES);
        originalRegion.setPolicy(EMPTY_POLICY);
        originalRegion.setKmsKeyId(KMS_KEY_ID);
        originalRegion.setKmsKeyArn(KMS_KEY_ARN);
        final AwsRegion updatedRegion = commonRegion();
        updatedRegion.setId(ID);
        updatedRegion.setName("anotherName");
        updatedRegion.setCorsRules(EMPTY_CORS_RULES);
        updatedRegion.setPolicy(EMPTY_POLICY);
        updatedRegion.setKmsKeyId(KMS_KEY_ID);
        updatedRegion.setKmsKeyArn(KMS_KEY_ARN);
        updatedRegion.setProfile(null);
        updatedRegion.setRegionCode(null);

        doReturn(Optional.of(originalRegion)).when(cloudRegionDao).loadById(ID);

        cloudRegionManager.update(ID, cloudRegionMapper.toAwsRegionVO(updatedRegion));

        verify(cloudRegionDao).update(eq(updatedRegion), eq(credentials()));
    }

    @Test
    public void updateShouldChangeProfile() {
        cloudRegionManager.update(ID, updateRegionBuilder().profile(ANOTHER_PROFILE).build());

        final ArgumentCaptor<AwsRegion> regionCaptor = ArgumentCaptor.forClass(AwsRegion.class);
        verify(cloudRegionDao).update(regionCaptor.capture(), eq(credentials()));
        final AwsRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getProfile(), is(ANOTHER_PROFILE));
    }

    @Test
    public void loadCredentialsByIdShouldThrowForAwsRegion() {
        cloudRegionManager.create(createRegionBuilder().build());

        assertThrows(() -> cloudRegionManager.loadCredentials(ID));
    }

    @Test
    public void loadCredentialsByRegionShouldThrowForAwsRegion() {
        cloudRegionManager.create(createRegionBuilder().build());

        assertThrows(() -> cloudRegionManager.loadCredentials(commonRegion()));
    }

    @Override
    AwsRegion commonRegion() {
        final AwsRegion awsRegion = new AwsRegion();
        awsRegion.setId(ID);
        awsRegion.setName(REGION_NAME);
        awsRegion.setRegionCode(validRegionId());
        awsRegion.setOwner("owner");
        awsRegion.setCreatedDate(new Date());
        awsRegion.setProfile(PROFILE);
        awsRegion.setCorsRules(CORS_RULES);
        awsRegion.setPolicy(POLICY);
        return awsRegion;
    }

    @Override
    CloudRegionVO.CloudRegionVOBuilder createRegionBuilder() {
        return updateRegionBuilder()
                .regionCode(validRegionId())
                .profile(PROFILE);
    }

    @Override
    CloudRegionVO.CloudRegionVOBuilder updateRegionBuilder() {
        return CloudRegionVO.builder()
                .name(REGION_NAME)
                .provider(CloudProvider.AWS);
    }

    @Override
    AbstractCloudRegionCredentials credentials() {
        return null;
    }

    @Override
    String validRegionId() {
        return "us-east-1";
    }

    @Override
    void assertRegionEquals(final AbstractCloudRegion expectedRegion, final AbstractCloudRegion actualRegion) {
        assertThat(actualRegion, instanceOf(AwsRegion.class));
        final AwsRegion expectedAwsRegion = (AwsRegion) expectedRegion;
        final AwsRegion actualAwsRegion = (AwsRegion) actualRegion;
        assertThat(expectedAwsRegion.getRegionCode(), is(actualAwsRegion.getRegionCode()));
        assertThat(expectedAwsRegion.getName(), is(actualAwsRegion.getName()));
        assertThat(expectedAwsRegion.getCorsRules(), is(actualAwsRegion.getCorsRules()));
        assertThat(expectedAwsRegion.getPolicy(), is(actualAwsRegion.getPolicy()));
        assertThat(expectedAwsRegion.getKmsKeyId(), is(actualAwsRegion.getKmsKeyId()));
        assertThat(expectedAwsRegion.getKmsKeyArn(), is(actualAwsRegion.getKmsKeyArn()));
    }

    @Override
    List<CloudRegionHelper> helpers() {
        return Collections.singletonList(new AwsRegionHelper(messageHelper));
    }

    @Override
    CloudProvider defaultProvider() {
        return CloudProvider.AWS;
    }
}
