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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.entity.metadata.CategoricalAttribute;
import com.epam.pipeline.entity.metadata.CategoricalAttributeValue;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.PipelineUserEvent;
import com.epam.pipeline.entity.user.PipelineUserWithStoragePath;
import com.epam.pipeline.entity.user.Role;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Generates users structure according to the given csv file
 */
@RequiredArgsConstructor
public class UserImporter {
    private final List<PipelineUserEvent> events;
    private final List<CategoricalAttribute> currentAttributes;
    private final List<String> attributesToCreate;

    /**
     * Returns List of {@link PipelineUserWithStoragePath}
     * @param file the input csv file. The header should have the following format:
     *             1 column - the user name
     *             2 column - the list of roles that shall be assigned to user (separated by '|' symbol)
     *             remains - the metadata keys
     *             The header example: UserName,Groups,MetadataItem1,MetadataItem2,MetadataItemN
     * @return the list of users specified in the input file
     */
    public List<PipelineUserWithStoragePath> importUsers(final MultipartFile file) {
        final List<String> initialKeys = ListUtils.emptyIfNull(currentAttributes).stream()
                .map(CategoricalAttribute::getKey)
                .collect(Collectors.toList());
        try (CSVParser csvParser = buildCsvParser(file)) {
            final List<String> metadataHeaders = getMetadataHeader(csvParser.getHeaderMap());
            final List<String> metadataKeysToCreate = getMetadataKeysToCreate(metadataHeaders, initialKeys);
            final List<PipelineUserWithStoragePath> users = StreamSupport.stream(csvParser.spliterator(), false)
                    .map(record -> parseRecord(record, metadataHeaders))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            logMetadataEvents(metadataKeysToCreate, initialKeys, metadataHeaders);
            return users;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse users from CSV file", e);
        }
    }

    private PipelineUserWithStoragePath parseRecord(final CSVRecord record, final List<String> metadataHeaders) {
        final String userName = record.get(0);
        if (StringUtils.isBlank(userName)) {
            return null;
        }
        final List<String> roles = parseGroups(record.get(1));
        final PipelineUser pipelineUser = PipelineUser.builder()
                .userName(userName.toUpperCase())
                .roles(buildRoles(roles))
                .build();
        return PipelineUserWithStoragePath.builder()
                .pipelineUser(pipelineUser)
                .metadata(processMetadata(record.toMap(), metadataHeaders))
                .build();
    }

    private Map<String, PipeConfValue> processMetadata(final Map<String, String> valuesByHeader,
                                                       final List<String> metadataHeaders) {
        final Map<String, CategoricalAttribute> attributes = currentAttributes.stream()
                .collect(Collectors.toMap(CategoricalAttribute::getKey, Function.identity()));
        final Map<String, PipeConfValue> userMetadata = new HashMap<>();
        valuesByHeader.entrySet().stream()
                .filter(entry -> StringUtils.isNotBlank(entry.getValue()))
                .filter(entry -> metadataHeaders.contains(entry.getKey()))
                .forEach(entry -> processMetadataItem(attributes, userMetadata, entry.getKey(), entry.getValue()));
        return userMetadata;
    }

    private void processMetadataItem(final Map<String, CategoricalAttribute> attributes,
                                     final Map<String, PipeConfValue> userMetadata,
                                     final String key, final String value) {
        if (attributes.containsKey(key)) {
            final List<CategoricalAttributeValue> values = ListUtils.emptyIfNull(attributes.get(key).getValues());
            if (values.stream().noneMatch(attributeValue -> attributeValue.getValue().equals(value))) {
                values.add(new CategoricalAttributeValue(key, value));
            }
        } else if (CollectionUtils.isNotEmpty(attributesToCreate) && attributesToCreate.contains(key)) {
            final List<CategoricalAttributeValue> attributeValues = new ArrayList<>();
            attributeValues.add(new CategoricalAttributeValue(key, value));
            currentAttributes.add(new CategoricalAttribute(key, attributeValues));
        } else {
            return;
        }
        userMetadata.put(key, new PipeConfValue(null, value));
    }

    private CSVParser buildCsvParser(final MultipartFile file) throws IOException {
        return new CSVParser(new InputStreamReader(file.getInputStream()), CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .withIgnoreHeaderCase()
                .withTrim());
    }

    private List<String> parseGroups(final String rawGroups) {
        return Arrays.stream(rawGroups.split("\\|"))
                .collect(Collectors.toList());
    }

    private List<String> getMetadataHeader(final Map<String, Integer> fullHeader) {
        return fullHeader.entrySet().stream()
                .filter(this::notUserOrGroup)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private boolean notUserOrGroup(final Map.Entry<String, Integer> entry) {
        return entry.getValue() != 0 && entry.getValue() != 1;
    }

    private List<Role> buildRoles(final List<String> roleNames) {
        return roleNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .map(String::toUpperCase)
                .map(this::prepareRoleName)
                .map(Role::new)
                .collect(Collectors.toList());
    }

    private String prepareRoleName(final String rawName) {
        return rawName.startsWith(Role.ROLE_PREFIX) ? rawName : Role.ROLE_PREFIX + rawName;
    }

    private List<String> getMetadataKeysToCreate(final List<String> metadataHeaders, final List<String> initialKeys) {
        return metadataHeaders.stream()
                .filter(header -> initialKeys.stream().noneMatch(key -> Objects.equals(key, header)))
                .collect(Collectors.toList());
    }

    private void logMetadataEvents(final List<String> metadataKeysToCreate, final List<String> alreadyExistKeys,
                                   final List<String> headers) {
        final List<String> attributeKeys = currentAttributes.stream()
                .map(CategoricalAttribute::getKey)
                .collect(Collectors.toList());

        headers.forEach(header -> logMetadataEvent(header, attributeKeys, alreadyExistKeys, metadataKeysToCreate));
    }

    private void logMetadataEvent(final String header, final List<String> attributeKeys,
                                  final List<String> alreadyExistKeys,
                                  final List<String> metadataKeysToCreate) {
        if (metadataKeysToCreate.contains(header) && attributeKeys.contains(header)
                && !alreadyExistKeys.contains(header)) {
            events.add(PipelineUserEvent.info(String.format("A new metadata '%s' will be created.", header)));
        }
    }
}
