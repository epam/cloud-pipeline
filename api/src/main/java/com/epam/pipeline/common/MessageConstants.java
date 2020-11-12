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

package com.epam.pipeline.common;

public final class MessageConstants {

    //Common errors
    public static final String ERROR_PARAMETER_REQUIRED = "error.parameter.required";
    public static final String ERROR_PARAMETER_NULL_OR_EMPTY = "error.null.param";
    public static final String ERROR_PARENT_REQUIRED = "error.parent.required";
    public static final String ERROR_TOO_MANY_RESULTS = "error.too.many.results";
    public static final String ERROR_UNSUPPORTED_OPERATION = "error.unsupported.operation";

    //Pipeline errors
    public static final String ERROR_PIPELINE_NOT_FOUND = "error.pipeline.not.found";
    public static final String ERROR_PIPELINE_NAME_IS_EMPTY = "error.pipeline.name.is.empty";
    public static final String ERROR_PIPELINE_WITH_URL_NOT_FOUND = "error.pipeline.with.url.not.found";
    public static final String ERROR_REPO_TOKEN_INVALID = "error.pipeline.token.is.invalid";
    public static final String ERROR_PIPELINE_DOCUMENT_PROPERTY_NOT_FOUND =
            "error.pipeline.document.property.not.found";
    public static final String ERROR_PIPELINE_REPO_EXISTS = "error.pipeline.name.exists";
    public static final String ERROR_INVALID_PIPELINE_NAME = "error.pipeline.name.invalid";
    public static final String ERROR_INVALID_PIPELINE_REVISION_NAME = "error.pipeline.revision.name.invalid";

    //Pipeline config
    public static final String ERROR_CONFIG_INVALID = "error.pipeline.config.invalid";
    public static final String ERROR_CONFIG_NAME_REQUIRED = "error.pipeline.config.name.required";
    public static final String ERROR_INVALID_CONFIG_NAME = "error.pipeline.config.name.invalid";
    public static final String ERROR_CONFIG_NOT_FOUND = "error.pipeline.config.not.found";
    public static final String ERROR_CONFIG_NAME_EXISTS = "error.pipeline.config.name.exists";
    public static final String INFO_CONFIG_UPDATE = "info.pipeline.config.update";
    public static final String INFO_CONFIG_DELETE = "info.pipeline.config.delete";
    public static final String INFO_CONFIG_RENAME = "info.pipeline.config.rename";
    public static final String ERROR_EXCEED_MAX_RUNS_COUNT = "error.exceed.max.runs.count";
    public static final String ERROR_PROXY_SECURITY_CONFIG_MISSING = "error.proxy.security.config.missing";

    //Run config
    public static final String ERROR_RUN_CONFIG_NOT_FOUND = "error.run.config.not.found";
    public static final String ERROR_RUN_CONFIG_DUPLICATES = "error.run.config.name.duplicates";

    //Folder errors
    public static final String ERROR_FOLDER_NOT_FOUND = "error.folder.not.found";
    public static final String ERROR_FOLDER_NAME_IS_EMPTY = "error.folder.name.empty";
    public static final String ERROR_INVALID_FOLDER_NAME = "error.invalid.folder.name";
    public static final String ERROR_FOLDER_NAME_EXISTS = "error.folder.name.exists";
    public static final String ERROR_FOLDER_IS_USED = "error.folder.is.used";
    public static final String ERROR_FOLDER_USED_IN_PIPELINES = "error.folder.used.pipelines";
    public static final String ERROR_FOLDER_USED_IN_FOLDERS = "error.folder.used.folders";
    public static final String ERROR_FOLDER_USED_IN_STORAGES = "error.folder.used.storages";
    public static final String ERROR_FOLDER_RECURSIVE_DEPENDENCY = "error.folder.recursive.deps";
    public static final String ERROR_PROJECT_FOLDER_NOT_FOUND = "folder.project.not.found";
    public static final String ERROR_FOLDER_TEMPLATE_NOT_FOUND = "error.folder.template.not.found";
    public static final String ERROR_TEMPLATE_FOLDER_NAME_IS_EMPTY = "error.template.folder.name.empty";
    public static final String ERROR_FOLDER_INVALID_TEMPLATE = "error.folder.template.invalid";
    public static final String ERROR_FOLDER_INVALID_ID = "error.invalid.folder.id";

    //Tools errors
    public static final String ERROR_TOOL_NOT_FOUND = "error.tool.not.found";
    public static final String ERROR_TOOL_ALREADY_EXIST = "error.tool.already.exists";
    public static final String ERROR_TOOL_ALREADY_EXIST_IN_REGISTRY = "error.tool.already.exists.in.registry";
    public static final String ERROR_NUMBER_OF_TOOLS_WITH_IMAGE_GREATER_THEN_ONE =
            "error.number.of.tools.with.image.greater.then.one";
    public static final String ERROR_REGISTRY_NOT_FOUND = "error.registry.not.found";
    public static final String ERROR_REGISTRY_ALREADY_EXISTS = "error.registry.already.exists";
    public static final String ERROR_TOOL_COPY_BETWEEN_REGISTRIES = "error.tool.copy.between.registries";
    public static final String ERROR_TOOL_IMAGE_UNAVAILABLE = "error.tool.image.unavailable";

    public static final String ERROR_REGISTRY_SCRIPT_TEMPLATE_UNAVAILABLE = "error.registry.script.unavailable";
    public static final String ERROR_REGISTRY_COULD_NOT_GET_MANIFEST = "error.registry.could.not.get.manifest";
    public static final String ERROR_TOOL_ICON_NOT_FOUND = "error.tool.icon.not.found";
    public static final String ERROR_TOOL_ICON_TOO_LARGE = "error.tool.icon.too.large";
    public static final String ERROR_TOOL_INVALID_IMAGE = "error.tool.invalid.image";
    public static final String ERROR_TOOL_VERSION_INVALID_SIZE = "error.tool.version.invalid.size";
    public static final String ERROR_TOOL_CLOUD_REGION_NOT_ALLOWED = "error.tool.cloud.region.not.allowed";

