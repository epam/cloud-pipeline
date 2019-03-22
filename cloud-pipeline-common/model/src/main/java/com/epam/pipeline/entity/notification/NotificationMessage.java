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

package com.epam.pipeline.entity.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.persistence.AttributeConverter;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
@EqualsAndHashCode
@Entity
@Table(name = "notification_queue", schema = "pipeline")
public class NotificationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne
    private NotificationTemplate template;

    @Column(name = "to_user_id")
    private Long toUserId;

    @Convert(converter = UserIdsConverter.class)
    @Column(name = "user_ids")
    private List<Long> copyUserIds;

    @Column(name = "template_parameters")
    @Convert(converter = ParameterConverterJson.class)
    private Map<String, Object> templateParameters;

    public static class ParameterConverterJson implements AttributeConverter<Map<String, Object>, String> {

        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public String convertToDatabaseColumn(Map<String, Object> attribute) {
            try {
                return mapper.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public Map<String, Object> convertToEntityAttribute(String dbData) {
            try {
                return StringUtils.isEmpty(dbData)
                        ? Collections.emptyMap()
                        : mapper.readValue(dbData, new TypeReference<Map<String, Object>>(){});
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public static class UserIdsConverter implements AttributeConverter<List<Long>, String> {

        @Override
        public String convertToDatabaseColumn(List<Long> attribute) {
            return attribute.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","));
        }

        @Override
        public List<Long> convertToEntityAttribute(String dbData) {
            return Arrays.stream(dbData.split(","))
                    .filter(s -> !StringUtils.isEmpty(s))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        }
    }
}
