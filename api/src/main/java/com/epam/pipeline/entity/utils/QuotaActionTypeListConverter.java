/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.utils;

import com.epam.pipeline.dto.quota.QuotaActionType;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.AttributeConverter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class QuotaActionTypeListConverter implements AttributeConverter<List<QuotaActionType>, String> {
    private static final String DELIMITER = ",";

    @Override
    public String convertToDatabaseColumn(final List<QuotaActionType> attribute) {
        return ListUtils.emptyIfNull(attribute).stream()
                .map(QuotaActionType::name)
                .collect(Collectors.joining(DELIMITER));
    }

    @Override
    public List<QuotaActionType> convertToEntityAttribute(final String dbData) {
        if (StringUtils.isBlank(dbData)) {
            return Collections.emptyList();
        }
        return Arrays.stream(dbData.split(DELIMITER))
                .filter(StringUtils::isNotBlank)
                .map(QuotaActionType::valueOf)
                .collect(Collectors.toList());
    }
}
