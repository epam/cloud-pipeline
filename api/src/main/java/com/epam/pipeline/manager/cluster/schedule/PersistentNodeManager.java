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

package com.epam.pipeline.manager.cluster.schedule;

import com.epam.pipeline.controller.vo.cluster.schedule.PersistentNodeVO;
import com.epam.pipeline.dao.cluster.schedule.PersistentNodeDao;
import com.epam.pipeline.entity.cluster.schedule.PersistentNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PersistentNodeManager {

    private final PersistentNodeDao nodeDao;

    public List<PersistentNode> getActiveNodes() {
        return nodeDao.loadAll();
    }

    @Transactional
    public PersistentNode createOrUpdate(final PersistentNodeVO vo) {
        return null;
    }

    public List<PersistentNode> loadAll() {
        return  nodeDao.loadAll();
    }

    public PersistentNode load(final long id) {
        return null;
    }

    @Transactional
    public PersistentNode delete(final long id) {
        final PersistentNode node = load(id);
        nodeDao.delete(id);
        return node;
    }
}
