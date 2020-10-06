/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
 *
 */

package com.epam.pipeline.manager.user;

import com.epam.pipeline.controller.vo.PipelineUserExportVO;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.user.PipelineUserWithStoragePath;
import com.epam.pipeline.entity.user.Role;
import com.opencsv.CSVWriter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

public class UserExporter {

    private static final String LIST_DELIMITER = "|";
    private static final DateTimeFormatter USER_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final String SPACE = " ";

    public String exportUsers(final PipelineUserExportVO exportSettings,
                              final Collection<PipelineUserWithStoragePath> users) {
        final StringWriter writer = new StringWriter();
        final List<String> attributeNames = CollectionUtils.emptyIfNull(users).stream()
                .map(user -> MapUtils.emptyIfNull(user.getAttributes()).keySet())
                .flatMap(Collection::stream)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        final List<String> metadataColumns = ListUtils.emptyIfNull(exportSettings.getMetadataColumns())
                .stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        final String[] csvHeader = getColumnMapping(exportSettings, attributeNames, metadataColumns);

        final CSVWriter csvWriter = new CSVWriter(writer);
        if (exportSettings.isIncludeHeader()) {
            csvWriter.writeNext(csvHeader, false);
        }
        CollectionUtils.emptyIfNull(users).stream()
                .map(user -> userToLine(user, attributeNames, exportSettings, metadataColumns))
                .forEach(line -> csvWriter.writeNext(line, false));
        return writer.toString();
    }


    private String[] getColumnMapping(final PipelineUserExportVO exportSettings,
                                      final List<String> attributeNames,
                                      final List<String> metadataColumns) {
        final List<String> result = new ArrayList<>();
        if (exportSettings.isIncludeId()) {
            result.add(PipelineUserWithStoragePath.PipelineUserFields.ID.getValue());
        }
        if (exportSettings.isIncludeUserName()) {
            result.add(PipelineUserWithStoragePath.PipelineUserFields.USER_NAME.getValue());
        }
        if (exportSettings.isIncludeAttributes()) {
            result.addAll(ListUtils.emptyIfNull(attributeNames));
        }
        if (CollectionUtils.isNotEmpty(metadataColumns)) {
            result.addAll(metadataColumns);
        }
        if (exportSettings.isIncludeRegistrationDate()) {
            result.add(PipelineUserWithStoragePath.PipelineUserFields.REGISTRATION_DATE.getValue()
                    + SPACE + TimeZone.getDefault().toZoneId());
        }
        if (exportSettings.isIncludeFirstLoginDate()) {
            result.add(PipelineUserWithStoragePath.PipelineUserFields.FIRST_LOGIN_DATE.getValue()
                    + SPACE + TimeZone.getDefault().toZoneId());
        }
        if (exportSettings.isIncludeRoles()) {
            result.add(PipelineUserWithStoragePath.PipelineUserFields.ROLES.getValue());
        }
        if (exportSettings.isIncludeGroups()) {
            result.add(PipelineUserWithStoragePath.PipelineUserFields.GROUPS.getValue());
        }
        if (exportSettings.isIncludeStatus()) {
            result.add(PipelineUserWithStoragePath.PipelineUserFields.BLOCKED.getValue());
        }
        if (exportSettings.isIncludeDataStorage()) {
            result.add(PipelineUserWithStoragePath.PipelineUserFields.DEFAULT_STORAGE_ID.getValue());
            result.add(PipelineUserWithStoragePath.PipelineUserFields.DEFAULT_STORAGE_PATH.getValue());
        }
        return result.toArray(new String[0]);
    }

    private String[] userToLine(final PipelineUserWithStoragePath user,
                                final List<String> attributeNames,
                                final PipelineUserExportVO exportSettings,
                                final List<String> metadataColumns) {
        final List<String> result = new ArrayList<>();
        if (exportSettings.isIncludeId()) {
            result.add(String.valueOf(user.getId()));
        }
        if (exportSettings.isIncludeUserName()) {
            result.add(user.getUserName());
        }
        if (exportSettings.isIncludeAttributes()) {
            result.addAll(ListUtils.emptyIfNull(attributeNames)
                    .stream()
                    .map(attribute -> {
                        final Map<String, String> userAttributes = MapUtils.emptyIfNull(user.getAttributes());
                        return userAttributes.getOrDefault(attribute, StringUtils.EMPTY);
                    })
                    .collect(Collectors.toList()));
        }
        if (CollectionUtils.isNotEmpty(metadataColumns)) {
            result.addAll(metadataColumns
                    .stream()
                    .map(metadataKey -> {
                        final Map<String, PipeConfValue> metadata = MapUtils.emptyIfNull(user.getMetadata());
                        return Optional.ofNullable(metadata.get(metadataKey))
                                .map(value -> StringUtils.defaultIfBlank(value.getValue(), StringUtils.EMPTY))
                                .orElse(StringUtils.EMPTY);
                    })
                    .collect(Collectors.toList()));
        }
        if (exportSettings.isIncludeRegistrationDate()) {
            result.add(formatDate(user.getRegistrationDate()));
        }
        if (exportSettings.isIncludeFirstLoginDate()) {
            result.add(formatDate(user.getFirstLoginDate()));
        }
        if (exportSettings.isIncludeRoles()) {
            result.add(ListUtils.emptyIfNull(user.getRoles()).stream()
                    .map(Role::getName)
                    .collect(Collectors.joining(LIST_DELIMITER)));
        }
        if (exportSettings.isIncludeGroups()) {
            result.add(String.join(LIST_DELIMITER, ListUtils.emptyIfNull(user.getGroups())));
        }
        if (exportSettings.isIncludeStatus()) {
            result.add(String.valueOf(user.isBlocked()));
        }
        if (exportSettings.isIncludeDataStorage()) {
            result.add(formatNullable(user.getDefaultStorageId()));
            result.add(formatNullable(user.getDefaultStoragePath()));
        }
        return result.toArray(new String[0]);
    }

    private String formatNullable(final Object object) {
        return Optional.ofNullable(object)
                .map(String::valueOf)
                .orElse(StringUtils.EMPTY);
    }

    private String formatDate(final LocalDateTime date) {
        return Optional.ofNullable(date)
                .map(USER_DATE_FORMATTER::format)
                .orElse(StringUtils.EMPTY);
    }
}
