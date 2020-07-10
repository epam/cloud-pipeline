/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class InstanceOfferSchedulerCore {

    private static final int ONE_DAY = 24;
    private static final int PRICE_LIST_REFRESH_PERIOD = 3 * ONE_DAY;

    private final Lock priceUpdateLock;
    private final MessageHelper messageHelper;
    private final InstanceOfferManager instanceOfferManager;

    public InstanceOfferSchedulerCore(final MessageHelper messageHelper,
                                      final InstanceOfferManager instanceOfferManager) {
        this.messageHelper = messageHelper;
        this.instanceOfferManager = instanceOfferManager;
        this.priceUpdateLock = new ReentrantLock();
    }

    @SchedulerLock(name = "InstanceOfferScheduler_checkAndUpdatePriceList", lockAtMostForString = "PT1H")
    public void checkAndUpdatePriceListIfNecessary() {
        log.debug(messageHelper.getMessage(MessageConstants.DEBUG_INSTANCE_OFFERS_EXPIRATION_CHECK_RUNNING));

        Date publishDate = instanceOfferManager.getPriceListPublishDate();
        if (publishDate == null || isPriceListExpired(publishDate) ||
                instanceOfferManager.getAllowedInstanceTypes().stream()
                        .noneMatch(instanceType -> instanceType.getVCPU() > 0)) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_INSTANCE_OFFERS_EXPIRED));
            updatePriceList();
        }
        log.debug(messageHelper.getMessage(MessageConstants.DEBUG_INSTANCE_OFFERS_EXPIRATION_CHECK_DONE));
    }

    public void updatePriceList() {
        try {
            priceUpdateLock.lock();
            instanceOfferManager.refreshPriceList();
        } finally {
            priceUpdateLock.unlock();
        }
    }

    public void updatePriceList(AbstractCloudRegion region) {
        try {
            priceUpdateLock.lock();
            instanceOfferManager.updatePriceListForRegion(region);
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
