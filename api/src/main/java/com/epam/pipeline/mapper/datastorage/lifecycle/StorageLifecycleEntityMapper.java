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
import com.epam.pipeline.dto.datastorage.lifecycle.execution.StorageLifecycleRuleExecution;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreAction;
import com.epam.pipeline.dto.datastorage.lifecycle.restore.StorageRestoreActionNotification;
import com.epam.pipeline.dto.datastorage.lifecycle.transition.StorageLifecycleRuleTransition;
import com.epam.pipeline.dto.datastorage.lifecycle.transition.StorageLifecycleTransitionCriterion;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleEntity;
import com.epam.pipeline.entity.datastorage.lifecycle.StorageLifecycleRuleExecutionEntity;
import com.epam.pipeline.entity.datastorage.lifecycle.restore.StorageRestoreActionEntity;
import com.epam.pipeline.entity.user.PipelineUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.util.StringUtils;

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

    String TRANSITION_CRITERION = "transitionCriterion";
    String TRANSITION_CRITERION_JSON = "transitionCriterionJson";
    String TRANSITION_CRITERION_JSON_TO_DTO = "transitionCriterionJsonToDto";
    String TRANSITION_CRITERION_TO_JSON = "transitionCriterionToJson";

    String PIPELINE_USER_TO_ID = "pipelineUserToId";
    String ID_TO_PIPELINE_USER = "idToPipelineUser";
    String USER_ACTOR_ID = "userActorId";
    String USER_ACTOR = "userActor";

    String RESTORE_NOTIFICATION_JSON_TO_DTO = "restoreNotificationJsonToDto";
    String RESTORE_NOTIFICATION_TO_JSON = "restoreNotificationToJson";


    ObjectMapper OBJECT_MAPPER = new JsonMapper();

    @Mapping(source = TRANSITION_CRITERION, target = TRANSITION_CRITERION_JSON,
            qualifiedByName = TRANSITION_CRITERION_TO_JSON)
    @Mapping(source = NOTIFICATION, target = NOTIFICATION_JSON, qualifiedByName = NOTIFICATION_TO_JSON)
    @Mapping(source = TRANSITIONS, target = TRANSITIONS_JSON, qualifiedByName = TRANSITIONS_TO_JSON)
    StorageLifecycleRuleEntity toEntity(StorageLifecycleRule rule);

    @Mapping(source = TRANSITION_CRITERION_JSON, target = TRANSITION_CRITERION,
            qualifiedByName = TRANSITION_CRITERION_JSON_TO_DTO)
    @Mapping(source = NOTIFICATION_JSON, target = NOTIFICATION, qualifiedByName = NOTIFICATION_JSON_TO_DTO)
    @Mapping(source = TRANSITIONS_JSON, target = TRANSITIONS, qualifiedByName = TRANSITIONS_JSON_TO_DTO)
    StorageLifecycleRule toDto(StorageLifecycleRuleEntity ruleEntity);

    StorageLifecycleRuleExecutionEntity toEntity(StorageLifecycleRuleExecution execution);
    StorageLifecycleRuleExecution toDto(StorageLifecycleRuleExecutionEntity executionEntity);

    @Mapping(source = USER_ACTOR_ID, target = USER_ACTOR, qualifiedByName = ID_TO_PIPELINE_USER)
    @Mapping(source = NOTIFICATION, target = NOTIFICATION_JSON, qualifiedByName = RESTORE_NOTIFICATION_TO_JSON)
    StorageRestoreActionEntity toEntity(StorageRestoreAction restoreAction);

    @Mapping(source = USER_ACTOR, target = USER_ACTOR_ID, qualifiedByName = PIPELINE_USER_TO_ID)
    @Mapping(source = NOTIFICATION_JSON, target = NOTIFICATION, qualifiedByName = RESTORE_NOTIFICATION_JSON_TO_DTO)
    StorageRestoreAction toDto(StorageRestoreActionEntity restoreActionEntity);

    @Named(PIPELINE_USER_TO_ID)
    static Long pipelineUserToId(final PipelineUser user) {
        if (user == null) {
            return null;
        }
        return user.getId();
    }

    @Named(ID_TO_PIPELINE_USER)
    static PipelineUser idToPipelineUser(final Long id) {
        return PipelineUser.builder().id(id).build();
    }

    @Named(NOTIFICATION_TO_JSON)
    static String notificationToJson(final StorageLifecycleNotification notification) throws JsonProcessingException {
        if (notification == null) {
            return null;
        }
        return OBJECT_MAPPER.writeValueAsString(notification);
    }

    @Named(NOTIFICATION_JSON_TO_DTO)
    static StorageLifecycleNotification notificationJsonToDto(final String notificationJson) throws IOException {
        if (StringUtils.isEmpty(notificationJson)) {
            return null;
        }
        return OBJECT_MAPPER.readValue(notificationJson, new TypeReference<StorageLifecycleNotification>(){});
    }

    @Named(TRANSITIONS_TO_JSON)
    static String transitionsToJson(final List<StorageLifecycleRuleTransition> transitions)
            throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(transitions);
    }

    @Named(TRANSITIONS_JSON_TO_DTO)
    static List<StorageLifecycleRuleTransition> transitionsJsonToDto(final String transitionsJson) throws IOException {
        return OBJECT_MAPPER.readValue(transitionsJson, new TypeReference<List<StorageLifecycleRuleTransition>>(){});
    }

    @Named(TRANSITION_CRITERION_TO_JSON)
    static String transitionCriterionToJson(final StorageLifecycleTransitionCriterion transitionCriterion)
            throws JsonProcessingException {
        if (transitionCriterion == null) {
            return null;
        }
        return OBJECT_MAPPER.writeValueAsString(transitionCriterion);
    }

    @Named(TRANSITION_CRITERION_JSON_TO_DTO)
    static StorageLifecycleTransitionCriterion transitionCriterionJsonToDto(
            final String transitionCriterionJson) throws IOException {
        if (StringUtils.isEmpty(transitionCriterionJson)) {
            return null;
        }
        return OBJECT_MAPPER.readValue(
                transitionCriterionJson,
                new TypeReference<StorageLifecycleTransitionCriterion>(){}
        );
    }

    @Named(RESTORE_NOTIFICATION_TO_JSON)
    static String restoreNotificationToJson(final StorageRestoreActionNotification notification)
            throws JsonProcessingException {
        if (notification == null) {
            return null;
        }
        return OBJECT_MAPPER.writeValueAsString(notification);
    }

    @Named(RESTORE_NOTIFICATION_JSON_TO_DTO)
    static StorageRestoreActionNotification restoreNotificationJsonToDto(final String notificationJson)
            throws IOException {
        if (StringUtils.isEmpty(notificationJson)) {
            return null;
        }
        return OBJECT_MAPPER.readValue(notificationJson, new TypeReference<StorageRestoreActionNotification>(){});
    }
}
