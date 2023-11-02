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

package com.epam.pipeline.manager.preference;

import com.amazonaws.services.fsx.model.LustreDeploymentType;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.cloudaccess.CloudAccessManagementConfig;
import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
import com.epam.pipeline.entity.cluster.ClusterKeepAlivePolicy;
import com.epam.pipeline.entity.cluster.DockerMount;
import com.epam.pipeline.entity.cluster.EnvVarsSettings;
import com.epam.pipeline.entity.cluster.LaunchCapability;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.container.ContainerMemoryResourcePolicy;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequestAction;
import com.epam.pipeline.entity.datastorage.StorageQuotaAction;
import com.epam.pipeline.entity.datastorage.nfs.NFSMountPolicy;
import com.epam.pipeline.entity.execution.OSSpecificLaunchCommandTemplate;
import com.epam.pipeline.entity.git.GitlabIssueLabelsFilter;
import com.epam.pipeline.entity.git.GitlabVersion;
import com.epam.pipeline.entity.ldap.LdapBlockedUserSearchMethod;
import com.epam.pipeline.entity.monitoring.IdleRunAction;
import com.epam.pipeline.entity.monitoring.LongPausedRunAction;
import com.epam.pipeline.entity.notification.filter.NotificationFilter;
import com.epam.pipeline.entity.pipeline.run.RunVisibilityPolicy;
import com.epam.pipeline.entity.pipeline.run.parameter.RuntimeParameter;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.search.StorageFileSearchMask;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.sharing.SharedStoragePermissions;
import com.epam.pipeline.entity.sharing.StaticResourceSettings;
import com.epam.pipeline.entity.templates.DataStorageTemplate;
import com.epam.pipeline.entity.utils.ControlEntry;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.cloud.CloudInstancePriceService;
import com.epam.pipeline.manager.cloud.gcp.GCPResourceMapping;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerClientFactory;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.git.GitManager;
import com.epam.pipeline.manager.git.GitlabClient;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.AbstractSystemPreference.BooleanPreference;
import com.epam.pipeline.manager.preference.AbstractSystemPreference.DoublePreference;
import com.epam.pipeline.manager.preference.AbstractSystemPreference.IntPreference;
import com.epam.pipeline.manager.preference.AbstractSystemPreference.LongPreference;
import com.epam.pipeline.manager.preference.AbstractSystemPreference.ObjectPreference;
import com.epam.pipeline.manager.preference.AbstractSystemPreference.StringPreference;
import com.epam.pipeline.security.ExternalServiceEndpoint;
import com.epam.pipeline.utils.CommonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.preference.PreferenceValidators.isGreaterThan;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isGreaterThanOrEquals;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isLessThan;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isNotBlank;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isNotLessThanValueOrNull;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isNullOrGreaterThan;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isNullOrValidEnum;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isNullOrValidJson;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isNullOrValidLocalPath;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isValidEnum;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isValidMapOfLaunchCommands;
import static com.epam.pipeline.manager.preference.PreferenceValidators.pass;

/**
 * A component class, that contains AbstractSystemPreference - a set of predefined preferences. It also provides
 * validation functionality.
 */
@Component
@SuppressWarnings("PMD.TooManyStaticImports")
public class SystemPreferences {
    private static final String COMMIT_GROUP = "Commit";
    private static final String GIT_GROUP = "Git";
    private static final String DATA_STORAGE_GROUP = "Data storage";
    private static final String DOCKER_SECURITY_GROUP = "Docker security";
    private static final String CLUSTER_GROUP = "Cluster";
    private static final String LAUNCH_GROUP = "Launch";
    private static final String DTS_GROUP = "DTS";
    private static final String UI_GROUP = "User Interface";
    private static final String BASE_URLS_GROUP = "Base URLs";
    private static final String MISC_GROUP = "Miscellaneous";
    private static final String FIRECLOUD_GROUP = "FireCloud";
    private static final String DATA_SHARING_GROUP = "Data sharing";
    private static final String SYSTEM_GROUP = "System"; // important stuff, related to system as a whole
    private static final String SEARCH_GROUP = "Search";
    private static final String GRID_ENGINE_AUTOSCALING_GROUP = "Grid engine autoscaling";
    private static final String GCP_GROUP = "GCP";
    private static final String BILLING_GROUP = "Billing Reports";
    private static final String LUSTRE_GROUP = "Lustre FS";
    private static final String LDAP_GROUP = "LDAP";
    private static final String BILLING_QUOTAS_GROUP= "Billing Quotas";
    private static final String NGS_PREPROCESSING_GROUP = "NGS Preprocessing";
    private static final String MONITORING_GROUP = "Monitoring";
    private static final String CLOUD = "Cloud";
    private static final String CLOUD_REGION_GROUP = "Cloud region";
    private static final String SYSTEM_JOBS_GROUP = "System Jobs";

    private static final String STORAGE_FSBROWSER_BLACK_LIST_DEFAULT =
            "/bin,/var,/home,/root,/sbin,/sys,/usr,/boot,/dev,/lib,/proc,/etc";
    private static final String FACETED_FILTER_GROUP = "Faceted Filter";

    // COMMIT_GROUP
    public static final StringPreference COMMIT_DEPLOY_KEY = new StringPreference("commit.deploy.key", null,
                                                                  COMMIT_GROUP, isNotBlank);
    public static final IntPreference COMMIT_TIMEOUT = new IntPreference("commit.timeout", 600, COMMIT_GROUP,
                                                                         isGreaterThan(0));
    public static final StringPreference COMMIT_USERNAME = new StringPreference("commit.username", null,
                                                                         COMMIT_GROUP, isNotBlank);
    public static final StringPreference PRE_COMMIT_COMMAND_PATH = new StringPreference("commit.pre.command.path",
            "/root/pre_commit.sh", COMMIT_GROUP, isNotBlank);
    public static final StringPreference POST_COMMIT_COMMAND_PATH = new StringPreference("commit.post.command.path",
            "/root/post_commit.sh", COMMIT_GROUP, isNotBlank);
    public static final IntPreference PAUSE_TIMEOUT = new IntPreference("pause.timeout", 24 * 60 * 60,
            COMMIT_GROUP, isGreaterThan(0));
    public static final IntPreference PAUSE_LAYERS_COUNT_TO_SQUASH = new IntPreference(
        "pause.layers.count.to.squash", 25, COMMIT_GROUP, isGreaterThanOrEquals(-1));
    // List of ',' separated env vars to be cleaned up from docker image before commit
    public static final StringPreference ADDITIONAL_ENVS_TO_CLEAN = new StringPreference(
            "commit.additional.envs.to.clean", "CP_EXEC_TIMEOUT", COMMIT_GROUP, pass);
    public static final IntPreference GET_LAYERS_COUNT_TIMEOUT = new IntPreference("get.layers.count.timeout", 600,
            COMMIT_GROUP, isGreaterThan(0));
    public static final IntPreference COMMIT_MAX_LAYERS = new IntPreference("commit.max.layers", 127,
            COMMIT_GROUP, isGreaterThan(0));

    // DATA_STORAGE_GROUP
    public static final BooleanPreference DATA_STORAGE_TAG_RESTRICTED_ACCESS_ENABLED = new BooleanPreference(
        "storage.tag.restricted.access", false, DATA_STORAGE_GROUP, pass);
    public static final ObjectPreference<List<String>> DATA_STORAGE_TAG_RESTRICTED_ACCESS_EXCLUDE_KEYS =
        new ObjectPreference<>("storage.tag.restricted.access.exclude.keys",
        ListUtils.unmodifiableList(Arrays.asList("CP_SOURCE", "CP_OWNER", "CP_RUN_ID", "CP_JOB_ID",
                "CP_JOB_NAME", "CP_JOB_VERSION", "CP_JOB_CONFIGURATION", "CP_DOCKER_IMAGE", "CP_CALC_CONFIG")),
        new TypeReference<List<String>>() {}, DATA_STORAGE_GROUP,
        isNullOrValidJson(new TypeReference<List<String>>() {}));
    public static final BooleanPreference DATA_STORAGE_MGMT_RESTRICTED_ACCESS_ENABLED = new BooleanPreference(
        "storage.mgmt.restricted.access", false, DATA_STORAGE_GROUP, pass);
    public static final IntPreference DATA_STORAGE_MAX_DOWNLOAD_SIZE = new IntPreference(
        "storage.max.download.size", 10000, DATA_STORAGE_GROUP, isGreaterThan(0));
    public static final IntPreference DATA_STORAGE_TEMP_CREDENTIALS_DURATION = new IntPreference(
        "storage.temp.credentials.duration", 3600, DATA_STORAGE_GROUP, isGreaterThan(0));
    public static final IntPreference PROFILE_TEMP_CREDENTIALS_DURATION = new IntPreference(
            "profile.temp.credentials.duration", 3600, DATA_STORAGE_GROUP, isGreaterThan(0));
    public static final IntPreference STORAGE_MOUNTS_PER_GB_RATIO = new IntPreference(
            "storage.mounts.per.gb.ratio", null, DATA_STORAGE_GROUP, isNullOrGreaterThan(0));
    public static final BooleanPreference DEFAULT_USER_DATA_STORAGE_ENABLED =
        new BooleanPreference("storage.user.home.auto", false, DATA_STORAGE_GROUP, pass);
    public static final ObjectPreference<DataStorageTemplate> DEFAULT_USER_DATA_STORAGE_TEMPLATE =
        new ObjectPreference<>("storage.user.home.template",
                               null,
                               new TypeReference<DataStorageTemplate>() {},
                               DATA_STORAGE_GROUP,
                               isNullOrValidJson(new TypeReference<DataStorageTemplate>() {}));
    public static final IntPreference DATA_STORAGE_OPERATIONS_BULK_SIZE = new IntPreference(
            "storage.operations.bulk.size", 1000, DATA_STORAGE_GROUP, isGreaterThan(0));
    public static final StringPreference VERSION_STORAGE_REPORT_TEMPLATE = new StringPreference(
            "storage.version.storage.report.template", null, DATA_STORAGE_GROUP, isNullOrValidLocalPath());
    public static final StringPreference VERSION_STORAGE_BINARY_FILE_EXTS = new StringPreference(
            "storage.version.storage.report.binary.file.exts",
            "pdf", DATA_STORAGE_GROUP, pass);
    public static final StringPreference VERSION_STORAGE_IGNORED_FILES = new StringPreference(
            "storage.version.storage.ignored.files",
            ".gitkeep", DATA_STORAGE_GROUP, PreferenceValidators.isEmptyOrValidBatchOfPaths);
    public static final IntPreference DATA_STORAGE_DAV_MOUNT_MAX_STORAGES = new IntPreference(
            "storage.dav.mount.max.storages", 32, DATA_STORAGE_GROUP, isGreaterThan(0));
    public static final IntPreference DATA_STORAGE_DAV_ACCESS_DURATION_SECONDS = new IntPreference(
            "storage.webdav.access.duration.seconds",  Constants.SECONDS_IN_DAY, DATA_STORAGE_GROUP, isGreaterThan(0));
    public static final BooleanPreference DATA_STORAGE_POLICY_BACKUP_VISIBLE_NON_ADMINS =
        new BooleanPreference("storage.policy.backup.visible.non.admins", true, DATA_STORAGE_GROUP, pass);

    /**
     * Black list for mount points, accept notation like: '/dir/*', '/dir/**'
     * */
    public static final StringPreference DATA_STORAGE_NFS_MOUNT_BLACK_LIST = new StringPreference(
            "storage.mount.black.list",
            "/,/etc,/runs,/common,/bin,/opt,/var,/home,/root,/sbin,/sys,/usr,/boot,/dev,/lib,/proc,/tmp",
            DATA_STORAGE_GROUP, PreferenceValidators.isEmptyOrValidBatchOfPaths);

    /**
     * Defines NFS mounting policy for sensitive runs. Can take SKIP, TIMEOUT, NONE values.
     * */
    public static final StringPreference DATA_STORAGE_NFS_MOUNT_SENSITIVE_POLICY = new StringPreference(
            "storage.mounts.nfs.sensitive.policy",
            NFSMountPolicy.SKIP.name(),
            DATA_STORAGE_GROUP,
            isNullOrValidEnum(NFSMountPolicy.class),
            true);

    /**
     * Configures a system data storage for storing attachments and etc.
     */
    public static final StringPreference DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME = new StringPreference(
        "storage.system.storage.name", null, DATA_STORAGE_GROUP, null);

    public static final StringPreference DATA_STORAGE_RUN_SHARED_STORAGE_NAME = new StringPreference(
            "storage.system.run.shared.storage.name", null, DATA_STORAGE_GROUP, pass);
    public static final StringPreference DATA_STORAGE_RUN_SHARED_FOLDER_PATTERN = new StringPreference(
            "storage.system.run.shared.folder.pattern", null, DATA_STORAGE_GROUP, pass);

    public static final LongPreference STORAGE_SYSTEM_TRANSFER_PIPELINE_ID = new LongPreference(
            "storage.transfer.pipeline.id", null, DATA_STORAGE_GROUP, pass);

