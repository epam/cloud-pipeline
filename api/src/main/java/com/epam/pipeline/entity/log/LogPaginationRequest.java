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

package com.epam.pipeline.entity.log;

import lombok.Builder;
import lombok.Data;

/**
 * Class that contains information about ElasticSearch result page parameters,
 * and also about navigation point and direction.
 * pageSize - number of entries to be sent back as a search response
 * forward - {@code true} if we should search for the next page of result
 * token - {@link LogEntry} object that represent edge of last search regarding this request
 * f.e. 1. We loaded first page and now send a request for the second one:
 *         forward is true, token - last entry from previous search
 *      2. We loaded second page and now send a request for the first one:
 *         forward is false, token - first entry previous search
 * */
@Data
@Builder
public class LogPaginationRequest {
    private LogEntry token;
    private Integer pageSize;
    private Boolean forward;
}
