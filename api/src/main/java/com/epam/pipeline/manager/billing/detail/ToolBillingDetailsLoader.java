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
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.utils.Lazy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Slf4j
public class ToolBillingDetailsLoader implements EntityBillingDetailsLoader {

    @Getter
    private final BillingGrouping grouping = BillingGrouping.TOOL;

    private final ToolManager toolManager;
    private final MessageHelper messageHelper;
    private final String emptyValue;

    @Override
    public Map<String, String> loadInformation(final String id, final boolean loadDetails,
                                               final Map<String, String> defaults) {
        final Lazy<Optional<Tool>> tool = Lazy.of(() -> load(id));
        final Map<String, String> details = new HashMap<>(defaults);
        details.computeIfAbsent(NAME, key -> tool.get().map(Tool::getName).orElse(id));
        if (loadDetails) {
            details.computeIfAbsent(OWNER, key -> tool.get().map(Tool::getOwner).orElse(emptyValue));
            details.computeIfAbsent(CREATED, key -> tool.get().map(Tool::getCreatedDate)
                    .map(DateUtils::convertDateToLocalDateTime)
                    .map(DateTimeFormatter.ISO_DATE_TIME::format)
                    .orElse(emptyValue));
            details.computeIfAbsent(IS_DELETED, key -> Boolean.toString(!tool.get().isPresent()));
        }
        return details;
    }

    private Optional<Tool> load(final String id) {
        try {
            return Optional.of(toolManager.loadByNameOrId(id));
        } catch (RuntimeException e) {
            log.info(messageHelper.getMessage(MessageConstants.INFO_BILLING_ENTITY_FOR_DETAILS_NOT_FOUND,
                    id, getGrouping()));
            return Optional.empty();
        }
    }
}