    public static final StringPreference STORAGE_SYSTEM_TRANSFER_PIPELINE_VERSION = new StringPreference(
            "storage.transfer.pipeline.version", null, DATA_STORAGE_GROUP, pass);
    public static final StringPreference STORAGE_OBJECT_PREFIX = new StringPreference("storage.object.prefix",
            null, DATA_STORAGE_GROUP, pass);
    public static final LongPreference STORAGE_LISTING_TIME_LIMIT =
            new LongPreference("storage.listing.time.limit", 3000L, DATA_STORAGE_GROUP, pass);
    public static final IntPreference STORAGE_INCOMPLETE_UPLOAD_CLEAN_DAYS =
            new IntPreference("storage.incomplete.upload.clean.days", 5, DATA_STORAGE_GROUP,
                    isNullOrGreaterThan(0));
    public static final BooleanPreference STORAGE_ALLOW_SIGNED_URLS =
            new BooleanPreference("storage.allow.signed.urls", true, DATA_STORAGE_GROUP, pass);
    public static final StringPreference STORAGE_CONVERT_SOURCE_ACTION =
            new StringPreference("storage.convert.source.action", DataStorageConvertRequestAction.LEAVE.name(),
                    DATA_STORAGE_GROUP, (v, ignored) -> DataStorageConvertRequestAction.isValid(v));
    public static final LongPreference STORAGE_LIFECYCLE_PROLONG_DAYS =
            new LongPreference("storage.lifecycle.prolong.days", 7L, DATA_STORAGE_GROUP,
                    isGreaterThan(0), true);
    public static final LongPreference STORAGE_LIFECYCLE_NOTIFY_BEFORE_DAYS =
            new LongPreference("storage.lifecycle.notify.before.days", 7L, DATA_STORAGE_GROUP,
                    isGreaterThan(0), true);

    public static final LongPreference STORAGE_LIFECYCLE_DEFAULT_RESTORE_DAYS =
            new LongPreference("storage.lifecycle.default.restore.days", 30L, DATA_STORAGE_GROUP,
                    isGreaterThan(0), true);

    /**
     * Configures parameters that will be passed to pipeline containers to be able to configure fbrowser.
     */
    public static final BooleanPreference STORAGE_FSBROWSER_ENABLED =
            new BooleanPreference("storage.fsbrowser.enabled", true, DATA_STORAGE_GROUP, pass);
    public static final IntPreference STORAGE_FSBROWSER_PORT =
            new IntPreference("storage.fsbrowser.port", 8091, DATA_STORAGE_GROUP, isGreaterThan(1000));
    public static final StringPreference STORAGE_FSBROWSER_WD =
            new StringPreference("storage.fsbrowser.wd", "/", DATA_STORAGE_GROUP, pass);
    public static final StringPreference STORAGE_FSBROWSER_TMP =
            new StringPreference("storage.fsbrowser.tmp", "/tmp", DATA_STORAGE_GROUP, pass);
    public static final StringPreference STORAGE_FSBROWSER_TRANSFER =
            new StringPreference("storage.fsbrowser.transfer", null, DATA_STORAGE_GROUP, pass);
    public static final StringPreference STORAGE_FSBROWSER_BLACK_LIST = new StringPreference(
            "storage.fsbrowser.black.list", STORAGE_FSBROWSER_BLACK_LIST_DEFAULT, DATA_STORAGE_GROUP, pass);

    /**
     * Storage quotas effective size masking configuration
     */
    public static final ObjectPreference<List<StorageFileSearchMask>> STORAGE_QUOTAS_SKIPPED_PATHS =
        new ObjectPreference<>(
            "storage.quotas.skipped.paths",
            Collections.emptyList(),
            new TypeReference<List<StorageFileSearchMask>>() {},
            DATA_STORAGE_GROUP,
            isNullOrValidJson(new TypeReference<List<StorageFileSearchMask>>() {}));

    /**
     * Storage quotas grace period configuration
     */
    public static final ObjectPreference<Map<StorageQuotaAction, Integer>> STORAGE_QUOTAS_ACTIONS_GRACE =
        new ObjectPreference<>(
            "storage.quotas.actions.grace.period",
            Collections.emptyMap(),
            new TypeReference<Map<StorageQuotaAction, Integer>>() {},
            DATA_STORAGE_GROUP,
            PreferenceValidators.isValidGraceConfiguration);

    // GIT_GROUP
    public static final StringPreference GIT_HOST = new StringPreference("git.host", null, GIT_GROUP, null);
    public static final StringPreference GIT_READER_HOST =
            new StringPreference("git.reader.service.host", null, GIT_GROUP, pass);
    public static final StringPreference GIT_EXTERNAL_URL =
            new StringPreference("git.external.url", null, GIT_GROUP, pass);
    public static final StringPreference GIT_TOKEN = new StringPreference("git.token", null, GIT_GROUP, null);
    public static final IntPreference GIT_USER_ID = new IntPreference("git.user.id", null, GIT_GROUP, null);
    public static final StringPreference GIT_USER_NAME = new StringPreference("git.user.name", null, GIT_GROUP, null);
    public static final StringPreference GIT_CLI_CONFIG_TEMPLATE =
            new StringPreference("ui.git.cli.configure.template", null, GIT_GROUP, pass);
    public static final BooleanPreference GIT_REPOSITORY_INDEXING_ENABLED = new BooleanPreference(
            "git.repository.indexing.enabled", true, GIT_GROUP, pass);
    public static final StringPreference GIT_REPOSITORY_HOOK_URL = new StringPreference(
            "git.repository.hook.url", null, GIT_GROUP, PreferenceValidators.isValidUrl);
    public static final IntPreference GIT_FORK_WAIT_TIMEOUT = new IntPreference("git.fork.wait.timeout", 500,
            GIT_GROUP, isGreaterThan(0));
    public static final IntPreference GIT_FORK_RETRY_COUNT = new IntPreference("git.fork.retry.count", 5,
            GIT_GROUP, isGreaterThan(0));
    public static final StringPreference GIT_FSBROWSER_WD =
            new StringPreference("git.fsbrowser.workdir", "/git-workdir", GIT_GROUP, pass);
    public static final StringPreference BITBUCKET_USER_NAME =
            new StringPreference("bitbucket.user.name", null, GIT_GROUP, pass);
    public static final StringPreference GITLAB_API_VERSION = new StringPreference(
            "git.gitlab.api.version", "v3", GIT_GROUP, pass);
    public static final BooleanPreference GITLAB_HASHED_REPO_SUPPORT = new BooleanPreference(
            "git.gitlab.hashed.repo.support", false, GIT_GROUP, pass);
    public static final StringPreference GITLAB_DEFAULT_SRC_DIRECTORY = new StringPreference(
            "gitlab.default.src.directory", "src/", GIT_GROUP, pass, true);
    public static final StringPreference GITLAB_DEFAULT_DOC_DIRECTORY = new StringPreference(
            "gitlab.default.doc.directory", "docs/", GIT_GROUP, pass, true);
    public static final StringPreference BITBUCKET_DEFAULT_SRC_DIRECTORY = new StringPreference(
            "bitbucket.default.src.directory", "/", GIT_GROUP, pass, true);
    public static final StringPreference BITBUCKET_DEFAULT_DOC_DIRECTORY = new StringPreference(
            "bitbucket.default.doc.directory", null, GIT_GROUP, pass, true);
    public static final StringPreference GITLAB_PROJECT_VISIBILITY = new StringPreference(
            "git.gitlab.repo.visibility", "private", GIT_GROUP, pass, true);
    public static final StringPreference GITLAB_ISSUE_PROJECT = new StringPreference(
            "git.gitlab.issue.project", null, GIT_GROUP, pass, true);

    public static final BooleanPreference GITLAB_SERVER_FILTERING = new BooleanPreference(
            "git.gitlab.issue.server.filtering", false, GIT_GROUP, pass);
    public static final ObjectPreference<List<String>> GITLAB_ISSUE_STATUSES = new ObjectPreference<>(
            "git.gitlab.issue.statuses", null, new TypeReference<List<String>>() {}, GIT_GROUP,
            isNullOrValidJson(new TypeReference<List<String>>() {}), true);
    public static final ObjectPreference<List<String>> GITLAB_DEFAULT_LABELS = new ObjectPreference<>(
            "git.gitlab.default.labels", null, new TypeReference<List<String>>() {}, GIT_GROUP,
            isNullOrValidJson(new TypeReference<List<String>>() {}), true);
    public static final ObjectPreference<GitlabIssueLabelsFilter> GITLAB_ISSUE_DEFAULT_FILTER = new ObjectPreference<>(
            "git.gitlab.issue.default.filter", null, new TypeReference<GitlabIssueLabelsFilter>() {},
            GIT_GROUP, isNullOrValidJson(new TypeReference<GitlabIssueLabelsFilter>() {}), true);
    public static final LongPreference GIT_DEFAULT_TOKEN_DURATION_DAYS = new LongPreference(
            "git.default.token.duration.days", 1L, GIT_GROUP, isGreaterThan(0L));

    // DOCKER_SECURITY_GROUP
    /**
     * The main trigger, that enables security scanning
     */
    public static final LongPreference DOCKER_SECURITY_TOOL_JWT_TOKEN_EXPIRATION = new LongPreference(
            "security.tools.jwt.token.expiration", 30L, DOCKER_SECURITY_GROUP,
            isGreaterThan(0L));
    public static final StringPreference DOCKER_SECURITY_TOOL_SCAN_CLAIR_ROOT_URL = new StringPreference(
            "security.tools.scan.clair.root.url", null, DOCKER_SECURITY_GROUP,
            PreferenceValidators.isValidUrlOrBlank);
    public static final StringPreference DOCKER_SECURITY_TOOL_OS = new StringPreference(
            "security.tools.os", "", DOCKER_SECURITY_GROUP,
            PreferenceValidators.isEmptyOrValidBatchOfOSes);
    public static final StringPreference DOCKER_COMP_SCAN_ROOT_URL = new StringPreference(
            "security.tools.docker.comp.scan.root.url", null, DOCKER_SECURITY_GROUP,
            PreferenceValidators.isValidUrlOrBlank);
    public static final BooleanPreference DOCKER_SECURITY_TOOL_SCAN_ENABLED = new BooleanPreference(
            "security.tools.scan.enabled", false, DOCKER_SECURITY_GROUP,
            PreferenceValidators.isDockerSecurityScanGroupValid, DOCKER_SECURITY_TOOL_SCAN_CLAIR_ROOT_URL);

    public static final IntPreference DOCKER_SECURITY_TOOL_GRACE_HOURS = new IntPreference(
            "security.tools.grace.hours", 0, DOCKER_SECURITY_GROUP, isGreaterThanOrEquals(0));
    /**
     * Defines, if all registries has to be scanned. If 'false', only registries, that has securityScanEnabled flag set
     * to true will be scanned
     */
    public static final BooleanPreference DOCKER_SECURITY_TOOL_SCAN_ALL_REGISTRIES = new BooleanPreference(
        "security.tools.scan.all.registries", true, DOCKER_SECURITY_GROUP, pass);
    /**
     * Clair Service URL
     */
    public static final IntPreference DOCKER_SECURITY_TOOL_SCAN_CLAIR_CONNECT_TIMEOUT = new IntPreference(
        "security.tools.scan.clair.connect.timeout", 60, DOCKER_SECURITY_GROUP, isGreaterThan(30));
    public static final IntPreference DOCKER_SECURITY_TOOL_SCAN_CLAIR_READ_TIMEOUT = new IntPreference(
        "security.tools.scan.clair.read.timeout", 600, DOCKER_SECURITY_GROUP, isGreaterThan(0));
    /**
     * Scan schedule cron expression
     */
    public static final StringPreference DOCKER_SECURITY_TOOL_SCAN_SCHEDULE_CRON = new StringPreference(
        "security.tools.scan.schedule.cron", "0 0 0 ? * *", DOCKER_SECURITY_GROUP, PreferenceValidators.isValidCron);
    /**
     * Denies running a Tool, if it hasn't been scanned yet, or all scans has failed
     */
    public static final BooleanPreference DOCKER_SECURITY_TOOL_POLICY_DENY_NOT_SCANNED = new BooleanPreference(
        "security.tools.policy.deny.not.scanned", false, DOCKER_SECURITY_GROUP, pass);
    /**
     * Denies running a Tool, if the number of it's medium vulnerabilities exceeds the threshold.
     * To disable the policy, set to -1
     */
    public static final IntPreference DOCKER_SECURITY_TOOL_POLICY_MAX_MEDIUM_VULNERABILITIES = new IntPreference(
        "security.tools.policy.max.medium.vulnerabilities", 50, DOCKER_SECURITY_GROUP, isGreaterThanOrEquals(0));
    /**
     * Denies running a Tool, if the number of it's critical vulnerabilities exceeds the threshold.
     * To disable the policy, set to -1
     */
    public static final IntPreference DOCKER_SECURITY_TOOL_POLICY_MAX_CRITICAL_VULNERABILITIES =
        new IntPreference("security.tools.policy.max.critical.vulnerabilities", 10, DOCKER_SECURITY_GROUP,
                          isGreaterThanOrEquals(0));
    /**
     * Denies running a Tool, if the number of it's high vulnerabilities exceeds the threshold.
     * To disable the policy, set to -1
     */
    public static final IntPreference DOCKER_SECURITY_TOOL_POLICY_MAX_HIGH_VULNERABILITIES = new IntPreference(
        "security.tools.policy.max.high.vulnerabilities", 20, DOCKER_SECURITY_GROUP, isGreaterThanOrEquals(0));
    public static final ObjectPreference<Set<String>> DOCKER_SECURITY_CUDNN_VERSION_LABEL =
            new ObjectPreference<>("security.tools.nvidia.cudnn.version.label", null,
                    new TypeReference<Set<String>>() {}, DOCKER_SECURITY_GROUP, pass, true);

    // CLUSTER_GROUP
    /**
     * If this property is true, any free node that doesn't match configuration of a pending pod will be scaled down
     * immediately, otherwise it will be left until it will be reused or expired. If most of the time we use nodes with
     * the same configuration set true.
     */

