/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.pipeline.run.EngineRunTaskSortVO;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.EngineRunTask;
import com.epam.pipeline.entity.pipeline.run.EngineRunTaskFilter;
import com.epam.pipeline.entity.pipeline.run.EngineTaskStatus;
import com.epam.pipeline.entity.pipeline.run.EngineType;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import com.epam.pipeline.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class EngineRunTaskDaoTest extends AbstractJdbcTest {
    private static final String TEST = "TEST";
    private static final String TEST2 = "TEST2";
    private static final String TASK_GROUP_1 = "Process1";
    private static final String TASK_GROUP_2 = "Process2";
    private static final String TAG = "tag";
    private static final String HASH = "Hash";
    private static final String TASK_1 = "Task1";
    private static final String TASK_2 = "Task2";
    private static final String TASK_3 = "Task3";
    private static final String TASK_4 = "Task4";
    private static final String TASK_5 = "Task5";
    private static final String TASK_6 = "Task6";
    private static final String TASK_7 = "Task7";
    private static final int PAGE_SIZE = 20;

    @Autowired
    private PipelineRunDao pipelineRunDao;
    @Autowired
    private EngineRunTaskDao engineRunTaskDao;

    @Test
    public void shouldBatchInsertEngineRunEvents() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        final EngineRunTask event1 = event(run.getId(), TEST);
        engineRunTaskDao.batchUpsert(Collections.singletonList(event1));

        event1.setStatus(EngineTaskStatus.COMPLETED);
        event1.setEndDateTime(new Date());
        final EngineRunTask event2 = event(run.getId(), TEST2);
        engineRunTaskDao.batchUpsert(Arrays.asList(event1, event2));

        assertThat(engineRunTaskDao.filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW,
                pagingBuilder().build())).hasSize(2);
    }

    @Test
    public void shouldLoadEngineRunTasksStats() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        engineRunTaskDao.batchUpsert(tasks(run.getId()));

        assertThat(engineRunTaskDao.loadStats(run.getId(), EngineType.NEXTFLOW)).hasSize(4);
    }

    @Test
    public void shouldFilterEngineRunTasksByTaskGroup() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        engineRunTaskDao.batchUpsert(tasks(run.getId()));

        final EngineRunTaskFilter filter = pagingBuilder().taskGroup("1").build();
        final List<EngineRunTask> actual = engineRunTaskDao.filterTasksByRunIdAndTypeAndFilter(
                run.getId(), EngineType.NEXTFLOW, filter);

        assertThat(actual).hasSize(3);
        actual.forEach(task -> assertThat(task.getTaskGroup()).isEqualTo(TASK_GROUP_1));
        assertThat(engineRunTaskDao.countTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW, filter))
                .isEqualTo(3);
    }

    @Test
    public void shouldFilterEngineRunTasksByTaskId() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        engineRunTaskDao.batchUpsert(tasks(run.getId()));

        final EngineRunTaskFilter filter = pagingBuilder().taskId("1").build();
        final List<EngineRunTask> actual = engineRunTaskDao
                .filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW, filter);
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0).getTaskId()).isEqualTo(TASK_1);
        assertThat(engineRunTaskDao.countTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW, filter))
                .isEqualTo(1);
    }

    @Test
    public void shouldFilterEngineRunTasksByTaskKey() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        engineRunTaskDao.batchUpsert(tasks(run.getId()));

        final EngineRunTaskFilter filter = pagingBuilder().taskKey(HASH).build();
        final List<EngineRunTask> actual = engineRunTaskDao
                .filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW, filter);
        assertThat(actual).hasSize(2);
        actual.forEach(task -> assertThat(task.getTaskKey()).containsIgnoringCase(HASH));
        assertThat(engineRunTaskDao.countTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW, filter))
                .isEqualTo(2);
    }

    @Test
    public void shouldFilterEngineRunTasksByTaskTag() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        engineRunTaskDao.batchUpsert(tasks(run.getId()));

        final EngineRunTaskFilter filter = pagingBuilder().taskTag(TAG).build();
        final List<EngineRunTask> actual = engineRunTaskDao
                .filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW, filter);
        assertThat(actual).hasSize(1);
        actual.forEach(task -> assertThat(task.getTaskTag()).containsIgnoringCase(TAG));
        assertThat(engineRunTaskDao.countTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW, filter))
                .isEqualTo(1);
    }

    @Test
    public void shouldFilterEngineRunTasksByStatus() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        engineRunTaskDao.batchUpsert(tasks(run.getId()));

        final EngineRunTaskFilter filter = pagingBuilder()
                .statuses(Collections.singletonList(EngineTaskStatus.RUNNING))
                .build();
        final List<EngineRunTask> actual = engineRunTaskDao
                .filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW, filter);
        assertThat(actual).hasSize(4);
        actual.forEach(task -> assertThat(task.getStatus()).isEqualTo(EngineTaskStatus.RUNNING));
        assertThat(engineRunTaskDao.countTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW, filter))
                .isEqualTo(4);
    }

    @Test
    public void shouldFilterEngineRunTasksByNotMatchingStatus() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        engineRunTaskDao.batchUpsert(tasks(run.getId()));

        final EngineRunTaskFilter filter = pagingBuilder()
                .statuses(Collections.singletonList(EngineTaskStatus.ABORTED))
                .build();
        final List<EngineRunTask> actual = engineRunTaskDao
                .filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW, filter);
        assertThat(actual).hasSize(0);
        assertThat(engineRunTaskDao.countTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW, filter))
                .isEqualTo(0);
    }

    @Test
    public void shouldFilterEngineRunTasksWithSortAndPaging() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        engineRunTaskDao.batchUpsert(tasks(run.getId()));

        final List<EngineRunTask> page1 = engineRunTaskDao
                .filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW,
                        pagingBuilder()
                                .pageSize(2)
                                .sorts(Collections.singletonList(EngineRunTaskSortVO.builder()
                                        .column(EngineRunTaskSortVO.Columns.taskId)
                                        .build()))
                                .build());
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).getTaskId()).isEqualTo(TASK_1);
        assertThat(page1.get(1).getTaskId()).isEqualTo(TASK_2);

        final List<EngineRunTask> page2 = engineRunTaskDao
                .filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW,
                        EngineRunTaskFilter.builder()
                                .page(2)
                                .pageSize(2)
                                .sorts(Collections.singletonList(EngineRunTaskSortVO.builder()
                                        .column(EngineRunTaskSortVO.Columns.taskId)
                                        .build()))
                                .build());
        assertThat(page2).hasSize(2);
        assertThat(page2.get(0).getTaskId()).isEqualTo(TASK_3);
        assertThat(page2.get(1).getTaskId()).isEqualTo(TASK_4);
    }

    @Test
    public void shouldFilterEngineRunTasksWithSortByStartDateAndPaging() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        engineRunTaskDao.batchUpsert(tasks(run.getId()));

        List<EngineRunTask> page = engineRunTaskDao
                .filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW,
                        pagingBuilder()
                                .pageSize(3)
                                .sorts(Collections.singletonList(EngineRunTaskSortVO.builder()
                                        .column(EngineRunTaskSortVO.Columns.startDate)
                                        .descending(true)
                                        .build()))
                                .build());
        assertThat(page).hasSize(3);
        assertThat(page.get(0).getTaskId()).isEqualTo(TASK_6);
        assertThat(page.get(1).getTaskId()).isEqualTo(TASK_5);
        assertThat(page.get(2).getTaskId()).isEqualTo(TASK_4);

        page = engineRunTaskDao
                .filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW,
                        pagingBuilder()
                                .pageSize(3)
                                .sorts(Collections.singletonList(EngineRunTaskSortVO.builder()
                                        .column(EngineRunTaskSortVO.Columns.startDate)
                                        .descending(false)
                                        .build()))
                                .build());
        assertThat(page).hasSize(3);
        assertThat(page.get(0).getTaskId()).isEqualTo(TASK_1);
        assertThat(page.get(1).getTaskId()).isEqualTo(TASK_2);
        assertThat(page.get(2).getTaskId()).isEqualTo(TASK_3);
    }

    @Test
    public void shouldFilterEngineRunTasksWithSortByEndDateAndPaging() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        engineRunTaskDao.batchUpsert(tasks(run.getId()));

        List<EngineRunTask> page = engineRunTaskDao
                .filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW,
                        pagingBuilder()
                                .pageSize(2)
                                .sorts(Collections.singletonList(EngineRunTaskSortVO.builder()
                                        .column(EngineRunTaskSortVO.Columns.endDate)
                                        .descending(true)
                                        .build()))
                                .build());
        assertThat(page).hasSize(2);
        assertThat(page.get(0).getTaskId()).isEqualTo(TASK_3);
        assertThat(page.get(1).getTaskId()).isEqualTo(TASK_6);

        page = engineRunTaskDao
                .filterTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW,
                        pagingBuilder()
                                .pageSize(2)
                                .sorts(Collections.singletonList(EngineRunTaskSortVO.builder()
                                        .column(EngineRunTaskSortVO.Columns.endDate)
                                        .descending(false)
                                        .build()))
                                .build());
        assertThat(page).hasSize(2);
        assertThat(page.get(0).getTaskId()).isEqualTo(TASK_6);
        assertThat(page.get(1).getTaskId()).isEqualTo(TASK_3);
    }

    @Test
    public void shouldCalculateCountOfEngineRunTasks() {
        final PipelineRun run = run();
        pipelineRunDao.createPipelineRun(run);

        engineRunTaskDao.batchUpsert(tasks(run.getId()));

        assertThat(engineRunTaskDao.countTasksByRunIdAndTypeAndFilter(run.getId(), EngineType.NEXTFLOW,
                EngineRunTaskFilter.builder().build())).isEqualTo(7);
    }

    private List<EngineRunTask> tasks(final Long runId) {
        final EngineRunTask runningEvent11 = event(runId, TEST);
        runningEvent11.setTaskId(TASK_1);
        runningEvent11.setTaskGroup(TASK_GROUP_1);
        runningEvent11.setTaskTag(TAG);
        runningEvent11.setStatus(EngineTaskStatus.RUNNING);
        runningEvent11.setStartDateTime(Date.from(DateUtils.now().toInstant().minus(10L, ChronoUnit.MINUTES)));

        final EngineRunTask runningEvent12 = event(runId, TEST);
        runningEvent12.setTaskId(TASK_2);
        runningEvent12.setTaskGroup(TASK_GROUP_1);
        runningEvent12.setStatus(EngineTaskStatus.RUNNING);
        runningEvent12.setStartDateTime(Date.from(DateUtils.now().toInstant().minus(9L, ChronoUnit.MINUTES)));

        final EngineRunTask completedEvent11 = event(runId, TEST);
        completedEvent11.setTaskId(TASK_3);
        completedEvent11.setTaskGroup(TASK_GROUP_1);
        completedEvent11.setStatus(EngineTaskStatus.COMPLETED);
        completedEvent11.setStartDateTime(Date.from(DateUtils.now().toInstant().minus(8L, ChronoUnit.MINUTES)));
        completedEvent11.setEndDateTime(DateUtils.now());

        final EngineRunTask runningEvent21 = event(runId, TEST);
        runningEvent21.setTaskId(TASK_4);
        runningEvent21.setTaskGroup(TASK_GROUP_2);
        runningEvent21.setTaskKey(TEST + HASH.toUpperCase(Locale.ROOT));
        runningEvent21.setStatus(EngineTaskStatus.RUNNING);
        runningEvent21.setStartDateTime(Date.from(DateUtils.now().toInstant().minus(7L, ChronoUnit.MINUTES)));


        final EngineRunTask runningEvent22 = event(runId, TEST);
        runningEvent22.setTaskId(TASK_5);
        runningEvent22.setTaskGroup(TASK_GROUP_2);
        runningEvent22.setStatus(EngineTaskStatus.RUNNING);
        runningEvent22.setStartDateTime(Date.from(DateUtils.now().toInstant().minus(6L, ChronoUnit.MINUTES)));

        final EngineRunTask completedEvent21 = event(runId, TEST);
        completedEvent21.setTaskId(TASK_6);
        completedEvent21.setTaskGroup(TASK_GROUP_2);
        completedEvent21.setTaskKey(TEST + HASH.toLowerCase(Locale.ROOT));
        completedEvent21.setStatus(EngineTaskStatus.COMPLETED);
        completedEvent21.setStartDateTime(Date.from(DateUtils.now().toInstant().minus(5L, ChronoUnit.MINUTES)));
        completedEvent21.setEndDateTime(Date.from(DateUtils.now().toInstant().minus(1L, ChronoUnit.MINUTES)));

        final EngineRunTask emptyGroupEvent = event(runId, TEST);
        emptyGroupEvent.setTaskId(TASK_7);
        emptyGroupEvent.setTaskGroup(null);
        emptyGroupEvent.setStatus(EngineTaskStatus.COMPLETED);

        return Arrays.asList(
                runningEvent11, runningEvent12, completedEvent11,
                runningEvent21, runningEvent22, completedEvent21,
                emptyGroupEvent);
    }

    private EngineRunTask event(final Long runId, final String task) {
        return EngineRunTask.builder()
                .runId(runId)
                .taskGroup(task)
                .taskTag(task)
                .taskId(task)
                .taskKey(task)
                .taskName(task)
                .status(EngineTaskStatus.CREATED)
                .engineType(EngineType.NEXTFLOW)
                .attributes("{\"test\": \"JSON object\"}")
                .build();
    }

    private PipelineRun run() {
        return TestUtils.createPipelineRun(null, null, TaskStatus.RUNNING, "USER",
                null, null, true, null, null, "POD", 1L);
    }

    private EngineRunTaskFilter.EngineRunTaskFilterBuilder pagingBuilder() {
        return EngineRunTaskFilter.builder().page(1).pageSize(PAGE_SIZE);
    }
}
