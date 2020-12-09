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

package com.epam.pipeline.test.creator.pipeline;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.CheckRepositoryVO;
import com.epam.pipeline.controller.vo.GenerateFileVO;
import com.epam.pipeline.controller.vo.PipelineRunScheduleVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemVO;
import com.epam.pipeline.controller.vo.PipelineSourceItemsVO;
import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.controller.vo.PipelineVO;
import com.epam.pipeline.controller.vo.PipelinesWithPermissionsVO;
import com.epam.pipeline.controller.vo.RegisterPipelineVersionVO;
import com.epam.pipeline.controller.vo.TaskGraphVO;
import com.epam.pipeline.controller.vo.UploadFileMetadata;
import com.epam.pipeline.entity.cluster.InstancePrice;
import com.epam.pipeline.entity.pipeline.DocumentGenerationProperty;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Date;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class PipelineCreatorUtils {

    public static final TypeReference<Result<List<RunSchedule>>> RUN_SCHEDULE_LIST_TYPE =
            new TypeReference<Result<List<RunSchedule>>>() { };

    private PipelineCreatorUtils() {

    }

    public static Pipeline getPipeline(final String owner) {
        Pipeline pipeline = new Pipeline();
        pipeline.setId(ID);
        pipeline.setOwner(owner);
        pipeline.setCurrentVersion(getRevision());
        return pipeline;
    }

    public static Pipeline getPipeline(final Long id, final String owner, final Long parentId) {
        final Pipeline pipeline = new Pipeline();
        pipeline.setId(id);
        pipeline.setOwner(owner);
        pipeline.setParentFolderId(parentId);
        return pipeline;
    }

    public static Pipeline getPipeline(final String owner) {
        return getPipeline(ID, owner, ID);
    }

    public static PipelineRun getPipelineRun(final Long id, final String owner) {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setId(id);
        pipelineRun.setOwner(owner);
        pipelineRun.setName(TEST_STRING);
        return pipelineRun;
    }

    public static PipelineUserVO getPipelineUserVO() {
        return new PipelineUserVO();
    }

    public static PipelineVO getPipelineVO(final Long id) {
        final PipelineVO pipelineVO = new PipelineVO();
        pipelineVO.setId(ID);
        pipelineVO.setParentFolderId(id);
        return pipelineVO;
    }

    public static CheckRepositoryVO getCheckRepositoryVO() {
        return new CheckRepositoryVO();
    }

    public static PipelinesWithPermissionsVO getPipelinesWithPermissionsVO() {
        return new PipelinesWithPermissionsVO();
    }

    public static Revision getRevision() {
        final Revision revision = new Revision();
        revision.setCommitId(TEST_STRING);
        return revision;
    }

    public static InstancePrice getInstancePrice() {
        return new InstancePrice();
    }

    public static TaskGraphVO getTaskGraphVO() {
        return new TaskGraphVO();
    }

    public static PipelineSourceItemVO getPipelineSourceItemVO() {
        return new PipelineSourceItemVO();
    }

    public static PipelineSourceItemsVO getPipelineSourceItemsVO() {
        return new PipelineSourceItemsVO();
    }

    public static UploadFileMetadata getUploadFileMetadata() {
        return new UploadFileMetadata();
    }

    public static GenerateFileVO getGenerateFileVO() {
        return new GenerateFileVO();
    }

    public static RegisterPipelineVersionVO getRegisterPipelineVersionVO() {
        final RegisterPipelineVersionVO pipelineVersionVO = new RegisterPipelineVersionVO();
        pipelineVersionVO.setPipelineId(ID);
        return pipelineVersionVO;
    }

    public static DocumentGenerationProperty getDocumentGenerationProperty() {
        final DocumentGenerationProperty property = new DocumentGenerationProperty();
        property.setPipelineId(ID);
        return property;
    }

    public static RunSchedule getRunSchedule() {
        final RunSchedule runSchedule = new RunSchedule();
        runSchedule.setAction(RunScheduledAction.RUN);
        runSchedule.setCreatedDate(new Date());
        runSchedule.setCronExpression(TEST_STRING);
        runSchedule.setType(ScheduleType.PIPELINE_RUN);
        return runSchedule;
    }

    public static PipelineRunScheduleVO getPipelineRunScheduleVO() {
        final PipelineRunScheduleVO pipelineRunScheduleVO = new PipelineRunScheduleVO();
        pipelineRunScheduleVO.setAction(RunScheduledAction.RUN);
        pipelineRunScheduleVO.setCronExpression(TEST_STRING);
        pipelineRunScheduleVO.setScheduleId(ID);
        pipelineRunScheduleVO.setTimeZone(TEST_STRING);
        return pipelineRunScheduleVO;
    }
}
