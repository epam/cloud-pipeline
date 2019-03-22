/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.elasticsearchagent;

import com.epam.pipeline.elasticsearchagent.model.PermissionsContainer;
import com.epam.pipeline.entity.configuration.FirecloudRunConfigurationEntry;
import com.epam.pipeline.entity.configuration.RunConfiguration;
import com.epam.pipeline.entity.configuration.RunConfigurationEntry;
import com.epam.pipeline.entity.datastorage.NFSDataStorage;
import com.epam.pipeline.entity.datastorage.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.StoragePolicy;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.vo.EntityVO;
import com.fasterxml.jackson.core.JsonFactory;
import com.mchange.util.AssertException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContentParser;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.epam.pipeline.elasticsearchagent.VerificationUtils.verifyStringArray;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings({"PMD.TooManyStaticImports", "unchecked"})
public final class MapperVerificationUtils {

    private static final String DOC_TYPE = "doc_type";
    private static final String NAME = "name";
    private static final String ID = "id";
    private static final String PARENT_ID = "parentId";
    private static final String DESCRIPTION = "description";
    private static final String PATH = "path";
    private static final String PIPELINE_ID = "pipelineId";
    private static final String PIPELINE_NAME = "pipelineName";
    private static final String PIPELINE_VERSION = "pipelineVersion";

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public static void verifyFolder(final Folder expected, final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("FOLDER", puttedObjects.get(DOC_TYPE));
        assertEquals(expected.getId().intValue(), puttedObjects.get(ID));
        assertEquals(expected.getName(), puttedObjects.get(NAME));
        assertEquals(expected.getParentId().intValue(), puttedObjects.get(PARENT_ID));
    }

    public static void verifyPipeline(final Pipeline expected, final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("PIPELINE", puttedObjects.get(DOC_TYPE));
        assertEquals(toInt(expected.getId()), puttedObjects.get(ID));
        assertEquals(expected.getName(), puttedObjects.get(NAME));
        assertEquals(SIMPLE_DATE_FORMAT.format(expected.getCreatedDate()), puttedObjects.get("createdDate"));
        assertEquals(toInt(expected.getParentFolderId()), puttedObjects.get(PARENT_ID));
        assertEquals(expected.getDescription(), puttedObjects.get(DESCRIPTION));
        assertEquals(expected.getRepository(), puttedObjects.get("repository"));
        assertEquals(expected.getTemplateId(), puttedObjects.get("templateId"));
    }

    public static void verifyPipelineCode(final Pipeline expected,
                                          final String pipelineVersion,
                                          final String path,
                                          final String fileContent,
                                          final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("PIPELINE_CODE", puttedObjects.get(DOC_TYPE));
        assertEquals(toInt(expected.getId()), puttedObjects.get(PIPELINE_ID));
        assertEquals(expected.getName(), puttedObjects.get(PIPELINE_NAME));
        assertEquals(pipelineVersion, puttedObjects.get(PIPELINE_VERSION));
        assertEquals(path, puttedObjects.get(PATH));
        assertEquals(fileContent, puttedObjects.get("content"));
    }

    public static void verifyDockerRegistry(final DockerRegistry expected,
                                            final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("DOCKER_REGISTRY", puttedObjects.get(DOC_TYPE));
        assertEquals(toInt(expected.getId()), puttedObjects.get(ID));
        assertEquals(expected.getName(), puttedObjects.get(NAME));
        assertEquals(expected.getDescription(), puttedObjects.get(DESCRIPTION));
        assertEquals(expected.getPath(), puttedObjects.get(PATH));
        assertEquals(expected.getUserName(), puttedObjects.get("userName"));
    }

    public static void verifyToolGroup(final ToolGroup expected, final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("TOOL_GROUP", puttedObjects.get(DOC_TYPE));
        assertEquals(toInt(expected.getId()), puttedObjects.get(ID));
        assertEquals(expected.getName(), puttedObjects.get(NAME));
        assertEquals(toInt(expected.getRegistryId()), puttedObjects.get("registryId"));
        assertEquals(expected.getDescription(), puttedObjects.get(DESCRIPTION));
    }

