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

package com.epam.pipeline.manager.billing.detail;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.billing.BillingUtils;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.utils.Lazy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class UserBillingDetailsLoader implements EntityBillingDetailsLoader {

    @Getter
    private final BillingGrouping grouping = BillingGrouping.USER;

    private final UserManager userManager;
    private final MetadataManager metadataManager;
    private final MessageHelper messageHelper;
    private final String emptyValue;
    private final String billingCenterKey;

    @Override
    public Map<String, String> loadInformation(final String id, final boolean loadDetails,
                                               final Map<String, String> defaults) {
        final Lazy<Optional<PipelineUser>> user = Lazy.of(() -> load(id));
        final Map<String, String> details = new HashMap<>(defaults);
        details.computeIfAbsent(NAME, key -> user.get().map(PipelineUser::getUserName).orElse(id));
        if (loadDetails) {
            details.computeIfAbsent(BILLING_CENTER, key -> user.get().map(this::toBillingCenter).orElse(emptyValue));
            details.computeIfAbsent(IS_DELETED, key -> Boolean.toString(!user.get().isPresent()));
        }
        return details;
    }

    private Optional<PipelineUser> load(final String id) {
        final Optional<PipelineUser> user = Optional.ofNullable(userManager.loadUserByName(id));
        if (!user.isPresent()) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_BILLING_ENTITY_FOR_DETAILS_NOT_FOUND,
                    id, getGrouping()));
        }
        return user;
    }

    private String toBillingCenter(final PipelineUser user) {
        return BillingUtils.getUserBillingCenter(user, billingCenterKey, metadataManager);
    }
}