    public static final String ERROR_TOOL_SYMLINK_SOURCE_TOOL_ID_MISSING = "error.tool.symlink.source.tool.id.missing";
    public static final String ERROR_TOOL_SYMLINK_TARGET_GROUP_ID_MISSING = 
            "error.tool.symlink.target.group.id.missing";
    public static final String ERROR_TOOL_SYMLINK_SOURCE_TOOL_NOT_FOUND = "error.tool.symlink.source.tool.not.found";
    public static final String ERROR_TOOL_SYMLINK_TARGET_GROUP_NOT_FOUND = "error.tool.symlink.target.group.not.found";
    public static final String ERROR_TOOL_SYMLINK_MODIFICATION_NOT_SUPPORTED = 
            "error.tool.symlink.modification.not.supported";
    public static final String ERROR_TOOL_SYMLINK_TARGET_SYMLINK = "error.tool.symlink.target.symlink";

    // Registry messages
    public static final String DEBUG_DOCKER_REGISTRY_AUTO_ENABLE = "debug.docker.registry.auto.enable";
    public static final String DEBUG_DOCKER_REGISTRY_AUTO_ENABLE_SUCCESS = "debug.docker.registry.auto.enable.success";
    public static final String ERROR_DOCKER_REGISTRY_NO_EXTERNAL = "error.docker.registry.no.external";
    public static final String ERROR_DOCKER_REGISTRY_AUTHENTICATION_REQUIRED =
            "error.docker.registry.authentication.required";
    // Registry access
    public static final String ERROR_REGISTRY_IS_NOT_ALLOWED = "error.registry.not.allowed";
    public static final String ERROR_REGISTRY_ACTION_IS_NOT_ALLOWED = "error.registry.action.not.allowed";
    public static final String ERROR_REGISTRY_IMAGE_ACTION_IS_NOT_ALLOWED = "error.registry.image.action.not.allowed";

    // ToolGroup errors
    public static final String ERROR_TOOL_GROUP_ALREADY_EXIST = "error.tool.group.already.exists";
    public static final String ERROR_TOOL_GROUP_NOT_FOUND = "error.tool.group.not.found";
    public static final String ERROR_TOOL_GROUP_IS_NOT_PROVIDED = "error.tool.group.is.not.provided";
    public static final String ERROR_TOOL_GROUP_BY_NAME_NOT_FOUND = "error.tool.group.by.name.not.found";
    public static final String ERROR_PRIVATE_TOOL_GROUP_NOT_FOUND = "error.private.tool.group.not.found";
    public static final String ERROR_TOOL_GROUP_NOT_EMPTY = "error.tool.group.not.empty";

    //PipelineLauncher
    public static final String ERROR_INVALID_CMD_TEMPLATE = "error.invalid.cmd.template";

    //PipelineRunManager
    public static final String ERROR_INVALID_IMAGE_REPOSITORY = "error.invalid.image.repository";
    public static final String WARN_COMMIT_TIMEOUT_HAS_EXPIRED = "warn.commit.timeout.has.expired";
    public static final String ERROR_PIPELINE_RUN_FINISHED = "error.pipeline.run.finished";
    public static final String ERROR_PIPELINE_RUN_NOT_STOPPED = "error.pipeline.run.not.stopped";
    public static final String ERROR_INSTANCE_DISK_NOT_ENOUGH = "error.run.instance.disk.not.enough";
    public static final String ERROR_PIPELINE_RUN_NOT_RUNNING = "error.pipeline.run.not.running";
    public static final String ERROR_RUN_IS_FINAL_STATUS = "error.run.is.final.status";

    //PipelineRun messages
    public static final String ERROR_WRONG_RUN_STATUS_UPDATE = "error.wrong.run.status.update";
    public static final String ERROR_RUN_PARAMETER_IS_REQUIRED = "error.run.parameter.required";
    public static final String ERROR_RUN_PIPELINES_NOT_FOUND = "error.run.pipelines.not.found";
    public static final String ERROR_RUN_PIPELINES_COMMIT_FAILED = "error.run.pipeline.commit.failed";
    public static final String ERROR_CONTAINER_ID_FOR_RUN_NOT_FOUND = "error.container.id.for.run.not.found";
    public static final String INFO_EXECUTE_COMMIT_RUN_PIPELINES = "info.execute.ssh.run.pipeline.command";
    public static final String ERROR_RUN_PIPELINES_PAUSE_FAILED = "error.run.pipeline.pause.failed";
    public static final String ERROR_RUN_PIPELINES_RESUME_FAILED = "error.run.pipeline.resume.failed";
    public static final String ERROR_INSTANCE_NOT_FOUND = "error.instance.not.found";
    public static final String ERROR_INSTANCE_ID_NOT_FOUND = "error.instance.id.not.found";
    public static final String ERROR_POD_ID_NOT_FOUND = "error.pod.id.not.found";
    public static final String ERROR_DOCKER_IMAGE_NOT_FOUND = "error.docker.image.not.found";
    public static final String ERROR_PIPELINE_RUN_ID_NOT_FOUND = "error.pipeline.run.id.not.found";
    public static final String ERROR_ON_DEMAND_REQUIRED = "error.instance.on.demand.required";
    public static final String ERROR_INSTANCE_IP_NOT_FOUND = "error.instance.ip.not.found";
    public static final String ERROR_ACTUAL_CMD_NOT_FOUND = "error.actual.cmd.not.found";
    public static final String ERROR_PIPELINE_RUN_NOT_INITIALIZED = "error.pipeline.run.not.initialized";
    public static final String ERROR_RUN_PRETTY_URL_IN_USE = "error.pipeline.run.pretty.url.in.use";
    public static final String ERROR_EXCEED_MAX_RESTART_RUN_COUNT = "error.exceed.max.restart.run.count";
    public static final String ERROR_GET_NODE_STAT = "error.get.node.stat";
    public static final String ERROR_CMD_TEMPLATE_NOT_RESOLVED = "error.cmd.template.not.resolved";
    public static final String ERROR_RUN_TERMINATION_WRONG_STATUS = "error.run.termination.wrong.status";
    public static final String WARN_RESUME_RUN_FAILED = "warn.resume.run.failed";
    public static final String INFO_INSTANCE_STARTED = "info.instance.started";
    public static final String ERROR_RUN_DISK_ATTACHING_WRONG_STATUS = "error.run.disk.attaching.wrong.status";
    public static final String ERROR_RUN_DISK_ATTACHING_MISSING_NODE_ID = "error.run.disk.attaching.missing.node.id";
    public static final String ERROR_RUN_DISK_SIZE_NOT_FOUND = "error.run.disk.size.not.found";
    public static final String ERROR_BAD_STATS_FILE_ENCODING = "error.run.stats.file.bad.encoding";
    public static final String ERROR_RUN_CLOUD_REGION_NOT_ALLOWED = "error.run.cloud.region.not.allowed";
    public static final String INFO_LOG_PAUSE_COMPLETED = "info.log.pause.completed";
    public static final String INFO_LOG_RESUME_COMPLETED = "info.log.resume.completed";