    public static void verifyTool(final Tool expected, final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("TOOL", puttedObjects.get(DOC_TYPE));
        assertEquals(toInt(expected.getId()), puttedObjects.get(ID));
        assertEquals(expected.getRegistry(), puttedObjects.get("registry"));
        assertEquals(toInt(expected.getRegistryId()), puttedObjects.get("registryId"));
        assertEquals(expected.getImage(), puttedObjects.get("image"));
        assertEquals(expected.getDescription(), puttedObjects.get(DESCRIPTION));
        assertEquals(expected.getDefaultCommand(), puttedObjects.get("defaultCommand"));
        verifyStringArray(expected.getLabels(), puttedObjects.get("labels"));
        assertEquals(toInt(expected.getToolGroupId()), puttedObjects.get("toolGroupId"));
    }

    public static void verifyIssue(final Issue expected, final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("ISSUE", puttedObjects.get(DOC_TYPE));
        assertEquals(toInt(expected.getId()), puttedObjects.get(ID));
        assertEquals(expected.getName(), puttedObjects.get(NAME));
        assertEquals(expected.getText(), puttedObjects.get("text"));
        assertEquals(expected.getStatus().name(), puttedObjects.get("status"));
        verifyStringArray(expected.getLabels(), puttedObjects.get("labels"));
        verifyEntityVO(expected.getEntity(), puttedObjects);
    }

    public static void verifyMetadataEntity(final MetadataEntity expected, final List<String> expectedData,
                                            final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("METADATA_ENTITY", puttedObjects.get(DOC_TYPE));
        assertEquals(toInt(expected.getId()), puttedObjects.get(ID));
        assertEquals(expected.getName(), puttedObjects.get(NAME));
        assertEquals(toInt(expected.getParent().getId()), puttedObjects.get(PARENT_ID));
        assertEquals(expected.getExternalId(), puttedObjects.get("externalId"));
        assertEquals(expected.getClassEntity().getName(), puttedObjects.get("className"));
        assertEquals(toInt(expected.getClassEntity().getId()), puttedObjects.get("classId"));
        verifyStringArray(expectedData, puttedObjects.get("fields"));
    }

    public static void verifyPipelineRun(final PipelineRun expected, final String description,
                                         final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("PIPELINE_RUN", puttedObjects.get(DOC_TYPE));
        assertEquals(toInt(expected.getId()), puttedObjects.get(ID));
        assertEquals(expected.getPodId(), puttedObjects.get(NAME));
        assertEquals(description, puttedObjects.get(DESCRIPTION));
        assertEquals(expected.getPipelineName(), puttedObjects.get(PIPELINE_NAME));
        assertEquals(expected.getVersion(), puttedObjects.get(PIPELINE_VERSION));
        assertEquals(expected.getStatus().name(), puttedObjects.get("status"));
        assertEquals(expected.getDockerImage(), puttedObjects.get("dockerImage"));
        assertEquals(expected.getActualCmd(), puttedObjects.get("actualCmd"));
        assertEquals(expected.getConfigName(), puttedObjects.get("configurationName"));
        assertEquals(expected.getConfigurationId(), puttedObjects.get("configurationId"));
        assertEquals(expected.getPodId(), puttedObjects.get("podId"));
        assertEquals(expected.getPricePerHour().doubleValue(), puttedObjects.get("pricePerHour"));
        assertEquals(expected.getParentRunId(), puttedObjects.get("parentRunId"));
        assertEquals(expected.getNodeCount(), puttedObjects.get("nodeCount"));
        assertEquals(expected.getExecutionPreferences().getEnvironment().name(), puttedObjects.get("environment"));

        verifyRunInstance(expected.getInstance(), (Map<String, Object>) puttedObjects.get("instance"));
        verifyStatuses(expected.getRunStatuses(), (ArrayList<Map<String, Object>>) puttedObjects.get("statuses"));
    }

    public static void verifyRunConfiguration(final RunConfiguration expected, final String description,
                                              final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("CONFIGURATION", puttedObjects.get(DOC_TYPE));
        assertEquals(description, puttedObjects.get(DESCRIPTION));
        assertEquals(expected.getName(), puttedObjects.get(NAME));
        assertNull(puttedObjects.get(PARENT_ID));
    }

