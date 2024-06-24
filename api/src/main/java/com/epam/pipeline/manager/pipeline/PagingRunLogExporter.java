/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
 *  limitations under the License.
 */

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.controller.vo.run.OffsetPagingFilter;
import com.epam.pipeline.controller.vo.run.OffsetPagingOrder;
import com.epam.pipeline.dao.pipeline.RunLogDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.exception.pipeline.RunLogException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.utils.LogsFormatter;
import com.epam.pipeline.utils.StreamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PagingRunLogExporter implements RunLogExporter {

    private final RunLogDao runLogDao;
    private final PreferenceManager preferenceManager;

    private final LogsFormatter logsFormatter = new LogsFormatter();

    public void export(final PipelineRun run, final Writer writer) {
        log.info("Exporting run logs #{}...", run.getId());
        try {
            logs(run).forEach(entry -> write(writer, entry));
        } finally {
            try {
                writer.flush();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    private void write(final Writer writer, final RunLog entry) {
        try {
            writer.write(logsFormatter.formatLog(entry));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RunLogException(e.getMessage(), e);
        }
    }

    private Stream<RunLog> logs(final PipelineRun run) {
        final int limit = getRunLogDefaultLimit();
        return StreamUtils.takeWhile(logsPages(run, limit), page -> page.size() >= limit)
                .peek(page -> log.info("Writing run logs #{} ({} lines)...",
                        run.getId(), page.size()))
                .flatMap(List::stream);
    }

    private Stream<List<RunLog>> logsPages(final PipelineRun run, final int limit) {
        return Stream.iterate(0, offset -> offset + limit)
                .map(offset -> new OffsetPagingFilter(offset, limit, OffsetPagingOrder.ASC))
                .peek(filter -> log.info("Reading run logs #{} ({} lines starting from {})...",
                        run.getId(), filter.getLimit(), filter.getOffset()))
                .map(filter -> runLogDao.loadLogsForRun(run.getId(), filter));
    }

    private int getRunLogDefaultLimit() {
        return preferenceManager.findPreference(SystemPreferences.SYSTEM_LIMIT_LOG_LINES)
                .orElseGet(SystemPreferences.SYSTEM_LIMIT_LOG_LINES::getDefaultValue);
    }
}
