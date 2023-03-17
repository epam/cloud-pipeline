/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.run.RestartRun;
import com.epam.pipeline.entity.pipeline.run.parameter.PipelineRunParameter;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.RunRegionShiftPolicy;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import com.epam.pipeline.test.creator.region.RegionCreatorUtils;
import org.junit.Test;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_NAME;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.notInvoked;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RunRegionShiftHandlerTest {

    private final PipelineRunManager pipelineRunManager = mock(PipelineRunManager.class);
    private final RestartRunManager restartRunManager = mock(RestartRunManager.class);
    private final CloudRegionManager cloudRegionManager = mock(CloudRegionManager.class);
    private final RunLogManager runLogManager = mock(RunLogManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final RunRegionShiftHandler runRegionShiftHandler = new RunRegionShiftHandler(
            pipelineRunManager, cloudRegionManager, restartRunManager, runLogManager, messageHelper);

    private static final Long PARENT_RUN_ID = 1L;
    private static final Long CURRENT_RUN_ID = 2L;
    private static final Long AVAILABLE_REGION_ID = 3L;
    private static final Long NEXT_AVAILABLE_REGION_ID = 4L;
    private static final Long PARENT_AVAILABLE_REGION_ID = 6L;
    private static final Long NOT_AVAILABLE_REGION_ID = 5L;

    @Test
    public void shouldRestartRunWhenParentRunNotRestarted() {
        final RunInstance runInstance = getInstance(AVAILABLE_REGION_ID);
        final PipelineRun currentRun = getCurrentRun(runInstance);
        currentRun.setPipelineRunParameters(Collections.singletonList(getCloudIndependentRunParameter()));

        firstRestartCaseMock(currentRun);
        doReturn(getRegions()).when(cloudRegionManager).loadAll();
        doReturn(restartedRun()).when(pipelineRunManager).restartRun(expectedSuccessfulRun());

        runRegionShiftHandler.restartRunInAnotherRegion(CURRENT_RUN_ID);
        verify(pipelineRunManager).restartRun(expectedSuccessfulRun());
        verify(pipelineRunManager).stop(CURRENT_RUN_ID);
    }

    @Test
    public void shouldRestartRunWhenParentRunWasRestarted() {
        final RunInstance runInstance = getInstance(AVAILABLE_REGION_ID);
        final PipelineRun currentRun = getCurrentRun(runInstance);
        currentRun.setPipelineRunParameters(Collections.singletonList(getCloudIndependentRunParameter()));
        final PipelineRun parentRun = getParentRun(PARENT_AVAILABLE_REGION_ID);

        doReturn(Optional.of(getRestartRun())).when(restartRunManager).findRestartRunById(CURRENT_RUN_ID);
        doReturn(parentRun).when(pipelineRunManager).loadPipelineRunWithRestartedRuns(PARENT_RUN_ID);
        doReturn(getRegionsWithParent()).when(cloudRegionManager).loadAll();
        doReturn(Collections.singletonList(currentRun)).when(pipelineRunManager)
                .loadPipelineRuns(Collections.singletonList(CURRENT_RUN_ID));
        doReturn(currentRun).when(pipelineRunManager).loadPipelineRun(CURRENT_RUN_ID);
        doReturn(restartedRun()).when(pipelineRunManager).restartRun(expectedSuccessfulParentRun());

        runRegionShiftHandler.restartRunInAnotherRegion(CURRENT_RUN_ID);
        verify(pipelineRunManager).restartRun(expectedSuccessfulParentRun());
        verify(pipelineRunManager).stop(CURRENT_RUN_ID);
    }

    @Test
    public void shouldNotRestartRunWhenCloudProviderIsNotDetermined() {
        final PipelineRun currentRun = getCurrentRun(new RunInstance());

        firstRestartCaseMock(currentRun);

        assertRunNotRestarted();
    }

    @Test
    public void shouldNotRestartRunWhenCloudRegionsAreNotFound() {
        final RunInstance runInstance = getInstance(ID);
        final PipelineRun currentRun = getCurrentRun(runInstance);

        firstRestartCaseMock(currentRun);
        doReturn(Collections.emptyList()).when(cloudRegionManager).loadAll();
        doReturn(getAvailableRegion()).when(cloudRegionManager).load(ID);

        assertRunNotRestarted();
    }

    @Test
    public void shouldNotRestartRunWhenCloudRegionsIdIsNotDetermined() {
        final RunInstance runInstance = getInstance(null);
        final PipelineRun currentRun = getCurrentRun(runInstance);

        firstRestartCaseMock(currentRun);
        doReturn(getRegions()).when(cloudRegionManager).loadAll();

        assertRunNotRestarted();
    }

    @Test
    public void shouldNotRestartRunWhenShiftIsNotAvailableForCurrentRegion() {
        final RunInstance runInstance = getInstance(NOT_AVAILABLE_REGION_ID);
        final PipelineRun currentRun = getCurrentRun(runInstance);

        firstRestartCaseMock(currentRun);
        doReturn(getRegions()).when(cloudRegionManager).loadAll();
        doReturn(getNotAvailableRegion()).when(cloudRegionManager).load(NOT_AVAILABLE_REGION_ID);

        assertRunNotRestarted();
    }

    @Test
    public void shouldNotRestartRunWhenNoNextRegionFound() {
        final RunInstance runInstance = getInstance(AVAILABLE_REGION_ID);
        final PipelineRun currentRun = getCurrentRun(runInstance);

        firstRestartCaseMock(currentRun);
        doReturn(Collections.singletonList(getAvailableRegion())).when(cloudRegionManager).loadAll();

        assertRunNotRestarted();
    }

    @Test
    public void shouldNotRestartRunWhenRunWithCloudDependentParameters() {
        final RunInstance runInstance = getInstance(AVAILABLE_REGION_ID);
        final PipelineRun currentRun = getCurrentRun(runInstance);
        currentRun.setPipelineRunParameters(Collections.singletonList(getCloudDependentRunParameter()));

        firstRestartCaseMock(currentRun);
        doReturn(getRegions()).when(cloudRegionManager).loadAll();

        assertRunNotRestarted();
    }

    @Test
    public void shouldNotRestartRunWhenClusterRun() {
        final RunInstance runInstance = getInstance(AVAILABLE_REGION_ID);
        final PipelineRun currentRun = getCurrentRun(runInstance);
        currentRun.setPipelineRunParameters(Collections.singletonList(getCloudIndependentRunParameter()));
        currentRun.setNodeCount(2);

        firstRestartCaseMock(currentRun);
        doReturn(getRegions()).when(cloudRegionManager).loadAll();

        assertRunNotRestarted();
    }

    @Test
    public void shouldNotRestartRunWhenWorker() {
        final RunInstance runInstance = getInstance(AVAILABLE_REGION_ID);
        final PipelineRun currentRun = getCurrentRun(runInstance);
        currentRun.setPipelineRunParameters(Collections.singletonList(getCloudIndependentRunParameter()));
        currentRun.setParentRunId(ID);

        firstRestartCaseMock(currentRun);
        doReturn(getRegions()).when(cloudRegionManager).loadAll();

        assertRunNotRestarted();
    }

    @Test
    public void shouldNotRestartRunWhenAllRegionsTried() {
        final RunInstance runInstance = getInstance(AVAILABLE_REGION_ID);
        final PipelineRun currentRun = getCurrentRun(runInstance);
        currentRun.setPipelineRunParameters(Collections.singletonList(getCloudIndependentRunParameter()));
        final PipelineRun parentRun = getParentRun(NEXT_AVAILABLE_REGION_ID);

        final List<AbstractCloudRegion> regions = getRegions();

        doReturn(Optional.of(getRestartRun())).when(restartRunManager).findRestartRunById(CURRENT_RUN_ID);
        doReturn(parentRun).when(pipelineRunManager).loadPipelineRunWithRestartedRuns(PARENT_RUN_ID);
        doReturn(regions).when(cloudRegionManager).loadAll();
        doReturn(Collections.singletonList(currentRun)).when(pipelineRunManager)
                .loadPipelineRuns(Collections.singletonList(CURRENT_RUN_ID));
        doReturn(currentRun).when(pipelineRunManager).loadPipelineRun(CURRENT_RUN_ID);

        assertRunNotRestarted();
    }

    private void assertRunNotRestarted() {
        runRegionShiftHandler.restartRunInAnotherRegion(CURRENT_RUN_ID);
        notInvoked(pipelineRunManager).restartRun(any());
        notInvoked(pipelineRunManager).stop(any());
    }

    private void firstRestartCaseMock(final PipelineRun currentRun) {
        doReturn(Optional.empty()).when(restartRunManager).findRestartRunById(CURRENT_RUN_ID);
        doReturn(currentRun).when(pipelineRunManager).loadPipelineRunWithRestartedRuns(CURRENT_RUN_ID);
    }

    private static List<AbstractCloudRegion> getRegions() {
        return Arrays.asList(
                getAvailableRegion(),
                getNotAvailableRegion(),
                getNotAvailableRegion(),
                getAnotherAvailableRegion(),
                getAnotherProviderRegion());
    }

    private static List<AbstractCloudRegion> getRegionsWithParent() {
        return Arrays.asList(
                getAvailableRegion(),
                getNotAvailableRegion(),
                getNotAvailableRegion(),
                getAnotherAvailableRegion(),
                getAnotherProviderRegion(),
                getParentRegion());
    }

    private static AbstractCloudRegion getAvailableRegion() {
        final AwsRegion region = RegionCreatorUtils.getDefaultAwsRegion(AVAILABLE_REGION_ID);
        region.setRunShiftPolicy(RunRegionShiftPolicy.builder().shiftEnabled(true).build());
        return region;
    }

    private static AbstractCloudRegion getNotAvailableRegion() {
        final AwsRegion region = RegionCreatorUtils.getDefaultAwsRegion(NOT_AVAILABLE_REGION_ID);
        region.setRunShiftPolicy(RunRegionShiftPolicy.builder().shiftEnabled(false).build());
        return region;
    }

    private static AbstractCloudRegion getAnotherAvailableRegion() {
        final AwsRegion region = RegionCreatorUtils.getDefaultAwsRegion(NEXT_AVAILABLE_REGION_ID);
        region.setRunShiftPolicy(RunRegionShiftPolicy.builder().shiftEnabled(true).build());
        return region;
    }

    private static AbstractCloudRegion getAnotherProviderRegion() {
        return RegionCreatorUtils.getDefaultAzureRegion();
    }

    private static AbstractCloudRegion getParentRegion() {
        final AwsRegion region = RegionCreatorUtils.getDefaultAwsRegion(PARENT_AVAILABLE_REGION_ID);
        region.setRunShiftPolicy(RunRegionShiftPolicy.builder().shiftEnabled(true).build());
        return region;
    }

    private static RunInstance getInstance(final Long regionId) {
        final RunInstance runInstance = new RunInstance();
        runInstance.setCloudProvider(CloudProvider.AWS);
        runInstance.setCloudRegionId(regionId);
        return runInstance;
    }

    private static PipelineRun getCurrentRun(final RunInstance runInstance) {
        final PipelineRun currentRun = PipelineCreatorUtils.getPipelineRun(CURRENT_RUN_ID);
        currentRun.setInstance(runInstance);
        return currentRun;
    }

    private static PipelineRun getParentRun(final Long regionId) {
        final RunInstance parentInstance = getInstance(regionId);
        final PipelineRun parentRun = PipelineCreatorUtils.getPipelineRun(PARENT_RUN_ID);
        parentRun.setInstance(parentInstance);
        parentRun.setRestartedRuns(Collections.singletonList(getRestartRun()));
        parentRun.setPipelineRunParameters(Collections.singletonList(getCloudIndependentRunParameter()));
        return parentRun;
    }

    private static RestartRun getRestartRun() {
        final RestartRun restartRun = new RestartRun();
        restartRun.setDate(DateUtils.now());
        restartRun.setRestartedRunId(CURRENT_RUN_ID);
        restartRun.setParentRunId(PARENT_RUN_ID);
        return restartRun;
    }

    private static PipelineRunParameter getCloudDependentRunParameter() {
        return new PipelineRunParameter(TEST_NAME, "s3://" + TEST_STRING);
    }

    private static PipelineRunParameter getCloudIndependentRunParameter() {
        return new PipelineRunParameter(TEST_NAME, TEST_STRING);
    }

    private static PipelineRun expectedSuccessfulRun() {
        final RunInstance runInstance = getInstance(NEXT_AVAILABLE_REGION_ID);
        final PipelineRun currentRun = getCurrentRun(runInstance);
        currentRun.setPipelineRunParameters(Collections.singletonList(getCloudIndependentRunParameter()));
        return currentRun;
    }

    private static PipelineRun expectedSuccessfulParentRun() {
        final RunInstance parentInstance = getInstance(NEXT_AVAILABLE_REGION_ID);
        final PipelineRun parentRun = PipelineCreatorUtils.getPipelineRun(PARENT_RUN_ID);
        parentRun.setInstance(parentInstance);
        parentRun.setRestartedRuns(Collections.singletonList(getRestartRun()));
        parentRun.setPipelineRunParameters(Collections.singletonList(getCloudIndependentRunParameter()));
        return parentRun;
    }

    private static PipelineRun restartedRun() {
        return new PipelineRun();
    }
}
