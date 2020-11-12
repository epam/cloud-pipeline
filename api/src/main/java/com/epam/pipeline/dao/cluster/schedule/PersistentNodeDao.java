/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.dao.cluster.schedule;

import com.epam.pipeline.entity.cluster.schedule.PersistentNode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class PersistentNodeDao extends NamedParameterJdbcDaoSupport {

    public List<PersistentNode> loadAll() {
        return Collections.emptyList();
    }

    public PersistentNode load(final long id) {
        return null;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(final long id) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PersistentNode create(final PersistentNode node) {
        return null;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PersistentNode update(final PersistentNode node) {
        return null;
    }
}
