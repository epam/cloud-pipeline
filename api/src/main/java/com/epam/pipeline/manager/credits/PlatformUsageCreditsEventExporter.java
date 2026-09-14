/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.manager.credits;

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateEvent;
import com.epam.pipeline.vo.SecuredEntityVO;
import org.apache.commons.lang.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PlatformUsageCreditsEventExporter {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(Constants.EXPORT_DATE_TIME_FORMAT);

    public String[] header() {
        return new String[]{"Timestamp", "User ID", "Rule ID", "Entity Class", "Entity ID", "Type", "Value", "Message"};
    }

    public List<String[]> lines(final List<PlatformUsageCreditsUpdateEvent> events) {
        return events.stream().map(this::toLine).collect(Collectors.toList());
    }

    private String[] toLine(final PlatformUsageCreditsUpdateEvent event) {
        final SecuredEntityVO entity = event.getEntity();
        return new String[]{
            Optional.ofNullable(event.getCreatedDate()).map(DATE_FORMATTER::format).orElse(StringUtils.EMPTY),
            formatNullable(event.getUserId()),
            formatNullable(event.getRuleId()),
            entity != null ? StringUtils.defaultString(entity.getEntityClass()) : StringUtils.EMPTY,
            entity != null ? String.valueOf(entity.getEntityId()) : StringUtils.EMPTY,
            event.getIncidentType() != null ? event.getIncidentType().name() : StringUtils.EMPTY,
            String.valueOf(event.getValue()),
            StringUtils.defaultString(event.getMessage())
        };
    }

    private String formatNullable(final Object value) {
        return value != null ? String.valueOf(value) : StringUtils.EMPTY;
    }
}
