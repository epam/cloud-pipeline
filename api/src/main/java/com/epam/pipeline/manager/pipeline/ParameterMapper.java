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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.configuration.AbstractRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.metadata.FolderWithMetadata;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.ResolvedConfiguration;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.metadata.parser.EntityTypeField;
import com.epam.pipeline.manager.pipeline.runner.AnalysisConfiguration;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link ParameterMapper} provides methods for mapping {@link AbstractRunConfigurationEntry} entries to
 * a {@link ResolvedConfiguration} that may be used to schedule pipeline execution. Configuration
 * is resolved by merging default parameters from {@link com.epam.pipeline.entity.pipeline.Pipeline}
 * with settings from {@link AbstractRunConfigurationEntry}. It also supports mapping {@link MetadataEntity}
 * fields and project metadata as values for template parameters.
 */
@Service
@RequiredArgsConstructor
public class ParameterMapper {

    private static final String PARAMETER_VALUES_DELIMITER = ",";
    private static final String ENTITY_PREFIX = "this.";
    private static final String PROJECT_PREFIX = "project.";
    private static final String REFERENCE_DELIMITER = "\\.";

    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterMapper.class);

    private final MessageHelper messageHelper;
    private final PipelineConfigurationManager configurationManager;
    private final MetadataEntityManager entityManager;
    private final FolderManager folderManager;

    public List<ResolvedConfiguration> resolveConfigurations(
            AnalysisConfiguration<? extends AbstractRunConfigurationEntry> configuration) {
        FolderWithMetadata project = folderManager.getProject(
                configuration.getConfigurationId(), AclClass.CONFIGURATION);
        Map<String, PipeConfValue> projectData = project == null ? new HashMap<>() : project.getData();

        List<? extends AbstractRunConfigurationEntry> entries = configuration.getEntries();
        if (CollectionUtils.isEmpty(configuration.getEntitiesIds())) {
            return Collections.singletonList(resolveParameters(entries, projectData));
        }

        //In case of array references one entity may be expanded to
        //list of references entities, e.g. SampleSet is expanded
        //to list of Sample entities
        //TODO: The only reason to store it as map - is to add association to run
        //TODO: to initial entity, from which link comes. Find better solution.
        Map<Long, List<MetadataEntity>> targetEntities =
                fetchAndExpandInputEntities(configuration);

        // resolve all parameter references in configurations
        Map<Long, ResolvedConfiguration> resolvedConfigurations =
                targetEntities.values().stream().flatMap(Collection::stream)
                        .collect(Collectors.toMap(BaseEntity::getId,
                            entity -> resolveParameters(entity, entries, projectData)));

        return targetEntities.entrySet().stream()
                .map(idToEntities -> idToEntities.getValue().stream()
                        .map(entity -> {
                            ResolvedConfiguration currentConfiguration =
                                    resolvedConfigurations.get(entity.getId());
                            currentConfiguration.getAssociatedEntityIds().add(idToEntities.getKey());
                            return currentConfiguration;
                        })
                        .collect(Collectors.toList()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public Map<Long, List<MetadataEntity>> expandExpression(List<Long> entitiesIds,
                                                            String expansionExpression,
                                                            Map<Long, MetadataEntity> entities,
                                                            Long rootEntityId) {
        if (CollectionUtils.isEmpty(entitiesIds)) {
            return Collections.emptyMap();
        }
        if (StringUtils.isBlank(expansionExpression)) {
            return entitiesIds.stream()
                    .collect(Collectors.toMap(id -> id, id -> Collections.singletonList(entities.get(id))));
        }

        Assert.isTrue(expansionExpression.startsWith(ENTITY_PREFIX), messageHelper
                .getMessage(MessageConstants.ERROR_EXPRESSION_INVALID_FORMAT, expansionExpression));

        String valueToResolve = expansionExpression.substring(ENTITY_PREFIX.length());

        return entitiesIds.stream()
                .map(entities::get)
                .collect(
                        Collectors.toMap(MetadataEntity::getId,
                            entity -> {
                                List<MetadataEntity> resolved = resolveReferences(valueToResolve,
                                            valueToResolve.split(REFERENCE_DELIMITER),
                                            loadReferences(entity), entity, true).stream()
                                            .peek(ref -> checkClassIdMatch(ref, rootEntityId))
                                            .collect(Collectors.toList());

                                LOGGER.debug("Resolved {} to list of entities '{}'", entity.getId(),
                                            resolved.stream()
                                                    .map(r -> r.getId().toString())
                                                    .collect(Collectors.joining(",")));
                                return resolved;
                            }));
    }

    /**
     * Gets configuration for a list of {@link AbstractRunConfigurationEntry} without resolving
     * template parameters
     * @param entries to resolve
     * @return configuration for all input entries
     */
    public ResolvedConfiguration resolveParameters(List<? extends AbstractRunConfigurationEntry> entries,
                                                   Map<String, PipeConfValue> projectData) {
        return resolveParameters(null, entries, projectData);
    }

    /**
     * Gets configuration for a list of {@link AbstractRunConfigurationEntry} resolving
     * template parameters from
     * @param entity        to use for parameters template mapping
     * @param entries       to resolve
     * @param projectData   metadata of associated project {@link com.epam.pipeline.entity.pipeline.Folder}
     * @return configuration for all input entries
     */
    public ResolvedConfiguration resolveParameters(MetadataEntity entity,
                                                   List<? extends AbstractRunConfigurationEntry> entries,
                                                   Map<String, PipeConfValue> projectData) {
        if (CollectionUtils.isEmpty(entries)) {
            return new ResolvedConfiguration(entity, Collections.emptyMap());
        }
        if (entity == null) {
            return new ResolvedConfiguration(null,
                    entries.stream()
                            .collect(Collectors.toMap(AbstractRunConfigurationEntry::getName,
                                    this::getEntryConfiguration)));
        }
        Map<MetadataKey, MetadataEntity> entityReferences = loadReferences(entity);

        Map<String, PipelineConfiguration> resolved = new HashMap<>();
        entries.forEach(entry -> {
            checkClassIdMatch(entity, entry.getRootEntityId());
            PipelineConfiguration configuration = getEntryConfiguration(entry);
            if (MapUtils.isNotEmpty(configuration.getParameters())) {
                configuration.setParameters(mapParameters(entity, projectData,
                        configuration.getParameters(), entityReferences));
            }
            resolved.put(entry.getName(), configuration);
        });
        return new ResolvedConfiguration(entity, resolved);
    }

    public Map<Long, List<MetadataEntity>> fetchAndExpandInputEntities(AnalysisConfiguration<?> configuration) {
        Map<Long, MetadataEntity> inputEntities = fetchInputEntities(configuration.getEntitiesIds());
        return expandExpression(
                configuration.getEntitiesIds(), configuration.getExpansionExpression(),
                inputEntities, configuration.getEntries().get(0).getRootEntityId());
    }

    private Map<Long, MetadataEntity> fetchInputEntities(List<Long> entitiesIds) {
        Map<Long, MetadataEntity> inputEntities = entityManager
                .loadEntitiesByIds(new HashSet<>(entitiesIds))
                .stream()
                .collect(Collectors.toMap(MetadataEntity::getId, Function.identity()));
        Assert.isTrue(inputEntities.size() == new HashSet<>(entitiesIds).size(),
                "Non existing inputEntities");
        return inputEntities;
    }


    protected Map<String, PipeConfValueVO> mapParameters(MetadataEntity entity, Map<String, PipeConfValue> projectData,
            Map<String, PipeConfValueVO> parameters, Map<MetadataKey, MetadataEntity> entityReferences) {
        Map<String, PipeConfValueVO> result = new HashMap<>();
        Map<String, PipeConfValue> entityData = entity.getData();
        parameters.forEach((name, runParameter) -> {
            if (runParameter == null || StringUtils.isBlank(runParameter.getValue())) {
                result.put(name, runParameter);
                return;
            }
            String parameter = runParameter.getValue();
            if (parameter.startsWith(ENTITY_PREFIX)) {
                String field = runParameter.getValue().substring(ENTITY_PREFIX.length());
                String[] chunks = field.split(REFERENCE_DELIMITER);
                if (chunks.length > 1) {
                    result.put(name, unmarshallDependency(field, chunks, entityReferences, entity, runParameter));
                } else {
                    result.put(name, getMetaValue(entityData, runParameter, field));
                }
            } else if (parameter.startsWith(PROJECT_PREFIX)) {
                String field = runParameter.getValue().substring(PROJECT_PREFIX.length());
                result.put(name, getMetaValue(projectData, runParameter, field));
            } else {
                result.put(name, runParameter);
            }
        });
        return  result;
    }

    private Map<MetadataKey, MetadataEntity> loadReferences(MetadataEntity entity) {
        return entityManager.loadReferencesForEntities(
                Collections.singletonList(entity.getId()), entity.getParent().getId())
                .stream()
                .collect(Collectors.toMap(
                    e -> new MetadataKey(e.getClassEntity().getName(), e.getExternalId()),
                        Function.identity()));
    }

    private void checkClassIdMatch(MetadataEntity entity, Long expectedClassId) {
        Assert.isTrue(Objects.equals(entity.getClassEntity().getId(), expectedClassId),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_ENTITY_CLASS,
                        expectedClassId, entity.getClassEntity().getId()));
    }


    private PipeConfValueVO unmarshallDependency(String reference, String[] fields,
            Map<MetadataKey, MetadataEntity> entityReferences, MetadataEntity entity, PipeConfValueVO parameter) {
        List<MetadataEntity> currentEntities =
                resolveReferences(reference, fields, entityReferences, entity, false);
        String fieldName = fields[fields.length - 1];
        return getMetaListValue(currentEntities, parameter, reference, fieldName);
    }

    private List<MetadataEntity> resolveReferences(String reference, String[] fields,
            Map<MetadataKey, MetadataEntity> entityReferences, MetadataEntity entity, boolean includeLast) {
        List<MetadataEntity> currentEntities = Collections.singletonList(entity);
        int lastIndex = includeLast ? fields.length : fields.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            List<MetadataEntity> links = new ArrayList<>();
            for (MetadataEntity currentEntity : currentEntities) {
                Map<String, PipeConfValue> currentEntityData = currentEntity.getData();
                String field = fields[i];
                if (MapUtils.isEmpty(currentEntityData) || !currentEntityData.containsKey(field)) {
                    throw new IllegalArgumentException(
                            messageHelper.getMessage(
                                    MessageConstants.ERROR_PARAMETER_MISSING_VALUE, reference, field));
                }
                PipeConfValue value = currentEntityData.get(field);
                if (StringUtils.isBlank(value.getValue())) {
                    throw new IllegalArgumentException(
                            messageHelper.getMessage(
                                    MessageConstants.ERROR_PARAMETER_MISSING_VALUE, reference, field));
                }
                String entityClassName = EntityTypeField.parseClass(value.getType());

                if (StringUtils.isBlank(entityClassName)) {
                    throw new IllegalArgumentException(
                            messageHelper.getMessage(
                                    MessageConstants.ERROR_PARAMETER_NON_REFERENCE_TYPE, reference, field));
                }

                if (EntityTypeField.isArrayType(value.getType())) {
                    List<String> arrayValues = JsonMapper
                            .parseData(value.getValue(), new TypeReference<List<String>>() {});
                    if (CollectionUtils.isEmpty(arrayValues)) {
                        throw new IllegalArgumentException(
                                messageHelper.getMessage(
                                        MessageConstants.ERROR_PARAMETER_INVALID_ARRAY,
                                        reference, value.getValue(), field));
                    }
                    arrayValues.forEach(v -> links.add(getReference(entityReferences, v, entityClassName, reference)));
                } else {
                    links.add(getReference(entityReferences, value.getValue(), entityClassName, reference));
                }
            }
            currentEntities = links;
        }
        return currentEntities;
    }

    private MetadataEntity getReference(Map<MetadataKey, MetadataEntity> entityReferences,
            String referenceId, String entityClassName, String field) {
        MetadataKey key = new MetadataKey(entityClassName, referenceId);
        if (!entityReferences.containsKey(key)) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(
                            MessageConstants.ERROR_PARAMETER_MISSING_REFERENCE, field, entityClassName, referenceId));
        }
        return entityReferences.get(key);
    }

    private PipeConfValueVO getMetaListValue(List<MetadataEntity> entities,
            PipeConfValueVO parameter, String reference, String fieldName) {
        String value = entities.stream()
                .map(entity -> getResolvedValue(entity.getData(), reference, fieldName))
                .map(PipeConfValue::getValue)
                .collect(Collectors.joining(PARAMETER_VALUES_DELIMITER));
        return new PipeConfValueVO(value, parameter.getType(), parameter.isRequired());
    }

    private PipeConfValueVO getMetaValue(Map<String, PipeConfValue> entityData,
            PipeConfValueVO parameter, String field) {
        PipeConfValue resolvedValue = getResolvedValue(entityData, field, field);
        return new PipeConfValueVO(resolvedValue.getValue(), parameter.getType(), parameter.isRequired());
    }

    private PipeConfValue getResolvedValue(Map<String, PipeConfValue> entityData, String reference, String field) {
        if (MapUtils.isEmpty(entityData)) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_PARAMETER_MISSING_VALUE, reference, field));
        }
        PipeConfValue resolvedValue = entityData.get(field);
        if (resolvedValue == null || StringUtils.isBlank(resolvedValue.getValue())) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_PARAMETER_MISSING_VALUE, reference, field));
        }
        if (EntityTypeField.isReferenceType(resolvedValue.getType()) ||
                EntityTypeField.isArrayType(resolvedValue.getType())) {
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_PARAMETER_NON_SCALAR_TYPE, reference, field));
        }
        return resolvedValue;
    }

    private PipelineConfiguration getEntryConfiguration(AbstractRunConfigurationEntry entry) {
        PipelineStart startVO = entry.toPipelineStart();
        return configurationManager.getPipelineConfiguration(startVO);
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class MetadataKey {
        private String className;
        private String externalId;
    }

}
