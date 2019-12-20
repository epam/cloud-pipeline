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

package com.epam.pipeline.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import javax.persistence.AttributeConverter;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
@AllArgsConstructor
@Entity
@Table(name = "user", schema = "pipeline")
public class PipelineUser implements StorageContainer {

    public static final String EMAIL_ATTRIBUTE = "email";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "name")
    private String userName;

    @Transient
    private List<Role> roles;

    @Transient
    private List<String> groups;

    @Transient
    private boolean admin;

    @Transient
    private boolean blocked;

    @Transient
    private LocalDateTime registrationDate;

    @Transient
    private LocalDateTime firstLoginDate;

    private Long defaultStorageId;

    @Convert(converter = AttributesConverterJson.class)
    private Map<String, String> attributes;

    public PipelineUser() {
        this.admin = false;
        this.blocked = false;
        this.roles = new ArrayList<>();
        this.groups = new ArrayList<>();
        this.attributes = new HashMap<>();
    }

    public PipelineUser(String userName) {
        this();
        this.userName = userName;
    }

    public String getEmail() {
        if (attributes == null) {
            return null;
        }
        return attributes.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(EMAIL_ATTRIBUTE))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    @JsonIgnore
    public Set<String> getAuthorities() {
        Set<String> authorities = new HashSet<>();
        authorities.add(userName);
        authorities.addAll(roles.stream().map(Role::getName).collect(Collectors.toList()));
        authorities.addAll(groups);
        return authorities;
    }

    @Override
    public Long getDefaultStorageId() {
        return defaultStorageId;
    }

    public static class AttributesConverterJson implements AttributeConverter<Map<String, String>, String>  {

        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public String convertToDatabaseColumn(Map<String, String> attribute) {
            try {
                return mapper.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public Map<String, String> convertToEntityAttribute(String dbData) {
            try {
                return StringUtils.isEmpty(dbData)
                        ? Collections.emptyMap()
                        : mapper.readValue(dbData, new TypeReference<Map<String, String>>(){});
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }

    }

    @Getter
    public enum PipelineUserFields {

        ID("id"),
        USER_NAME("userName"),
        EMAIL("email"),
        ROLES("roles"),
        GROUPS("groups"),
        BLOCKED("blocked"),
        REGISTRATION_DATE("registrationDate"),
        FIRST_LOGIN_DATE("firstLoginDate"),
        ATTRIBUTES("attributes"),
        DEFAULT_STORAGE_ID("defaultStorageId");

        private final String value;

        PipelineUserFields(final String value) {
            this.value = value;
        }
    }
}
