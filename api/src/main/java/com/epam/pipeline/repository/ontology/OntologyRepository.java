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

package com.epam.pipeline.repository.ontology;

import com.epam.pipeline.dto.ontology.OntologyType;
import com.epam.pipeline.entity.ontology.OntologyEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface OntologyRepository extends CrudRepository<OntologyEntity, Long> {

    @SuppressWarnings("PMD.MethodNamingConventions")
    Iterable<OntologyEntity> findByParent_Id(Long id);

    Iterable<OntologyEntity> findByParentIsNullAndType(OntologyType type);

    Iterable<OntologyEntity> findByExternalIdIn(List<String> ids);

    @SuppressWarnings("PMD.MethodNamingConventions")
    Optional<OntologyEntity> findByExternalIdAndParent_Id(String externalId, Long parentId);
}
