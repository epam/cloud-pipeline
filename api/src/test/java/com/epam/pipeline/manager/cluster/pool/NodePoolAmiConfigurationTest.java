/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.dao.cluster.pool.NodePoolDao;
import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.InstanceImage;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * A pool's own launch configuration: stored with the pool, kept through edits that do not carry it, and only
 * accepted where the region's networks would actually let its nodes launch.
 */
@Transactional
public class NodePoolAmiConfigurationTest extends AbstractManagerTest {

    private static final long REGION_ID = 1L;
    private static final int INSTANCE_DISK = 50;
    private static final String REGION_CODE = "eu-central-1";
    private static final String ZONE_A = "eu-central-1a";
    private static final String ZONE_B = "eu-central-1b";
    private static final String ZONE_UNCONFIGURED = "eu-central-1c";
    private static final String SUBNET_A = "subnet-a";
    private static final String SUBNET_B = "subnet-b";
    private static final String OWNER = "pool-owner";
    private static final String ADMIN = "ADMIN";
    private static final String AMI = "ami-0123456789abcdef0";
    private static final String WINDOWS_AMI = "ami-windows";
    private static final String NETWORKS = "{\"regions\": [{\"name\": \"" + REGION_CODE + "\", \"networks\": {\""
            + ZONE_A + "\": \"" + SUBNET_A + "\", \"" + ZONE_B + "\": \"" + SUBNET_B + "\"}}]}";

    @Autowired
    private NodePoolManager poolManager;

    @Autowired
    private NodePoolDao poolDao;

    @Autowired
    private PreferenceManager preferenceManager;

    @MockBean
    private CloudRegionManager regionManager;

    @MockBean
    private InstanceOfferManager instanceOfferManager;

    /**
     * Checking an image's platform is a question for the cloud, answered here without one - the test application
     * already replaces the facade with a mock.
     */
    @Autowired
    private CloudFacade cloudFacade;

    @Before
    public void setUp() {
        final AwsRegion region = new AwsRegion();
        region.setId(REGION_ID);
        region.setRegionCode(REGION_CODE);
        when(regionManager.load(any())).thenReturn((AbstractCloudRegion) region);
        when(instanceOfferManager.isPriceTypeAllowed(any())).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(any(), any(), anyBoolean())).thenReturn(true);
        when(cloudFacade.getInstanceImageDescription(any(), any(String.class)))
                .thenAnswer(invocation -> WINDOWS_AMI.equals(invocation.getArguments()[1])
                        ? image(WINDOWS_AMI, "windows")
                        : image(AMI, "linux"));
        setNetworks(NETWORKS);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldStoreThePoolsConfigurationAndLaunchFromItsImage() {
        final NodePool created = poolManager.create(pool(configuration(ZONE_A, SUBNET_A)));

        final NodePool loaded = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        assertThat(loaded.getAmiConfiguration()).isEqualTo(configuration(ZONE_A, SUBNET_A));
        assertThat(loaded.toRunInstance().getNodeImage()).isEqualTo(AMI);
    }

    /**
     * Editors and the autoscaler that know nothing of the configuration must not erase it.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldKeepTheConfigurationThroughAnEditThatDoesNotCarryIt() {
        final NodePool created = poolManager.create(pool(configuration(ZONE_A, SUBNET_A)));

        poolManager.update(created.getId(), pool(null));

        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getAmiConfiguration())
                .isEqualTo(configuration(ZONE_A, SUBNET_A));
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldReplaceTheConfigurationWithOneAnEditSends() {
        final NodePool created = poolManager.create(pool(configuration(ZONE_A, SUBNET_A)));

        poolManager.update(created.getId(), pool(configuration(ZONE_B, SUBNET_B)));

        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getAmiConfiguration())
                .isEqualTo(configuration(ZONE_B, SUBNET_B));
    }

    /**
     * The launch scripts only put nodes in zones the region's networks configure, so a pool pinned elsewhere could
     * never launch at all.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseAZoneTheRegionDoesNotConfigure() {
        assertThatThrownBy(() -> poolManager.create(pool(configuration(ZONE_UNCONFIGURED, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ZONE_UNCONFIGURED);
    }

    /**
     * A subnet fixes the zone, so one that belongs to another zone - or to none the region configures - contradicts
     * the pool's own zone.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseASubnetThatIsNotTheZonesOwn() {
        assertThatThrownBy(() -> poolManager.create(pool(configuration(ZONE_A, SUBNET_B))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SUBNET_B);
        assertThatThrownBy(() -> poolManager.create(pool(configuration(null, "subnet-unknown"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subnet-unknown");
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldAcceptAnyZoneWhereTheRegionConfiguresNoNetworks() {
        setNetworks("{\"regions\": [{\"name\": \"" + REGION_CODE + "\"}]}");

        final NodePool created = poolManager.create(pool(configuration(ZONE_UNCONFIGURED, "subnet-any")));

        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getAmiConfiguration()
                .getAvailabilityZone()).isEqualTo(ZONE_UNCONFIGURED);
    }

    /**
     * Pools launch Linux nodes only - checked on the image the nodes would actually use, which is now the
     * configuration's.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseAWindowsImageInTheConfiguration() {
        final AMIConfiguration configuration = configuration(ZONE_A, SUBNET_A);
        configuration.setAmi(WINDOWS_AMI);

        assertThatThrownBy(() -> poolManager.create(pool(configuration)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static InstanceImage image(final String id, final String platform) {
        return InstanceImage.builder().imageId(id).name(id).platform(platform).build();
    }

    private void setNetworks(final String json) {
        final Preference preference = SystemPreferences.CLUSTER_NETWORKS_CONFIG.toPreference();
        preference.setValue(json);
        preferenceManager.update(Collections.singletonList(preference));
    }

    private static AMIConfiguration configuration(final String zone, final String subnet) {
        final AMIConfiguration configuration = new AMIConfiguration();
        configuration.setAmi(AMI);
        configuration.setAvailabilityZone(zone);
        configuration.setSubnet(subnet);
        return configuration;
    }

    private static NodePoolVO pool(final AMIConfiguration configuration) {
        final NodePoolVO vo = new NodePoolVO();
        vo.setName("configured pool");
        vo.setRegionId(REGION_ID);
        vo.setInstanceType("m5.large");
        vo.setInstanceDisk(INSTANCE_DISK);
        vo.setPriceType(PriceType.ON_DEMAND);
        vo.setCount(1);
        vo.setAmiConfiguration(configuration);
        return vo;
    }
}
