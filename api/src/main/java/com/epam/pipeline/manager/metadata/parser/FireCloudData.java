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

package com.epam.pipeline.manager.metadata.parser;

import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.HashMap;

import static com.epam.pipeline.manager.metadata.parser.EntityTypeField.isArrayType;
import static com.epam.pipeline.manager.metadata.parser.EntityTypeField.isReferenceType;

public class FireCloudData {
    private static final String PARTICIPANT_ATTRIBUTE = FireCloudClass.PARTICIPANT.name().toLowerCase();
    private static final String CASE_SAMPLE_ATTRIBUTE = "case_sample";
    private static final String CONTROL_SAMPLE_ATTRIBUTE = "control_sample";
    private static final Pattern ARRAY_VALUE_PATTERN = Pattern.compile("\"(\\w+)\"");
    private static final String LINE_FORMAT = "%s\t%s";
    private static final String MERGE_LINES_FORMAT = "%s%n%s";
    private static final String PUT_MEMBERSHIP_LINE_FORMAT = "%s%n%s\t%s";

    private String content;
    private String membershipContent;
    private List<String> columnNames;

    private FireCloudClass fireCloudClass;

    public FireCloudData(FireCloudClass fireCloudClass, Map<String, PipeConfValue> data) {
        this.fireCloudClass = fireCloudClass;
        columnNames = new ArrayList<>();
        content = fireCloudClass.getHeaderEntityId();
        columnNames = data.entrySet().stream()
                .map(entity -> getAttributeName(entity.getValue().getType(), entity.getKey()))
                .filter(Objects::nonNull)
                .map(attributeName -> {
                    content = String.format(LINE_FORMAT, content, attributeName);
                    return attributeName;
                })
                .collect(Collectors.toList());

        if (isSet()) {
            membershipContent = String.format(LINE_FORMAT, fireCloudClass.getMembershipHeaderEntityId(),
                    fireCloudClass.getMembershipEntity());
        }

        if ((fireCloudClass == FireCloudClass.SAMPLE || fireCloudClass == FireCloudClass.PAIR)
                && !columnNames.contains(PARTICIPANT_ATTRIBUTE)) {
            throw new IllegalArgumentException(String.format("Missing required attribute: %s",
                    PARTICIPANT_ATTRIBUTE));
        }

        if (fireCloudClass == FireCloudClass.PAIR && (!columnNames.contains(CASE_SAMPLE_ATTRIBUTE)
                || !columnNames.contains(CONTROL_SAMPLE_ATTRIBUTE))) {
            throw new IllegalArgumentException(String.format("Missing required attributes: %s, %s",
                    CASE_SAMPLE_ATTRIBUTE, CONTROL_SAMPLE_ATTRIBUTE));
        }
    }

    public void put(MetadataEntity entity) {
        if (entity.getExternalId() == null) {
            throw new IllegalArgumentException(String.format("External ID must be specified for entity ID %s.",
                    entity.getId()));
        }

        if (entity.getClassEntity().getFireCloudClassName() != fireCloudClass) {
            throw new IllegalArgumentException(String.format("Fire Cloud class is invalid: expected %s, but found %s.",
                    fireCloudClass, entity.getClassEntity().getFireCloudClassName()));
        }

        content = String.format(MERGE_LINES_FORMAT, content, entity.getExternalId());

        if (CollectionUtils.isEmpty(entity.getData())) {
            return;
        }

        Map<String, String> data = new HashMap<>();
        int processedColumnCount = 0;
        boolean membershipFilled = false;

        for (Map.Entry<String, PipeConfValue> entry : entity.getData().entrySet()) {
            String name = entry.getKey();
            PipeConfValue confValue = entry.getValue();

            if (!isArrayType(confValue.getType())) {
                checkColumnExistence(entity.getId(), name);
                data.put(name, confValue.getValue());
                processedColumnCount++;
            } else {
                membershipFilled = putMembership(entity, confValue);
            }
        }

        assertSizes(processedColumnCount, columnNames.size());
        checkMembershipFilled(membershipFilled);
        columnNames.forEach(column -> content = String.format(LINE_FORMAT, content, data.get(column)));
    }

    public boolean isSet() {
        return fireCloudClass == FireCloudClass.PARTICIPANT_SET || fireCloudClass == FireCloudClass.SAMPLE_SET
                || fireCloudClass == FireCloudClass.PAIR_SET;
    }

    public String getContent() {
        return content;
    }

    public String getMembershipContent() {
        return membershipContent;
    }

    private String getAttributeName(String type, String name) {
        if (isArrayType(type)) {
            return null;
        }

        if (name.equalsIgnoreCase(FireCloudClass.PARTICIPANT.name()) || name.equalsIgnoreCase(CASE_SAMPLE_ATTRIBUTE)
                || name.equalsIgnoreCase(CONTROL_SAMPLE_ATTRIBUTE)) {
            return isReferenceType(type) ? name : null;
        }

        return name;
    }

    private void checkMembershipFilled(boolean membershipFilled) {
        if (isSet()) {
            Assert.state(membershipFilled, "Entities IDs are required for set entity type.");
        }
    }

    private void checkColumnExistence(Long entityId, String name) {
        Assert.state(columnNames.contains(name),
                String.format("Actual data for entity ID %s does not contain expected column %s.", entityId, name));
    }

    private boolean putMembership(MetadataEntity entity, PipeConfValue confValue) {
        if (!isSet()) {
            return false;
        }
        Matcher matcher = ARRAY_VALUE_PATTERN.matcher(confValue.getValue());
        while (matcher.find()) {
            membershipContent = String.format(PUT_MEMBERSHIP_LINE_FORMAT, membershipContent, entity.getExternalId(),
                    matcher.group().replaceAll("\"", ""));
        }
        return true;
    }

    private static void assertSizes(int actualDataSize, int columnCount) {
        Assert.state(actualDataSize == columnCount,
                String.format("Illegal column count for data: expected %s but found %s", columnCount, actualDataSize));
    }
}