    //Run schedule
    public static final String CRON_EXPRESSION_IS_NOT_PROVIDED = "cron.expression.is.not.provided";
    public static final String CRON_EXPRESSION_IS_NOT_VALID = "cron.expression.is.not.valid";
    public static final String CRON_EXPRESSION_ALREADY_EXISTS = "cron.expression.already.exists";
    public static final String CRON_EXPRESSION_IDENTICAL = "cron.expression.identical";
    public static final String SCHEDULE_ACTION_IS_NOT_PROVIDED = "schedule.action.is.not.provided";
    public static final String SCHEDULE_ACTION_IS_NOT_ALLOWED = "schedule.action.is.not.allowed";
    public static final String SCHEDULE_ID_IS_NOT_PROVIDED = "schedule.id.is.not.provided";
    public static final String ERROR_RUN_SCHEDULE_NOT_FOUND = "error.run.schedule.not.found";
    public static final String ERROR_TIME_ZONE_IS_NOT_PROVIDED = "error.time.zone.is.not.provided";
    public static final String ERROR_SCHEDULABLE_ID_NOT_CORRESPONDING = "error.schedulable.id.not.corresponding";

    // PodMonitor messages
    public static final String DEBUG_MONITOR_CHECK_RUNNING = "debug.monitor.check.running";
    public static final String DEBUG_MONITOR_CHECK_FINISHED = "debug.monitor.check.finished";
    public static final String INFO_MONITOR_KILL_TASK = "info.monitor.kill.task";
    public static final String ERROR_POD_RELEASE_TASK = "error.pod.release.task";
    public static final String ERROR_RESTART_STATE_REASONS_NOT_FOUND = "error.instance.restart.state.reasons.not.found";

    // ResourceMonitoringManager messages
    public static final String INFO_RUN_IDLE_NOTIFY = "info.run.idle.notify";
    public static final String INFO_RUN_IDLE_ACTION = "info.run.idle.action";
    public static final String DEBUG_CPU_RUN_METRICS_RECEIVED = "debug.cpu.run.metrics.received";
    public static final String DEBUG_RUN_METRICS_REQUEST = "debug.run.metrics.request";
    public static final String DEBUG_RUN_IDLE_SKIP_CHECK = "debug.run.idle.skip.check";
    public static final String DEBUG_RUN_NOT_IDLED = "debug.run.not.idled";
    public static final String DEBUG_RUN_HAS_NOT_NODE_NAME = "debug.run.has.not.node.name";
    public static final String DEBUG_MEMORY_METRICS = "debug.memory.metrics.received";


    // Kubernetes messages
    public static final String ERROR_NODE_NOT_FOUND = "error.node.not.found";
    public static final String ERROR_NODE_IS_PROTECTED = "error.node.is.protected";
    public static final String ERROR_KUBE_TOKEN_NOT_FOUND = "error.kube.token.not.found";
    public static final String ERROR_KUBE_MASTER_IP_NOT_FOUND = "error.kube.master.ip.not.found";
    public static final String ERROR_KUBE_SERVICE_IP_UNDEFINED = "error.kube.service.ip.undefined";
    public static final String ERROR_KUBE_SERVICE_PORT_UNDEFINED = "error.kube.service.port.undefined";
    public static final String ERROR_NODE_DOWN_TIMEOUT = "error.kube.node.down.timeout";
    public static final String LOG_WAS_TRUNCATED = "log.truncated";