    public static final StringPreference CLUSTER_ALLOWED_INSTANCE_TYPES = new StringPreference(
            "cluster.allowed.instance.types", "m5.*,c5.*,r4.*,t2.*", CLUSTER_GROUP,
            isNotBlank);
    public static final StringPreference CLUSTER_ALLOWED_PRICE_TYPES = new StringPreference(
            "cluster.allowed.price.types", String.format("%s,%s", PriceType.SPOT, PriceType.ON_DEMAND),
            CLUSTER_GROUP, isNotBlank);
    public static final StringPreference CLUSTER_ALLOWED_MASTER_PRICE_TYPES = new StringPreference(
            "cluster.allowed.price.types.master", String.format("%s,%s", PriceType.SPOT, PriceType.ON_DEMAND),
            CLUSTER_GROUP, isNotBlank);
    public static final StringPreference CLUSTER_INSTANCE_TYPE = new StringPreference("cluster.instance.type",
        "m5.large", CLUSTER_GROUP, PreferenceValidators.isClusterInstanceTypeAllowed, CLUSTER_ALLOWED_INSTANCE_TYPES);

    public static final BooleanPreference CLUSTER_KILL_NOT_MATCHING_NODES = new BooleanPreference(
        "cluster.kill.not.matching.nodes", true, CLUSTER_GROUP, pass);
    public static final BooleanPreference CLUSTER_ENABLE_AUTOSCALING = new BooleanPreference(
        "cluster.enable.autoscaling", true, CLUSTER_GROUP, pass);
    public static final IntPreference CLUSTER_NODE_UNAVAILABLE_GRACE_PERIOD_MINUTES = new IntPreference(
        "cluster.node.unavailable.grace.period.minutes", 30, CLUSTER_GROUP, isGreaterThanOrEquals(0));

