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
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * A pool's launch configuration: the one a create or update request sends, laid over the region's matching
 * {@code amis} rule at launch - nothing is taken from the rule into the pool; an update that sends none leaves it as it
 * is. Always within what the region's networks allow.
 */
@Transactional
public class NodePoolAmiConfigurationTest extends AbstractManagerTest {

    private static final long REGION_ID = 1L;
    private static final int INSTANCE_DISK = 50;
    private static final String REGION_CODE = "eu-central-1";
    private static final String REGION_NAME = "\"name\": \"" + REGION_CODE + "\"";
    private static final String GIVEN_AMI = "ami-given";
    private static final String ZONE_A = "eu-central-1a";
    private static final String ZONE_B = "eu-central-1b";
    private static final String ZONE_UNCONFIGURED = "eu-central-1c";
    private static final String SUBNET_A = "subnet-a";
    private static final String SUBNET_B = "subnet-b";
    private static final String SUBNET_UNKNOWN = "subnet-unknown";
    private static final String OWNER = "pool-owner";
    private static final String ADMIN = "ADMIN";
    private static final String INSTANCE_TYPE = "m5.large";
    private static final String AMI = "ami-0123456789abcdef0";
    private static final String WINDOWS_AMI = "ami-windows";
    private static final String NETWORKS = "\"networks\": {\"" + ZONE_A + "\": \"" + SUBNET_A + "\", \"" + ZONE_B
            + "\": \"" + SUBNET_B + "\"}";
    /**
     * A rule the pools' instance type matches - which a new pool must not take anything from.
     */
    private static final String AMIS = "\"amis\": [{\"platform\": \"linux\", \"instance_mask\": \"m5.*\", \"ami\": \""
            + AMI + "\", \"init_script\": \"/opt/api/scripts/init_multicloud.sh\"}]";

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

