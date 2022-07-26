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

package com.epam.pipeline.mapper.datastorage.lifecycle;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleNotification;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRuleTemplate;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRule;
import com.epam.pipeline.dto.datastorage.lifecycle.StorageLifecycleRuleTransition;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleTemplateEntity;
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

    ObjectMapper OBJECT_MAPPER = new JsonMapper();

    @Mapping(source = "notification", target = "notificationJson", qualifiedByName = "notificationToJson")
    @Mapping(source = "transitions", target = "transitionsJson", qualifiedByName = "transitionsToJson")
    StorageLifecycleRuleTemplateEntity toEntity(StorageLifecycleRuleTemplate policy);

    @Mapping(source = "notificationJson", target = "notification", qualifiedByName = "notificationJsonToDto")
    @Mapping(source = "transitionsJson", target = "transitions", qualifiedByName = "transitionsJsonToDto")
    StorageLifecycleRuleTemplate toDto(StorageLifecycleRuleTemplateEntity policyEntity);

    @Mapping(source = "notification", target = "notificationJson", qualifiedByName = "notificationToJson")
    @Mapping(source = "transitions", target = "transitionsJson", qualifiedByName = "transitionsToJson")
    StorageLifecycleRuleEntity toEntity(StorageLifecycleRule rule);

    @Mapping(source = "notificationJson", target = "notification", qualifiedByName = "notificationJsonToDto")
    @Mapping(source = "transitionsJson", target = "transitions", qualifiedByName = "transitionsJsonToDto")
    StorageLifecycleRule toDto(StorageLifecycleRuleEntity policyEntity);

    @Named("notificationToJson")
    static String notificationToJson(final StorageLifecycleNotification notification) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(notification);
    }

    @Named("notificationToJson")
    static String transitionsToJson(final List<StorageLifecycleRuleTransition> transitions)
            throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(transitions);
    }

    @Named("notificationJsonToDto")
    static StorageLifecycleNotification notificationJsonToDto(final String notificationJson) throws IOException {
        return OBJECT_MAPPER.readValue(notificationJson, new TypeReference<StorageLifecycleNotification>(){});
    }

    @Named("transitionsJsonToDto")
    static List<StorageLifecycleRuleTransition> transitionsJsonToDto(final String transitionsJson) throws IOException {
        return OBJECT_MAPPER.readValue(transitionsJson, new TypeReference<List<StorageLifecycleRuleTransition>>(){});
    }

}