    // Data storage messages
    public static final String ERROR_DATASTORAGE_NOT_FOUND = "error.datastorage.not.found";
    public static final String ERROR_DATASTORAGE_NOT_FOUND_BY_NAME = "error.datastorage.not.found.by.name";
    public static final String ERROR_DATASTORAGE_ALREADY_EXIST = "error.datastorage.already.exist";
    public static final String ERROR_DATASTORAGE_NAME_IS_EMPTY = "error.datastorage.name.is.empty";
    public static final String ERROR_DATASTORAGE_NOT_SUPPORTED = "error.datastorage.not.supported";
    public static final String ERROR_DATASTORAGE_PATH_IS_EMPTY = "error.datastorage.path.is.empty";
    public static final String ERROR_DATASTORAGE_RULE_NOT_FOUND = "error.datastorage.rule.not.found";
    public static final String ERROR_DATASTORAGE_RULE_STS_OR_LTS_REQUIRED =
            "error.datastorage.rule.sts.or.lts.required";
    public static final String ERROR_DATASTORAGE_ILLEGAL_DURATION =
            "error.datastorage.rule.illegal.duration";
    public static final String ERROR_DATASTORAGE_ILLEGAL_DURATION_COMBINATION =
            "error.datastorage.rule.illegal.duration.combination";
    public static final String ERROR_INVALID_CREDENTIALS_REQUEST = "error.datastorage.invalid.request";
    public static final String ERROR_DATASTORAGE_VERSIONING_REQUIRED = "error.datastorage.versioning.required";
    public static final String ERROR_DATASTORAGE_CREATE_FAILED = "error.datastorage.create.failed";
    public static final String ERROR_DATASTORAGE_DELETE_FAILED = "error.datastorage.delete.failed";
    public static final String ERROR_DATASTORAGE_TYPE_NOT_SPECIFIED = "error.datastorage.type.not.specified";
    public static final String ERROR_DATASTORAGE_FORBIDDEN_MOUNT_POINT = "error.datastorage.forbidden.mount.point";
    public static final String ERROR_DATASTORAGE_IS_NOT_SHARED = "error.datastorage.is.not.shared";
    public static final String ERROR_SHARED_ROOT_URL_IS_NOT_SET = "error.shared.root.url.is.not.set";
    public static final String ERROR_DATASTORAGE_USED_AS_DEFAULT = "error.datastorage.is.used.default";
    public static final String ERROR_DATASTORAGE_FILE_TAG_NOT_EXIST = "error.datastorage.file.tag.not.exist";
    public static final String ERROR_DATASTORAGE_PATH_NOT_FOUND = "error.datastorage.path.not.found";
    public static final String ERROR_DATASTORAGE_PATH_ALREADY_EXISTS = "error.datastorage.path.already.exists";
    public static final String ERROR_DATASTORAGE_FOLDER_ALREADY_EXISTS = "error.datastorage.folder.already.exists";
    public static final String ERROR_DATASTORAGE_PATH_INVALID_SCHEMA = "error.datastorage.path.invalid.schema";
    public static final String ERROR_DATASTORAGE_PATH_PROCCESSING = "error.datastorage.path.processing.error";
    public static final String ERROR_AZURE_STORAGE_CREDENTIAL_INVALID = "error.azure.storage.credentials.invalid";
    public static final String ERROR_SENSITIVE_DATASTORAGE_OPERATION =
        "error.sensitive.datastorage.forbidden.operation";
    public static final String ERROR_SENSITIVE_REQUEST_WRONG_CONTEXT =
            "error.sensitive.request.wrong.context";
    public static final String ERROR_SENSITIVE_WRITE_FORBIDDEN = "error.sensitive.datastorage.write.forbidden";
    public static final String ERROR_SENSITIVE_RUN_NOT_ALLOWED_FOR_TOOL = "error.sensitive.tool.forbidden";
    public static final String ERROR_SHARED_STORAGE_IS_NOT_CONFIGURED = "error.share.storage.not.configured";
    public static final String ERROR_DATASTORAGES_TYPES_NOT_SAME = "error.datastorages.types.not.same";
    public static final String ERROR_DATASTORAGES_NOT_FOUND = "error.datastorages.not.found";

    // NFS
    public static final String ERROR_DATASTORAGE_NFS_MOUNT = "error.datastorage.nfs.mount";
    public static final String ERROR_DATASTORAGE_NFS_MOUNT_2 = "error.datastorage.nfs.mount.2";
    public static final String ERROR_DATASTORAGE_NFS_MOUNT_DIRECTORY_NOT_CREATED =
        "error.datastorage.nfs.mount.dir.not.created";
    public static final String ERROR_DATASTORAGE_NFS_UNMOUNT_ERROR_2 = "error.datastorage.nfs.unmount.2";
    public static final String ERROR_DATASTORAGE_NFS_CREATE_FOLDER = "error.datastorage.nfs.create.folder";
    public static final String ERROR_DATASTORAGE_NFS_CREATE_FILE = "error.datastorage.nfs.create.file";
    public static final String ERROR_DATASTORAGE_NFS_DELETE_DIRECTORY = "error.datastorage.nfs.delete.directory";

    public static final String ERROR_DATASTORAGE_NFS_PATH_NOT_FOUND = "error.datastorage.nfs.path.not.found";
    public static final String ERROR_DATASTORAGE_CANNOT_CREATE_FILE = "error.datastorage.cannot.set.file.permission";

    // Git messages
    public static final String ERROR_REPOSITORY_FILE_WAS_UPDATED = "error.repository.file.was.updated";
    public static final String ERROR_REPOSITORY_WAS_UPDATED = "error.repository.was.updated";
    public static final String ERROR_REPOSITORY_FOLDER_NOT_FOUND = "error.repository.folder.not.found";
    public static final String ERROR_REPOSITORY_FOLDER_ALREADY_EXISTS =
            "error.repository.folder.already.exists";
    public static final String ERROR_REPOSITORY_ROOT_FOLDER_CANNOT_BE_REMOVED =
            "error.repository.root.folder.cannot.be.removed";
    public static final String ERROR_REPOSITORY_FOLDER_CANNOT_BE_REMOVED =
            "error.repository.folder.cannot.be.removed";
    public static final String ERROR_INVALID_PIPELINE_FILE_NAME = "error.pipeline.file.name.invalid";
    public static final String ERROR_REPOSITORY_INDEXING_DISABLED = "error.repository.indexing.disabled";

