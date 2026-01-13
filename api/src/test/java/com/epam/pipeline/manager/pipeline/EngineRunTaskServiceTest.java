package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.entity.pipeline.run.EngineTaskStatus;
import com.epam.pipeline.entity.pipeline.run.EngineType;
import com.epam.pipeline.entity.run.EngineRunTaskGroupStatsEntity;
import com.epam.pipeline.entity.run.EngineRunTaskStatsEntity;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(Parameterized.class)
public class EngineRunTaskServiceTest {

    public static final Date NOW = new Date();
    public static final int HOUR_IN_MILLS = 3600000;
    public static final Date ERLIEST_START_DATE = new Date(NOW.toInstant().toEpochMilli() - HOUR_IN_MILLS);
    public static final Date MIDDLE_START_DATE = new Date(NOW.toInstant().toEpochMilli() - HOUR_IN_MILLS / 2);
    public static final String TASK_GROUP_2 = "TaskGroup2";
    public static final String TASK_GROUP_1 = "TaskGroup1";

    public EngineRunTaskServiceTest(List<EngineRunTaskStatsEntity> testCase,
                                    Map<String, EngineRunTaskGroupStatsEntity> expected) {
        this.testCase = testCase;
        this.expected = expected;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            // SUBMITTED without startDate works fine
            {
                    Arrays.asList(
                            EngineRunTaskStatsEntity.builder()
                                    .engineType(EngineType.NEXTFLOW)
                                    .taskGroup(TASK_GROUP_1)
                                    .tasksCount(4L)
                                    .status(EngineTaskStatus.SUBMITTED)
                                    .build()
                    ),
                new HashMap<String, EngineRunTaskGroupStatsEntity>() {{
                        put(
                            TASK_GROUP_1,
                            EngineRunTaskGroupStatsEntity.builder()
                                    .taskGroup(TASK_GROUP_1)
                                    .statusCounts(Collections.singletonMap(EngineTaskStatus.SUBMITTED, 4L))
                                    .build()
                        );
                    }}
            },
            // Earliest date will be taken
            {
                    Arrays.asList(
                            EngineRunTaskStatsEntity.builder()
                                    .engineType(EngineType.NEXTFLOW)
                                    .taskGroup(TASK_GROUP_1)
                                    .tasksCount(4L)
                                    .startDateTime(MIDDLE_START_DATE)
                                    .status(EngineTaskStatus.ABORTED)
                                    .build(),
                            EngineRunTaskStatsEntity.builder()
                                    .engineType(EngineType.NEXTFLOW)
                                    .taskGroup(TASK_GROUP_1)
                                    .tasksCount(1L)
                                    .status(EngineTaskStatus.RUNNING)
                                    .startDateTime(ERLIEST_START_DATE)
                                    .build()
                    ),
                new HashMap<String, EngineRunTaskGroupStatsEntity>() {{
                        put(
                                TASK_GROUP_1,
                                EngineRunTaskGroupStatsEntity.builder()
                                        .taskGroup(TASK_GROUP_1)
                                        .statusCounts(
                                                new HashMap<EngineTaskStatus, Long>() {{
                                                    put(EngineTaskStatus.ABORTED, 4L);
                                                    put(EngineTaskStatus.RUNNING, 1L);
                                                }}
                                        )
                                        .startDateTime(ERLIEST_START_DATE).build()
                        );
                    }}
            },
            // several task groups can be grouped normally
            {
                    Arrays.asList(
                            EngineRunTaskStatsEntity.builder()
                                    .engineType(EngineType.NEXTFLOW)
                                    .taskGroup(TASK_GROUP_1)
                                    .tasksCount(4L)
                                    .status(EngineTaskStatus.SUBMITTED)
                                    .build(),
                            EngineRunTaskStatsEntity.builder()
                                    .engineType(EngineType.NEXTFLOW)
                                    .taskGroup(TASK_GROUP_1)
                                    .tasksCount(1L)
                                    .status(EngineTaskStatus.RUNNING)
                                    .startDateTime(ERLIEST_START_DATE)
                                    .build(),
                            EngineRunTaskStatsEntity.builder()
                                    .engineType(EngineType.NEXTFLOW)
                                    .taskGroup(TASK_GROUP_2)
                                    .tasksCount(5L)
                                    .status(EngineTaskStatus.COMPLETED)
                                    .startDateTime(MIDDLE_START_DATE)
                                    .build()
                    ),
                new HashMap<String, EngineRunTaskGroupStatsEntity>() {{
                        put(
                            TASK_GROUP_1,
                            EngineRunTaskGroupStatsEntity.builder()
                                    .taskGroup(TASK_GROUP_1)
                                    .statusCounts(
                                            new HashMap<EngineTaskStatus, Long>() {{
                                                put(EngineTaskStatus.SUBMITTED, 4L);
                                                put(EngineTaskStatus.RUNNING, 1L);
                                            }}
                                    )
                                    .startDateTime(ERLIEST_START_DATE).build()
                        );
                        put(
                            TASK_GROUP_2,
                            EngineRunTaskGroupStatsEntity.builder()
                                    .taskGroup(TASK_GROUP_2)
                                    .statusCounts(Collections.singletonMap(EngineTaskStatus.COMPLETED, 5L))
                                    .startDateTime(MIDDLE_START_DATE).build()
                        );
                    }}
            }
        });
    }

    private final List<EngineRunTaskStatsEntity> testCase;
    private final Map<String, EngineRunTaskGroupStatsEntity> expected;

    @Test
    public void calculateTaskGroupStatistic() {
        Map<String, EngineRunTaskGroupStatsEntity> actual = EngineRunTaskService.calculateTaskGroupStatistic(testCase);
        Assert.assertEquals(actual.keySet(), expected.keySet());
        actual.forEach((tgn, actualStats) -> {
            EngineRunTaskGroupStatsEntity expectedStats = expected.get(tgn);
            Assert.assertEquals(actualStats.getStartDateTime(), expectedStats.getStartDateTime());
            Assert.assertEquals(actualStats.getStatusCounts(), expectedStats.getStatusCounts());
        });
    }
}