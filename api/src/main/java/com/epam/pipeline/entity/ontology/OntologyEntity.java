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

package com.epam.pipeline.entity.ontology;

import com.epam.pipeline.dto.ontology.OntologyType;
import com.epam.pipeline.hibernate.JsonDataUserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Getter
@Setter
@Builder
@Table(name = "ontology", schema = "pipeline")
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "JsonDataUserType", typeClass = JsonDataUserType.class)
public class OntologyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Type(type = "JsonDataUserType")
    private Map<String, String> attributes;

    private String externalId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OntologyEntity parent;

    @Convert(converter = TimestampConverter.class)
    private LocalDateTime created;

    @Convert(converter = TimestampConverter.class)
    private LocalDateTime modified;

    @Enumerated(EnumType.STRING)
    private OntologyType type;
}