    // Instance offers expiration check messages
    public static final String DEBUG_INSTANCE_OFFERS_EXPIRATION_CHECK_RUNNING =
            "instance.offers.expiration.check.running";
    public static final String DEBUG_INSTANCE_OFFERS_EXPIRATION_CHECK_DONE =
        "instance.offers.expiration.check.done";
    public static final String INFO_INSTANCE_OFFERS_EXPIRED = "instance.offers.expired";
    public static final String DEBUG_INSTANCE_OFFERS_UPDATE_STARTED = "instance.offers.update.started";
    public static final String DEBUG_INSTANCE_OFFERS_UPDATE_FINISHED = "instance.offers.update.finished";
    public static final String INFO_INSTANCE_OFFERS_UPDATED = "instance.offers.updated";
    public static final String SETTING_IS_NOT_PROVIDED = "setting.is.not.provided";
    public static final String ERROR_INSTANCE_TYPE_IS_NOT_ALLOWED = "instance.type.not.allowed";
    public static final String ERROR_PRICE_TYPE_IS_NOT_ALLOWED = "price.type.not.allowed";
    public static final String ERROR_INSTANCE_DISK_IS_INVALID = "instance.disk.invalid";

    // Cloud
    public static final String CLOUD_BUCKET_ALREADY_EXISTS = "cloud.bucket.already.exists";

    //CAdvicer
    public static final String DEBUG_SEND_CADVISOR_REQUEST = "cadvisor.send.request";
    public static final String DEBUG_RECEIVE_CADVISOR_RESPONSE = "cadvisor.receive.response";
    public static final String CADVISOR_STATS_REPORTS_NOT_SUPPORTED = "cadvisor.reports.not.supported";

    // Users
    public static final String ERROR_USER_ID_NOT_FOUND = "user.id.not.found";
    public static final String ERROR_USER_LIST_EMPTY = "user.list.is.empty";
    public static final String ERROR_ROLE_ID_NOT_FOUND = "role.id.not.found";
    public static final String ERROR_ROLE_NAME_NOT_FOUND = "role.name.not.found";
    public static final String USER_PREFIX_IS_REQUIRED = "user.prefix.required";
    public static final String ERROR_USER_NAME_NOT_FOUND = "user.name.not.found";
    public static final String ERROR_USER_NAME_REQUIRED = "user.name.required";
    public static final String ERROR_USER_NAME_EXISTS = "user.name.exists";
    public static final String ERROR_ROLE_NAME_REQUIRED = "role.name.is.required";
    public static final String ERROR_ROLE_NAME_EXISTS = "role.name.exists";
    public static final String USER_GROUP_IS_REQUIRED = "user.group.is.required";
    public static final String ERROR_MUTABLE_ACL_RETURN = "error.mutable.acl.return";
    public static final String ERROR_NO_GROUP_WAS_FOUND = "error.no.group.was.found";
    public static final String ERROR_GROUP_STATUS_EXISTS = "group.status.exists";
    public static final String INFO_ASSIGN_ROLE = "info.assign.role";
    public static final String INFO_UNASSIGN_ROLE = "info.unassign.role";
    public static final String INFO_CREATE_USER = "info.create.user";
    public static final String INFO_DELETE_USER = "info.delete.user";
    public static final String INFO_UPDATE_USER_ROLES = "info.update.user.roles";
    public static final String INFO_UPDATE_USER_DATASTORAGE = "info.update.user.datastorage";
    public static final String INFO_UPDATE_USER_BLOCK_STATUS= "info.update.user.block.status";
    public static final String INFO_UPDATE_USER_SAML_INFO = "info.update.user.saml.info";


    // Security
    public static final String ERROR_PERMISSION_PARAM_REQUIRED = "permission.param.is.required";
    public static final String UNSUPPORTED_SECURITY_CLASS = "unsupported.security.class";
    public static final String ERROR_PERMISSION_IS_NOT_GRANTED = "error.permission.is.not.granted";
    public static final String ERROR_ENTITY_IS_LOCKED = "error.entity.is.locked";
    public static final String ERROR_USER_NOT_AUTHORIZED = "error.user.not.authorized";
    public static final String ERROR_USER_NOT_REGISTERED_EXPLICITLY = "user.not.registered.explicitly";
    public static final String ERROR_USER_NOT_REGISTERED_GROUP_EXPLICITLY = "user.not.registered.group.explicitly";

    // Metadata
    public static final String ERROR_METADATA_NOT_FOUND = "error.metadata.not.found";
    public static final String ERROR_INVALID_METADATA_ENTITY_ID = "error.invalid.metadata.entity.id";
    public static final String ERROR_INVALID_METADATA_ENTITY_CLASS = "error.invalid.metadata.entity.class";
    public static final String ERROR_INVALID_METADATA = "error.invalid.metadata";
    public static final String ERROR_METADATA_CLASS_NAME_IS_EMPTY = "error.metadata.class.name.empty";
    public static final String ERROR_INVALID_METADATA_ENTITY_CLASS_ID = "error.invalid.metadata.entity.class.id";
    public static final String ERROR_METADATA_ENTITY_CLASS_NOT_FOUND = "error.metadata.entity.class.not.found";
    public static final String ERROR_METADATA_ENTITY_NOT_FOUND = "error.metadata.entity.not.found";
    public static final String ERROR_METADATA_UPDATE_KEY_NOT_FOUND = "error.metadata.update.key.not.found";
    public static final String ERROR_INVALID_METADATA_FILTER = "error.invalid.metadata.filter";
    public static final String ERROR_METADATA_UPLOAD_CHANGED_TYPE = "error.metadata.upload.changed.type";
    public static final String ERROR_METADATA_ENTITIES_NOT_FOUND = "error.metadata.entities.not.found";
    public static final String ERROR_METADATA_ENTITY_WRITING_BAD_ENCODING =
            "error.metadata.entity.writing.bad.encoding";
    public static final String ERROR_METADATA_ENTITY_WRITING_UNSUPPORTED_FORMAT =
            "error.metadata.entity.writing.unsupported.format";
    public static final String ERROR_METADATA_ENTITY_CLASS_NOT_FOUND_IN_FOLDER =
            "error.metadata.entity.class.not.found.in.folder";
    public static final String ERROR_ENTITY_FOR_METADATA_NOT_FOUND = "error.entity.for.metadata.not.found";
    public static final String ERROR_ENTITY_FOR_METADATA_NOT_SPECIFIED = "error.entity.for.metadata.not.specified";
    public static final String ERROR_KEY_FOR_METADATA_UNIQUE_VALUES_REQUEST_NOT_SPECIFIED =
        "error.key.for.metadata.unique.values.not.specified";

