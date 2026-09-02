/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.performancemonitoring.monitor;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractRunMonitor implements RunMonitor {

    protected final MessageHelper messageHelper;
    protected final PreferenceManager preferenceManager;

    protected AbstractRunMonitor(final MessageHelper messageHelper, final PreferenceManager preferenceManager) {
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
    }

    protected Map<String, PipelineRun> groupedByNode(final List<PipelineRun> runs) {
        return runs.stream()
                .filter(r -> {
                    final boolean hasNodeName = Objects.nonNull(r.getInstance())
                            && Objects.nonNull(r.getInstance().getNodeName());
                    if (!hasNodeName) {
                        log.debug(messageHelper.getMessage(
                                MessageConstants.DEBUG_RUN_HAS_NOT_NODE_NAME, r.getId()));
                    }
                    return hasNodeName;
                })
                .collect(Collectors.toMap(r -> r.getInstance().getNodeName(), r -> r));
    }

    protected String getTimestampTag(final String tag) {
        final String suffix = preferenceManager.getPreference(SystemPreferences.SYSTEM_RUN_TAG_DATE_SUFFIX);
        return StringUtils.isNotEmpty(suffix) ? tag + suffix : null;
    }

    protected LocalDateTime readTimestampTag(final PipelineRun run, final String tag) {
        final String timestampTag = getTimestampTag(tag);
        final String date = MapUtils.emptyIfNull(run.getTags()).get(timestampTag);
        if (StringUtils.isBlank(date)) {
            return null;
        }
        try {
            return DateUtils.strToUTCDate(date);
        } catch (DateTimeParseException e) {
            log.error("Failed to parse timestamp tag {} for run {}: {}", timestampTag, run.getId(), date, e);
            return null;
        }
    }

    protected boolean actionTimeoutElapsed(final PipelineRun run, final String tag, final int actionTimeoutMinutes) {
        final LocalDateTime timestamp = readTimestampTag(run, tag);
        return timestamp != null && timestamp.isBefore(DateUtils.nowUTC().minusMinutes(actionTimeoutMinutes));
    }
}
