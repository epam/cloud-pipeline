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

package com.epam.pipeline.acl.log;

import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogPagination;
import com.epam.pipeline.manager.log.LogManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;
import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_OR_GENERAL_USER;

@Service
@RequiredArgsConstructor
public class LogApiService {

    private final LogManager manager;

    @PreAuthorize(ADMIN_ONLY)
    public LogPagination filter(final LogFilter logFilter) {
        return manager.filter(logFilter);
    }

    @PreAuthorize(ADMIN_ONLY)
    public LogFilter getFilters() {
        return manager.getFilters();
    }

    @PreAuthorize(ADMIN_OR_GENERAL_USER)
    public void save(final List<LogEntry> logEntries) {
        manager.save(logEntries);
    }
}
