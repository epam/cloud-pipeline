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

package com.epam.pipeline.manager.log;

import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogPagination;
import com.epam.pipeline.entity.log.LogPaginationRequest;
import com.epam.pipeline.entity.utils.DateUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LogManager {

    private static final String[] USERS = new String[]{"user", "pipe_admin"};
    private static final Random RANDOM = new Random();

    public Collection<LogEntry> filter(LogFilter logFilter) {
        return generateStub(logFilter);
    }

    private Collection<LogEntry> generateStub(LogFilter logFilter) {
        final LogPaginationRequest pagination = logFilter.getPagination() == null ?
                LogPaginationRequest.builder().token(1L).pageSize(100L).build()
                : logFilter.getPagination() ;
        final long pageSize = pagination.getPageSize();
        return Stream.generate(
                () -> LogEntry.builder()
                        .hostname("cp-api-srv.internal")
                        .message(getMessage())
                        .messageTimestamp(DateUtils.nowUTC())
                        .timestamp(DateUtils.nowUTC())
                        .serviceName("api-srv")
                        .source("/opt/api/log/security.json")
                        .type("Security")
                        .user(getUser())
                        .severity("INFO")
                        .logger("JwtFilterAuthenticationFilter")
                        .build()
        ).limit(pageSize).collect(Collectors.toList());
    }

    private String getMessage() {
        return "Message " + RANDOM.nextInt();
    }

    private String getUser() {
        return USERS[RANDOM.nextInt(USERS.length -1)];
    }
}
