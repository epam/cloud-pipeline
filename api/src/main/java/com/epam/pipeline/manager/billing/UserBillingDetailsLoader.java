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

package com.epam.pipeline.manager.billing;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.utils.CommonUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class UserBillingDetailsLoader implements EntityBillingDetailsLoader {

    @Getter
    private final BillingGrouping grouping = BillingGrouping.USER;

    private final UserManager userManager;
    private final MetadataManager metadataManager;
    private final MessageHelper messageHelper;
    private final String emptyValue;
    private final String billingCenterKey;

    public UserBillingDetailsLoader(final UserManager userManager,
                                    final MetadataManager metadataManager,
                                    final MessageHelper messageHelper,
                                    @Value("${billing.empty.report.value:unknown}")
                                    final String emptyValue,
                                    @Value("${billing.center.key}")
                                    final String billingCenterKey) {
        this.userManager = userManager;
        this.metadataManager = metadataManager;
        this.messageHelper = messageHelper;
        this.emptyValue = emptyValue;
        this.billingCenterKey = billingCenterKey;
    }

    @Override
    public Map<String, String> loadInformation(final String id, final boolean loadDetails,
                                               final Map<String, String> defaults) {
        final Optional<PipelineUser> user = load(id);
        final Map<String, String> details = new HashMap<>();
        details.put(NAME, id);
        details.put(BILLING_CENTER, CommonUtils.first(defaults, "owner_billing_center", "billing_center")
                .map(Optional::of)
                .orElseGet(() -> getUserBillingCenter(id))
                .orElse(emptyValue));
        details.put(IS_DELETED, Boolean.toString(!user.isPresent()));
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

    public Optional<String> getUserBillingCenter(final String id) {
        return load(id).map(user -> BillingUtils.getUserBillingCenter(user, billingCenterKey, metadataManager));
    }
}
