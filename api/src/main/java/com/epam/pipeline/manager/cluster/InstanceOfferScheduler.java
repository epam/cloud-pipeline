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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A class, that schedules price list updates and checks
 */
@Service
public class InstanceOfferScheduler extends AbstractSchedulingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceOfferScheduler.class);

    private static final int ONE_DAY = 24;
    private static final int PRICE_LIST_REFRESH_PERIOD = 3 * ONE_DAY;

    private Lock priceUpdateLock = new ReentrantLock();

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private InstanceOfferManager instanceOfferManager;

    @PostConstruct
    public void init() {
        scheduleFixedDelay(this::checkAndUpdatePriceListIfNecessary,
                SystemPreferences.CLUSTER_INSTANCE_OFFER_UPDATE_RATE,
                "Instance Offers Expiration Status Check");
    }

    public void checkAndUpdatePriceListIfNecessary() {
        LOGGER.debug(messageHelper.getMessage(MessageConstants.DEBUG_INSTANCE_OFFERS_EXPIRATION_CHECK_RUNNING));

        Date publishDate = instanceOfferManager.getPriceListPublishDate();
        if (publishDate == null || isPriceListExpired(publishDate) ||
                instanceOfferManager.getAllowedInstanceTypes().stream()
                        .noneMatch(instanceType -> instanceType.getVCPU() > 0)) {
            LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_INSTANCE_OFFERS_EXPIRED));
            updatePriceList();
        }
        LOGGER.debug(messageHelper.getMessage(MessageConstants.DEBUG_INSTANCE_OFFERS_EXPIRATION_CHECK_DONE));
    }

    public void updatePriceList() {
        try {
            priceUpdateLock.lock();
            instanceOfferManager.refreshPriceList();
        } finally {
            priceUpdateLock.unlock();
        }
    }

    public void updatePriceList(String awsRegion) {
        try {
            priceUpdateLock.lock();
            instanceOfferManager.updatePriceListForRegion(awsRegion);
            instanceOfferManager.updateOfferedInstanceTypes();
        } finally {
            priceUpdateLock.unlock();
        }
    }

    private boolean isPriceListExpired(final Date publishDate) {
        return DateUtils.now().after(org.apache.commons.lang3.time.DateUtils.addHours(
                publishDate, PRICE_LIST_REFRESH_PERIOD));
    }
}
