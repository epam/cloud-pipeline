/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.cluster;

import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class InstanceOfferDaoTest extends AbstractJdbcTest {

    private static final String INSTANCE_TYPE = "instanceType1";
    private static final String ANOTHER_INSTANCE_TYPE = "instanceType2";
    private static final String SKU = "sku";
    private static final Date PUBLISH_DATE = new Date();
    private static final int CPU = 2;
    private static final float MEMORY = 8;

    @Autowired
    private InstanceOfferDao instanceOfferDao;

    @Autowired
    private CloudRegionDao cloudRegionDao;

    private AbstractCloudRegion region;

    @Before
    public void setUp() throws Exception {

        region = createRegion("region1");
        final AbstractCloudRegion anotherRegion = createRegion("region2");

        final List<InstanceOffer> instanceOffers = new ArrayList<>();
        cloudRegionDao.create(region);
        instanceOffers.add(offer(region.getId(), INSTANCE_TYPE));
        instanceOffers.add(offer(anotherRegion.getId(), INSTANCE_TYPE));
        instanceOffers.add(offer(anotherRegion.getId(), ANOTHER_INSTANCE_TYPE));
        instanceOfferDao.insertInstanceOffers(instanceOffers);
    }

    private AbstractCloudRegion createRegion(final String name) {
        AwsRegion tmp = new AwsRegion();
        tmp.setProvider(CloudProvider.AWS);
        tmp.setName(name);
        tmp.setRegionCode(name);
        return cloudRegionDao.create(tmp);
    }

    @After
    public void tearDown() {
        instanceOfferDao.removeInstanceOffers();
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadInstanceTypesShouldReturnAvailableInstancesInSpecifiedRegion() {
        final List<InstanceType> instanceTypes = instanceOfferDao.loadInstanceTypes(offerRequest(region.getId()));

        assertThat(instanceTypes.size(), is(1));
        final InstanceType instanceType = instanceTypes.get(0);
        assertThat(instanceType.getName(), is(INSTANCE_TYPE));
        assertThat(instanceType.getRegionId(), is(region.getId()));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadInstanceTypesShouldReturnNoInstancesIfRequiredInstancesAreNotAvailableInSpecifiedRegion() {
        final List<InstanceType> instanceTypes = instanceOfferDao.loadInstanceTypes(offerRequest(region.getId(),
                ANOTHER_INSTANCE_TYPE));

        assertThat(instanceTypes.size(), is(0));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadInstanceTypesShouldReturnAvailableInstancesForAllRegionsIfNoRegionIsSpecified() {
        final List<InstanceType> instanceTypes = instanceOfferDao.loadInstanceTypes(offerRequest());

        assertThat(instanceTypes.size(), is(3));
        final InstanceType instanceType = instanceTypes.get(0);
        assertThat(instanceType.getName(), is(INSTANCE_TYPE));
        final InstanceType instanceType1 = instanceTypes.get(1);
        assertThat(instanceType1.getName(), is(INSTANCE_TYPE));
        final InstanceType instanceType2 = instanceTypes.get(2);
        assertThat(instanceType2.getName(), is(ANOTHER_INSTANCE_TYPE));
    }

    private InstanceOffer offer(final Long regionId, final String instanceType) {
        final InstanceOffer offer = new InstanceOffer();
        offer.setPriceListPublishDate(PUBLISH_DATE);
        offer.setSku(SKU);
        offer.setVCPU(CPU);
        offer.setMemory(MEMORY);
        offer.setInstanceType(instanceType);
        offer.setRegionId(regionId);
        offer.setCloudProvider(CloudProvider.AWS);
        return offer;
    }

    private InstanceOfferRequestVO offerRequest() {
        return offerRequest(null);
    }

    private InstanceOfferRequestVO offerRequest(final Long regionId) {
        return offerRequest(regionId, null);
    }

    private InstanceOfferRequestVO offerRequest(final Long regionId, final String instanceType) {
        final InstanceOfferRequestVO request = new InstanceOfferRequestVO();
        request.setInstanceType(instanceType);
        request.setRegionId(regionId);
        return request;
    }

}
