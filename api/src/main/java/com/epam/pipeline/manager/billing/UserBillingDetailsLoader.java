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

import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.user.UserManager;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserBillingDetailsLoader implements EntityBillingDetailsLoader {

    private final String emptyValue;
    private final String billingCenterKey;
    private final UserManager userManager;
    private final MetadataManager metadataManager;

    public UserBillingDetailsLoader(
            @Value("${billing.empty.report.value:unknown}") final String emptyValue,
            @Value("${billing.center.key}") final String billingCenterKey,
            final UserManager userManager,
            final MetadataManager metadataManager) {
        this.emptyValue = emptyValue;
        this.billingCenterKey = billingCenterKey;
        this.userManager = userManager;
        this.metadataManager = metadataManager;
    }

    @Override
    public BillingGrouping getGrouping() {
        return BillingGrouping.USER;
    }

    @Override
    public Map<String, String> loadDetails(final String entityIdentifier) {
        final Map<String, String> details = new HashMap<>();
        final PipelineUser user = userManager.loadUserByName(entityIdentifier);
        if (user != null) {
            final String billingCenter = Optional.ofNullable(
                    metadataManager.loadMetadataItem(user.getId(), AclClass.PIPELINE_USER))
                    .map(MetadataEntry::getData)
                    .filter(MapUtils::isNotEmpty)
                    .flatMap(attributes -> Optional.ofNullable(attributes.get(billingCenterKey)))
                    .flatMap(value -> Optional.ofNullable(value.getValue()))
                    .orElse(emptyValue);
            details.put(BillingGrouping.BILLING_CENTER.getCorrespondingField(), billingCenter);
        } else {
            details.putAll(getEmptyDetails());
        }
        return details;
    }

    @Override
    public Map<String, String> getEmptyDetails() {
        return Collections.singletonMap(BillingGrouping.BILLING_CENTER.getCorrespondingField(), emptyValue);
    }
}
