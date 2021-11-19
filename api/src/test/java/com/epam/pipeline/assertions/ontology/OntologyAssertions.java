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


package com.epam.pipeline.assertions.ontology;

import com.epam.pipeline.dto.ontology.Ontology;
import com.epam.pipeline.entity.ontology.OntologyEntity;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public final class OntologyAssertions {

    private OntologyAssertions() {
        // no-op
    }

    public static void assertEquals(final OntologyEntity first, final OntologyEntity second) {
        if (first == null && second == null) {
            return;
        }
        assertThat(first.getId(), is(second.getId()));
        assertThat(first.getExternalId(), is(second.getExternalId()));
        assertThat(first.getName(), is(second.getName()));
        assertThat(first.getParent(), is(second.getParent()));
        assertThat(first.getAttributes(), is(second.getAttributes()));
    }

    public static void assertEquals(final OntologyEntity entity, final Ontology dto) {
        if (entity == null && dto == null) {
            return;
        }
        assertThat(entity.getId(), is(dto.getId()));
        assertThat(entity.getExternalId(), is(dto.getExternalId()));
        assertThat(entity.getName(), is(dto.getName()));
        assertThat(entity.getParent(), is(dto.getParent()));
        assertThat(entity.getAttributes(), is(dto.getAttributes()));
    }
}
