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
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
@RequiredArgsConstructor
public class InstanceOfferSchedulerCore {

    private static final Duration FALLBACK_INSTANCE_OFFER_EXPIRATION_RATE = Duration.ofDays(3);

    private final Lock priceUpdateLock = new ReentrantLock();
    private final MessageHelper messageHelper;
    private final InstanceOfferManager instanceOfferManager;
    private final PreferenceManager preferenceManager;

    @SchedulerLock(name = "InstanceOfferScheduler_checkAndUpdatePriceList", lockAtMostForString = "PT1H")
    public void checkAndUpdatePriceListIfNecessary() {
        log.debug(messageHelper.getMessage(MessageConstants.DEBUG_INSTANCE_OFFERS_EXPIRATION_CHECK_RUNNING));
        if (isPriceListMissingOrExpired() || isInstanceOfferMissing()) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_INSTANCE_OFFERS_EXPIRED));
            updatePriceList();
        }
        log.debug(messageHelper.getMessage(MessageConstants.DEBUG_INSTANCE_OFFERS_EXPIRATION_CHECK_DONE));
    }

    private boolean isPriceListMissingOrExpired() {
        final Date publishDate = instanceOfferManager.getPriceListPublishDate();
        if (publishDate == null) {
            return true;
        }
        final LocalDateTime publish = DateUtils.convertDateToLocalDateTime(publishDate);
        final LocalDateTime expiration = publish.plus(getExpirationRate());
        final LocalDateTime now = DateUtils.nowUTC();
        return now.isAfter(expiration);
    }

    private Duration getExpirationRate() {
        return Optional.of(SystemPreferences.CLUSTER_INSTANCE_OFFER_EXPIRATION_RATE_HOURS)
                .map(preferenceManager::getPreference)
                .map(Duration::ofHours)
                .orElse(FALLBACK_INSTANCE_OFFER_EXPIRATION_RATE);
    }

    private boolean isInstanceOfferMissing() {
        return instanceOfferManager.getAllowedInstanceTypes()
                .stream()
                .noneMatch(type -> type.getVCPU() > 0);
    }

    public void updatePriceList() {
        withLock(instanceOfferManager::refreshPriceList);
    }

    public void updatePriceList(final AbstractCloudRegion region) {
        withLock(() -> instanceOfferManager.refreshPriceList(region));
    }

    public void updatePriceList(final Long id) {
        withLock(() -> instanceOfferManager.refreshPriceList(id));
    }

    private void withLock(final Runnable runnable) {
        try {
            priceUpdateLock.lock();
            runnable.run();
        } finally {
            priceUpdateLock.unlock();
        }
    }
}
