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

package com.epam.pipeline.acl.ontology;

import com.epam.pipeline.dto.ontology.Ontology;
import com.epam.pipeline.dto.ontology.OntologyType;
import com.epam.pipeline.manager.ontology.OntologyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;

@Service
@RequiredArgsConstructor
public class OntologyApiService {
    private final OntologyManager ontologyManager;

    @PreAuthorize(ADMIN_ONLY)
    @Transactional
    public Ontology create(final Ontology ontology) {
        return ontologyManager.create(ontology);
    }

    @PreAuthorize(ADMIN_ONLY)
    public Ontology get(final Long id) {
        return ontologyManager.get(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    @Transactional
    public Ontology update(final Long id, final Ontology ontology) {
        return ontologyManager.update(id, ontology);
    }

    @PreAuthorize(ADMIN_ONLY)
    @Transactional
    public Ontology delete(final Long id) {
        return ontologyManager.delete(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<Ontology> getTree(final OntologyType type, final Long parentId, final Integer depth) {
        return ontologyManager.getTree(type, parentId, depth);
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<Ontology> getExternals(final List<String> externalIds) {
        return ontologyManager.getExternals(externalIds);
    }

    @PreAuthorize(ADMIN_ONLY)
    public Ontology getExternal(final String externalId, final Long parentId) {
        return ontologyManager.getExternal(externalId, parentId);
    }
}
