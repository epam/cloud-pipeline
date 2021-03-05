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

package com.epam.pipeline.test.creator.pipeline;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.CheckRepositoryVO;
import com.epam.pipeline.controller.vo.GenerateFileVO;
import com.epam.pipeline.controller.vo.InstanceOfferParametersVO;
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
import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import com.epam.pipeline.entity.pipeline.CommitStatus;
import com.epam.pipeline.entity.pipeline.DocumentGenerationProperty;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Revision;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.pipeline.run.RunSchedule;
import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import com.epam.pipeline.entity.pipeline.run.ScheduleType;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.utils.DateUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_LONG;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class PipelineCreatorUtils {

    public static final TypeReference<Result<Pipeline>> PIPELINE_INSTANCE_TYPE =
            new TypeReference<Result<Pipeline>>() {};
    public static final TypeReference<Result<CheckRepositoryVO>> CHECK_REPOSITORY_INSTANCE_TYPE =
            new TypeReference<Result<CheckRepositoryVO>>() {};
    public static final TypeReference<Result<PipelinesWithPermissionsVO>> PIPELINE_WITH_PERMISSIONS_TYPE =
            new TypeReference<Result<PipelinesWithPermissionsVO>>() {};
    public static final TypeReference<Result<InstancePrice>> INSTANCE_PRICE_TYPE =
            new TypeReference<Result<InstancePrice>>() {};
    public static final TypeReference<Result<TaskGraphVO>> TASK_GRAPH_VO_TYPE =
            new TypeReference<Result<TaskGraphVO>>() {};
    public static final TypeReference<Result<Revision>> REVISION_INSTANCE_TYPE =
            new TypeReference<Result<Revision>>() {};
    public static final TypeReference<Result<DocumentGenerationProperty>> DOCUMENT_GENERATION_PROPERTY_TYPE =
            new TypeReference<Result<DocumentGenerationProperty>>() {};
    public static final TypeReference<Result<List<Pipeline>>> PIPELINE_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<Pipeline>>>() {};
    public static final TypeReference<Result<List<PipelineRun>>> PIPELINE_RUN_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<PipelineRun>>>() {};
    public static final TypeReference<Result<List<Revision>>> REVISION_LIST_INSTANCE_TYPE =
            new TypeReference<Result<List<Revision>>>() {};
    public static final TypeReference<Result<List<DocumentGenerationProperty>>> DOCUMENT_GENERATION_PROPERTY_LIST_TYPE =
            new TypeReference<Result<List<DocumentGenerationProperty>>>() {};
    public static final TypeReference<List<UploadFileMetadata>> UPLOAD_METADATA_LIST_TYPE =
            new TypeReference<List<UploadFileMetadata>>() {};
    public static final TypeReference<Result<List<RunSchedule>>> RUN_SCHEDULE_LIST_TYPE =
            new TypeReference<Result<List<RunSchedule>>>() {};

    private PipelineCreatorUtils() {

    }

    public static Pipeline getPipeline() {
        return new Pipeline();
    }

    public static Pipeline getPipeline(final Long id, final String owner, final Long parentId) {
        final Pipeline pipeline = getPipeline();
        pipeline.setId(id);
        pipeline.setOwner(owner);
        pipeline.setParentFolderId(parentId);
        pipeline.setCurrentVersion(getRevision());
        return pipeline;
    }

    public static Pipeline getPipeline(final Long id, final String owner) {
        final Pipeline pipeline = getPipeline();
        pipeline.setId(id);
        pipeline.setOwner(owner);
        return pipeline;
    }

    public static Pipeline getPipeline(final String owner) {
        return getPipeline(ID, owner, ID);
    }

    public static PipelineRun getPipelineRun() {
        return new PipelineRun();
    }

    public static PipelineRun getPipelineRun(final TaskStatus taskStatus) {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setStatus(taskStatus);
        pipelineRun.setStartDate(new Date());
        return pipelineRun;
    }

    public static PipelineRun getPipelineRun(final Long id, final String owner) {
        final PipelineRun pipelineRun = getPipelineRun();
        pipelineRun.setId(id);
        pipelineRun.setOwner(owner);
        pipelineRun.setName(TEST_STRING);
        return pipelineRun;
    }

    public static PipelineRun getPipelineRunWithInstance(final Long id, final String owner, final Long runInstanceId) {
        final PipelineRun pipelineRun = getPipelineRun(id, owner);
        final RunInstance parentRunInstance = new RunInstance();
        parentRunInstance.setCloudRegionId(runInstanceId);
        parentRunInstance.setCloudProvider(CloudProvider.AWS);
        pipelineRun.setInstance(parentRunInstance);
        pipelineRun.setStatus(TaskStatus.RUNNING);
        pipelineRun.setCommitStatus(CommitStatus.NOT_COMMITTED);
        pipelineRun.setStartDate(DateUtils.now());
        pipelineRun.setPodId(TEST_STRING);
        pipelineRun.setOwner(owner);
        pipelineRun.setLastChangeCommitTime(DateUtils.now());
        return pipelineRun;
    }

    public static PipelineUserVO getPipelineUserVO() {
        return new PipelineUserVO();
    }

    public static PipelineVO getPipelineVO() {
        return new PipelineVO();
    }

    public static PipelineVO getPipelineVO(final Long id) {
        final PipelineVO pipelineVO = getPipelineVO();
        pipelineVO.setId(ID);
        pipelineVO.setParentFolderId(id);
        return pipelineVO;
    }

    public static PipelineStart getPipelineStart(final Map<String, PipeConfValueVO> params, final String image) {
        final PipelineStart vo = new PipelineStart();
        vo.setNonPause(false);
        vo.setInstanceImage(TEST_STRING);
        vo.setPrettyUrl(TEST_STRING);
        vo.setWorkerCmd(TEST_STRING);
        vo.setInstanceType(TEST_STRING);
        vo.setDockerImage(image);
        vo.setHddSize(TEST_INT);
        vo.setCmdTemplate(TEST_STRING);
        vo.setTimeout(TEST_LONG);
        vo.setNodeCount(TEST_INT);
        vo.setIsSpot(true);
        vo.setParams(params);
        return vo;
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
        final PipelineSourceItemVO sourceItemVO = new PipelineSourceItemVO();
        sourceItemVO.setComment(TEST_STRING);
        sourceItemVO.setLastCommitId(TEST_STRING);
        sourceItemVO.setPath(TEST_STRING);
        return sourceItemVO;
    }

    public static PipelineSourceItemsVO getPipelineSourceItemsVO() {
        return new PipelineSourceItemsVO();
    }

    public static UploadFileMetadata getUploadFileMetadata() {
        return new UploadFileMetadata();
    }

    public static UploadFileMetadata getUploadFileMetadata(final String fileName,
                                                           final String fileSize,
                                                           final String fileType) {
        final UploadFileMetadata uploadFileMetadata = getUploadFileMetadata();
        uploadFileMetadata.setFileName(fileName);
        uploadFileMetadata.setFileSize(fileSize);
        uploadFileMetadata.setFileType(fileType);
        return uploadFileMetadata;
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
        property.setPropertyName(TEST_STRING);
        return property;
    }

    public static InstanceOfferParametersVO getInstanceOfferParametersVO() {
        final InstanceOfferParametersVO instance = new InstanceOfferParametersVO();
        instance.setInstanceType(TEST_STRING);
        instance.setInstanceDisk(TEST_INT);
        instance.setSpot(true);
        instance.setRegionId(ID);
        return instance;
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