    //Paging
    public static final String ERROR_PAGE_INDEX = "error.page.index";
    public static final String ERROR_PAGE_SIZE = "error.page.size";
    public static final String ERROR_INVALID_PAGE_INDEX_OR_SIZE = "error.invalid.page.index.size";
    public static final String ERROR_PAGINATION_IS_NOT_PROVIDED = "error.pagination.is.not.provided";

    //Entities
    public static final String ERROR_CLASS_NOT_SUPPORTED = "error.class.not.supported";
    public static final String ERROR_ENTITY_NOT_FOUND = "error.entity.not.found";
    public static final String ERROR_INVALID_ENTITY_CLASS = "error.invalid.entity.class";

    //System notifications
    public static final String ERROR_NOTIFICATION_NOT_FOUND = "error.notification.not.found";
    public static final String ERROR_NOTIFICATION_ID_REQUIRED = "error.notification.id.required";
    public static final String ERROR_NOTIFICATION_TITLE_REQUIRED = "error.notification.title.required";
    public static final String INFO_NOTIFICATION_SUBMITTED = "info.notification.submitted";

    //Pipeline notification
    public static final String ERROR_NOTIFICATION_SETTINGS_NOT_FOUND = "error.notification.settings.not.found";
    public static final String INFO_NOTIFICATION_TEMPLATE_NOT_CONFIGURED = "info.notification.template.not.configured";
    public static final String INFO_RUN_STATUS_NOT_CONFIGURED_FOR_NOTIFICATION = 
            "info.run.status.not.configured.for.notification";
    public static final String ERROR_TEMPLATE_ID_SHOULD_BE_EQUAL_TO_TYPE = "error.template.id.should.be.equal.to.type";
    public static final String ERROR_NOTIFICATION_SUBJECT_NOT_SPECIFIED = "error.notification.subject.not.specified";
    public static final String ERROR_NOTIFICATION_BODY_NOT_SPECIFIED = "error.notification.body.not.specified";
    public static final String ERROR_NOTIFICATION_RECEIVER_NOT_SPECIFIED = "error.notification.receiver.not.specified";

    //Parameters mapping
    public static final String ERROR_PARAMETER_MISSING_REFERENCE = "error.parameter.missing.reference";
    public static final String ERROR_PARAMETER_MISSING_VALUE = "error.parameter.missing.value";
    public static final String ERROR_PARAMETER_NON_REFERENCE_TYPE = "error.parameter.non.reference.type";
    public static final String ERROR_PARAMETER_NON_SCALAR_TYPE = "error.parameter.non.scalar.type";
    public static final String ERROR_PARAMETER_INVALID_ARRAY = "error.parameter.invalid.array";
    public static final String ERROR_EXPRESSION_INVALID_FORMAT = "error.expression.invalid.format";

    // ToolScan messages
    public static final String ERROR_TOOL_SCAN_FAILED = "error.tool.scan.failed";
    public static final String ERROR_UPDATE_TOOL_VERSION_FAILED = "error.update.tool.version.failed";
    public static final String INFO_TOOL_SCAN_REGISTRY_STARTED = "info.tool.scan.registry.started";
    public static final String INFO_TOOL_SCAN_SCHEDULED_STARTED = "info.tool.scan.scheduled.started";
    public static final String INFO_TOOL_SCAN_SCHEDULED_DONE = "info.tool.scan.scheduled.done";
    public static final String INFO_TOOL_FORCE_SCAN_STARTED = "info.tool.scan.force.started";
    public static final String ERROR_TOOL_SCAN_DISABLED = "error.tool.scan.disabled";
    public static final String ERROR_TOOL_SECURITY_POLICY_VIOLATION = "error.tool.security.policy.violation";
    public static final String INFO_TOOL_SCAN_ALREADY_SCANNED = "info.tool.scan.already.scanned";
    public static final String INFO_TOOL_SCAN_NEW_LAYERS = "info.tool.scan.new.layers";


    //Issues
    public static final String ERROR_ISSUE_NOT_FOUND = "error.issue.not.found";
    public static final String ERROR_INVALID_ISSUE_PARAMETERS = "error.invalid.issue.parameters";
    public static final String ERROR_INVALID_ISSUE_NAME = "error.invalid.issue.name";
    public static final String ERROR_INVALID_ISSUE_STATUS = "error.invalid.issue.status";
    public static final String ERROR_INVALID_ISSUE_ENTITY_PARAMETERS = "error.invalid.issue.entity.parameters";
    public static final String ERROR_INVALID_ISSUE_ENTITY_ID = "error.invalid.issue.entity.id";
    public static final String ERROR_INVALID_ISSUE_ENTITY_CLASS = "error.invalid.issue.entity.class";
    public static final String ERROR_INVALID_ISSUE_TEXT = "error.invalid.issue.text";
    public static final String ERROR_ISSUE_STATUS_IS_CLOSED = "error.issue.status.is.closed";
    public static final String ERROR_COMMENT_NOT_FOUND = "error.comment.not.found";
    public static final String ERROR_INVALID_COMMENT_TEXT = "error.invalid.comment.text";
    public static final String ERROR_WRONG_ISSUE_ID_OR_COMMENT_ID = "error.wrong.issue.id.or.comment.id";

