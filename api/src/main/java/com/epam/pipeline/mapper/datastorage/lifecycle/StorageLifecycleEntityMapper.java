/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.mapper.datastorage.lifecycle;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleNotification;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRuleProlongation;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRuleTransition;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.io.IOException;
import java.util.List;

@Mapper(componentModel = "spring")
public interface StorageLifecycleEntityMapper {

    String NOTIFICATION = "notification";
    String NOTIFICATION_JSON = "notificationJson";
    String NOTIFICATION_JSON_TO_DTO = "notificationJsonToDto";
    String NOTIFICATION_TO_JSON = "notificationToJson";

    String TRANSITIONS = "transitions";
    String TRANSITIONS_JSON = "transitionsJson";
    String TRANSITIONS_JSON_TO_DTO = "transitionsJsonToDto";
    String TRANSITIONS_TO_JSON = "transitionsToJson";

    String PROLONGATIONS = "prolongations";
    String PROLONGATIONS_JSON = "prolongationsJson";
    String PROLONGATIONS_JSON_TO_DTO = "prolongationsJsonToDto";
    String PROLONGATIONS_TO_JSON = "prolongationsToJson";

    ObjectMapper OBJECT_MAPPER = new JsonMapper();

    @Mapping(source = PROLONGATIONS, target = PROLONGATIONS_JSON, qualifiedByName = PROLONGATIONS_TO_JSON)
    @Mapping(source = NOTIFICATION, target = NOTIFICATION_JSON, qualifiedByName = NOTIFICATION_TO_JSON)
    @Mapping(source = TRANSITIONS, target = TRANSITIONS_JSON, qualifiedByName = TRANSITIONS_TO_JSON)
    StorageLifecycleRuleEntity toEntity(StorageLifecycleRule rule);

    @Mapping(source = PROLONGATIONS_JSON, target = PROLONGATIONS, qualifiedByName = PROLONGATIONS_JSON_TO_DTO)
    @Mapping(source = NOTIFICATION_JSON, target = NOTIFICATION, qualifiedByName = NOTIFICATION_JSON_TO_DTO)
    @Mapping(source = TRANSITIONS_JSON, target = TRANSITIONS, qualifiedByName = TRANSITIONS_JSON_TO_DTO)
    StorageLifecycleRule toDto(StorageLifecycleRuleEntity policyEntity);

    @Named(PROLONGATIONS_TO_JSON)
    static String prolongationsToJson(final List<StorageLifecycleRuleProlongation> prolongations) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(prolongations);
    }

    @Named(PROLONGATIONS_JSON_TO_DTO)
    static List<StorageLifecycleRuleProlongation> prolongationsJsonToDto(final String prolongationsJson) throws IOException {
        return OBJECT_MAPPER.readValue(prolongationsJson, new TypeReference<List<StorageLifecycleRuleProlongation>>(){});
    }

    @Named(NOTIFICATION_TO_JSON)
    static String notificationToJson(final StorageLifecycleNotification notification) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(notification);
    }

    @Named(NOTIFICATION_JSON_TO_DTO)
    static StorageLifecycleNotification notificationJsonToDto(final String notificationJson) throws IOException {
        return OBJECT_MAPPER.readValue(notificationJson, new TypeReference<StorageLifecycleNotification>(){});
    }

    @Named(NOTIFICATION_TO_JSON)
    static String transitionsToJson(final List<StorageLifecycleRuleTransition> transitions)
            throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(transitions);
    }

    @Named(TRANSITIONS_JSON_TO_DTO)
    static List<StorageLifecycleRuleTransition> transitionsJsonToDto(final String transitionsJson) throws IOException {
        return OBJECT_MAPPER.readValue(transitionsJson, new TypeReference<List<StorageLifecycleRuleTransition>>(){});
    }

}
