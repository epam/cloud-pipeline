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

package com.epam.pipeline.dao.cluster;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.cluster.InstanceType;
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

public class InstanceOfferDaoTest extends AbstractSpringTest  {

    private static final String INSTANCE_TYPE = "instanceType1";
    private static final String ANOTHER_INSTANCE_TYPE = "instanceType2";
    private static final Long REGION_ID = 1L;
    private static final Long ANOTHER_REGION_ID = 2L;
    private static final String SKU = "sku";
    private static final Date PUBLISH_DATE = new Date();
    private static final int CPU = 2;
    private static final float MEMORY = 8;

    @Autowired
    private InstanceOfferDao instanceOfferDao;

    @Before
    public void setUp() throws Exception {
        final List<InstanceOffer> instanceOffers = new ArrayList<>();
        instanceOffers.add(offer(REGION_ID, INSTANCE_TYPE));
        instanceOffers.add(offer(ANOTHER_REGION_ID, INSTANCE_TYPE));
        instanceOffers.add(offer(ANOTHER_REGION_ID, ANOTHER_INSTANCE_TYPE));
        instanceOfferDao.insertInstanceOffers(instanceOffers);
    }

    @After
    public void tearDown() {
        instanceOfferDao.removeInstanceOffers();
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadInstanceTypesShouldReturnAvailableInstancesInSpecifiedRegion() {
        final List<InstanceType> instanceTypes = instanceOfferDao.loadInstanceTypes(offerRequest(REGION_ID));

        assertThat(instanceTypes.size(), is(1));
        final InstanceType instanceType = instanceTypes.get(0);
        assertThat(instanceType.getName(), is(INSTANCE_TYPE));
        assertThat(instanceType.getRegionId(), is(REGION_ID));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void loadInstanceTypesShouldReturnNoInstancesIfRequiredInstancesAreNotAvailableInSpecifiedRegion() {
        final List<InstanceType> instanceTypes = instanceOfferDao.loadInstanceTypes(offerRequest(REGION_ID,
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
        assertThat(instanceType.getRegionId(), is(REGION_ID));
        final InstanceType instanceType1 = instanceTypes.get(1);
        assertThat(instanceType1.getName(), is(INSTANCE_TYPE));
        assertThat(instanceType1.getRegionId(), is(ANOTHER_REGION_ID));
        final InstanceType instanceType2 = instanceTypes.get(2);
        assertThat(instanceType2.getName(), is(ANOTHER_INSTANCE_TYPE));
        assertThat(instanceType2.getRegionId(), is(ANOTHER_REGION_ID));
    }

    private InstanceOffer offer(final Long regionId, final String instanceType) {
        final InstanceOffer offer = new InstanceOffer();
        offer.setPriceListPublishDate(PUBLISH_DATE);
        offer.setSku(SKU);
        offer.setVCPU(CPU);
        offer.setMemory(MEMORY);
        offer.setInstanceType(instanceType);
        offer.setRegionId(regionId);
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
