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

package com.epam.pipeline.mapper.ontology;

import com.epam.pipeline.assertions.ontology.OntologyAssertions;
import com.epam.pipeline.dto.ontology.Ontology;
import com.epam.pipeline.entity.ontology.OntologyEntity;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import static com.epam.pipeline.test.creator.ontology.OntologyCreatorsUtils.ontology;
import static com.epam.pipeline.test.creator.ontology.OntologyCreatorsUtils.ontologyEntity;

public class OntologyMapperTest {

    private final OntologyMapper mapper = Mappers.getMapper(OntologyMapper.class);

    @Test
    public void shouldMapEntityToDto() {
        final OntologyEntity entity = ontologyEntity(null);
        final Ontology resultDto = mapper.toDto(entity);

        OntologyAssertions.assertEquals(entity, resultDto);
    }

    @Test
    public void shouldMapDtoToEntity() {
        final Ontology dto = ontology(null);
        final OntologyEntity resultEntity = mapper.toEntity(dto);

        OntologyAssertions.assertEquals(resultEntity, dto);
    }
}
