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

package com.epam.pipeline.entity.log;

import lombok.Builder;
import lombok.Data;

/**
 * Class that contains information about ElasticSearch result page parameters.
 * pageSize - number of entries to be sent back as a search response
 * token - {@link LogEntry} object that represent start of the current page
 * */
@Data
@Builder
public class LogPaginationRequest {
    private PageMarker token;
    private Integer pageSize;
}
