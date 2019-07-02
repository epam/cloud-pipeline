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
package com.epam.pipeline.elasticsearchagent.model;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "pipeline_event", schema = "pipeline")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class PipelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Enumerated
    @Column(name = "operation")
    private EventType eventType;
    @Column(name = "stamp", columnDefinition = "timestamp with time zone")
    private LocalDateTime createdDate;
    @Enumerated
    private ObjectType objectType;
    private Long objectId;
    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private String data;

    @RequiredArgsConstructor
    @Getter
    public enum ObjectType {
        PIPELINE("pipeline"),
        PIPELINE_CODE("pipeline_code"),
        RUN("run"),
        NFS("NFS"),
        S3("S3"),
        AZ("AZ"),
        GS("GS"),
        TOOL("tool"),
        FOLDER("folder"),
        TOOL_GROUP("tool_group"),
        DOCKER_REGISTRY("docker_registry"),
        ISSUE("issue"),
        METADATA_ENTITY("metadata_entity"),
        CONFIGURATION("configuration");

        private final String dbName;

    }
}
