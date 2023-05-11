/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.log;

import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogPagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogApiService {

    @Autowired
    private LogManager logManager;

    @PreAuthorize("hasRole('ADMIN')")
    public LogPagination filter(final LogFilter logFilter) {
        return logManager.filter(logFilter);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public LogFilter getFilters() {
        return logManager.getFilters();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void save(final List<LogEntry> logEntries) {
        logManager.save(logEntries);
    }
}
