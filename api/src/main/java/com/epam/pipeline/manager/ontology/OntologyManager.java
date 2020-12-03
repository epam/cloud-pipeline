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

package com.epam.pipeline.manager.ontology;

import com.epam.pipeline.dto.ontology.Ontology;
import com.epam.pipeline.dto.ontology.OntologyType;
import com.epam.pipeline.entity.ontology.OntologyEntity;
import com.epam.pipeline.mapper.ontology.OntologyMapper;
import com.epam.pipeline.repository.ontology.OntologyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Slf4j
public class OntologyManager {
    private final OntologyMapper ontologyMapper;
    private final OntologyRepository ontologyRepository;

    @Transactional
    public Ontology create(final Ontology ontology) {
        Assert.notNull(ontology.getName(), "Ontology name is missing.");
        Assert.notNull(ontology.getType(), "Ontology type is missing.");
        final OntologyEntity entity = ontologyMapper.toEntity(ontology);
        entity.setId(null);
        entity.setCreated(LocalDateTime.now());
        entity.setModified(LocalDateTime.now());
        if (entity.getParent() != null) {
            final OntologyEntity parent = findEntity(ontology.getParent().getId());
            entity.setParent(parent);
        }
        return ontologyMapper.toDto(ontologyRepository.save(entity));
    }

    @Transactional
    public Ontology update(final Long id, final Ontology ontology) {
        final OntologyEntity entity = findEntity(id);
        final Ontology parent = ontology.getParent();
        validateAndSetParent(entity, parent);
        entity.setName(ontology.getName());
        entity.setExternalId(ontology.getExternalId());
        entity.setAttributes(ontology.getAttributes());
        entity.setModified(LocalDateTime.now());
        ontologyRepository.save(entity);
        return ontologyMapper.toDto(entity);
    }

    @Transactional
    public Ontology delete(final Long id, final boolean recursive) {
        final Ontology ontology = ontologyMapper.toDto(findEntity(id));
        final List<OntologyEntity> children = findChildren(ontology.getId());
        if (!recursive && CollectionUtils.isNotEmpty(children)) {
            throw new IllegalStateException(
                    String.format("Ontology '%d' cannot be deleted: children were found.", id));
        }
        if (!recursive || CollectionUtils.isEmpty(children)) {
            ontologyRepository.delete(id);
            return ontology;
        }
        children.forEach(this::deleteRecursive);
        ontologyRepository.delete(id);
        return ontology;
    }

    public Ontology get(final Long id) {
        final OntologyEntity ontologyEntity = findEntity(id);
        return toDtoWithParents(ontologyEntity);
    }

    public List<Ontology> getTree(final OntologyType type, final Long parentId, final Integer depth) {
        final List<OntologyEntity> children = StreamSupport
                .stream(getTreeRoots(type, parentId).spliterator(), false)
                .collect(Collectors.toList());
        if (depth == 1) {
            return toOntologyList(children);
        }
        return children.stream()
                .map(child -> buildTree(findByParent(child.getId()), ontologyMapper.toDto(child), depth))
                .collect(Collectors.toList());
    }

    public List<Ontology> getExternals(final List<String> externals) {
        final Iterable<OntologyEntity> entities = ontologyRepository.findByExternalIdIn(externals);
        return toOntologyList(entities);
    }

    public Ontology getExternal(final String externalId, final Long parentId) {
        return ontologyMapper.toDto(ontologyRepository.findByExternalIdAndParent_Id(externalId, parentId)
                .orElse(null));
    }

    private Ontology buildTree(final Iterable<OntologyEntity> children, Ontology ontology, final Integer depth) {
        if (depth == 1) {
            return ontology;
        }
        return ontology.toBuilder()
                .children(StreamSupport.stream(children.spliterator(), false)
                        .map(child -> buildTree(findByParent(child.getId()),
                                ontologyMapper.toDto(child), depth - 1))
                        .collect(Collectors.toList()))
                .build();
    }

    private Iterable<OntologyEntity> getTreeRoots(final OntologyType type, final Long parentId) {
        if (Objects.isNull(parentId)) {
            Assert.notNull(type, "Ontology type must be specified for roots loading");
            return findRoots(type);
        }
        findEntity(parentId);
        return findByParent(parentId);
    }

    private Iterable<OntologyEntity> findRoots(final OntologyType type) {
        return ontologyRepository.findByParentIsNullAndType(type);
    }

    private Iterable<OntologyEntity> findByParent(final Long id) {
        return ontologyRepository.findByParent_Id(id);
    }

    private OntologyEntity findEntity(final Long id) {
        final OntologyEntity ontology = ontologyRepository.findOne(id);
        Assert.notNull(ontology, String.format("Ontology with id %s wasn't found.", id));
        return ontology;
    }

    private List<Ontology> toOntologyList(final Iterable<OntologyEntity> ontologyEntities) {
        return StreamSupport.stream(ontologyEntities.spliterator(), false)
                .map(ontologyMapper::toDto)
                .collect(Collectors.toList());
    }

    private Ontology toDtoWithParents(final OntologyEntity ontologyEntity) {
        return ontologyMapper.toDto(ontologyEntity).toBuilder()
                .parent(fillParents(ontologyEntity.getParent()))
                .build();
    }

    private Ontology fillParents(final OntologyEntity parent) {
        if (parent == null) {
            return null;
        }
        return ontologyMapper.toDto(parent)
                .toBuilder()
                .parent(fillParents(parent.getParent()))
                .build();
    }

    private void validateAndSetParent(final OntologyEntity entity, final Ontology parent) {
        if (Objects.isNull(parent)) {
            return;
        }
        final Long parentId = parent.getId();
        Assert.notNull(parentId, "Parent id was not found");
        findEntity(parentId);
        entity.setParent(ontologyMapper.toEntity(parent));
    }

    private void deleteRecursive(final OntologyEntity ontology) {
        final List<OntologyEntity> children = findChildren(ontology.getId());
        if (CollectionUtils.isEmpty(children)) {
            ontologyRepository.delete(ontology.getId());
            return;
        }
        children.forEach(this::deleteRecursive);
        ontologyRepository.delete(ontology.getId());
    }

    private List<OntologyEntity> findChildren(final Long id) {
        return StreamSupport
                .stream(findByParent(id).spliterator(), false)
                .collect(Collectors.toList());
    }
}
