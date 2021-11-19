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
import com.epam.pipeline.test.creator.ontology.OntologyCreatorsUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.*;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class OntologyManagerTest {
    private final OntologyRepository ontologyRepository = mock(OntologyRepository.class);
    private final OntologyMapper mapper = mock(OntologyMapper.class);
    private final OntologyManager manager = new OntologyManager(mapper, ontologyRepository);
    private final Ontology ontology = OntologyCreatorsUtils.ontology(null);
    private final OntologyEntity ontologyEntity = OntologyCreatorsUtils.ontologyEntity(null);

    @Before
    public void setUp() {
        doReturn(ontologyEntity).when(mapper).toEntity(any());
        doReturn(ontology).when(mapper).toDto(any());
    }

    @Test
    public void shouldCreateOntology() {
        doReturn(null).when(ontologyRepository).findOne(anyLong());
        manager.create(ontology);
        verify(ontologyRepository).save((OntologyEntity) any());
    }

    @Test
    public void shouldGetOntologyById() {
        doReturn(ontologyEntity).when(ontologyRepository).findOne(anyLong());
        manager.get(ID);
        verify(ontologyRepository).findOne(anyLong());
    }

    @Test
    public void shouldFailGetIfIdIsNotCorrect() {
        doReturn(null).when(ontologyRepository).findOne(anyLong());
        assertThrows(IllegalArgumentException.class, () -> manager.get(ID));
    }

    @Test
    public void shouldUpdateOntologyById() {
        doReturn(ontologyEntity).when(ontologyRepository).findOne(anyLong());
        manager.update(ID, ontology);
        verify(ontologyRepository).findOne(anyLong());
        verify(ontologyRepository).save((OntologyEntity) any());
    }

    @Test
    public void shouldLoadRoots() {
        final OntologyType type = ontologyEntity.getType();
        final Iterable<OntologyEntity> findResult = () -> Collections.singletonList(ontologyEntity).iterator();
        doReturn(findResult).when(ontologyRepository).findByParentIsNullAndType(type);
        manager.getTree(type, null, 1);
        verify(ontologyRepository).findByParentIsNullAndType(type);
    }

    @Test
    public void shouldLoadTree() {
        mockOntologyTree();

        final List<Ontology> tree = manager.getTree(OntologyType.QUAL, ID, 1);
        Assert.assertThat(tree.size(), is(2));
    }

    @Test
    public void shouldDeleteOntologyNonRecursive() {
        doReturn(ontologyEntity).when(ontologyRepository).findOne(ID);
        doReturn(EMPTY_ITERABLE).when(ontologyRepository).findByParent_Id(ID);
        manager.delete(ID, false);
        verify(ontologyRepository).delete(ID);
    }

    @Test
    public void shouldNotDeleteOntologyIfChildrenExist() {
        mockOntologyTree();

        assertThrows(IllegalStateException.class, () -> manager.delete(ID, false));
    }

    @Test
    public void shouldDeleteOntologyRecursive() {
        mockOntologyTree();

        manager.delete(ID, true);

        verify(ontologyRepository).delete(ID);
        verify(ontologyRepository).delete(ID_2);
        verify(ontologyRepository).delete(ID_3);
    }

    private void mockOntologyTree() {
        final OntologyEntity parentOntologyEntity = OntologyCreatorsUtils.ontologyEntity(null);
        parentOntologyEntity.setId(ID);
        final Ontology parentOntology = OntologyCreatorsUtils.ontology(null);
        parentOntology.toBuilder().id(ID).build();
        doReturn(parentOntology).when(mapper).toDto(parentOntologyEntity);
        doReturn(parentOntologyEntity).when(mapper).toEntity(parentOntology);
        doReturn(parentOntologyEntity).when(ontologyRepository).findOne(ID);

        final OntologyEntity ontologyEntity1 = mockOntology(parentOntologyEntity, parentOntology, ID_2);
        final OntologyEntity ontologyEntity2 = mockOntology(parentOntologyEntity, parentOntology, ID_3);

        final Iterable<OntologyEntity> parentIterable = () ->
                Arrays.asList(ontologyEntity1, ontologyEntity2).iterator();
        doReturn(parentIterable).when(ontologyRepository).findByParent_Id(ID);
        doReturn(EMPTY_ITERABLE).when(ontologyRepository).findByParent_Id(ID_2);
        doReturn(EMPTY_ITERABLE).when(ontologyRepository).findByParent_Id(ID_3);
    }

    private OntologyEntity mockOntology(final OntologyEntity parentOntologyEntity,
                                        final Ontology parentOntology, final long id) {
        final OntologyEntity ontologyEntity = OntologyCreatorsUtils.ontologyEntity(parentOntologyEntity);
        ontologyEntity.setId(id);
        final Ontology ontology = OntologyCreatorsUtils.ontology(parentOntology)
                .toBuilder()
                .id(id)
                .build();
        doReturn(ontology).when(mapper).toDto(ontologyEntity);
        doReturn(ontologyEntity).when(mapper).toEntity(ontology);
        return ontologyEntity;
    }
}