    // Attachments
    public static final String ERROR_ATTACHMENT_NOT_FOUND = "error.attachment.not.found";
    public static final String ERROR_ATTACHMENT_SYSTEM_DATA_STORAGE_NOT_CONFIGURED =
        "error.attachment.system.data.storage.not.configured";

    //Preferences
    public static final String ERROR_PREFERENCE_NAME_NOT_SPECIFIED = "error.preference.name.not.specified";
    public static final String ERROR_PREFERENCE_TYPE_NOT_SPECIFIED = "error.preference.type.not.specified";
    public static final String ERROR_PREFERENCE_WITH_NAME_NOT_FOUND = "error.preference.with.name.not.found";
    public static final String ERROR_PREFERENCE_WITH_NAME_HAS_DIFFERENT_TYPE =
            "error.preference.with.name.has.different.type";
    public static final String INFO_PREFERENCE_UPDATED_WITH_ADDITIONAL_TASKS =
        "info.preference.update.with.additional.tasks";

    public static final String ERROR_PREFERENCE_VALUE_INVALID = "error.preference.value.invalid";
    public static final String ERROR_PREFERENCE_REQUIREMENTS_NOT_MET = "error.preference.requirements.not.met";

    //Google and Firecloud
    public static final String ERROR_GOOGLE_CREDENTIALS = "error.google.credentials";
    public static final String ERROR_GOOGLE_AUTH_CODE_MISSING = "error.google.auth.code.missing";
    public static final String ERROR_GOOGLE_SCOPES_MISSING = "error.google.scopes.missing";
    public static final String ERROR_GOOGLE_REDIRECT_URL_MISSING = "error.google.redirect.uri.missing";
    public static final String ERROR_GOOGLE_SECRET_MISSING = "error.google.secret.json.missing";
    public static final String ERROR_GOOGLE_INVALID_SECRET_JSON = "error.google.secret.json.invalid";
    public static final String ERROR_FIRECLOUD_REQUEST_FAILED = "error.firecloud.request.failed";

    //DTS
    public static final String ERROR_DTS_REGISTRY_DOES_NOT_EXIST = "error.dts.registry.does.not.exist";
    public static final String ERROR_DTS_REGISTRY_IS_EMPTY = "error.dts.registry.is.empty";
    public static final String ERROR_DTS_REGISTRY_URL_IS_EMPTY = "error.dts.registry.url.is.empty";
    public static final String ERROR_DTS_REGISTRY_PREFIXES_ARE_EMPTY = "error.dts.registry.prefixes.are.empty";
    public static final String ERROR_DTS_REGISTRY_ID_IS_EMPTY = "error.dts.registry.id.is.empty";
    public static final String ERROR_DTS_REGISTRY_NAME_IS_EMPTY = "error.dts.registry.name.is.empty";
    public static final String ERROR_DTS_NOT_SCHEDULABLE = "error.dts.registry.not.schedulable";

    //Cloud region
    public static final String ERROR_REGION_NOT_FOUND = "error.region.not.found";
    public static final String ERROR_REGION_CREDENTIALS_NOT_FOUND = "error.region.credentials.not.found";
    public static final String ERROR_REGION_DEFAULT_UNDEFINED = "error.region.default.undefined";
    public static final String ERROR_REGION_NAME_MISSING = "error.region.name.missing";
    public static final String ERROR_REGION_MOUNT_RULE_MISSING = "error.region.mount.rule.missing";
    public static final String ERROR_REGION_PROVIDER_MISMATCH = "error.region.provider.mismatch";
    public static final String ERROR_REGION_REGIONID_MISSING = "error.region.regionid.missing";
    public static final String ERROR_REGION_REGIONID_INVALID = "error.region.regionid.invalid";
    public static final String ERROR_REGION_JSON_WRITING_FAILED = "error.region.json.writing.failed";
    public static final String ERROR_REGION_CORS_RULES_INVALID = "error.region.cors.rules.invalid";
    public static final String ERROR_REGION_POLICY_INVALID = "error.region.policy.invalid";