    public static final DoublePreference CLUSTER_NODE_KUBE_MEM_RATIO = new DoublePreference(
        "cluster.node.kube.mem.ratio", 0.025, CLUSTER_GROUP, isGreaterThan(0.0f).and(isLessThan(1.0f)));
    public static final IntPreference CLUSTER_NODE_KUBE_MEM_MIN_MIB = new IntPreference(
        "cluster.node.kube.mem.min.mib", 256, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_NODE_KUBE_MEM_MAX_MIB = new IntPreference(
        "cluster.node.kube.mem.max.mib", 1024, CLUSTER_GROUP, isGreaterThan(0));
    public static final DoublePreference CLUSTER_NODE_SYSTEM_MEM_RATIO = new DoublePreference(
        "cluster.node.system.mem.ratio", 0.025, CLUSTER_GROUP, isGreaterThan(0.0f).and(isLessThan(1.0f)));
    public static final IntPreference CLUSTER_NODE_SYSTEM_MEM_MIN_MIB = new IntPreference(
        "cluster.node.system.mem.min.mib", 256, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_NODE_SYSTEM_MEM_MAX_MIB = new IntPreference(
        "cluster.node.system.mem.max.mib", 1024, CLUSTER_GROUP, isGreaterThan(0));
    public static final DoublePreference CLUSTER_NODE_EXTRA_MEM_RATIO = new DoublePreference(
        "cluster.node.extra.mem.ratio", 0.04, CLUSTER_GROUP, isGreaterThan(0.0f).and(isLessThan(1.0f)));
    public static final IntPreference CLUSTER_NODE_EXTRA_MEM_MIN_MIB = new IntPreference(
        "cluster.node.extra.mem.min.mib", 512, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_NODE_EXTRA_MEM_MAX_MIB = new IntPreference(
        "cluster.node.extra.mem.max.mib", Integer.MAX_VALUE, CLUSTER_GROUP, isGreaterThan(0));

    public static final IntPreference CLUSTER_AUTOSCALE_RATE = new IntPreference("cluster.autoscale.rate",
                                                    40000, CLUSTER_GROUP, isGreaterThan(1000));
    public static final IntPreference CLUSTER_MAX_SIZE = new IntPreference("cluster.max.size", 50,
                                                                           CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_MIN_SIZE = new IntPreference("cluster.min.size", 0,
                                                                           CLUSTER_GROUP, isGreaterThanOrEquals(0));
    public static final IntPreference CLUSTER_NODEUP_MAX_THREADS = new IntPreference("cluster.nodeup.max.threads",
                                                                                10, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_NODEUP_RETRY_COUNT = new IntPreference("cluster.nodeup.retry.count",
                                                                                5, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_NODEUP_WAIT_SEC = new IntPreference("cluster.nodeup.wait.sec",
                                                                                900, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_SPOT_MAX_ATTEMPTS = new IntPreference("cluster.spot.max.attempts", 2,
                                                                                    CLUSTER_GROUP, isGreaterThan(0));
    public static final StringPreference CLOUD_DEFAULT_PROVIDER = new StringPreference(
            "cloud.provider.default", CloudProvider.AWS.name(), CLUSTER_GROUP, pass);
    /**
     * If this property is true, pipeline scheduler will rely on Kubernetes order of pods, otherwise
     * pipelines will be ordered according to their parent (batch) ID
     */
    public static final BooleanPreference CLUSTER_RANDOM_SCHEDULING = new BooleanPreference("cluster.random.scheduling",
                                                                                           false, CLUSTER_GROUP, pass);
    public static final IntPreference CLUSTER_INSTANCE_HDD = new IntPreference("cluster.instance.hdd", 10,
                                                                               CLUSTER_GROUP, isGreaterThan(0));
    public static final BooleanPreference CLUSTER_INSTANCE_HDD_SCALE_ENABLED = new BooleanPreference(
            "cluster.instance.hdd.scale.enabled", false, CLUSTER_GROUP, pass);
    public static final IntPreference CLUSTER_INSTANCE_HDD_SCALE_MONITORING_DELAY = new IntPreference(
            "cluster.instance.hdd.scale.monitoring.delay", 10, CLUSTER_GROUP, isGreaterThan(0));
    public static final DoublePreference CLUSTER_INSTANCE_HDD_SCALE_THRESHOLD_RATIO = new DoublePreference(
            "cluster.instance.hdd.scale.threshold.ratio", 0.75, CLUSTER_GROUP,
            isGreaterThan(0.0f).and(isLessThan(1.0f)));
    public static final DoublePreference CLUSTER_INSTANCE_HDD_SCALE_DELTA_RATIO = new DoublePreference(
            "cluster.instance.hdd.scale.delta.ratio", 0.5, CLUSTER_GROUP, isGreaterThan(0.0f));
    public static final IntPreference CLUSTER_INSTANCE_HDD_SCALE_MAX_DEVICES = new IntPreference(
            "cluster.instance.hdd.scale.max.devices", 40, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_INSTANCE_HDD_SCALE_MAX_SIZE = new IntPreference(
            "cluster.instance.hdd.scale.max.size", 16384, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_INSTANCE_HDD_SCALE_DISK_MIN_SIZE = new IntPreference(
            "cluster.instance.hdd.scale.disk.min.size", 10, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_INSTANCE_HDD_SCALE_DISK_MAX_SIZE = new IntPreference(
            "cluster.instance.hdd.scale.disk.max.size", 16384, CLUSTER_GROUP, isGreaterThan(0));
    public static final StringPreference CLUSTER_INSTANCE_DEVICE_PREFIX = new StringPreference(
            "cluster.instance.device.prefix", "/dev/sd", CLUSTER_GROUP, isNotBlank);
    public static final StringPreference CLUSTER_INSTANCE_DEVICE_SUFFIXES = new StringPreference(
            "cluster.instance.device.suffixes", "defghijklmnopqrstuvwxyz", CLUSTER_GROUP,
            isNotBlank);
    public static final ObjectPreference<Map<String, Integer>> CLUSTER_INSTANCE_GPU_CORES_MAPPING =
            new ObjectPreference<>("cluster.instance.gpu.cores.mapping", CommonUtils.toMap(
                                   Pair.of("NVIDIA M40", 3072),
                                   Pair.of("NVIDIA M60", 2048),
                                   Pair.of("NVIDIA P4", 2560),
                                   Pair.of("NVIDIA P40", 3840),
                                   Pair.of("NVIDIA P100", 3584),
                                   Pair.of("NVIDIA V100", 5120),
                                   Pair.of("NVIDIA T4", 2560),
                                   Pair.of("NVIDIA T4G", 2560),
                                   Pair.of("NVIDIA A100", 6912),
                                   Pair.of("NVIDIA A10G", 9216),
                                   Pair.of("NVIDIA H100", 18432),
                                   Pair.of("NVIDIA K80", 4992),
                                   Pair.of("NVIDIA K520", 3072),
                                   Pair.of("NVIDIA L4", 7424),
                                   Pair.of("NVIDIA A100-80GB", 6912)),
                                   new TypeReference<Map<String, Integer>>() {}, CLUSTER_GROUP,
                                   isNullOrValidJson(new TypeReference<Map<String, Integer>>() {}), true);

    public static final ObjectPreference<CloudRegionsConfiguration> CLUSTER_NETWORKS_CONFIG =
        new ObjectPreference<>("cluster.networks.config", null, new TypeReference<CloudRegionsConfiguration>() {},
                               CLUSTER_GROUP, isNullOrValidJson(new TypeReference<CloudRegionsConfiguration>() {}));
    public static final IntPreference CLUSTER_REASSIGN_DISK_DELTA = new IntPreference("cluster.reassign.disk.delta",
            100, CLUSTER_GROUP, isGreaterThanOrEquals(0));
    public static final IntPreference CLUSTER_LOST_RUN_ATTEMPTS = new IntPreference("cluster.lost.run.attempts",
            5, CLUSTER_GROUP, isGreaterThan(0));
    public static final StringPreference CLUSTER_AWS_EBS_TYPE = new StringPreference(
            "cluster.aws.ebs.type", "gp3", CLUSTER_GROUP, isNotBlank);
    public static final StringPreference CLUSTER_AWS_EC2_PRICING_URL_TEMPLATE = new StringPreference(
            "cluster.aws.ec2.pricing.url.template",
            "https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/%s/index.csv",
            CLUSTER_GROUP, isNotBlank);

    /**
     * If this property is true, pipelines without parent (batch ID) will have the highest priority,
     * otherwise - the lowest
     */
    public static final BooleanPreference CLUSTER_HIGH_NON_BATCH_PRIORITY = new BooleanPreference(
        "cluster.high.non.batch.priority", false, CLUSTER_GROUP, pass);
    public static final IntPreference CLUSTER_KEEP_ALIVE_MINUTES = new IntPreference("cluster.keep.alive.minutes",
                                                                                10, CLUSTER_GROUP, isGreaterThan(0));
    public static final StringPreference CLUSTER_KEEP_ALIVE_POLICY = new StringPreference("cluster.keep.alive.policy",
            ClusterKeepAlivePolicy.MINUTES_TILL_HOUR.name(), CLUSTER_GROUP, isValidEnum(ClusterKeepAlivePolicy.class));
    public static final BooleanPreference CLUSTER_SPOT = new BooleanPreference("cluster.spot", true, CLUSTER_GROUP,
                                                                               pass);
    public static final StringPreference CLUSTER_SPOT_ALLOC_STRATEGY = new StringPreference(
        "cluster.spot.alloc.strategy", "on_demand", CLUSTER_GROUP, PreferenceValidators.isValidSpotAllocStrategy);
    public static final DoublePreference CLUSTER_SPOT_BID_PRICE = new DoublePreference("cluster.spot.bid.price", null,
                                                                                 CLUSTER_GROUP, isGreaterThan(0.0f));
    public static final StringPreference CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER = new StringPreference(
        "cluster.allowed.instance.types.docker", "m5.*,c5.*,r4.*,t2.*", CLUSTER_GROUP, pass);

    public static final IntPreference CLUSTER_INSTANCE_OFFER_UPDATE_RATE = new IntPreference(
        "instance.offer.update.rate", 3600000, CLUSTER_GROUP, isGreaterThan(10000));
    public static final IntPreference CLUSTER_INSTANCE_OFFER_EXPIRATION_RATE_HOURS = new IntPreference(
        "instance.offer.expiration.rate.hours", 72, CLUSTER_GROUP, isGreaterThan(0));
    public static final BooleanPreference CLUSTER_INSTANCE_OFFER_FETCH_GPU = new BooleanPreference(
        "instance.offer.fetch.gpu", true, CLUSTER_GROUP, pass);
    public static final BooleanPreference CLUSTER_INSTANCE_OFFER_FILTER_UNIQUE = new BooleanPreference(
        "instance.offer.filter.unique", true, CLUSTER_GROUP, pass);
    public static final StringPreference CLUSTER_INSTANCE_OFFER_FILTER_TERM_TYPES = new StringPreference(
        "instance.offer.filter.term.types",
        Arrays.stream(CloudInstancePriceService.TermType.values())
                .map(CloudInstancePriceService.TermType::getName)
                .collect(Collectors.joining(",")),
        CLUSTER_GROUP, pass);
    public static final IntPreference CLUSTER_INSTANCE_OFFER_FILTER_CPU_MIN = new IntPreference(
        "instance.offer.filter.cpu.min", 2, CLUSTER_GROUP, pass);
    public static final IntPreference CLUSTER_INSTANCE_OFFER_FILTER_MEM_MIN = new IntPreference(
        "instance.offer.filter.mem.min", 3, CLUSTER_GROUP, pass);
    public static final IntPreference CLUSTER_INSTANCE_OFFER_INSERT_BATCH_SIZE = new IntPreference(
        "instance.offer.insert.batch.size", 10_000, CLUSTER_GROUP, isGreaterThan(0));

    public static final IntPreference CLUSTER_BATCH_RETRY_COUNT = new IntPreference("cluster.batch.retry.count",
            0, CLUSTER_GROUP, isGreaterThanOrEquals(0));
    public static final ObjectPreference<List<String>> INSTANCE_RESTART_STATE_REASONS = new ObjectPreference<>(
            "instance.restart.state.reasons", null, new TypeReference<List<String>>() {}, CLUSTER_GROUP,
            isNullOrValidJson(new TypeReference<List<String>>() {}));
    public static final ObjectPreference<List<String>> INSTANCE_LIMIT_STATE_REASONS = new ObjectPreference<>(
            "instance.limit.state.reasons", null, new TypeReference<List<String>>() {}, CLUSTER_GROUP,
            isNullOrValidJson(new TypeReference<List<String>>() {}));
    public static final IntPreference CLUSTER_INSTANCE_HDD_EXTRA_MULTI =
            new IntPreference("cluster.instance.hdd_extra_multi", 3, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_DOCKER_EXTRA_MULTI =
            new IntPreference("cluster.docker.extra_multi", 3, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_MONITORING_ELASTIC_INTERVALS_NUMBER = new IntPreference(
            "cluster.monitoring.elastic.intervals.number", 10, CLUSTER_GROUP, isGreaterThan(0));
    public static final LongPreference CLUSTER_MONITORING_ELASTIC_MINIMAL_INTERVAL = new LongPreference(
            "cluster.monitoring.elastic.minimal.interval", TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES),
            CLUSTER_GROUP, isGreaterThan(0L));
    public static final IntPreference CLUSTER_KUBE_MASTER_PORT =
            new IntPreference("cluster.kube.master.port", 6443, CLUSTER_GROUP, isGreaterThan(0));
    public static final IntPreference CLUSTER_KUBE_WINDOWS_SERVICE_PORT =
            new IntPreference("cluster.kube.windows.service.port", 22, CLUSTER_GROUP, isGreaterThan(0));
    public static final BooleanPreference CLUSTER_WINDOWS_NODE_LOOPBACK_ROUTE =
            new BooleanPreference("cluster.windows.node.loopback.route", false, CLUSTER_GROUP, pass);
    public static final ObjectPreference<Set<String>> INSTANCE_COMPUTE_FAMILY_NAMES = new ObjectPreference<>(
            "instance.compute.family.names", null, new TypeReference<Set<String>>() {}, CLUSTER_GROUP,
            isNullOrValidJson(new TypeReference<Set<String>>() {}));

    /**
     * Configures global dns hosted zone id.
     * Generally should not be used in favor of region specific dns hosted zone id.
     */
    public static final StringPreference INSTANCE_DNS_HOSTED_ZONE_ID = new StringPreference(
            "instance.dns.hosted.zone.id", null, CLUSTER_GROUP, pass);

    /**
     * Configures global dns hosted zone base.
     * Generally should not be used in favor of region specific dns hosted zone base.
     */
    public static final StringPreference INSTANCE_DNS_HOSTED_ZONE_BASE = new StringPreference(
            "instance.dns.hosted.zone.base", null, CLUSTER_GROUP, pass);

    public static final StringPreference DEFAULT_EDGE_REGION = new StringPreference(
            "default.edge.region", "eu-central", CLUSTER_GROUP, pass);
    public static final IntPreference DEFAULT_EDGE_REGION_ID = new IntPreference(
            "default.edge.region.id", 1, CLUSTER_GROUP, isGreaterThan(0L), true);

    public static final ObjectPreference<Map<String, RuntimeParameter>> CLUSTER_RUN_PARAMETERS_MAPPING =
            new ObjectPreference<>("cluster.run.parameters.mapping", null,
                    new TypeReference<Map<String, RuntimeParameter>>() {}, CLUSTER_GROUP,
                    isNullOrValidJson(new TypeReference<Map<String, RuntimeParameter>>() {}));

    //LAUNCH_GROUP
    public static final StringPreference LAUNCH_CMD_TEMPLATE = new StringPreference("launch.cmd.template",
                                                            "sleep infinity", LAUNCH_GROUP, pass);
    public static final ObjectPreference<List<OSSpecificLaunchCommandTemplate>> LAUNCH_POD_CMD_TEMPLATE_LINUX =
            new ObjectPreference<>(
                "launch.pod.cmd.template.linux",
                Collections.singletonList(
                    OSSpecificLaunchCommandTemplate.builder()
                        .os("*")
                        .command("set -o pipefail; "
                            + "command -v wget >/dev/null 2>&1 && " +
                                "{ LAUNCH_CMD=\"wget --no-check-certificate -q -O - '$linuxLaunchScriptUrl'\"; }; "
                            + "command -v curl >/dev/null 2>&1 && " +
                                "{ LAUNCH_CMD=\"curl -s -k '$linuxLaunchScriptUrl'\"; }; "
                            + "eval $LAUNCH_CMD " +
                                "| bash /dev/stdin \"$gitCloneUrl\" '$gitRevisionName' '$pipelineCommand'"
                        ).build()
                ),
                new TypeReference<List<OSSpecificLaunchCommandTemplate>>() {},
                LAUNCH_GROUP, isValidMapOfLaunchCommands);
    public static final StringPreference LAUNCH_POD_CMD_TEMPLATE_WINDOWS = new StringPreference(
            "launch.pod.cmd.template.windows", "Add-Type @\"\n" +
            "using System.Net;\n" +
            "using System.Security.Cryptography.X509Certificates;\n" +
            "public class TrustAllCertsPolicy : ICertificatePolicy {\n" +
            "    public bool CheckValidationResult(\n" +
            "        ServicePoint srvPoint, X509Certificate certificate,\n" +
            "        WebRequest request, int certificateProblem) {\n" +
            "            return true;\n" +
            "        }\n" +
            " }\n" +
            "\"@\n" +
            "[System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy\n" +
            "Invoke-WebRequest %s -Outfile .\\launch.py\n" +
            "@\"\n" +
            "%s\n" +
            "\"@ | Out-File -FilePath .\\task.ps1 -Encoding ascii -Force\n" +
            "$env:CP_TASK_PATH = Join-Path $(pwd) \"task.ps1\"\n" +
            "python .\\launch.py",
            LAUNCH_GROUP, isNotBlank);
    public static final IntPreference LAUNCH_JWT_TOKEN_EXPIRATION = new IntPreference(
        "launch.jwt.token.expiration", 2592000, LAUNCH_GROUP, isGreaterThan(0));
    public static final ObjectPreference<EnvVarsSettings> LAUNCH_ENV_PROPERTIES = new ObjectPreference<>(
        "launch.env.properties", null, new TypeReference<EnvVarsSettings>() {}, LAUNCH_GROUP,
        isNullOrValidJson(new TypeReference<EnvVarsSettings>() {}));
    public static final ObjectPreference<List<DockerMount>> DOCKER_IN_DOCKER_MOUNTS = new ObjectPreference<>(
            "launch.dind.mounts", null, new TypeReference<List<DockerMount>>() {},
            LAUNCH_GROUP, isNullOrValidJson(new TypeReference<List<DockerMount>>() {}));

    public static final ObjectPreference<List<DockerMount>> LAUNCH_COMMON_MOUNTS = new ObjectPreference<>(
            "launch.common.mounts", null, new TypeReference<List<DockerMount>>() {},
            LAUNCH_GROUP, isNullOrValidJson(new TypeReference<List<DockerMount>>() {}));

    public static final BooleanPreference LAUNCH_RUN_RESCHEDULE_ENABLED = new BooleanPreference(
            "launch.run.reschedule.enabled", true, LAUNCH_GROUP, pass);

    /**
     * Specifies a comma-separated list of environment variables that should be inherited by DIND containers
     * from run container.
     */
    public static final StringPreference DOCKER_IN_DOCKER_CONTAINER_VARS = new StringPreference(
            "launch.dind.container.vars", "http_proxy,https_proxy,no_proxy,API,API_TOKEN",
            LAUNCH_GROUP, pass);
    public static final StringPreference RUN_VISIBILITY_POLICY = new StringPreference("launch.run.visibility",
            RunVisibilityPolicy.INHERIT.name(), LAUNCH_GROUP, isValidEnum(RunVisibilityPolicy.class));
    public static final IntPreference LAUNCH_CONTAINER_CPU_RESOURCE = new IntPreference(
            "launch.container.cpu.resource", 1, LAUNCH_GROUP, isGreaterThan(-1));
    public static final StringPreference LAUNCH_CONTAINER_MEMORY_RESOURCE_POLICY = new StringPreference(
            "launch.container.memory.resource.policy", ContainerMemoryResourcePolicy.DEFAULT.name(),
            LAUNCH_GROUP, isValidEnum(ContainerMemoryResourcePolicy.class));
    public static final IntPreference LAUNCH_CONTAINER_MEMORY_RESOURCE_REQUEST = new IntPreference(
            "launch.container.memory.resource.request", 1, LAUNCH_GROUP, isGreaterThan(0));
    public static final IntPreference LAUNCH_SERVERLESS_WAIT_COUNT = new IntPreference(
            "launch.serverless.wait.count", 20, LAUNCH_GROUP, isGreaterThan(0));
    public static final IntPreference LAUNCH_SERVERLESS_STOP_TIMEOUT = new IntPreference(
            "launch.serverless.stop.timeout", 60, LAUNCH_GROUP, isGreaterThan(0));
    public static final IntPreference LAUNCH_SERVERLESS_ENDPOINT_WAIT_COUNT = new IntPreference(
            "launch.serverless.endpoint.wait.count", 40, LAUNCH_GROUP, isGreaterThan(0));
    public static final IntPreference LAUNCH_SERVERLESS_ENDPOINT_WAIT_TIME = new IntPreference(
            "launch.serverless.endpoint.wait.time", 20000, LAUNCH_GROUP, isGreaterThan(0));
    public static final StringPreference LAUNCH_ORIGINAL_OWNER_PARAMETER = new StringPreference(
            "launch.original.owner.parameter", "ORIGINAL_OWNER", LAUNCH_GROUP, isNotBlank);
    public static final StringPreference KUBE_SERVICE_SUFFIX = new StringPreference("launch.kube.service.suffix",
            "svc.cluster.local", LAUNCH_GROUP, pass);
    public static final ObjectPreference<Map<String, LaunchCapability>> LAUNCH_CAPABILITIES = new ObjectPreference<>(
            "launch.capabilities", null, new TypeReference<Map<String, LaunchCapability>>() {},
            LAUNCH_GROUP, isNullOrValidJson(new TypeReference<Map<String, LaunchCapability>>() {}));

    public static final BooleanPreference KUBE_POD_DOMAINS_ENABLED = new BooleanPreference(
            "launch.kube.pod.domains.enabled", true, LAUNCH_GROUP, pass);
    public static final StringPreference KUBE_POD_SERVICE = new StringPreference("launch.kube.pod.service",
            "pods", LAUNCH_GROUP, pass);
    public static final StringPreference KUBE_POD_SUBDOMAIN = new StringPreference("launch.kube.pod.subdomain",
            "pods", LAUNCH_GROUP, pass);
    public static final StringPreference KUBE_POD_SEARCH_PATH = new StringPreference("launch.kube.pod.search.path",
            "pods.default.svc.cluster.local", LAUNCH_GROUP, pass);
    public static final LongPreference KUBE_POD_GRACE_PERIOD_SECONDS = new LongPreference(
            "launch.kube.pod.grace.period.seconds", 30L, LAUNCH_GROUP, pass, false);
    public static final IntPreference  LAUNCH_UID_SEED = new IntPreference("launch.uid.seed", 70000,
            LAUNCH_GROUP, pass, true);

    public static final ObjectPreference<Map<String, Object>> LAUNCH_PRE_COMMON_COMMANDS = new ObjectPreference<>(
            "launch.pre.common.commands", null, new TypeReference<Map<String, Object>>() {},
            LAUNCH_GROUP, isNullOrValidJson(new TypeReference<Map<String, Object>>() {}));

    public static final ObjectPreference<List<Map<String, Object>>> LAUNCH_DISK_THRESHOLDS = new ObjectPreference<>(
            "launch.job.disk.size.thresholds", null, new TypeReference<List<Map<String, Object>>>() {},
            LAUNCH_GROUP, isNullOrValidJson(new TypeReference<List<Map<String, Object>>>() {}), true);

    //DTS submission
    public static final StringPreference DTS_LAUNCH_CMD_TEMPLATE = new StringPreference("dts.launch.cmd",
            "sleep infinity", DTS_GROUP, pass);
    public static final StringPreference DTS_LAUNCH_URL = new StringPreference("dts.launch.script.url",
            "", DTS_GROUP, pass);
    public static final StringPreference DTS_DISTRIBUTION_URL = new StringPreference("dts.dist.url",
            "", DTS_GROUP, pass);
    public static final IntPreference DTS_MONITORING_PERIOD_SECONDS = new IntPreference("dts.monitoring.period.seconds",
            (int) TimeUnit.MINUTES.toSeconds(1), DTS_GROUP, isGreaterThan(10));
    public static final IntPreference DTS_OFFLINE_TIMEOUT_SECONDS = new IntPreference("dts.offline.timeout.seconds",
            (int) TimeUnit.MINUTES.toSeconds(5), DTS_GROUP, isGreaterThan(0));

    /**
     * Controls maximum number of scheduled at once runs
     */
    public static final IntPreference LAUNCH_MAX_SCHEDULED_NUMBER = new IntPreference(
        "launch.max.scheduled.number", 10, LAUNCH_GROUP, isGreaterThan(0));
    public static final ObjectPreference<List<DefaultSystemParameter>> LAUNCH_SYSTEM_PARAMETERS =
        new ObjectPreference<>("launch.system.parameters", null,
                               new TypeReference<List<DefaultSystemParameter>>() {},
                               LAUNCH_GROUP, isNullOrValidJson(new TypeReference<List<DefaultSystemParameter>>() {}));

    /**
     * Controls maximum number of active runs for specific user/group
     */
    public static final ObjectPreference<Integer> LAUNCH_MAX_RUNS_USER_GLOBAL_LIMIT = new ObjectPreference<>(
        "launch.max.runs.user.global", null, new TypeReference<Integer>() {},
        LAUNCH_GROUP, isNullOrGreaterThan(0));

    /**
     * Sets task status update rate, on which application will query Kubernetes cluster for running task status,
     * milliseconds
     */
    public static final IntPreference LAUNCH_TASK_STATUS_UPDATE_RATE = new IntPreference(
        "launch.task.status.update.rate", 30000, LAUNCH_GROUP, isGreaterThan(5000));
    public static final StringPreference LAUNCH_DOCKER_IMAGE = new StringPreference("launch.docker.image", null,
                                                                                    LAUNCH_GROUP, null);
    /**
     * Sets unused pods release rate, on which application will kill Kubernetes pods, which were used by finished
     * pipeline runs, milliseconds. This rate should be less, than
     * @see #LAUNCH_TASK_STATUS_UPDATE_RATE
     * to kill unused pods as soon as possible
     */
    public static final IntPreference RELEASE_UNUSED_NODES_RATE = new IntPreference(
        "launch.pods.release.rate", 3000, LAUNCH_GROUP, isLessThan(LAUNCH_TASK_STATUS_UPDATE_RATE.getDefaultValue()));
    public static final LongPreference LAUNCH_JWT_TOKEN_EXPIRATION_REFRESH_THRESHOLD = new LongPreference(
            "launch.jwt.token.expiration.refresh.threshold", 172800L, LAUNCH_GROUP, isGreaterThan(0L));
    public static final StringPreference LAUNCH_INSUFFICIENT_CAPACITY_MESSAGE = new StringPreference(
            "launch.insufficient.capacity.message", "Insufficient instance capacity.",
            LAUNCH_GROUP, pass);

    // UI_GROUP
    public static final StringPreference UI_PROJECT_INDICATOR = new StringPreference("ui.project.indicator",
                                                                                     "type=project", UI_GROUP, pass);
    public static final StringPreference UI_NGS_PROJECT_INDICATOR =
            new StringPreference("ui.ngs.project.indicator", "type=project,project-type=ngs", UI_GROUP, pass);
    public static final ObjectPreference<Map<String, String>> UI_CLI_CONFIGURE_TEMPLATE = new ObjectPreference<>(
        "ui.pipe.cli.configure.template", null, new TypeReference<Map<String, String>>() {}, UI_GROUP,
            isNullOrValidJson(new TypeReference<Map<String, String>>() {}));
    public static final ObjectPreference<Map<String, String>> UI_CLI_INSTALL_TEMPLATE = new ObjectPreference<>(
        "ui.pipe.cli.install.template", null, new TypeReference<Map<String, String>>() {}, UI_GROUP,
        isNullOrValidJson(new TypeReference<Map<String, String>>() {}));
    public static final ObjectPreference<List<ControlEntry>> UI_CONTROLS_SETTINGS = new ObjectPreference<>(
        "ui.controls.settings", null, new TypeReference<List<ControlEntry>>() {}, UI_GROUP,
        isNullOrValidJson(new TypeReference<List<ControlEntry>>() {}));
    public static final StringPreference UI_DEPLOYMENT_NAME = new StringPreference("ui.pipeline.deployment.name",
            "Cloud Pipeline", UI_GROUP, isNotBlank);
    public static final ObjectPreference<Map<String, String>> UI_PIPE_DRIVE_MAPPING = new ObjectPreference<>(
            "ui.pipe.drive.mapping", null, new TypeReference<Map<String, String>>() {}, UI_GROUP,
            isNullOrValidJson(new TypeReference<Map<String, String>>() {}));
    public static final StringPreference UI_SUPPORT_TEMPLATE = new StringPreference("ui.support.template",
            "", UI_GROUP, pass);
    public static final BooleanPreference UI_LIBRARY_DRAG = new BooleanPreference("ui.library.drag",
            true, UI_GROUP, pass);
    public static final StringPreference UI_LAUNCH_TEMPLATE = new StringPreference("ui.launch.command.template",
            "", UI_GROUP, pass);
    public static final ObjectPreference<Map<String, String>> UI_PIPE_FILE_BROWSER_APP = new ObjectPreference<>(
            "ui.pipe.file.browser.app", null, new TypeReference<Map<String, String>>() {}, UI_GROUP,
            isNullOrValidJson(new TypeReference<Map<String, String>>() {}));
    public static final ObjectPreference<Map<String, String>> UI_PIPE_FILE_BROWSER_REQUEST = new ObjectPreference<>(
            "ui.pipe.file.browser.request", null, new TypeReference<Map<String, String>>() {}, UI_GROUP,
            isNullOrValidJson(new TypeReference<Map<String, String>>() {}));
    public static final ObjectPreference<Map<String, Object>> UI_HIDDEN_OBJECTS = new ObjectPreference<>(
            "ui.hidden.objects", null, new TypeReference<Map<String, Object>>() {}, UI_GROUP,
            isNullOrValidJson(new TypeReference<Map<String, Object>>() {}));
    public static final DoublePreference UI_WSI_NOTIFICATION_FACTOR = new DoublePreference(
            "ui.wsi.magnification.factor", 1.0, UI_GROUP, isGreaterThan(0));
    public static final BooleanPreference UI_LIBRARY_INLINE_METADATA = new BooleanPreference(
            "ui.library.metadata.inline", false, UI_GROUP, pass);
    public static final ObjectPreference<List<String>> UI_KUBE_LABELS = new ObjectPreference<>(
            "ui.tool.kube.labels", null, new TypeReference<List<String>>() {}, UI_GROUP,
            isNullOrValidJson(new TypeReference<List<String>>() {}), true);
    public static final StringPreference UI_MY_COSTS_DISCLAIMER = new StringPreference("ui.my.costs.disclaimer",
            "", UI_GROUP, pass);
    public static final StringPreference UI_STORAGE_STATIC_PREVIEW_MASK =
            new StringPreference("ui.storage.static.preview.mask", "html,htm", UI_GROUP, pass, true);
    public static final ObjectPreference<Map<String, Object>> UI_MAINTENANCE_PIPELINE_ENABLED = new ObjectPreference<>(
            "ui.run.maintenance.pipeline.enabled", null, new TypeReference<Map<String, Object>>() {},
            UI_GROUP, isNullOrValidJson(new TypeReference<Map<String, Object>>() {}), true);
    public static final ObjectPreference<Map<String, Object>> UI_MAINTENANCE_TOOL_ENABLED = new ObjectPreference<>(
            "ui.run.maintenance.tool.enabled", null, new TypeReference<Map<String, Object>>() {},
            UI_GROUP, isNullOrValidJson(new TypeReference<Map<String, Object>>() {}), true);
    public static final StringPreference UI_STORAGE_DOWNLOAD_ATTRIBUTE = new StringPreference(
            "ui.storage.download.attribute", "download_allowed_roles", UI_GROUP, pass, true);
    public static final ObjectPreference<Map<String, Object>> UI_RUNS_COUNT_FILTER = new ObjectPreference<>(
            "ui.runs.counter.filter", null, new TypeReference<Map<String, Object>>() {},
            UI_GROUP, isNullOrValidJson(new TypeReference<Map<String, Object>>() {}), true);
    public static final ObjectPreference<List<Object>> UI_RUNS_FILTERS = new ObjectPreference<>(
            "ui.runs.filters", Collections.emptyList(), new TypeReference<List<Object>>() {},
            UI_GROUP, isNullOrValidJson(new TypeReference<List<Object>>() {}), true);
    public static final ObjectPreference<Object> UI_RUNS_OWNERS_FILTERS = new ObjectPreference<>(
            "ui.runs.owners.filter", Collections.emptyList(), new TypeReference<Object>() {},
            UI_GROUP, isNullOrValidJson(new TypeReference<Object>() {}), true);
    public static final ObjectPreference<Map<String, Object>> UI_TOOLS_FILTERS = new ObjectPreference<>(
            "ui.tools.filters", Collections.emptyMap(), new TypeReference<Map<String, Object>>() {},
            UI_GROUP, isNullOrValidJson(new TypeReference<Map<String, Object>>() {}), true);
    public static final BooleanPreference UI_RUNS_CLUSTER_DETAILS_SHOW_ACTIVE_ONLY = new BooleanPreference(
            "ui.runs.cluster.details.show.active.only", true, UI_GROUP, pass);
    public static final BooleanPreference UI_PERSONAL_TOOL_WARNING_ENABLED = new BooleanPreference(
            "ui.personal.tools.launch.warning.enabled", false, UI_GROUP, pass, true);
    public static final ObjectPreference<List<Object>> UI_PERSONAL_TOOL_RESTRICTIONS = new ObjectPreference<>(
            "ui.personal.tools.permissions.restrictions",
            Collections.emptyList(), new TypeReference<List<Object>>() {},
            UI_GROUP, isNullOrValidJson(new TypeReference<List<Object>>() {}), true);
    public static final ObjectPreference<Object> UI_SEARCH_COLUMNS_ORDER = new ObjectPreference<>(
            "ui.search.columns.order",
            Collections.emptyList(), new TypeReference<Object>() {},
            UI_GROUP, isNullOrValidJson(new TypeReference<Object>() {}), true);
    public static final IntPreference UI_UPLOAD_CHUNK_COUNT = new IntPreference("ui.upload.chunk.count",
            null, UI_GROUP, isNullOrGreaterThan(0));
    public static final IntPreference UI_UPLOAD_CHUNK_SIZE = new IntPreference("ui.upload.chunk.size.mb", 
            null, UI_GROUP, isNullOrGreaterThan(0));

    // Facet Filters
    public static final ObjectPreference<Map<String, Object>> FACETED_FILTER_DICT = new ObjectPreference<>(
            "faceted.filter.dictionaries", null, new TypeReference<Map<String, Object>>() {},
            FACETED_FILTER_GROUP, isNullOrValidJson(new TypeReference<Map<String, Object>>() {}));

    public static final StringPreference FACETED_FILTER_DISPLAY_NAME_TAG = new StringPreference(
            "faceted.filter.display.name.tag", null, FACETED_FILTER_GROUP, pass, true);
    public static final StringPreference FACETED_FILTER_STORAGE_DISPLAY_NAME_TAG = new StringPreference(
            "faceted.filter.storage.display.file.name.tag", null, FACETED_FILTER_GROUP, pass, true);
    public static final ObjectPreference<Map<String, Object>> FACETED_FILTER_DOWNLOAD = new ObjectPreference<>(
            "faceted.filter.download", null, new TypeReference<Map<String, Object>>() {},
            FACETED_FILTER_GROUP, isNullOrValidJson(new TypeReference<Map<String, Object>>() {}), true);

    public static final StringPreference FACETED_FILTER_DOWNLOAD_FILE_TAG = new StringPreference(
            "faceted.filter.download.file.tag", "download_url", FACETED_FILTER_GROUP, pass, true);


    // BASE_URLS_GROUP
    public static final StringPreference BASE_API_HOST = new StringPreference("base.api.host", null,
                                                                  BASE_URLS_GROUP, PreferenceValidators.isValidUrl);
    public static final StringPreference BASE_API_HOST_EXTERNAL = new StringPreference("base.api.host.external", null,
                                                              BASE_URLS_GROUP, PreferenceValidators.isValidUrlSyntax);
    public static final StringPreference BASE_PIPE_DISTR_URL = new StringPreference("base.pipe.distributions.url",
                                                        null, BASE_URLS_GROUP, PreferenceValidators.isValidUrl);
    public static final StringPreference BASE_DAV_AUTH_URL = new StringPreference("base.dav.auth.url",
            null, BASE_URLS_GROUP, pass);
    public static final StringPreference BASE_EDGE_INVALIDATE_AUTH_PATH =
            new StringPreference("base.invalidate.edge.auth.path", "/invalidate", BASE_URLS_GROUP, pass);
    public static final ObjectPreference<Map<String, String>> CLOUD_DATA_DISTRIBUTION_URL = new ObjectPreference<>(
            "base.cloud.data.distribution.url", null, new TypeReference<Map<String, String>>() {},
            BASE_URLS_GROUP, isNullOrValidJson(new TypeReference<Map<String, String>>() {}), true);
    public static final StringPreference BASE_GLOBAL_DISTRIBUTION_URL = new StringPreference(
            "base.global.distribution.url", "https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/",
            BASE_URLS_GROUP, isNotBlank, true);

    //Data sharing
    public static final StringPreference BASE_API_SHARED = new StringPreference("data.sharing.base.api", null,
            DATA_SHARING_GROUP, PreferenceValidators.isEmptyOrValidUrlSyntax);
    public static final StringPreference DATA_SHARING_DISCLAIMER = new StringPreference("data.sharing.disclaimer", null,
            DATA_SHARING_GROUP, pass);
    public static final StringPreference DATA_SHARING_FOLDERS_DIR =
            new StringPreference("data.sharing.storage.folders.directory", null, DATA_SHARING_GROUP, pass);
    public static final ObjectPreference<SharedStoragePermissions> DATA_SHARING_DEFAULT_PERMISSIONS =
            new ObjectPreference<>("data.sharing.storage.folders.default.permissions", null,
                    new TypeReference<SharedStoragePermissions>() {}, DATA_SHARING_GROUP,
                    isNullOrValidJson(new TypeReference<SharedStoragePermissions>() {}));
    public static final ObjectPreference<Map<String, StaticResourceSettings>> DATA_SHARING_STATIC_RESOURCE_SETTINGS =
            new ObjectPreference<>("data.sharing.static.resource.settings", null,
                    new TypeReference<Map<String, StaticResourceSettings>>() {}, DATA_SHARING_GROUP,
                    isNullOrValidJson(new TypeReference<Map<String, StaticResourceSettings>>() {}));
    public static final StringPreference STATIC_RESOURCES_FOLDER_TEMPLATE_PATH =
            new StringPreference("data.sharing.static.resource.template.path", "classpath:views/folder.vm",
                    DATA_SHARING_GROUP, pass);

    // SYSTEM_GROUP
    /**
     * A list of endpoint IDs of external services. Those are services, that need to get Pipeline's JWT token
     */
    public static final ObjectPreference<List<ExternalServiceEndpoint>> SYSTEM_EXTERNAL_SERVICES_ENDPOINTS =
        new ObjectPreference<>("system.external.services.endpoints", null,
                   new TypeReference<List<ExternalServiceEndpoint>>() {}, SYSTEM_GROUP,
                               PreferenceValidators.isValidExternalServices);
    /**
     * Controls the period of resource monitoring task
     */
    public static final IntPreference SYSTEM_RESOURCE_MONITORING_PERIOD = new IntPreference(
        "system.resource.monitoring.period", 60000, SYSTEM_GROUP, isGreaterThan(10000));
    /**
     * Controls the period of schedule monitoring task
     */
    public static final IntPreference SYSTEM_SCHEDULE_MONITORING_PERIOD = new IntPreference(
        "system.schedule.monitoring.period.seconds",
            (int) TimeUnit.HOURS.toSeconds(1), SYSTEM_GROUP, isGreaterThan(10));

    /**
     * Controls the amount of pod logs to be loaded
     */
    public static final IntPreference SYSTEM_LIMIT_LOG_LINES = new IntPreference(
            "system.log.line.limit", 8000, SYSTEM_GROUP, isGreaterThan(0));

    public static final StringPreference SYSTEM_RUN_TAG_DATE_SUFFIX = new StringPreference(
            "system.run.tag.date.suffix", "_date", SYSTEM_GROUP, pass);

    public static final StringPreference SYSTEM_RUN_TAG_STOP_REASON = new StringPreference(
            "system.run.tag.stop.reason", "STOP_REASON", SYSTEM_GROUP, pass, true);

    /**
     * Level of CPU load, below which a Run is considered `idle`
     */
    public static final IntPreference SYSTEM_IDLE_CPU_THRESHOLD_PERCENT =
            new IntPreference("system.idle.cpu.threshold", 10, SYSTEM_GROUP, isGreaterThan(0));
    /**
     * Level of memory load, below which a Run is considered `overloaded`
     */
    public static final IntPreference SYSTEM_MEMORY_THRESHOLD_PERCENT =
            new IntPreference("system.memory.consume.threshold", 95, SYSTEM_GROUP, isGreaterThan(0));
    /**
     * Level of filesystem load, below which a Run is considered `overloaded`
     */
    public static final IntPreference SYSTEM_DISK_THRESHOLD_PERCENT =
            new IntPreference("system.disk.consume.threshold", 95, SYSTEM_GROUP, isGreaterThan(0));
    /**
     * Period of time for monitoring metrics query
     */
    public static final IntPreference SYSTEM_MONITORING_METRIC_TIME_RANGE =
            new IntPreference("system.monitoring.time.range", 30, SYSTEM_GROUP, isGreaterThan(0));
    /**
     * Controls maximum timeout (in minutes), which a node can stay idle, before an action will be taken
     */
    public static final IntPreference SYSTEM_MAX_IDLE_TIMEOUT_MINUTES =
            new IntPreference("system.max.idle.timeout.minutes", 30, SYSTEM_GROUP, isGreaterThan(0));

    /**
     * A timeout to wait before an idle action will be taken
     */
    public static final IntPreference SYSTEM_IDLE_ACTION_TIMEOUT_MINUTES =
            new IntPreference("system.idle.action.timeout.minutes", 30, SYSTEM_GROUP, isGreaterThan(0));
    /**
     * Controls which action will be executed After idle and Action timeouts. Can take values from {@link IdleRunAction}
     */
    // TODO: rewrite to an EnumPreference?
    public static final StringPreference SYSTEM_IDLE_ACTION = new StringPreference("system.idle.action",
                                   IdleRunAction.NOTIFY.name(), SYSTEM_GROUP, PreferenceValidators.isValidIdleAction);
    /**
     * Controls which action will be performed after action threshold for long paused runs.
     * Can take values from {@link LongPausedRunAction} only.
     */
    public static final StringPreference SYSTEM_LONG_PAUSED_ACTION = new StringPreference("system.long.paused.action",
            LongPausedRunAction.NOTIFY.name(), SYSTEM_GROUP, PreferenceValidators.isValidLongPauseRunAction);
    public static final IntPreference SYSTEM_LONG_PAUSED_ACTION_TIMEOUT_MINUTES =
            new IntPreference("system.long.paused.action.timeout.minutes",
                    30, SYSTEM_GROUP, isGreaterThan(0));
    /**
     * Controls how long resource monitoring stats are being kept, in days
     */
    public static final IntPreference SYSTEM_RESOURCE_MONITORING_STATS_RETENTION_PERIOD = new IntPreference(
        "system.resource.monitoring.stats.retention.period", 3, SYSTEM_GROUP, isGreaterThanOrEquals(0));
    /**
     * Specifies if interactive run ssh sessions should use root as a default user.
     */
    public static final BooleanPreference SYSTEM_SSH_DEFAULT_ROOT_USER_ENABLED = new BooleanPreference(
            "system.ssh.default.root.user.enabled", true, SYSTEM_GROUP, pass, true);
    /**
     * Controls which instance types will be excluded from notification list.
     */
    public static final StringPreference SYSTEM_NOTIFICATIONS_EXCLUDE_INSTANCE_TYPES = new StringPreference(
            "system.notifications.exclude.instance.types", null, SYSTEM_GROUP, pass);
    public static final IntPreference SYSTEM_CLUSTER_PRICE_MONITOR_DELAY = new IntPreference(
            "system.cluster.price.monitor.delay", 30000, SYSTEM_GROUP, pass);
    /**
     * Controls which events will be ommitted from the OOM Logger output (
     * e.g. flannel, iptables and other system services)
     */
    public static final StringPreference SYSTEM_OOM_EXCLUDE_EVENTS = new StringPreference(
            "system.oom.exclude.events", "flanneld|iptables|canal|kube-proxy|calico", SYSTEM_GROUP, pass);
    public static final IntPreference SYSTEM_INACTIVE_USER_MONITOR_DELAY = new IntPreference(
            "system.inactive.user.monitor.delay", Constants.MILLISECONDS_IN_DAY, SYSTEM_GROUP, isGreaterThan(0));
    public static final BooleanPreference SYSTEM_INACTIVE_USER_MONITOR_ENABLED = new BooleanPreference(
            "system.inactive.user.monitor.enable", false, SYSTEM_GROUP, pass);
    public static final IntPreference SYSTEM_INACTIVE_USER_MONITOR_BLOCKED_DAYS = new IntPreference(
            "system.inactive.user.monitor.blocked.days", 365, SYSTEM_GROUP, pass);
    public static final IntPreference SYSTEM_INACTIVE_USER_MONITOR_IDLE_DAYS = new IntPreference(
            "system.inactive.user.monitor.idle.days", 365, SYSTEM_GROUP, pass);
    public static final BooleanPreference SYSTEM_LDAP_USER_BLOCK_MONITOR_ENABLED = new BooleanPreference(
            "system.ldap.user.block.monitor.enable", false, SYSTEM_GROUP, pass);
    public static final IntPreference SYSTEM_LDAP_USER_BLOCK_MONITOR_DELAY = new IntPreference(
            "system.ldap.user.block.monitor.delay", Constants.MILLISECONDS_IN_DAY, SYSTEM_GROUP, isGreaterThan(0));
    public static final IntPreference SYSTEM_LDAP_USER_BLOCK_MONITOR_GRACE_PERIOD_DAYS = new IntPreference(
            "system.ldap.user.block.monitor.grace.period.days", 7, SYSTEM_GROUP, isGreaterThan(0));
    public static final IntPreference SYSTEM_NODE_POOL_MONITOR_DELAY = new IntPreference(
            "system.node.pool.monitor.delay", 30000, SYSTEM_GROUP, pass);
    /**
     * Indicates difference between current timestamp and user's previous last login. If this threshold exceeded
     * update action shall be performed for user.
     */
    public static final IntPreference SYSTEM_USER_JWT_LAST_LOGIN_THRESHOLD = new IntPreference(
            "system.user.jwt.last.login.threshold.hours", 1, SYSTEM_GROUP, pass);
    public static final BooleanPreference SYSTEM_USER_SSH_KEYS_AUTO_CREATE = new BooleanPreference(
            "system.user.ssh.keys.auto.create", true, SYSTEM_GROUP, pass);
    public static final StringPreference SYSTEM_USER_SSH_KEYS_PRV_METADATA_KEY = new StringPreference(
            "system.user.ssh.keys.prv.metadata.key", "ssh_prv", SYSTEM_GROUP, isNotBlank);
    public static final StringPreference SYSTEM_USER_SSH_KEYS_PUB_METADATA_KEY = new StringPreference(
            "system.user.ssh.keys.pub.metadata.key", "ssh_pub", SYSTEM_GROUP, isNotBlank);

    public static final ObjectPreference<Map<String, NotificationFilter>>
            SYSTEM_NOTIFICATIONS_EXCLUDE_PARAMS = new ObjectPreference("system.notifications.exclude.params",
            null, new TypeReference<Map<String, NotificationFilter>>() {},
            SYSTEM_GROUP, isNullOrValidJson(new TypeReference<Map<String, NotificationFilter>>() {}));

    public static final BooleanPreference SYSTEM_DISABLE_NAT_SYNC = new BooleanPreference(
            "system.disable.nat.sync", true, SYSTEM_GROUP, pass);
    public static final IntPreference SYSTEM_NAT_HOST_CHECK_ATTEMPTS = new IntPreference(
            "system.nat.host.check.attempts", 10, SYSTEM_GROUP, isGreaterThan(0));
    public static final IntPreference SYSTEM_NAT_HOST_CHECK_RETRY_MS = new IntPreference(
            "system.nat.host.check.retry", 100, SYSTEM_GROUP, isGreaterThan(0));
    public static final StringPreference KUBE_NETWORK_POLICY_NAME = new StringPreference(
            "system.kube.network.policy.name", "sensitive-runs-policy", SYSTEM_GROUP, pass);

    public static final BooleanPreference SYSTEM_MAINTENANCE_MODE = new BooleanPreference(
            "system.maintenance.mode", false, SYSTEM_GROUP, pass, true);
    public static final StringPreference SYSTEM_MAINTENANCE_MODE_BANNER = new StringPreference(
            "system.maintenance.mode.banner",
            "Platform is in a maintenance mode, operation is temporary unavailable",
            SYSTEM_GROUP, pass, true);
    public static final BooleanPreference SYSTEM_BLOCKING_MAINTENANCE_MODE = new BooleanPreference(
            "system.blocking.maintenance.mode", false, SYSTEM_GROUP, pass, true);
    public static final IntPreference SYSTEM_USAGE_USERS_MONITOR_DELAY = new IntPreference(
            "system.usage.users.monitor.delay", 300000, SYSTEM_GROUP, isGreaterThan(0));
    public static final BooleanPreference SYSTEM_USAGE_USERS_MONITOR_ENABLE = new BooleanPreference(
            "system.usage.users.monitor.enable", false, SYSTEM_GROUP, pass);
    public static final BooleanPreference SYSTEM_USAGE_USERS_CLEAN_ENABLE = new BooleanPreference(
            "system.usage.users.clean.enable", false, SYSTEM_GROUP, pass);
    public static final IntPreference SYSTEM_USAGE_USERS_CLEAN_DELAY = new IntPreference(
            "system.usage.users.clean.delay",  Constants.MILLISECONDS_IN_DAY, SYSTEM_GROUP, isGreaterThan(0));
    public static final IntPreference SYSTEM_USAGE_USERS_STORE_DAYS = new IntPreference(
            "system.usage.users.store.days", 365, SYSTEM_GROUP, pass);
    public static final IntPreference SYSTEM_NOTIFICATIONS_EXP_PERIOD = new IntPreference(
            "system.notifications.exp.period", 3, SYSTEM_GROUP, pass);
    public static final BooleanPreference SYSTEM_NOTIFICATIONS_ENABLE = new BooleanPreference(
            "system.notifications.enable", false, SYSTEM_GROUP, pass);

    // FireCloud Integration
    public static final ObjectPreference<List<String>> FIRECLOUD_SCOPES = new ObjectPreference<>(
        "firecloud.api.scopes", null, new TypeReference<List<String>>() {}, FIRECLOUD_GROUP,
        isNullOrValidJson(new TypeReference<List<String>>() {}));
    public static final StringPreference FIRECLOUD_BASE_URL = new StringPreference("firecloud.base.url",
            "https://api.firecloud.org/api/", FIRECLOUD_GROUP, PreferenceValidators.isValidUrl);
    public static final BooleanPreference FIRECLOUD_ENABLE_USER_AUTH = new BooleanPreference(
            "firecloud.enable.user.auth", false, FIRECLOUD_GROUP, pass);
    public static final StringPreference GOOGLE_REDIRECT_URL = new StringPreference("google.redirect.url",
            null, FIRECLOUD_GROUP, PreferenceValidators.isEmptyOrValidUrlSyntax);
    public static final StringPreference GOOGLE_CLIENT_SETTINGS = new StringPreference("google.client.settings",
            null, FIRECLOUD_GROUP, PreferenceValidators.isEmptyOrFileExist);
    public static final StringPreference GOOGLE_CLIENT_ID = new StringPreference("google.client.id",
            null, FIRECLOUD_GROUP, pass);
    public static final StringPreference FIRECLOUD_LAUNCHER_TOOL = new StringPreference("firecloud.launcher.tool",
            null, FIRECLOUD_GROUP, null);
    public static final StringPreference FIRECLOUD_LAUNCHER_CMD = new StringPreference("firecloud.launcher.cmd",
            null, FIRECLOUD_GROUP, isNotBlank);
    public static final StringPreference FIRECLOUD_BILLING_PROJECT = new StringPreference("firecloud.billing.project",
            null, FIRECLOUD_GROUP, isNotBlank);
    public static final StringPreference FIRECLOUD_INSTANCE_TYPE = new StringPreference("firecloud.instance.type",
            "m4.xlarge", FIRECLOUD_GROUP, pass);
    public static final IntPreference FIRECLOUD_INSTANCE_DISK = new IntPreference("firecloud.instance.disk",
            50, FIRECLOUD_GROUP, isGreaterThan(0));

    // Misc
    public static final IntPreference MISC_MAX_TOOL_ICON_SIZE_KB = new IntPreference("misc.max.tool.icon.size.kb", 50,
                                                                                     MISC_GROUP, isGreaterThan(0));
    public static final StringPreference MISC_SYSTEM_EVENTS_CONFIRMATION_METADATA_KEY = new StringPreference(
            "system.events.confirmation.metadata.key", "confirmed_notifications", MISC_GROUP,
            isNotBlank);
    public static final ObjectPreference<List<String>> MISC_METADATA_SENSITIVE_KEYS = new ObjectPreference<>(
            "misc.metadata.sensitive.keys", null, new TypeReference<List<String>>() {}, MISC_GROUP,
            isNullOrValidJson(new TypeReference<List<String>>() {}));
    public static final ObjectPreference<List<String>> MISC_METADATA_MANDATORY_KEYS = new ObjectPreference<>(
            "misc.metadata.mandatory.keys", null, new TypeReference<List<String>>() {}, MISC_GROUP,
            isNullOrValidJson(new TypeReference<List<String>>() {}));
    public static final ObjectPreference<Map<String, Object>> MISC_GROUPS_UI_PREF = new ObjectPreference<>(
            "misc.groups.ui.preferences", null, new TypeReference<Map<String, Object>>() {}, MISC_GROUP,
            isNullOrValidJson(new TypeReference<Map<String, Object>>() {}));

    // Search
    public static final StringPreference SEARCH_ELASTIC_SCHEME = new StringPreference("search.elastic.scheme",
            null, SEARCH_GROUP, pass);
    public static final StringPreference SEARCH_ELASTIC_HOST = new StringPreference("search.elastic.host",
            null, SEARCH_GROUP, pass);
    public static final IntPreference SEARCH_ELASTIC_PORT = new IntPreference("search.elastic.port",
            null, SEARCH_GROUP, pass);
    public static final IntPreference SEARCH_ELASTIC_SOCKET_TIMEOUT = new IntPreference(
            "search.elastic.socket.timeout", 30000, SEARCH_GROUP, pass);
    public static final StringPreference SEARCH_ELASTIC_CP_INDEX_PREFIX = new StringPreference(
            "search.elastic.index.common.prefix", null, SEARCH_GROUP, pass);
    public static final StringPreference SEARCH_ELASTIC_REQUESTS_INDEX_PREFIX = new StringPreference(
            "search.elastic.index.requests.prefix", "cp-storage-requests-*", SEARCH_GROUP, pass);
    public static final StringPreference SEARCH_ELASTIC_TYPE_FIELD = new StringPreference(
            "search.elastic.type.field", "doc_type", SEARCH_GROUP, pass);

    public static final ObjectPreference<Map<SearchDocumentType, String>> SEARCH_ELASTIC_TYPE_INDEX_PREFIX =
            new ObjectPreference<>("search.elastic.index.type.prefix", null,
                    new TypeReference<Map<SearchDocumentType, String>>() {}, SEARCH_GROUP,
                    isNullOrValidJson(new TypeReference<Map<SearchDocumentType, String>>() {}));

    public static final ObjectPreference<List<String>> SEARCH_ELASTIC_SEARCH_FIELDS = new ObjectPreference<>(
            "search.elastic.search.fields", null,
            new TypeReference<List<String>>() {}, SEARCH_GROUP,
            isNullOrValidJson(new TypeReference<List<String>>() {}));

    public static final StringPreference SEARCH_ELASTIC_ALLOWED_USERS_FIELD = new StringPreference(
            "search.elastic.allowed.users.field", null, SEARCH_GROUP, pass);

    public static final StringPreference SEARCH_ELASTIC_DENIED_USERS_FIELD = new StringPreference(
            "search.elastic.denied.users.field", null, SEARCH_GROUP, pass);

    public static final StringPreference SEARCH_ELASTIC_ALLOWED_GROUPS_FIELD = new StringPreference(
            "search.elastic.allowed.groups.field", null, SEARCH_GROUP, pass);

    public static final StringPreference SEARCH_ELASTIC_DENIED_GROUPS_FIELD = new StringPreference(
            "search.elastic.denied.groups.field", null, SEARCH_GROUP, pass);
    public static final IntPreference SEARCH_AGGS_MAX_COUNT = new IntPreference("search.aggs.max.count",
            20, SEARCH_GROUP, pass);
    public static final IntPreference SEARCH_LOGS_AGGS_MAX_COUNT = new IntPreference("search.logs.aggs.max.count",
            10000, SEARCH_GROUP, pass);
    public static final BooleanPreference SEARCH_HIDE_DELETED = new BooleanPreference(
            "search.elastic.hide.deleted", true, SEARCH_GROUP, pass);
    public static final IntPreference SEARCH_EXPORT_PAGE_SIZE = new IntPreference(
            "search.export.page.size", 5000, SEARCH_GROUP, isGreaterThan(0));

    public static final ObjectPreference<List<StorageFileSearchMask>> FILE_SEARCH_MASK_RULES = new ObjectPreference<>(
        "search.storage.elements.settings",
        Collections.emptyList(),
        new TypeReference<List<StorageFileSearchMask>>() {},
        SEARCH_GROUP,
        isNullOrValidJson(new TypeReference<List<StorageFileSearchMask>>() {}));

    public static final StringPreference SEARCH_ELASTIC_PREFIX_FILTER_FIELD = new StringPreference(
            "search.elastic.prefix.filter.field", "id", SEARCH_GROUP, pass);
    public static final ObjectPreference<List<String>> SEARCH_ELASTIC_METADATA_FIELDS = new ObjectPreference<>(
            "search.elastic.index.metadata.fields", null,
            new TypeReference<List<String>>() {}, SEARCH_GROUP,
            isNullOrValidJson(new TypeReference<List<String>>() {}), true);

    // Grid engine autoscaling
    public static final IntPreference GE_AUTOSCALING_SCALE_UP_TIMEOUT =
            new IntPreference("ge.autoscaling.scale.up.timeout", 30,
                    GRID_ENGINE_AUTOSCALING_GROUP, pass);
    public static final IntPreference GE_AUTOSCALING_SCALE_DOWN_TIMEOUT =
            new IntPreference("ge.autoscaling.scale.down.timeout", 30,
                    GRID_ENGINE_AUTOSCALING_GROUP, pass);
    public static final IntPreference GE_AUTOSCALING_SCALE_UP_POLLING_TIMEOUT =
            new IntPreference("ge.autoscaling.scale.up.polling.timeout", 900,
                    GRID_ENGINE_AUTOSCALING_GROUP, pass);
    public static final IntPreference GE_AUTOSCALING_SCALE_UP_TO_MAX =
            new IntPreference("ge.autoscaling.scale.up.to.max", null,
                    GRID_ENGINE_AUTOSCALING_GROUP,
                    isNotLessThanValueOrNull(LAUNCH_MAX_SCHEDULED_NUMBER.getKey()),
                    LAUNCH_MAX_SCHEDULED_NUMBER);
    public static final ObjectPreference<Map<String, Object>> GE_AUTOSCALING_SCALE_MULTI_QUEUES_TEMPLATE =
            new ObjectPreference<>("ge.autoscaling.scale.multi.queues.template", null,
                    new TypeReference<Map<String, Object>>() {}, GRID_ENGINE_AUTOSCALING_GROUP,
                    isNullOrValidJson(new TypeReference<Map<String, Object>>() {}));
    public static final StringPreference GE_UTILITY_ALLOWED_GROUPS =
            new StringPreference("ge.utility.allowed.groups", "ROLE_ADMIN,ROLE_ADVANCED_USER",
                    GRID_ENGINE_AUTOSCALING_GROUP, pass, true);

    //GCP
    public static final ObjectPreference<List<String>> GCP_REGION_LIST = new ObjectPreference<>(
            "gcp.regions.list", null, new TypeReference<List<String>>() {}, GCP_GROUP,
            isNullOrValidJson(new TypeReference<List<String>>() {}));
    public static final ObjectPreference<Map<String, GCPResourceMapping>> GCP_SKU_MAPPING = new ObjectPreference<>(
            "gcp.sku.mapping", null, new TypeReference<Map<String, GCPResourceMapping>>() {}, GCP_GROUP,
            isNullOrValidJson(new TypeReference<Map<String, GCPResourceMapping>>() {}));
    public static final StringPreference GCP_DEFAULT_GPU_TYPE = new StringPreference(
            "gcp.default.gpu.type", "a100", GCP_GROUP, isNotBlank);

    // Billing Reports
    public static final StringPreference BILLING_USER_NAME_ATTRIBUTE = new StringPreference(
            "billing.reports.user.name.attribute", null, BILLING_GROUP, pass);
    public static final BooleanPreference BILLING_REPORTS_ENABLED = new BooleanPreference(
            "billing.reports.enabled", true, BILLING_GROUP, pass);
    public static final BooleanPreference BILLING_REPORTS_ENABLED_ADMINS = new BooleanPreference(
            "billing.reports.enabled.admins", true, BILLING_GROUP, pass);
    public static final IntPreference BILLING_EXPORT_AGGREGATION_PAGE_SIZE = new IntPreference(
            "billing.export.aggregation.page.size", 5000, BILLING_GROUP, pass);
    public static final IntPreference BILLING_EXPORT_PERIOD_AGGREGATION_PAGE_SIZE = new IntPreference(
            "billing.export.period.aggregation.page.size", 1000, BILLING_GROUP, pass);

    // Billing quotas
    public static final BooleanPreference BILLING_QUOTAS_ENABLED = new BooleanPreference(
            "billing.quotas.enabled", false, BILLING_QUOTAS_GROUP, pass);
    public static final IntPreference BILLING_QUOTAS_MONITORING_PERIOD_SECONDS = new IntPreference(
            "billing.quotas.period.seconds", Constants.SECONDS_IN_DAY, BILLING_QUOTAS_GROUP, isGreaterThan(10));
    public static final IntPreference BILLING_QUOTAS_CLEARING_PERIOD_SECONDS = new IntPreference(
            "billing.quotas.clear.period.seconds", Constants.SECONDS_IN_DAY * 30,
            BILLING_QUOTAS_GROUP, isGreaterThan(10));

    // Lustre FS
    public static final IntPreference LUSTRE_FS_DEFAULT_SIZE_GB = new IntPreference(
            "lustre.fs.default.size.gb", 1200, LUSTRE_GROUP, pass);
    public static final IntPreference LUSTRE_FS_BKP_RETENTION_DAYS = new IntPreference(
            "lustre.fs.backup.retention.days", 7, LUSTRE_GROUP, pass);
    public static final IntPreference LUSTRE_FS_DEFAULT_THROUGHPUT = new IntPreference(
            "lustre.fs.default.throughput", 500, LUSTRE_GROUP, pass);
    public static final StringPreference LUSTRE_FS_MOUNT_OPTIONS = new StringPreference(
            "lustre.fs.mount.options", null, LUSTRE_GROUP, pass);
    public static final StringPreference LUSTRE_FS_DEPLOYMENT_TYPE = new StringPreference(
            "lustre.fs.deployment.type", LustreDeploymentType.SCRATCH_2.name(), LUSTRE_GROUP,
            isValidEnum(LustreDeploymentType.class));
    public static final BooleanPreference LUSTRE_FS_MOUNT_IP = new BooleanPreference(
            "lustre.fs.mount.ip", false, LUSTRE_GROUP, pass);
    // Cloud Region
    public static final IntPreference CLOUD_REGION_TEMP_CREDENTIALS_DURATION = new IntPreference(
            "cloud.temp.credentials.duration", 3600, CLOUD_REGION_GROUP, isGreaterThan(0));
    public static final IntPreference CLOUD_REGION_TEMP_CREDENTIALS_EXPIRATION = new IntPreference(
            "cloud.temp.credentials.expiration", 3600, CLOUD_REGION_GROUP, isGreaterThan(0));

    //LDAP
    public static final StringPreference LDAP_URLS = new StringPreference(
            "ldap.urls", "ldap://localhost:389", LDAP_GROUP, pass);
    public static final StringPreference LDAP_USERNAME = new StringPreference(
            "ldap.username", "", LDAP_GROUP, pass);
    public static final StringPreference LDAP_PASSWORD = new StringPreference(
            "ldap.password", "", LDAP_GROUP, pass);
    public static final StringPreference LDAP_BASE_PATH = new StringPreference(
            "ldap.base.path", "", LDAP_GROUP, pass);
    public static final StringPreference LDAP_USER_FILTER = new StringPreference(
            "ldap.user.filter", "(&(objectClass=person)(cn=*%s*))", LDAP_GROUP, pass);
    public static final StringPreference LDAP_GROUP_FILTER = new StringPreference(
            "ldap.group.filter", "(&(objectClass=group)(cn=*%s*))", LDAP_GROUP, pass);
    public static final StringPreference LDAP_NAME_ATTRIBUTE = new StringPreference(
            "ldap.entity.attribute.name", "cn", LDAP_GROUP, pass);
    public static final StringPreference LDAP_ENTITY_ATTRIBUTES = new StringPreference(
            "ldap.entity.attributes", "cn,distinguishedName,mail", LDAP_GROUP, pass);
    public static final IntPreference LDAP_RESPONSE_SIZE = new IntPreference(
            "ldap.response.size", 10, LDAP_GROUP, isGreaterThan(0));
    public static final IntPreference LDAP_RESPONSE_TIMEOUT = new IntPreference(
            "ldap.response.timeout", 60000, LDAP_GROUP, isGreaterThanOrEquals(0));
    public static final StringPreference LDAP_BLOCKED_USER_FILTER = new StringPreference(
            "ldap.blocked.user.filter", "", LDAP_GROUP, pass);

    public static final StringPreference LDAP_BLOCKED_USER_SEARCH_METHOD = new StringPreference(
            "ldap.blocked.user.search.method",
            LdapBlockedUserSearchMethod.LOAD_BLOCKED.name(), LDAP_GROUP,
            isValidEnum(LdapBlockedUserSearchMethod.class));
    public static final StringPreference LDAP_BLOCKED_USER_NAME_ATTRIBUTE = new StringPreference(
            "ldap.blocked.user.name.attribute", "sAMAccountName", LDAP_GROUP, pass);
    public static final IntPreference LDAP_BLOCKED_USERS_FILTER_PAGE_SIZE = new IntPreference(
            "ldap.blocked.user.filter.page.size", 50, LDAP_GROUP, pass);
    public static final ObjectPreference<Set<String>> LDAP_INVALID_USER_ENTRIES = new ObjectPreference<>(
            "ldap.blocked.invalid.user.entries",
            Collections.singleton("Name not found"), new TypeReference<Set<String>>() {}, LDAP_GROUP,
            isNullOrValidJson(new TypeReference<Set<String>>() {}));

    //NGS Preprocessing
    public static final StringPreference PREPROCESSING_MACHINE_RUN_CLASS = new StringPreference(
            "ngs.preprocessing.machine.run.metadata.class.name", "MachineRun",
            NGS_PREPROCESSING_GROUP, isNotBlank);
    public static final StringPreference PREPROCESSING_SAMPLE_CLASS = new StringPreference(
            "ngs.preprocessing.sample.metadata.class.name", "Sample",
            NGS_PREPROCESSING_GROUP, isNotBlank);
    public static final StringPreference PREPROCESSING_MACHINE_RUN_TO_SAMPLE_COLUMN = new StringPreference(
            "ngs.preprocessing.machinerun.to.sample.column", "Samples",
            NGS_PREPROCESSING_GROUP, isNotBlank);
    public static final StringPreference PREPROCESSING_SAMPLESHEET_FILE_NAME = new StringPreference(
            "ngs.preprocessing.samplesheet.file.name", "samplesheet.csv",
            NGS_PREPROCESSING_GROUP, isNotBlank);
    public static final StringPreference PREPROCESSING_DATA_FOLDER = new StringPreference(
            "ngs.preprocessing.data.folder", "ngs-data",
            NGS_PREPROCESSING_GROUP, isNotBlank);
    public static final StringPreference PREPROCESSING_SAMPLESHEET_LINK_COLUMN = new StringPreference(
            "ngs.preprocessing.samplesheet.link.column", "Sample Sheet",
            NGS_PREPROCESSING_GROUP, isNotBlank);
    public static final StringPreference PREPROCESSING_MACHINE_RUN_COLUMN_NAME = new StringPreference(
            "ngs.preprocessing.machine.run.column.name", "Machine Run",
            NGS_PREPROCESSING_GROUP, isNotBlank);

    // Monitoring
    public static final IntPreference MONITORING_POOL_USAGE_DELAY = new IntPreference(
            "monitoring.node.pool.usage.delay", 300000, MONITORING_GROUP, isGreaterThan(0));
    public static final BooleanPreference MONITORING_POOL_USAGE_ENABLE = new BooleanPreference(
            "monitoring.node.pool.usage.enable", false, MONITORING_GROUP, pass);
    public static final BooleanPreference MONITORING_POOL_USAGE_CLEAN_ENABLE = new BooleanPreference(
            "monitoring.node.pool.usage.clean.enable", false, MONITORING_GROUP, pass);
    public static final IntPreference MONITORING_POOL_USAGE_CLEAN_DELAY = new IntPreference(
            "monitoring.node.pool.usage.clean.delay",  Constants.MILLISECONDS_IN_DAY, MONITORING_GROUP,
            isGreaterThan(0));
    public static final IntPreference MONITORING_POOL_USAGE_STORE_DAYS = new IntPreference(
            "monitoring.node.pool.usage.store.days", 365, MONITORING_GROUP, pass);

    // Cloud
    public static final ObjectPreference<List<CloudAccessManagementConfig>> CLOUD_ACCESS_MANAGEMENT_CONFIG =
            new ObjectPreference<>(
                    "cloud.access.management.config", Collections.emptyList(),
                    new TypeReference<List<CloudAccessManagementConfig>>() {}, CLOUD,
                    isNullOrValidJson(new TypeReference<List<CloudAccessManagementConfig>>() {}));

    private static final Pattern GIT_VERSION_PATTERN = Pattern.compile("(\\d)\\.(\\d)");

    // System Jobs
    public static final StringPreference SYSTEM_JOBS_SCRIPTS_LOCATION = new StringPreference(
            "system.jobs.scripts.location", "src/system-jobs", SYSTEM_JOBS_GROUP, pass, false);

    public static final StringPreference SYSTEM_JOBS_OUTPUT_TASK = new StringPreference(
            "system.jobs.output.pipeline.task", "SystemJob", SYSTEM_JOBS_GROUP, pass, false);

    public static final LongPreference SYSTEM_JOBS_PIPELINE = new LongPreference(
            "system.jobs.pipeline.id", null, SYSTEM_JOBS_GROUP, isNullOrGreaterThan(0), false);

    private static final Map<String, AbstractSystemPreference<?>> PREFERENCE_MAP;

    private ToolManager toolManager;
    private DockerRegistryManager dockerRegistryManager;
    private DockerClientFactory dockerClientFactory;
    private PreferenceManager preferenceManager;
    private MessageHelper messageHelper;
    private GitManager gitManager;
    private DataStorageManager dataStorageManager;

    static {
        PREFERENCE_MAP = Collections.unmodifiableMap(Arrays.stream(SystemPreferences.class.getFields())
                .map(f -> {
                    try {
                        return (AbstractSystemPreference<?>) f.get(null);
                    } catch (IllegalAccessException e) {
                        throw new PipelineException(e);
                    }
                })
                .collect(Collectors.toMap(AbstractSystemPreference::getKey, pref -> pref)));

        GIT_HOST.setDependencies(GIT_TOKEN, GIT_USER_ID, GIT_USER_NAME);
        GIT_TOKEN.setDependencies(GIT_HOST, GIT_USER_ID, GIT_USER_NAME);
        GIT_USER_ID.setDependencies(GIT_TOKEN, GIT_HOST, GIT_USER_NAME);
        GIT_USER_NAME.setDependencies(GIT_HOST, GIT_TOKEN, GIT_USER_ID);
    }

    @Autowired
    public SystemPreferences(@Lazy ToolManager toolManager,
                             @Lazy DockerRegistryManager dockerRegistryManager,
                             @Lazy DockerClientFactory dockerClientFactory,
                             @Lazy PreferenceManager preferenceManager,
                             @Lazy MessageHelper messageHelper,
                             @Lazy GitManager gitManager,
                             @Lazy DataStorageManager dataStorageManager,
                             @Value("${cluster.enable.autoscaling:true}") boolean enableAutoscaling) {
        this.toolManager = toolManager;
        this.dockerRegistryManager = dockerRegistryManager;
        this.dockerClientFactory = dockerClientFactory;
        this.preferenceManager = preferenceManager;
        this.messageHelper = messageHelper;
        this.gitManager = gitManager;
        this.dataStorageManager = dataStorageManager;

        LAUNCH_DOCKER_IMAGE.setValidator(isValidToolOrImage);
        FIRECLOUD_LAUNCHER_TOOL.setValidator(isValidToolOrImage);
        DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME.setValidator(isValidDataStorageName);

        GIT_HOST.setValidator(isGitGroupValid);
        GIT_TOKEN.setValidator(isGitGroupValid);
        GIT_USER_ID.setValidator(isGitGroupValid);
        GIT_USER_NAME.setValidator(isGitGroupValid);

        if (!enableAutoscaling) { // For testing: enable to set the default value for this parameter from properties
            CLUSTER_ENABLE_AUTOSCALING.setDefaultValue(false);
        }
        Assert.isTrue(PREFERENCE_MAP.values().stream().allMatch(p -> p.getValidator() != null), "Validator missing");
    }

    /**
     * Validates a list of {@link Preference} against AbstractSystemPreference's validation rules.
     * @param preferences list of preferences to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validate(List<Preference> preferences) {
        Optional<Preference> invalidPreference = preferences.stream()
                .filter(p -> !isValid(p, getDependentPreferences(preferences, p)))
                .findFirst();

        invalidPreference.ifPresent((p) -> {
            throw new IllegalArgumentException(messageHelper.getMessage(MessageConstants.ERROR_PREFERENCE_VALUE_INVALID,
                                                                        p.getName(), p.getValue()));
        });
    }

    /**
     * Checks that system pre-defined preference value is valid
     * @param preference a {@link Preference}, referencing a known {@link AbstractSystemPreference}
     * @return true/false if preference's value is valid/invalid
     */
    public boolean isValid(Preference preference, Map<String, Preference> dependentPreferences) {
        AbstractSystemPreference<?> systemPreference = PREFERENCE_MAP.get(preference.getName());
        if (systemPreference == null) {
            return true;
        }

        return systemPreference.validate(preference.getValue(), dependentPreferences);
    }

    private Map<String, Preference> getDependentPreferences(List<Preference> allPreferences,
                                                            Preference preference) {
        return getSystemPreference(preference.getName())
            .map(systemPreference -> {
                if (systemPreference.getDependencies().isEmpty()) {
                    return new HashMap<String, Preference>();
                }

                Map<String, Preference> dependentPreferences = allPreferences.stream()
                    .filter(p -> systemPreference.getDependencies().containsKey(p.getName()))
                    .collect(Collectors.toMap(Preference::getName, p -> p));

                for (String preferenceName : systemPreference.getDependencies().keySet()) {
                    if (!dependentPreferences.containsKey(preferenceName)) {
                        dependentPreferences.put(preferenceName, preferenceManager.getSystemPreference(
                            PREFERENCE_MAP.get(preferenceName)));
                    }
                }

                dependentPreferences.put(systemPreference.getKey(), preference);
                return dependentPreferences;
            })
            .orElse(Collections.emptyMap());
    }

    // Validators, that require Spring context

    private BiPredicate<String, Map<String, Preference>> isValidToolOrImage = (pref, dependencies) -> {
        try {
            toolManager.loadByNameOrId(pref);
            return true;
        } catch (IllegalArgumentException e) {
            return dockerRegistryManager.loadAllDockerRegistry().stream().anyMatch(registry -> {
                DockerClient client = dockerClientFactory.getDockerClient(registry, dockerRegistryManager.getImageToken(
                        registry, pref));
                return client.getManifest(registry, pref, "latest").isPresent();
            });
        }
    };

    private BiPredicate<String, Map<String, Preference>> isValidDataStorageName = (pref, dependencies) -> {
        try {
            dataStorageManager.loadByNameOrId(pref);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    };

    private BiPredicate<String, Map<String, Preference>> isGitGroupValid = (pref, dependencies) -> {
        if (pref == null || dependencies.isEmpty() ||
                dependencies.values().stream().anyMatch(dependency -> dependency.getValue() == null)) {
            throw new IllegalArgumentException("Git options can't be null.");
        }
        return areGitPreferencesValid(dependencies);
    };

    private boolean areGitPreferencesValid(Map<String, Preference> gitPreferences) {
        long adminId = Long.parseLong(gitPreferences.get(GIT_USER_ID.getKey()).getValue());
        GitlabClient client =  gitManager.getGitlabClient(
                gitPreferences.get(GIT_HOST.getKey()).getValue(),
                gitPreferences.get(GIT_TOKEN.getKey()).getValue(),
                adminId,
                gitPreferences.get(GIT_USER_NAME.getKey()).getValue());
        try {
            client.buildCloneCredentials(false, false, 1L);
            GitlabVersion version = client.getVersion();
            Matcher matcher = GIT_VERSION_PATTERN.matcher(version.getVersion());
            if (matcher.find()) {
                Integer major = Integer.parseInt(matcher.group(1));
                Integer minor = Integer.parseInt(matcher.group(2));

                if ((major == 9 && minor >= 5) || (major == 8 && minor < 3)) {
                    throw new IllegalArgumentException("Invalid git version: " + version.getVersion());
                }
            } else {
                throw new IllegalArgumentException("Invalid git version: " + version.getVersion());
            }
        } catch (GitClientException e) {
            throw new IllegalArgumentException("Could not request Gitlab version", e);
        }
        return true;
    }


    public Collection<AbstractSystemPreference<?>> getSystemPreferences() {
        return PREFERENCE_MAP.values();
    }

    public static Optional<AbstractSystemPreference<?>> getSystemPreference(String key) {
        return Optional.ofNullable(PREFERENCE_MAP.get(key));
    }
}
