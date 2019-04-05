/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.region;

import com.epam.pipeline.controller.vo.region.GCPRegionDTO;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AbstractCloudRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPCustomInstanceType;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;

public class GCPCloudRegionManagerTest extends AbstractCloudRegionManagerTest {

    private static final String GCP_PROJECT = "Project";
    private static final String GCP_PROJECT_CHANGED = "Bio";
    private static final String SSH_PUB_PATH = "/ssh.pub";
    private static final String IMPERSONATED_ACCOUNT = "acc";
    private static final List<GCPCustomInstanceType> CUSTOM_INSTANCE_TYPES = Arrays.asList(
            GCPCustomInstanceType.cpu(1, 3.75),
            GCPCustomInstanceType.gpu(1, 3.75, 1, "K80")
    );

    @Before
    public void initGCPPreferences() {
        doReturn(Collections.singletonList(validRegionId())).when(preferenceManager)
                .getPreference(SystemPreferences.GCP_REGION_LIST);
    }

    @Override
    AbstractCloudRegion commonRegion() {
        final GCPRegion region = new GCPRegion();
        region.setId(ID);
        region.setName(REGION_NAME);
        region.setRegionCode(validRegionId());
        region.setOwner("owner");
        region.setCreatedDate(new Date());
        region.setProject(GCP_PROJECT);
        region.setAuthFile(SSH_PUB_PATH);
        region.setImpersonatedAccount(IMPERSONATED_ACCOUNT);
        region.setCustomInstanceTypes(CUSTOM_INSTANCE_TYPES);
        return region;
    }

    @Override
    GCPRegionDTO createRegionDTO() {
        final GCPRegionDTO gcpRegionDTO = updateRegionDTO();
        gcpRegionDTO.setRegionCode(validRegionId());
        gcpRegionDTO.setSshPublicKeyPath(SSH_PUB_PATH);
        gcpRegionDTO.setProject(GCP_PROJECT);
        gcpRegionDTO.setCustomInstanceTypes(CUSTOM_INSTANCE_TYPES);
        return gcpRegionDTO;
    }

    @Override
    GCPRegionDTO updateRegionDTO() {
        final GCPRegionDTO gcpRegionDTO = new GCPRegionDTO();
        gcpRegionDTO.setName(REGION_NAME);
        gcpRegionDTO.setProject(GCP_PROJECT_CHANGED);
        gcpRegionDTO.setSshPublicKeyPath(SSH_PUB_PATH);
        gcpRegionDTO.setProvider(CloudProvider.GCP);
        gcpRegionDTO.setImpersonatedAccount(IMPERSONATED_ACCOUNT);
        gcpRegionDTO.setCustomInstanceTypes(CUSTOM_INSTANCE_TYPES);
        return gcpRegionDTO;
    }

    @Override
    AbstractCloudRegionCredentials credentials() {
        return null;
    }

    @Override
    String validRegionId() {
        return "us-central1";
    }

    @Override
    void assertRegionEquals(final AbstractCloudRegion expectedRegion, final AbstractCloudRegion actualRegion) {
        assertThat(actualRegion, instanceOf(GCPRegion.class));
        final GCPRegion expectedGcpRegion = (GCPRegion) expectedRegion;
        final GCPRegion actualGcpRegion = (GCPRegion) actualRegion;
        assertThat(expectedGcpRegion.getRegionCode(), is(actualGcpRegion.getRegionCode()));
        assertThat(expectedGcpRegion.getName(), is(actualGcpRegion.getName()));
        assertThat(expectedGcpRegion.getAuthFile(), is(actualGcpRegion.getAuthFile()));
        assertThat(expectedGcpRegion.getProject(), is(actualGcpRegion.getProject()));
        assertThat(expectedGcpRegion.getSshPublicKeyPath(), is(actualGcpRegion.getSshPublicKeyPath()));
        assertThat(expectedGcpRegion.getCustomInstanceTypes(), is(actualGcpRegion.getCustomInstanceTypes()));
    }

    @Override
    List<CloudRegionHelper> helpers() {
        return Collections.singletonList(new GCPRegionHelper(messageHelper, preferenceManager));
    }

    @Override
    CloudProvider defaultProvider() {
        return CloudProvider.GCP;
    }
}
