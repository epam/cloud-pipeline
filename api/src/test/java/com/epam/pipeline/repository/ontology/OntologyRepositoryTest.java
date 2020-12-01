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

import com.epam.pipeline.assertions.ontology.OntologyAssertions;
import com.epam.pipeline.entity.ontology.OntologyEntity;
import com.epam.pipeline.test.repository.AbstractJpaTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.epam.pipeline.test.creator.ontology.OntologyCreatorsUtils.ontologyEntity;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class OntologyRepositoryTest extends AbstractJpaTest {
    private static final String ATTRIBUTE_KEY = "attribute_key";
    private static final String ATTRIBUTE_VALUE = "attribute_value";

    @Autowired
    private OntologyRepository ontologyRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    @Transactional
    public void crudTest() {
        final OntologyEntity parentOntology = ontologyEntity(null);
        final OntologyEntity savedParentOntology = ontologyRepository.save(parentOntology);

        assertThat(savedParentOntology.getId(), notNullValue());
        final OntologyEntity storedParentOntology = ontologyRepository.findOne(savedParentOntology.getId());
        OntologyAssertions.assertEquals(parentOntology, storedParentOntology);

        final OntologyEntity childOntology = ontologyEntity(parentOntology);
        childOntology.setParent(parentOntology);
        childOntology.setAttributes(Collections.singletonMap(ATTRIBUTE_KEY, ATTRIBUTE_VALUE));
        ontologyRepository.save(childOntology);

        assertThat(childOntology.getId(), notNullValue());
        assertThat(childOntology.getParent(), notNullValue());

        entityManager.clear();

        final OntologyEntity storedOntologyEntity = ontologyRepository.findOne(childOntology.getId());

        final Map<String, String> storedAttributes = storedOntologyEntity.getAttributes();
        assertThat(storedAttributes.get(ATTRIBUTE_KEY), is(ATTRIBUTE_VALUE));
        final OntologyEntity storedParent = storedOntologyEntity.getParent();
        assertThat(storedParent, notNullValue());
        assertThat(parentOntology.getId(), is(storedParent.getId()));
    }

    @Test
    @Transactional
    public void shouldLoadOntologyByParent() {
        final OntologyEntity rootOntology = ontologyRepository.save(ontologyEntity(null));
        ontologyRepository.save(ontologyEntity(rootOntology));
        ontologyRepository.save(ontologyEntity(rootOntology));

        entityManager.clear();

        final Iterable<OntologyEntity> children = ontologyRepository.findByParent_Id(rootOntology.getId());
        assertThat(StreamSupport.stream(children.spliterator(), false).count(), is(2L));
        children.forEach(child -> assertThat(child.getParent().getId(), is(rootOntology.getId())));
    }

    @Test
    @Transactional
    public void shouldLoadRoots() {
        final OntologyEntity ontology = ontologyRepository.save(ontologyEntity(null));
        ontologyRepository.save(ontologyEntity(null));
        ontologyRepository.save(ontologyEntity(ontology));

        entityManager.clear();

        final Iterable<OntologyEntity> roots = ontologyRepository.findByParentIsNullAndType(ontology.getType());
        assertThat(StreamSupport.stream(roots.spliterator(), false).count(), is(2L));
    }
}
