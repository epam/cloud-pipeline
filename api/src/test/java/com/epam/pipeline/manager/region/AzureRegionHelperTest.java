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
import com.epam.pipeline.entity.region.AzurePolicy;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import org.junit.Test;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.mockito.Mockito.mock;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
public class AzureRegionHelperTest {

    private static final String MAX_IP = "10.0.0.4";
    private static final String MIN_IP = "10.0.0.1";

    private final MessageHelper messageHelper = mock(MessageHelper.class);

    @Test
    public void shouldThrowIfRegionIdIsMissing() {
        final AzureRegionHelper azureRegionHelper = new AzureRegionHelper(messageHelper);
        final AzureRegion region = new AzureRegion();
        assertThrows(IllegalArgumentException.class, () -> azureRegionHelper.validateRegion(region,
                new AzureRegionCredentials()));
    }

    @Test
    public void createShouldThrowIfRegionIdIsInvalid() {
        final AzureRegionHelper azureRegionHelper = new AzureRegionHelper(messageHelper);

        final AzureRegion region = new AzureRegion();
        region.setRegionCode("invalid");
        assertThrows(IllegalArgumentException.class, () -> azureRegionHelper.validateRegion(region,
                new AzureRegionCredentials()));
    }

    @Test
    public void shouldThrowIfMinIpMoreThanMaxIp() {
        final AzureRegionHelper azureRegionHelper = new AzureRegionHelper(messageHelper);

        final AzurePolicy policy = new AzurePolicy();
        policy.setIpMax(MIN_IP);
        policy.setIpMin(MAX_IP);
        assertThrows(IllegalArgumentException.class, () -> azureRegionHelper.validateStoragePolicy(policy));
    }

    @Test
    public void shouldThrowIfAzurePolicyIsEmpty() {
        final AzureRegionHelper azureRegionHelper = new AzureRegionHelper(messageHelper);

        final AzurePolicy policy = new AzurePolicy();
        policy.setIpMax(MAX_IP);
        assertThrows(IllegalArgumentException.class, () -> azureRegionHelper.validateStoragePolicy(policy));
    }
}