    public static void verifyRunConfigurationEntry(final RunConfigurationEntry expected, final Pipeline pipeline,
                                                   final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals(expected.getExecutionEnvironment().name(), puttedObjects.get("environment"));
        assertEquals(expected.getName(), puttedObjects.get("entryName"));
        assertEquals(toInt(expected.getRootEntityId()), puttedObjects.get("rootEntityId"));
        assertEquals(expected.getConfigName(), puttedObjects.get("configName"));
        assertEquals(expected.isDefaultConfiguration(), puttedObjects.get("defaultConfiguration"));
        assertEquals(toInt(expected.getPipelineId()), puttedObjects.get(PIPELINE_ID));
        assertEquals(expected.getPipelineVersion(), puttedObjects.get(PIPELINE_VERSION));
        assertEquals(expected.getConfigurationEntry().getConfiguration().getDockerImage(),
                puttedObjects.get("dockerImage"));
        assertNull(puttedObjects.get("methodName"));
        assertNull(puttedObjects.get("methodSnapshot"));
        assertNull(puttedObjects.get("methodConfigurationName"));
        assertNull(puttedObjects.get("methodConfigurationSnapshot"));
        assertEquals(pipeline.getName(), puttedObjects.get(PIPELINE_NAME));
    }

    public static void verifyFirecloudConfigurationEntry(final FirecloudRunConfigurationEntry expected,
                                                         final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals(expected.getExecutionEnvironment().name(), puttedObjects.get("environment"));
        assertEquals(expected.getName(), puttedObjects.get("entryName"));
        assertEquals(toInt(expected.getRootEntityId()), puttedObjects.get("rootEntityId"));
        assertEquals(expected.getConfigName(), puttedObjects.get("configName"));
        assertEquals(expected.isDefaultConfiguration(), puttedObjects.get("defaultConfiguration"));
        assertEquals(expected.getMethodName(), puttedObjects.get("methodName"));
        assertEquals(expected.getMethodSnapshot(), puttedObjects.get("methodSnapshot"));
        assertEquals(expected.getMethodConfigurationName(), puttedObjects.get("methodConfigurationName"));
        assertEquals(expected.getMethodConfigurationSnapshot(), puttedObjects.get("methodConfigurationSnapshot"));
        assertNull(puttedObjects.get(PIPELINE_NAME));
        assertNull(puttedObjects.get(PIPELINE_ID));
        assertNull(puttedObjects.get(PIPELINE_VERSION));
        assertNull(puttedObjects.get("dockerImage"));
    }

    public static void verifyS3Storage(final S3bucketDataStorage expected, final String region,
                                       final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("S3_STORAGE", puttedObjects.get(DOC_TYPE));
        assertEquals(region, puttedObjects.get("awsRegion"));
        assertEquals(toInt(expected.getId()), puttedObjects.get(ID));
        assertEquals(expected.getName(), puttedObjects.get(NAME));
        assertEquals(expected.getPath(), puttedObjects.get(PATH));
        assertEquals(toInt(expected.getParentFolderId()), puttedObjects.get(PARENT_ID));
        assertEquals(expected.getType().name(), puttedObjects.get("storageType"));
        assertEquals(expected.getDescription(), puttedObjects.get(DESCRIPTION));

        verifyStoragePolicy(expected.getStoragePolicy(), puttedObjects);
    }

    public static void verifyNFSStorage(final NFSDataStorage expected,
                                        final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals("NFS_STORAGE", puttedObjects.get(DOC_TYPE));
        assertNull(puttedObjects.get("awsRegion"));
        assertEquals(toInt(expected.getId()), puttedObjects.get(ID));
        assertEquals(expected.getName(), puttedObjects.get(NAME));
        assertEquals(expected.getPath(), puttedObjects.get(PATH));
        assertEquals(toInt(expected.getParentFolderId()), puttedObjects.get(PARENT_ID));
        assertEquals(expected.getType().name(), puttedObjects.get("storageType"));
        assertEquals(expected.getDescription(), puttedObjects.get(DESCRIPTION));

        assertNull(puttedObjects.get("storagePolicyBackupDuration"));
        assertNull(puttedObjects.get("storagePolicyLongTermStorageDuration"));
        assertNull(puttedObjects.get("storagePolicyShortTermStorageDuration"));
        assertNull(puttedObjects.get("storagePolicyVersioningEnabled"));
    }

    private static void verifyStoragePolicy(final StoragePolicy expected, final Map<String, Object> puttedObjects) {
        if (expected == null) {
            return;
        }
        assertEquals(expected.getBackupDuration(), puttedObjects.get("storagePolicyBackupDuration"));
        assertEquals(expected.getLongTermStorageDuration(), puttedObjects.get("storagePolicyLongTermStorageDuration"));
        assertEquals(expected.getShortTermStorageDuration(),
                puttedObjects.get("storagePolicyShortTermStorageDuration"));
        assertEquals(expected.getVersioningEnabled(), puttedObjects.get("storagePolicyVersioningEnabled"));
    }

