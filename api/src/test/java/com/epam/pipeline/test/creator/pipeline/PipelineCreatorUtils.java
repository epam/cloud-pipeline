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
import com.epam.pipeline.controller.vo.InstanceOfferParametersVO;
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

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
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
    public static final TypeReference<Result<List<UploadFileMetadata>>> UPLOAD_METADATA_LIST_TYPE =
            new TypeReference<Result<List<UploadFileMetadata>>>() {};
    public static final TypeReference<Result<List<DocumentGenerationProperty>>> DOCUMENT_GENERATION_PROPERTY_LIST_TYPE =
            new TypeReference<Result<List<DocumentGenerationProperty>>>() {};

    public static final TypeReference<Result<List<RunSchedule>>> RUN_SCHEDULE_LIST_TYPE =
            new TypeReference<Result<List<RunSchedule>>>() { };

    private PipelineCreatorUtils() {

    }

    public static Pipeline getPipeline() {
        return new Pipeline();
    }

    public static Pipeline getPipeline(final String owner) {
        final Pipeline pipeline = new Pipeline();
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
        final UploadFileMetadata uploadFileMetadata = new UploadFileMetadata();
        uploadFileMetadata.setFileName("file.txt");
        uploadFileMetadata.setFileSize("0 Kb");
        uploadFileMetadata.setFileType("application/octet-stream");
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
        return property;
    }

}
