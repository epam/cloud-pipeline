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
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

@SuppressWarnings({"PMD.TooManyStaticImports"})
public class AzureCloudRegionManagerTest extends AbstractCloudRegionManagerTest {

    private static final String RESOURCE_GROUP = "resourceGroup";
    private static final String ANOTHER_RESOURCE_GROUP = "anotherResourceGroup";
    private static final String STORAGE_ACCOUNT = "storageAccount";
    private static final String ANOTHER_STORAGE_ACCOUNT = "anotherStorageAccount";
    private static final String STORAGE_ACCOUNT_KEY = "storageAccountKey";
    private static final String SUBSCRIPTION = "subscription";
    private static final String ANOTHER_SUBSCRIPTION = "anotherSubscription";
    private static final String AUTH_FILE = "authFile";

    @Test
    public void createShouldThrowIfAccountNameIsNotSpecified() {
        assertThrows(IllegalArgumentException.class,
            () -> cloudRegionManager.create(createRegionBuilder().storageAccount(null).build()));
    }

    @Test
    public void createShouldThrowIfAccountKeyIsNotSpecified() {
        assertThrows(IllegalArgumentException.class,
            () -> cloudRegionManager.create(createRegionBuilder().storageAccountKey(null).build()));
    }

    @Test
    public void updateShouldSaveResourceGroupFromTheOldValue() {
        cloudRegionManager.update(ID, updateRegionBuilder().resourceGroup(ANOTHER_RESOURCE_GROUP).build());

        final ArgumentCaptor<AzureRegion> regionCaptor = ArgumentCaptor.forClass(AzureRegion.class);
        verify(cloudRegionDao).update(regionCaptor.capture(), eq(credentials()));
        final AzureRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getResourceGroup(), is(RESOURCE_GROUP));
    }

    @Test
    public void updateShouldSaveStorageAccountFromTheOldValue() {
        cloudRegionManager.update(ID, updateRegionBuilder().storageAccount(ANOTHER_STORAGE_ACCOUNT).build());

        final ArgumentCaptor<AzureRegion> regionCaptor = ArgumentCaptor.forClass(AzureRegion.class);
        verify(cloudRegionDao).update(regionCaptor.capture(), eq(credentials()));
        final AzureRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getStorageAccount(), is(STORAGE_ACCOUNT));
    }

    @Test
    public void updateShouldSaveSubscriptionFromTheOldValue() {
        cloudRegionManager.update(ID, updateRegionBuilder().subscription(ANOTHER_SUBSCRIPTION).build());

        final ArgumentCaptor<AzureRegion> regionCaptor = ArgumentCaptor.forClass(AzureRegion.class);
        verify(cloudRegionDao).update(regionCaptor.capture(), eq(credentials()));
        final AzureRegion actualRegion = regionCaptor.getValue();
        assertThat(actualRegion.getSubscription(), is(SUBSCRIPTION));
    }

    @Test
    public void updateShouldThrowIfAccountKeyIsNotSpecified() {
        assertThrows(IllegalArgumentException.class,
            () -> cloudRegionManager.update(ID, updateRegionBuilder().storageAccountKey(null).build()));
    }

    @Test
    public void loadCredentialsByIdShouldReturnAzureRegionCredentials() {
        cloudRegionManager.create(createRegionBuilder().build());

        final AbstractCloudRegionCredentials credentials = cloudRegionManager.loadCredentials(ID);
        assertThat(credentials, instanceOf(AzureRegionCredentials.class));
        assertThat(((AzureRegionCredentials) credentials).getStorageAccountKey(), is(STORAGE_ACCOUNT_KEY));
    }

    @Test
    public void loadCredentialsByRegionShouldReturnAzureRegionCredentials() {
        cloudRegionManager.create(createRegionBuilder().build());

        final AzureRegionCredentials credentials = cloudRegionManager.loadCredentials(commonRegion());
        assertThat(credentials.getStorageAccountKey(), is(STORAGE_ACCOUNT_KEY));
    }

    @Override
    AzureRegion commonRegion() {
        final AzureRegion region = new AzureRegion();
        region.setId(ID);
        region.setName(REGION_NAME);
        region.setRegionCode(validRegionId());
        region.setOwner("owner");
        region.setCreatedDate(new Date());
        region.setResourceGroup(RESOURCE_GROUP);
        region.setStorageAccount(STORAGE_ACCOUNT);
        region.setSubscription(SUBSCRIPTION);
        region.setAuthFile(AUTH_FILE);
        return region;
    }

    @Override
    CloudRegionVO.CloudRegionVOBuilder createRegionBuilder() {
        return updateRegionBuilder()
                .regionCode(validRegionId())
                .resourceGroup(RESOURCE_GROUP)
                .storageAccount(STORAGE_ACCOUNT);
    }

    @Override
    CloudRegionVO.CloudRegionVOBuilder updateRegionBuilder() {
        return CloudRegionVO.builder()
                .name(REGION_NAME)
                .storageAccountKey(STORAGE_ACCOUNT_KEY)
                .provider(CloudProvider.AZURE);
    }

    @Override
    AbstractCloudRegionCredentials credentials() {
        return new AzureRegionCredentials(STORAGE_ACCOUNT_KEY);
    }

    @Override
    String validRegionId() {
        return "eastus";
    }

    @Override
    void assertRegionEquals(final AbstractCloudRegion expectedRegion, final AbstractCloudRegion actualRegion) {
        assertThat(actualRegion, instanceOf(AzureRegion.class));
        final AzureRegion expectedAzureRegion = (AzureRegion)expectedRegion;
        final AzureRegion actualAzureRegion = (AzureRegion)actualRegion;
        assertThat(expectedAzureRegion.getRegionCode(), is(actualAzureRegion.getRegionCode()));
        assertThat(expectedAzureRegion.getName(), is(actualAzureRegion.getName()));
        assertThat(expectedAzureRegion.getCorsRules(), is(actualAzureRegion.getCorsRules()));
        assertThat(expectedAzureRegion.getAzurePolicy(), is(actualAzureRegion.getAzurePolicy()));
    }

    @Override
    List<CloudRegionHelper> helpers() {
        return Collections.singletonList(new AzureRegionHelper(messageHelper));
    }

    @Override
    CloudProvider defaultProvider() {
        return CloudProvider.AZURE;
    }
}
