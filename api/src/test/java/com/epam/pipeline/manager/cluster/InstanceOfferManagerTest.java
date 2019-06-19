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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.cluster.InstanceOffer;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

public class InstanceOfferManagerTest extends AbstractManagerTest {

    private static final String M5_INSTANCE_TYPE = "m5.large";
    private static final String X5_INSTANCE_TYPE = "x5.bmw";
    private static final String M5_LARGE_INSTANCE_TYPE = "m5.xlarge";
    private static final String M5_PATTERN = "m5.*";
    private static final String M5_X5_PATTERN = "m5.*,x5.*";
    private static final ContextualPreferenceExternalResource NO_TOOL = null;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private InstanceOfferManager instanceOfferManager;

    @Autowired
    private InstanceOfferDao instanceOfferDao;

    @Autowired
    private CloudRegionDao cloudRegionDao;

    private AwsRegion region;

    @Before
    public void setUp() {
        region = ObjectCreatorUtils.getDefaultAwsRegion();
        cloudRegionDao.create(region);
        instanceOfferDao.insertInstanceOffers(Arrays.asList(makeInstanceOffer(M5_INSTANCE_TYPE),
                                                            makeInstanceOffer(M5_LARGE_INSTANCE_TYPE),
                                                            makeInstanceOffer(X5_INSTANCE_TYPE)));
    }

    private InstanceOffer makeInstanceOffer(String type) {
        InstanceOffer offer = new InstanceOffer();
        offer.setInstanceType(type);
        offer.setTermType("OnDemand");
        offer.setVCPU(1);
        offer.setGpu(1);
        offer.setPricePerUnit(1);
        offer.setMemory(1);
        offer.setSku("sku");
        offer.setPriceListPublishDate(new Date());
        offer.setRegionId(region.getId());
        offer.setCloudProvider(CloudProvider.AWS);
        return offer;
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testUpdateAllowedInstanceTypes() {
        Preference allowedTypesPreference = SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES.toPreference();
        allowedTypesPreference.setValue(M5_PATTERN);
        preferenceManager.update(Collections.singletonList(allowedTypesPreference));

        Assert.assertTrue(instanceOfferManager.isInstanceAllowed(M5_INSTANCE_TYPE, region.getId(), false));
        Assert.assertFalse(instanceOfferManager.isInstanceAllowed(X5_INSTANCE_TYPE, region.getId(), false));

        allowedTypesPreference.setValue(M5_X5_PATTERN);
        preferenceManager.update(Collections.singletonList(allowedTypesPreference));

        Assert.assertTrue(instanceOfferManager.isInstanceAllowed(M5_INSTANCE_TYPE, region.getId(), false));
        Assert.assertTrue(instanceOfferManager.isInstanceAllowed(X5_INSTANCE_TYPE, region.getId(), false));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testUpdateAllowedToolInstanceTypes() {
        final Preference allowedToolTypesPreference =
            SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER.toPreference();
        allowedToolTypesPreference.setValue(M5_PATTERN);
        preferenceManager.update(Collections.singletonList(allowedToolTypesPreference));

        Assert.assertTrue(instanceOfferManager.isToolInstanceAllowed(M5_INSTANCE_TYPE, NO_TOOL, region.getId(), false));
        Assert.assertFalse(instanceOfferManager
                .isToolInstanceAllowed(X5_INSTANCE_TYPE, NO_TOOL, region.getId(), false));

        allowedToolTypesPreference.setValue(M5_X5_PATTERN);
        preferenceManager.update(Collections.singletonList(allowedToolTypesPreference));

        Assert.assertTrue(instanceOfferManager.isToolInstanceAllowed(M5_INSTANCE_TYPE, NO_TOOL, region.getId(), false));
        Assert.assertTrue(instanceOfferManager.isToolInstanceAllowed(X5_INSTANCE_TYPE, NO_TOOL, region.getId(), false));
    }
}