    //Contextual preferences
    public static final String ERROR_CONTEXTUAL_PREFERENCE_NOT_FOUND = "error.contextual.preference.not.found";
    public static final String ERROR_CONTEXTUAL_PREFERENCE_NAME_MISSING = "error.contextual.preference.name.missing";
    public static final String ERROR_CONTEXTUAL_PREFERENCE_NAMES_MISSING = "error.contextual.preference.names.missing";
    public static final String ERROR_CONTEXTUAL_PREFERENCE_NAMES_EMPTY = "error.contextual.preference.names.empty";
    public static final String ERROR_CONTEXTUAL_PREFERENCE_VALUE_MISSING = "error.contextual.preference.value.missing";
    public static final String ERROR_CONTEXTUAL_PREFERENCE_VALUE_INVALID = "error.contextual.preference.value.invalid";
    public static final String ERROR_CONTEXTUAL_PREFERENCE_TYPE_MISSING = "error.contextual.preference.type.missing";
    public static final String ERROR_CONTEXTUAL_PREFERENCE_TYPE_INVALID = "error.contextual.preference.type.invalid";
    public static final String ERROR_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_MISSING =
            "error.contextual.preference.external.resource.missing";
    public static final String ERROR_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_LEVEL_MISSING =
            "error.contextual.preference.external.resource.level.missing";
    public static final String ERROR_SAVE_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_LEVEL_INVALID =
            "error.save.contextual.preference.external.resource.level.invalid";
    public static final String ERROR_SEARCH_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_LEVEL_INVALID =
            "error.search.contextual.preference.external.resource.level.invalid";
    public static final String ERROR_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_ID_MISSING =
            "error.contextual.preference.external.resource.id.missing";
    public static final String ERROR_CONTEXTUAL_PREFERENCE_EXTERNAL_RESOURCE_NOT_FOUND =
            "error.contextual.preference.external.resource.not.found";
    public static final String WARN_CONTEXTUAL_PREFERENCE_DIFFERENT_VALUES =
            "warn.contextual.preference.reducer.different.values";
    public static final String WARN_CONTEXTUAL_PREFERENCE_REDUCING_FAILED =
            "warn.contextual.preference.reducing.failed";

    //Common cloud messages
    public static final String ERROR_CLOUD_PROVIDER_NOT_SUPPORTED = "error.cloud.provider.not.supported";

    //Azure
    public static final String ERROR_AZURE_STORAGE_ACC_REQUIRED = "error.azure.storage.account.required";
    public static final String ERROR_AZURE_STORAGE_KEY_REQUIRED = "error.azure.storage.key.required";
    public static final String ERROR_AZURE_INSTANCE_NOT_FOUND = "error.azure.instance.not.found";
    public static final String ERROR_AZURE_RESOURCE_IS_NOT_VM_LIKE = "error.azure.resource.is.not.vm.like";
    public static final String ERROR_AZURE_SCALE_SET_DOESNT_CONTAIN_VMS = "error.azure.scale.set.doesnt.contain.vm";
    public static final String ERROR_AZURE_INSTANCE_NOT_RUNNING = "error.azure.instance.not.running";
    public static final String ERROR_DATASTORAGE_AZURE_INVALID_ACCOUNT_KEY =
            "error.datastorage.azure.invalid.account.key";
    public static final String ERROR_DATASTORAGE_AZURE_CREATE_FILE = "error.datastorage.azure.create.file";
    public static final String ERROR_AZURE_RESOURCE_GROUP_NOT_FOUND = "error.azure.resource.group.not.found";
    public static final String ERROR_AZURE_AUTH_FILE_IS_INVALID = "error.azure.auth.file.invalid";
    public static final String ERROR_AZURE_IP_RANGE_IS_INVALID = "error.azure.policy.ip.range.invalid";
    public static final String ERROR_AZURE_IP_IS_INVALID = "error.azure.ip.policy.invalid";


    //GCP
    public static final String ERROR_GCP_PROJECT_REQUIRED = "error.gcp.project.required";
    public static final String ERROR_GCP_IMP_ACC_REQUIRED = "error.gcp.impersonate.account";
    public static final String ERROR_GCP_SSH_KEY_REQUIRED = "error.gcp.ssh.key.required";

    public static final String ERROR_GCP_INSTANCE_NOT_RUNNING = "error.gcp.instance.not.running";
    public static final String ERROR_GCP_INSTANCE_NOT_FOUND = "error.gcp.instance.not.found";

    //AWS
    public static final String ERROR_AWS_PROFILE_UNIQUENESS = "error.aws.profile.uniqueness";
    public static final String ERROR_AWS_S3_ROLE_UNIQUENESS = "error.aws.s3.role.uniqueness";

    //Billing
    public static final String ERROR_BILLING_FIELD_DATE_GROUPING_NOT_SUPPORTED =
        "error.billing.date.field.grouping.not.supported";
    public static final String INFO_BILLING_ENTITY_FOR_DETAILS_NOT_FOUND =
        "error.billing.entity.for.grouping.not.found";
    public static final String ERROR_BILLING_DETAILS_NOT_SUPPORTED = "error.billing.details.not.supported";
    public static final String ERROR_BILLING_INTERVAL_NOT_SUPPORTED = "error.billing.interval.not.supported";
    public static final String ERROR_ILLEGAL_PAGING_PARAMETERS = "error.billing.invalid.paging";

    //Disks    
    public static final String ERROR_DISK_NODE_MISSING = "error.disk.node.missing";
    public static final String ERROR_DISK_DATE_MISSING = "error.disk.date.missing";
    public static final String ERROR_DISK_SIZE_MISSING = "error.disk.size.missing";
    public static final String ERROR_DISK_SIZE_INVALID = "error.disk.size.invalid";

    //System dictionaries
    public static final String ERROR_CATEGORICAL_ATTRIBUTE_DOESNT_EXIST = "categorical.attribute.not.exist";
    public static final String ERROR_CATEGORICAL_ATTRIBUTE_INVALID_LINK = "categorical.attribute.invalid.link";

    //Other
    public static final String ERROR_KEEP_ALIVE_POLICY_NOT_SUPPORTED = "error.keep.alive.policy.not.supported";

    //Lustre
    public static final String ERROR_LUSTRE_NOT_FOUND = "error.lustre.not.found.for.run";
    public static final String ERROR_LUSTRE_NOT_CREATED = "error.lustre.not.created.for.run";
    public static final String ERROR_LUSTRE_REGION_NOT_SUPPORTED = "error.lustre.region.not.supported";
    public static final String ERROR_LUSTRE_MISSING_CONFIG = "error.lustre.missing.config";
    public static final String ERROR_LUSTRE_MISSING_INSTANCE = "error.lustre.missing.instance";

    private MessageConstants() {
        // no-op
    }
}
