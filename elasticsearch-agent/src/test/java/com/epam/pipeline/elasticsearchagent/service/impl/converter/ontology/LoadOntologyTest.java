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

package com.epam.pipeline.elasticsearchagent.service.impl.converter.ontology;

import com.epam.pipeline.elasticsearchagent.exception.EntityNotFoundException;
import com.epam.pipeline.elasticsearchagent.model.EntityContainer;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.folder.FolderLoader;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.ontology.Ontology;
import com.epam.pipeline.entity.ontology.OntologyType;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.vo.EntityPermissionVO;
import com.epam.pipeline.vo.EntityVO;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.elasticsearchagent.ObjectCreationUtils.buildEntityPermissionVO;
import static com.epam.pipeline.elasticsearchagent.TestConstants.ALLOWED_GROUPS;
import static com.epam.pipeline.elasticsearchagent.TestConstants.ALLOWED_USERS;
import static com.epam.pipeline.elasticsearchagent.TestConstants.DENIED_GROUPS;
import static com.epam.pipeline.elasticsearchagent.TestConstants.DENIED_USERS;
import static com.epam.pipeline.elasticsearchagent.TestConstants.USER;
import static com.epam.pipeline.elasticsearchagent.TestConstants.USER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoadOntologyTest {
    private static final String EXTERNAL = "Q1";
    private static final String NAME_BY_EXTERNAL = "nameByExternal";
    private static final String QUALIFIER_NAME = "qualifierName";
    private static final String QUALIFIER_TERM = "qualifierTerm";
    private static final String DESCRIPTOR_NAME1 = "descriptorName1";
    private static final String DESCRIPTOR_NAME2 = "descriptorName2";
    private static final String DESCRIPTOR_TERM1 = "descriptorTerm1";
    private static final String DESCRIPTOR_TERM2 = "descriptorTerm2";
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String KEY3 = "key3";
    private static final String ONTOLOGY_ID1 = "1";
    private static final String ONTOLOGY_ID2 = "3";
    private final Folder folder = new Folder(1L);
    private final EntityVO folderEntityVO = new EntityVO(1L, AclClass.FOLDER);
    private final Ontology qualifier = ontology(null, null, Collections.singletonList(QUALIFIER_TERM),
            OntologyType.QUAL, QUALIFIER_NAME, 1L);
    private final Ontology parentDescriptor = ontology(null, null,
            Collections.singletonList(DESCRIPTOR_TERM1), OntologyType.DESC, DESCRIPTOR_NAME1, 2L);
    private final Ontology descriptor = ontology(parentDescriptor, Collections.singletonList(EXTERNAL),
            Collections.singletonList(DESCRIPTOR_TERM2), OntologyType.DESC, DESCRIPTOR_NAME2, 3L);
    private final Ontology external = ontology(null, null, null,
            OntologyType.QUAL, NAME_BY_EXTERNAL, 4L);

    private final CloudPipelineAPIClient apiClient = mock(CloudPipelineAPIClient.class);
    private final FolderLoader loader = new FolderLoader(apiClient);

    @BeforeEach
    void setup() {
        final EntityPermissionVO entityPermissionVO =
                buildEntityPermissionVO(USER_NAME, ALLOWED_USERS, DENIED_USERS, ALLOWED_GROUPS, DENIED_GROUPS);

        when(apiClient.loadPipelineFolder(anyLong())).thenReturn(folder);
        when(apiClient.loadUserByName(anyString())).thenReturn(USER);
        when(apiClient.loadPermissionsForEntity(anyLong(), any())).thenReturn(entityPermissionVO);
    }

    @Test
    void shouldLoadOntology() throws EntityNotFoundException {
        final Map<String, PipeConfValue> metadata = new HashMap<>();
        metadata.put(KEY1, new PipeConfValue("ontologyId", ONTOLOGY_ID1));
        metadata.put(KEY2, new PipeConfValue("ontologyId", ONTOLOGY_ID2));
        metadata.put(KEY3, null);
        final MetadataEntry metadataEntry = new MetadataEntry(folderEntityVO, metadata);
        doReturn(Collections.singletonList(metadataEntry)).when(apiClient).loadMetadataEntry(any());
        doReturn(qualifier).when(apiClient).findOntology(ONTOLOGY_ID1);
        doReturn(descriptor).when(apiClient).findOntology(ONTOLOGY_ID2);
        doReturn(Collections.singletonList(external)).when(apiClient)
                .findOntologiesByExternalId(Collections.singletonList(EXTERNAL));

        final EntityContainer<Folder> container = loader.loadEntity(1L).orElseThrow(AssertionError::new);
        final List<String> expectedOntologies = Arrays.asList(NAME_BY_EXTERNAL,
                DESCRIPTOR_NAME1, DESCRIPTOR_TERM1, DESCRIPTOR_TERM2, DESCRIPTOR_NAME2,
                QUALIFIER_TERM, QUALIFIER_NAME);
        assertThat(container.getOntologies())
                .hasSize(expectedOntologies.size())
                .containsExactlyInAnyOrderElementsOf(expectedOntologies);
        assertThat(container.getMetadata())
                .hasSize(3)
                .containsKeys(KEY1, KEY2, KEY3);
        assertThat(container.getMetadata().values()).containsOnlyNulls();
    }

    private Ontology ontology(final Ontology parent,
                              final List<String> seeAlso,
                              final List<String> terms,
                              final OntologyType type,
                              final String name,
                              final Long id) {
        final Map<String, String> attributes = new HashMap<>();
        if (CollectionUtils.isNotEmpty(seeAlso)) {
            attributes.put("See Also", String.join(",", seeAlso));
        }
        if (CollectionUtils.isNotEmpty(terms)) {
            attributes.put("Entry Term(s)", String.join(",", terms));
        }
        return Ontology.builder()
                .id(id)
                .name(name)
                .attributes(attributes)
                .parent(parent)
                .type(type)
                .build();
    }
}
