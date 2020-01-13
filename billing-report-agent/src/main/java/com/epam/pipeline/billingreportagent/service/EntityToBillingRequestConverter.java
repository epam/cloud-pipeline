/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.billingreportagent.service;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import org.elasticsearch.action.DocWriteRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public interface EntityToBillingRequestConverter<T> {

    String INDEX_TYPE = "_doc";
    String DATE_PATTERN = "yyyy-MM-dd";
    DateTimeFormatter SIMPLE_DATE_FORMAT = DateTimeFormatter.ofPattern(DATE_PATTERN);

    List<DocWriteRequest> convertEntityToRequests(EntityContainer<T> entityContainer,
                                                  String indexName,
                                                  LocalDateTime previousSync,
                                                  LocalDateTime syncStart);

    default List<DocWriteRequest> convertEntitiesToRequests(List<EntityContainer<T>> entityContainers,
                                                            String indexName,
                                                            LocalDateTime previousSync,
                                                            LocalDateTime syncStart) {
        return entityContainers.stream()
            .map(entityContainer -> convertEntityToRequests(entityContainer, indexName, previousSync, syncStart))
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    default String parseDateToString(final LocalDate date) {
        if (date == null) {
            return null;
        }
        return SIMPLE_DATE_FORMAT.format(date);
    }
}
