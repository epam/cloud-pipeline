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

package com.epam.pipeline.dao.pipeline;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.StopServerlessRun;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Transactional
public class StopServerlessRunDaoTest extends AbstractSpringTest {

    private static final Long TEST_STOP_AFTER = 60L;

    @Autowired
    private StopServerlessRunDao stopServerlessRunDao;
    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Test
    public void crud() {
        final PipelineRun pipelineRun = pipelineRun();
        pipelineRunDao.createPipelineRun(pipelineRun);

        final LocalDateTime firstUpdate = LocalDateTime.now();
        final StopServerlessRun stopServerlessRun = StopServerlessRun.builder()
                .runId(pipelineRun.getId())
                .lastUpdate(firstUpdate)
                .stopAfter(TEST_STOP_AFTER)
                .build();
        stopServerlessRunDao.createServerlessRun(stopServerlessRun);
        assertNotNull(stopServerlessRun.getId());

        assertEquals(stopServerlessRunDao.loadAll().size(), 1);

        final LocalDateTime newUpdate = firstUpdate.plusHours(1);
        stopServerlessRun.setLastUpdate(newUpdate);
        stopServerlessRunDao.updateServerlessRun(stopServerlessRun);

        final List<StopServerlessRun> loaded = stopServerlessRunDao.loadAll();
        assertEquals(loaded.size(), 1);
        assertEquals(loaded.get(0).getLastUpdate(), newUpdate);

        final Optional<StopServerlessRun> loadedRun = stopServerlessRunDao.loadByRunId(pipelineRun.getId());
        assertTrue(loadedRun.isPresent());
        assertEquals(loadedRun.get().getRunId(), stopServerlessRun.getRunId());
        assertEquals(loadedRun.get().getStopAfter(), stopServerlessRun.getStopAfter());

        stopServerlessRunDao.deleteByRunId(pipelineRun.getId());

        assertEquals(stopServerlessRunDao.loadAll().size(), 0);

        pipelineRunDao.deleteRunsByPipeline(1L);
    }

    private PipelineRun pipelineRun() {
        return ObjectCreatorUtils.createPipelineRun(null, null, null, 1L);
    }
}