    public static void verifyRunParameters(final List<String> expected,
                                           final XContentBuilder content) throws IOException {
        verifyArrays(expected, content, "parameters");
    }

    public static void verifyRunLogs(final List<String> expected, final XContentBuilder content) throws IOException {
        verifyArrays(expected, content, "logs");
    }

    public static void verifyPipelineUser(final PipelineUser expected,
                                          final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        assertEquals(expected.getId().intValue(), puttedObjects.get("ownerUserId"));
        assertEquals(expected.getUserName(), puttedObjects.get("ownerUserName"));
        verifyStringArray(expected.getGroups(), puttedObjects.get("ownerGroups"));
        String prettyName = MapUtils.emptyIfNull(expected.getAttributes()).get("Name");
        assertEquals(prettyName, puttedObjects.get("ownerFriendlyName"));
    }

    public static void verifyPermissions(final PermissionsContainer expected,
                                         final XContentBuilder content) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        verifyStringArray(expected.getAllowedUsers(), puttedObjects.get("allowed_users"));
        verifyStringArray(expected.getDeniedUsers(), puttedObjects.get("denied_users"));
        verifyStringArray(expected.getAllowedGroups(), puttedObjects.get("allowed_groups"));
        verifyStringArray(expected.getDeniedGroups(), puttedObjects.get("denied_groups"));
    }

    public static void verifyMetadata(final List<String> expected, final XContentBuilder content) throws IOException {
        verifyArrays(expected, content, "metadata");
    }

    public static void verifyAttachments(final List<String> expected,
                                         final XContentBuilder content) throws IOException {
        verifyArrays(expected, content, "attachments");
    }

    public static void verifyComments(final List<String> expected, final XContentBuilder content) throws IOException {
        verifyArrays(expected, content, "comments");
    }

    private static void verifyStatuses(final List<RunStatus> expected, final ArrayList<Map<String, Object>> content) {
        if (CollectionUtils.isEmpty(expected)) {
            if (CollectionUtils.isNotEmpty(content)) {
                throw new AssertException("Expected list is empty but actual not");
            }
            return;
        }
        assertEquals(expected.size(), content.size());
        // TODO: expand to all sizes
        RunStatus expectedRunStatus = expected.get(0);
        Map<String, Object> actualRunStatus = content.get(0);
        assertEquals(expectedRunStatus.getStatus().name(), actualRunStatus.get("status"));
    }

    private static void verifyArrays(final List<String> expected, final XContentBuilder content,
                                     final String fieldName) throws IOException {
        Map<String, Object> puttedObjects = getPuttedObject(content);

        verifyStringArray(expected, puttedObjects.get(fieldName));
    }

    private static void verifyRunInstance(final RunInstance runInstance, final Map<String, Object> content) {
        assertEquals(runInstance.getNodeType(), content.get("nodeType"));
        assertEquals(runInstance.getNodeDisk(), content.get("nodeDisk"));
        assertEquals(runInstance.getNodeIP(), content.get("nodeIP"));
        assertEquals(runInstance.getNodeId(), content.get("nodeId"));
        assertEquals(runInstance.getNodeImage(), content.get("nodeImage"));
        assertEquals(runInstance.getNodeName(), content.get("nodeName"));
        assertEquals(runInstance.getSpot(), content.get("priceType"));
        assertEquals(runInstance.getAwsRegionId(), content.get("awsRegion"));
    }

    private static Map<String, Object> getPuttedObject(final XContentBuilder contentBuilder) throws IOException {
        JsonFactory factory = new JsonFactory();
        JsonXContentParser parser = new JsonXContentParser(NamedXContentRegistry.EMPTY, null,
                factory.createParser(Strings.toString(contentBuilder)));
        return parser.map();
    }

    private static void verifyEntityVO(final EntityVO expected, final Map<String, Object> puttedObjects) {
        if (expected == null) {
            return;
        }
        assertEquals(toInt(expected.getEntityId()), puttedObjects.get("entityId"));
        assertEquals(expected.getEntityClass().name(), puttedObjects.get("entityClass"));
    }

    private static Integer toInt(final Long value) {
        return Optional.ofNullable(value).map(Long::intValue).orElse(null);
    }

    private MapperVerificationUtils() {
        // no-op
    }
}
