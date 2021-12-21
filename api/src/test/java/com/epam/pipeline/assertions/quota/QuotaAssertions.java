/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.assertions.quota;

import com.epam.pipeline.dto.quota.Quota;
import com.epam.pipeline.dto.quota.QuotaAction;
import com.epam.pipeline.dto.quota.QuotaActionType;
import com.epam.pipeline.entity.quota.QuotaActionEntity;
import com.epam.pipeline.entity.quota.QuotaEntity;
import com.epam.pipeline.entity.quota.QuotaSidEntity;
import com.epam.pipeline.entity.user.Sid;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public interface QuotaAssertions {

    static void assertEquals(final QuotaEntity entity, final Quota dto) {
        assertNulls(entity, dto);
        assertThat(entity.getId(), is(dto.getId()));
        assertThat(entity.getSubject(), is(dto.getSubject()));
        assertThat(entity.getQuotaGroup(), is(dto.getQuotaGroup()));
        assertThat(entity.getType(), is(dto.getType()));
        assertActionEntitiesAndDtos(entity.getActions(), dto.getActions());
        assertQuotaSidEntitiesAndSids(entity.getRecipients(), dto.getRecipients());
    }

    static void assertEquals(final QuotaEntity first, final QuotaEntity second) {
        assertNulls(first, second);
        assertThat(first.getId(), is(second.getId()));
        assertThat(first.getSubject(), is(second.getSubject()));
        assertThat(first.getQuotaGroup(), is(second.getQuotaGroup()));
        assertThat(first.getType(), is(second.getType()));
        assertActions(first.getActions(), second.getActions());
        assertRecipients(first.getRecipients(), second.getRecipients());
    }

    static void assertActionEntitiesAndDtos(final List<QuotaActionEntity> entities, final List<QuotaAction> dtos) {
        assertEmptyCollections(entities, dtos);
        assertThat(entities.size(), is(dtos.size()));
        final Map<Long, QuotaActionEntity> entitiesById = entities.stream()
                .collect(Collectors.toMap(QuotaActionEntity::getId, Function.identity()));
        dtos.forEach(action -> assertEquals(entitiesById.get(action.getId()), action));
    }

    static void assertActions(final List<QuotaActionEntity> first, final List<QuotaActionEntity> second) {
        assertEmptyCollections(first, second);
        assertThat(first.size(), is(second.size()));
        final Map<Long, QuotaActionEntity> firstById = first.stream()
                .collect(Collectors.toMap(QuotaActionEntity::getId, Function.identity()));
        second.forEach(action -> assertEquals(action, firstById.get(action.getId())));
    }

    static void assertEquals(final QuotaActionEntity entity, final QuotaAction dto) {
        assertNulls(entity, dto);
        assertThat(entity.getId(), is(dto.getId()));
        assertThat(entity.getThreshold(), is(dto.getThreshold()));
        assertEmptyCollections(entity.getActions(), dto.getActions());
        assertThat(entity.getActions().size(), is(dto.getActions().size()));
        assertThat(
                entity.getActions().stream()
                        .map(QuotaActionType::name)
                        .collect(Collectors.toList()),
                hasItems(dto.getActions().stream()
                        .map(QuotaActionType::name)
                        .toArray()));
    }

    static void assertEquals(final QuotaActionEntity first, final QuotaActionEntity second) {
        assertNulls(first, second);
        assertThat(first.getId(), is(second.getId()));
        assertThat(first.getThreshold(), is(second.getThreshold()));
        assertThat(first.getActions().size(), is(second.getActions().size()));
        assertThat(
                first.getActions().stream()
                        .map(QuotaActionType::name)
                        .collect(Collectors.toList()),
                hasItems(second.getActions().stream()
                        .map(QuotaActionType::name)
                        .toArray()));
    }

    static void assertRecipients(final List<QuotaSidEntity> first, final List<QuotaSidEntity> second) {
        assertEmptyCollections(first, second);
        assertThat(first.size(), is(second.size()));
        assertThat(new ArrayList<>(first), hasItems(second.toArray()));
    }

    static void assertQuotaSidEntitiesAndSids(final List<QuotaSidEntity> entities, final List<Sid> sids) {
        assertEmptyCollections(entities, sids);
        assertThat(entities.size(), is(sids.size()));

        assertThat(
                entities.stream()
                        .map(entity -> {
                            final Sid sid = new Sid();
                            sid.setName(entity.getName());
                            sid.setPrincipal(entity.isPrincipal());
                            return sid;
                        })
                        .collect(Collectors.toList()),
                hasItems(sids.toArray()));
    }

    static void assertNulls(final Object first, final Object second) {
        if (first == null && second == null) {
            return;
        }
        if (first == null || second == null) {
            throw new AssertionError("One of the asserted objects is null");
        }
    }

    static void assertEmptyCollections(final Collection<?> first, final Collection<?> second) {
        if (CollectionUtils.isEmpty(first) && CollectionUtils.isEmpty(second)) {
            return;
        }
        if (CollectionUtils.isEmpty(first) || CollectionUtils.isEmpty(second)) {
            throw new AssertionError("One of the asserted collections is empty");
        }
    }
}
