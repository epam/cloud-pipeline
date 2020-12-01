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

package com.epam.pipeline.test.creator.ontology;

import com.epam.pipeline.dto.ontology.Ontology;
import com.epam.pipeline.dto.ontology.OntologyType;
import com.epam.pipeline.entity.ontology.OntologyEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;

public final class OntologyCreatorsUtils {
    public static final String EXTERNAL_ID = "1";
    public static final Map<String, String> ATTRIBUTES = new HashMap<>();
    public static final LocalDateTime CREATED = LocalDateTime.of(2019, 10, 7, 0, 0);
    public static final String NAME = "NAME";

    private OntologyCreatorsUtils() {
        // no-op
    }

    public static OntologyEntity ontologyEntity(final OntologyEntity parent) {
        final OntologyEntity entity = new OntologyEntity();
        entity.setExternalId(EXTERNAL_ID);
        entity.setName(NAME);
        entity.setAttributes(ATTRIBUTES);
        entity.setParent(parent);
        entity.setCreated(CREATED);
        entity.setModified(CREATED);
        entity.setType(OntologyType.QUAL);
        return entity;
    }

    public static Ontology ontology(final Ontology parent) {
        return Ontology.builder()
                .id(ID)
                .externalId(EXTERNAL_ID)
                .name(NAME)
                .attributes(ATTRIBUTES)
                .parent(parent)
                .type(OntologyType.QUAL)
                .build();
    }
}
