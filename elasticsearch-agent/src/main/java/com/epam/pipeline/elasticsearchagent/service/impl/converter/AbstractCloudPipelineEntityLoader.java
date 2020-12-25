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
package com.epam.pipeline.elasticsearchagent.service.impl.converter;

import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.elasticsearchagent.service.EntityLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.ontology.Ontology;
import com.epam.pipeline.entity.ontology.OntologyType;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.exception.PipelineResponseException;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.EntityVO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractCloudPipelineEntityLoader<T> implements EntityLoader<T> {
    private static final String ONTOLOGY_TYPE = "ontologyId";
    private static final String ENTRY_TERMS = "Entry Term(s)";
    private static final String SEE_ALSO = "See Also";

    private final CloudPipelineAPIClient apiClient;

    @Override
    public Optional<EntityContainer<T>> loadEntity(final Long id) throws EntityNotFoundException {
        try {
            return Optional.of(buildContainer(id));
        } catch (PipelineResponseException e) {
            log.error(e.getMessage(), e);
            final String errorMessageWithId = buildNotFoundErrorMessage(id);
            log.debug("Expected error message: {}", errorMessageWithId);
            if (e.getMessage().replaceAll("[^\\w\\s]", "").contains(errorMessageWithId)) {
                throw new EntityNotFoundException(e);
            }
            return Optional.empty();
        }
    }

    protected abstract T fetchEntity(Long id);

    protected abstract String getOwner(T entity);

    protected abstract AclClass getAclClass(T entity);

    protected EntityContainer<T> buildContainer(final Long id) {
        final T entity = fetchEntity(id);
        final List<MetadataEntry> metadataEntries = loadMetadata(id, getAclClass(entity));
        return EntityContainer.<T>builder()
                .entity(entity)
                .owner(loadUser(getOwner(entity)))
                .metadata(prepareMetadataForEntity(metadataEntries))
                .ontologies(prepareOntologies(loadOntologies(metadataEntries)))
                .permissions(loadPermissions(id, getAclClass(entity)))
                .build();
    }

    protected List<Ontology> loadOntologies(final List<MetadataEntry> metadataEntries) {
        if (CollectionUtils.isEmpty(metadataEntries) || metadataEntries.size() != 1) {
            return Collections.emptyList();
        }
        return MapUtils.emptyIfNull(metadataEntries.get(0).getData()).values().stream()
                .filter(Objects::nonNull)
                .filter(pipeConfValue -> ONTOLOGY_TYPE.equalsIgnoreCase(pipeConfValue.getType()))
                .map(pipeConfValue -> findOntology(pipeConfValue.getValue()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    protected PermissionsContainer loadPermissions(final Long id, final AclClass entityClass) {
        PermissionsContainer permissionsContainer = new PermissionsContainer();
        if (entityClass == null) {
            return permissionsContainer;
        }
        EntityPermissionVO entityPermission = apiClient.loadPermissionsForEntity(id, entityClass);

        if (entityPermission != null) {
            String owner = entityPermission.getOwner();
            permissionsContainer.add(entityPermission.getPermissions(), owner);
        }

        return permissionsContainer;
    }

    protected PipelineUser loadUser(final String username) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        return apiClient.loadUserByName(username);
    }

    protected List<MetadataEntry> loadMetadata(final Long id, final AclClass aclClass) {
        if (aclClass == null) {
            return Collections.emptyList();
        }
        return apiClient.loadMetadataEntry(Collections.singletonList(new EntityVO(id, aclClass)));
    }

    protected Map<String, String> prepareMetadataForEntity(final List<MetadataEntry> metadataEntries) {
        Map<String, String> metadata = null;
        if (!CollectionUtils.isEmpty(metadataEntries) && metadataEntries.size() == 1) {
            metadata = Stream.of(MapUtils.emptyIfNull(metadataEntries.get(0).getData()))
                    .map(Map::entrySet)
                    .flatMap(Set::stream)
                    .collect(HashMap::new,
                        (map, entry) -> {
                            final String value = Optional.ofNullable(entry.getValue())
                                .flatMap(this::buildMetadataValue)
                                .orElse(null);
                            map.put(entry.getKey(), value);
                        },
                        HashMap::putAll);
        }
        return MapUtils.emptyIfNull(metadata);
    }

    protected String buildNotFoundErrorMessage(final Long id) {
        return String.format("%d was not found", id);
    }

    protected Set<String> prepareOntologies(final List<Ontology> ontologies) {
        if (CollectionUtils.isEmpty(ontologies)) {
            return Collections.emptySet();
        }
        final Set<String> results = new HashSet<>();
        ontologies.forEach(ontology -> addOntology(ontology, results));
        return results;
    }

    private void addOntology(final Ontology ontology, final Set<String> results) {
        if (Objects.isNull(ontology)) {
            return;
        }
        if (ontology.getType().equals(OntologyType.DESC)) {
            addNamesAndTerms(ontology, results);
            addSeeAlso(ontology, results);
        } else if (ontology.getType().equals(OntologyType.QUAL)) {
            addNamesAndTerms(ontology, results);
        } else {
            log.warn("Unsupported type for ontology: '{}'", ontology.getType());
        }
    }

    private void addSeeAlso(final Ontology ontology, final Set<String> results) {
        final List<String> externalIds = parseArray(ontology.getAttributes().get(SEE_ALSO));
        if (CollectionUtils.isEmpty(externalIds)) {
            return;
        }
        results.addAll(ListUtils.emptyIfNull(apiClient.findOntologiesByExternalId(externalIds)).stream()
                .map(Ontology::getName)
                .collect(Collectors.toList()));
    }

    private List<String> parseArray(final String field) {
        if (StringUtils.isBlank(field)) {
            return Collections.emptyList();
        }
        return Arrays.stream(field.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    private Set<String> addNamesAndTerms(final Ontology ontology, final Set<String> ontologies) {
        if (Objects.isNull(ontology)) {
            return ontologies;
        }
        ontologies.add(ontology.getName());
        final Map<String, String> attributes = MapUtils.emptyIfNull(ontology.getAttributes());
        if (attributes.containsKey(ENTRY_TERMS)) {
            ontologies.addAll(parseArray(attributes.get(ENTRY_TERMS)));
        }
        return addNamesAndTerms(ontology.getParent(), ontologies);
    }

    private Ontology findOntology(final String value) {
        try {
            return apiClient.findOntology(value);
        } catch (PipelineResponseException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private Optional<String> buildMetadataValue(final PipeConfValue metadataValue) {
        if (ONTOLOGY_TYPE.equalsIgnoreCase(metadataValue.getType())) {
            return Optional.empty();
        }
        return Optional.of(metadataValue.getValue());
    }
}