    @BeforeEach
    public void setUp() {
        givenRegion(new AwsRegion());
        when(instanceOfferManager.isPriceTypeAllowed(any())).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(any(), any(), anyBoolean())).thenReturn(true);
        when(cloudFacade.getInstanceImageDescription(any(), any(String.class)))
                .thenAnswer(invocation -> WINDOWS_AMI.equals(invocation.getArguments()[1])
                        ? image(WINDOWS_AMI, "windows")
                        : image(AMI, "linux"));
        setRegion(REGION_NAME + ", " + NETWORKS + ", " + AMIS);
    }

    /**
     * A matching rule is not copied into a new pool: its nodes launch as the region's rules say, and a pool with no
     * image of its own matches a run that asks for none - which would get the rule's image just the same.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldGiveANewPoolNoConfigurationOfItsOwn() {
        final NodePool created = poolManager.create(pool());

        final NodePool loaded = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        assertThat(loaded.getAmiConfiguration()).isNull();
        assertThat(loaded.toRunInstance().getNodeImage()).isNull();
        assertThat(loaded.toRunInstance().requirementsMatch(runAskingForNoImage(), 0)).isTrue();
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    @SuppressWarnings("deprecation")
    public void shouldKeepTheDeprecatedImageAsThePoolsImage() {
        final NodePoolVO vo = pool();
        vo.setInstanceImage(GIVEN_AMI);

        final NodePool loaded = poolDao.find(poolManager.create(vo).getId()).orElseThrow(AssertionError::new);

        assertThat(loaded.getAmiConfiguration()).isNull();
        assertThat(loaded.toRunInstance().getNodeImage()).isEqualTo(GIVEN_AMI);
    }

    /**
     * The deprecated field still names the image: an edit of it reaches a configuration the pool has, rather than
     * being silently outvoted by the image in it.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    @SuppressWarnings("deprecation")
    public void shouldFollowAnEditOfTheDeprecatedImage() {
        final NodePoolVO vo = pool();
        vo.setAmiConfiguration(configuration(ZONE_A, SUBNET_A));
        final NodePool created = poolManager.create(vo);
        final NodePoolVO edit = pool();
        edit.setInstanceImage("ami-edited");

        poolManager.update(created.getId(), edit);

        final NodePool loaded = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        assertThat(loaded.getAmiConfiguration().getAmi()).isEqualTo("ami-edited");
        assertThat(loaded.getAmiConfiguration().getAvailabilityZone()).isEqualTo(ZONE_A);
        assertThat(loaded.toRunInstance().getNodeImage()).isEqualTo("ami-edited");
    }

    /**
     * An edit that sends no configuration - a user's, or the autoscaler's resize - neither replaces nor clears the
     * pool's.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldLeaveTheConfigurationAloneThroughAnEdit() {
        final NodePoolVO vo = pool();
        vo.setAmiConfiguration(configuration(ZONE_A, SUBNET_A));
        final NodePool created = poolManager.create(vo);
        final NodePoolVO edit = pool();
        edit.setCount(2);

        poolManager.update(created.getId(), edit);

        assertThat(configurationOf(created)).isEqualTo(configuration(ZONE_A, SUBNET_A));
    }

    /**
     * The launch scripts only put nodes in zones the region's networks configure, so a pool pinned elsewhere could
     * never launch at all.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseAZoneTheRegionDoesNotConfigure() {
        final NodePool created = poolManager.create(pool());

        assertThatThrownBy(() -> updateWith(created, configuration(ZONE_UNCONFIGURED, null)))
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
        final NodePool created = poolManager.create(pool());

        assertThatThrownBy(() -> updateWith(created, configuration(ZONE_A, SUBNET_B)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SUBNET_B);
        final Throwable refusal = catchThrowable(() ->
                updateWith(created, configuration(null, SUBNET_UNKNOWN)));
        assertThat(refusal).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(SUBNET_UNKNOWN);
        assertThat(refusal.getMessage()).doesNotContain("null");
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldAcceptAnyZoneWhereTheRegionConfiguresNoNetworks() {
        setRegion(REGION_NAME);
        final NodePool created = poolManager.create(pool());

        updateWith(created, configuration(ZONE_UNCONFIGURED, "subnet-any"));

        assertThat(configurationOf(created).getAvailabilityZone()).isEqualTo(ZONE_UNCONFIGURED);
    }

    /**
     * Only AWS regions key their networks by zone, and only the AWS launch applies a pool's zone and subnet.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldNotCheckTheZoneOfAPoolOutsideAws() {
        givenRegion(new AzureRegion());
        final NodePool created = poolManager.create(pool());

        updateWith(created, configuration(ZONE_UNCONFIGURED, null));

        assertThat(configurationOf(created).getAvailabilityZone()).isEqualTo(ZONE_UNCONFIGURED);
    }

    /**
     * Pools launch Linux nodes only - checked on the image the configuration names.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseAWindowsImage() {
        final NodePool created = poolManager.create(pool());
        final AMIConfiguration configuration = configuration(ZONE_A, SUBNET_A);
        configuration.setAmi(WINDOWS_AMI);

        assertThatThrownBy(() -> updateWith(created, configuration))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Like the launch config: a configuration the request sends is the pool's, and nothing is generated for it.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldUseAConfigurationTheCreateRequestSends() {
        final NodePoolVO vo = pool();
        vo.setAmiConfiguration(configuration(ZONE_B, SUBNET_B));

        assertThat(configurationOf(poolManager.create(vo))).isEqualTo(configuration(ZONE_B, SUBNET_B));
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldReplaceTheConfigurationWithOneTheUpdateRequestSends() {
        final NodePool created = poolManager.create(pool());
        final NodePoolVO edit = pool();
        edit.setAmiConfiguration(configuration(ZONE_A, SUBNET_A));

        poolManager.update(created.getId(), edit);

        assertThat(configurationOf(created)).isEqualTo(configuration(ZONE_A, SUBNET_A));
    }

    /**
     * A configuration a create request sends is checked like an update's: a zone the region does not configure could
     * never be launched into.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseARequestedConfigurationWithAZoneTheRegionDoesNotConfigure() {
        final NodePoolVO vo = pool();
        vo.setAmiConfiguration(configuration(ZONE_UNCONFIGURED, null));

        assertThatThrownBy(() -> poolManager.create(vo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ZONE_UNCONFIGURED);
    }

    /**
     * The autoscaler resizes a pool by sending all of it back. A configuration sent back unchanged is not checked
     * again - if the region's networks dropped its zone since, the pool would otherwise stop scaling at all.
     */
    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldAcceptAnUnchangedConfigurationAfterTheRegionsNetworksChanged() {
        final NodePoolVO vo = pool();
        vo.setAmiConfiguration(configuration(ZONE_A, SUBNET_A));
        final NodePool created = poolManager.create(vo);
        setRegion(REGION_NAME + ", \"networks\": {\"" + ZONE_B + "\": \"" + SUBNET_B + "\"}");
        final NodePoolVO resize = pool();
        resize.setAmiConfiguration(configuration(ZONE_A, SUBNET_A));
        resize.setCount(2);

        poolManager.update(created.getId(), resize);

        final NodePool loaded = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        assertThat(loaded.getCount()).isEqualTo(2);
        assertThat(loaded.getAmiConfiguration()).isEqualTo(configuration(ZONE_A, SUBNET_A));
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    @SuppressWarnings("deprecation")
    public void shouldUseTheDeprecatedImageForARequestedConfigurationWithoutOne() {
        final NodePoolVO vo = pool();
        final AMIConfiguration configuration = configuration(ZONE_A, SUBNET_A);
        configuration.setAmi(null);
        vo.setAmiConfiguration(configuration);
        vo.setInstanceImage(GIVEN_AMI);

        assertThat(configurationOf(poolManager.create(vo)).getAmi()).isEqualTo(GIVEN_AMI);
    }

    private NodePool updateWith(final NodePool pool, final AMIConfiguration configuration) {
        final NodePoolVO edit = pool();
        edit.setAmiConfiguration(configuration);
        return poolManager.update(pool.getId(), edit);
    }

    private AMIConfiguration configurationOf(final NodePool pool) {
        return poolDao.find(pool.getId()).orElseThrow(AssertionError::new).getAmiConfiguration();
    }

    private void givenRegion(final AbstractCloudRegion region) {
        region.setId(REGION_ID);
        region.setRegionCode(REGION_CODE);
        when(regionManager.load(any())).thenReturn(region);
    }

    private void setRegion(final String region) {
        final Preference preference = SystemPreferences.CLUSTER_NETWORKS_CONFIG.toPreference();
        preference.setValue("{\"regions\": [{" + region + "}]}");
        preferenceManager.update(Collections.singletonList(preference));
    }

    private static InstanceImage image(final String id, final String platform) {
        return InstanceImage.builder().imageId(id).name(id).platform(platform).build();
    }

    private static AMIConfiguration configuration(final String zone, final String subnet) {
        final AMIConfiguration configuration = new AMIConfiguration();
        configuration.setAmi(AMI);
        configuration.setAvailabilityZone(zone);
        configuration.setSubnet(subnet);
        return configuration;
    }

    /** What a run that names no node image asks for, on the pool's instance type, disk, price type and region. */
    private static RunInstance runAskingForNoImage() {
        final RunInstance instance = new RunInstance();
        instance.setNodeType(INSTANCE_TYPE);
        instance.setNodeDisk(INSTANCE_DISK);
        instance.setEffectiveNodeDisk(INSTANCE_DISK);
        instance.setSpot(false);
        instance.setCloudRegionId(REGION_ID);
        return instance;
    }

    private static NodePoolVO pool() {
        final NodePoolVO vo = new NodePoolVO();
        vo.setName("configured pool");
        vo.setRegionId(REGION_ID);
        vo.setInstanceType(INSTANCE_TYPE);
        vo.setInstanceDisk(INSTANCE_DISK);
        vo.setPriceType(PriceType.ON_DEMAND);
        vo.setCount(1);
        return vo;
    }
}
