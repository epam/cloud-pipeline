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

package com.epam.pipeline.manager.preference;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.cluster.CloudRegionsConfiguration;
import com.epam.pipeline.entity.cluster.ClusterKeepAlivePolicy;
import com.epam.pipeline.entity.cluster.DockerMount;
import com.epam.pipeline.entity.cluster.EnvVarsSettings;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.container.ContainerMemoryResourcePolicy;
import com.epam.pipeline.entity.git.GitlabVersion;
import com.epam.pipeline.entity.monitoring.IdleRunAction;
import com.epam.pipeline.entity.monitoring.LongPausedRunAction;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.utils.ControlEntry;
import com.epam.pipeline.entity.utils.DefaultSystemParameter;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.exception.git.GitClientException;
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
import com.epam.pipeline.manager.security.run.RunVisibilityPolicy;
import com.epam.pipeline.security.ExternalServiceEndpoint;
import com.fasterxml.jackson.core.type.TypeReference;
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
import static com.epam.pipeline.manager.preference.PreferenceValidators.isNotLessThanValueOrNull;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isNullOrGreaterThan;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isNullOrValidJson;
import static com.epam.pipeline.manager.preference.PreferenceValidators.isValidEnum;
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
    private static final String STORAGE_FSBROWSER_BLACK_LIST_DEFAULT =
            "/bin,/var,/home,/root,/sbin,/sys,/usr,/boot,/dev,/lib,/proc,/etc";

    // COMMIT_GROUP
    public static final StringPreference COMMIT_DEPLOY_KEY = new StringPreference("commit.deploy.key", null,
                                                                  COMMIT_GROUP, PreferenceValidators.isNotBlank);
    public static final IntPreference COMMIT_TIMEOUT = new IntPreference("commit.timeout", 600, COMMIT_GROUP,
                                                                         isGreaterThan(0));
    public static final StringPreference COMMIT_USERNAME = new StringPreference("commit.username", null,
                                                                         COMMIT_GROUP, PreferenceValidators.isNotBlank);
    public static final StringPreference PRE_COMMIT_COMMAND_PATH = new StringPreference("commit.pre.command.path",
            "/root/pre_commit.sh", COMMIT_GROUP, PreferenceValidators.isNotBlank);
    public static final StringPreference POST_COMMIT_COMMAND_PATH = new StringPreference("commit.post.command.path",
            "/root/post_commit.sh", COMMIT_GROUP, PreferenceValidators.isNotBlank);
    public static final IntPreference PAUSE_TIMEOUT = new IntPreference("pause.timeout", 24 * 60 * 60,
            COMMIT_GROUP, isGreaterThan(0));

    // DATA_STORAGE_GROUP
    public static final IntPreference DATA_STORAGE_MAX_DOWNLOAD_SIZE = new IntPreference(
        "storage.max.download.size", 10000, DATA_STORAGE_GROUP, isGreaterThan(0));
    public static final IntPreference DATA_STORAGE_TEMP_CREDENTIALS_DURATION = new IntPreference(
        "storage.temp.credentials.duration", 3600, DATA_STORAGE_GROUP, isGreaterThan(0));

    /**
     * Black list for mount points, accept notation like: '/dir/*', '/dir/**'
     * */
    public static final StringPreference DATA_STORAGE_NFS_MOUNT_BLACK_LIST = new StringPreference(
            "storage.mount.black.list",
            "/,/etc,/runs,/common,/bin,/opt,/var,/home,/root,/sbin,/sys,/usr,/boot,/dev,/lib,/proc,/tmp",
            DATA_STORAGE_GROUP, PreferenceValidators.isEmptyOrValidBatchOfPaths);
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

    // GIT_GROUP
    public static final StringPreference GIT_HOST = new StringPreference("git.host", null, GIT_GROUP, null);
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

    // CLUSTER_GROUP
    /**
     * If this property is true, any free node that doesn't match configuration of a pending pod will be scaled down
     * immediately, otherwise it will be left until it will be reused or expired. If most of the time we use nodes with
     * the same configuration set true.
     */

    public static final StringPreference CLUSTER_ALLOWED_INSTANCE_TYPES = new StringPreference(
            "cluster.allowed.instance.types", "m5.*,c5.*,r4.*,t2.*", CLUSTER_GROUP,
            PreferenceValidators.isNotBlank);
    public static final StringPreference CLUSTER_ALLOWED_PRICE_TYPES = new StringPreference(
            "cluster.allowed.price.types", String.format("%s,%s", PriceType.SPOT, PriceType.ON_DEMAND),
            CLUSTER_GROUP, PreferenceValidators.isNotBlank);
    public static final StringPreference CLUSTER_ALLOWED_MASTER_PRICE_TYPES = new StringPreference(
            "cluster.allowed.price.types.master", String.format("%s,%s", PriceType.SPOT, PriceType.ON_DEMAND),
            CLUSTER_GROUP, PreferenceValidators.isNotBlank);
    public static final StringPreference CLUSTER_INSTANCE_TYPE = new StringPreference("cluster.instance.type",
        "m5.large", CLUSTER_GROUP, PreferenceValidators.isClusterInstanceTypeAllowed, CLUSTER_ALLOWED_INSTANCE_TYPES);

    public static final BooleanPreference CLUSTER_KILL_NOT_MATCHING_NODES = new BooleanPreference(
        "cluster.kill.not.matching.nodes", true, CLUSTER_GROUP, pass);
    public static final BooleanPreference CLUSTER_ENABLE_AUTOSCALING = new BooleanPreference(
        "cluster.enable.autoscaling", true, CLUSTER_GROUP, pass);
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
            "cluster.instance.device.prefix", "/dev/sd", CLUSTER_GROUP, PreferenceValidators.isNotBlank);
    public static final StringPreference CLUSTER_INSTANCE_DEVICE_SUFFIXES = new StringPreference(
            "cluster.instance.device.suffixes", "defghijklmnopqrstuvwxyz", CLUSTER_GROUP,
            PreferenceValidators.isNotBlank);
    public static final ObjectPreference<CloudRegionsConfiguration> CLUSTER_NETWORKS_CONFIG =
        new ObjectPreference<>("cluster.networks.config", null, new TypeReference<CloudRegionsConfiguration>() {},
                               CLUSTER_GROUP, isNullOrValidJson(new TypeReference<CloudRegionsConfiguration>() {}));
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
    public static final ObjectPreference<Set<String>> INSTANCE_COMPUTE_FAMILY_NAMES = new ObjectPreference<>(
            "instance.compute.family.names", null, new TypeReference<Set<String>>() {}, CLUSTER_GROUP,
            isNullOrValidJson(new TypeReference<Set<String>>() {}));

    //LAUNCH_GROUP
    public static final StringPreference LAUNCH_CMD_TEMPLATE = new StringPreference("launch.cmd.template",
                                                            "sleep infinity", LAUNCH_GROUP, pass);
    public static final IntPreference LAUNCH_JWT_TOKEN_EXPIRATION = new IntPreference(
        "launch.jwt.token.expiration", 2592000, LAUNCH_GROUP, isGreaterThan(0));
    public static final ObjectPreference<EnvVarsSettings> LAUNCH_ENV_PROPERTIES = new ObjectPreference<>(
        "launch.env.properties", null, new TypeReference<EnvVarsSettings>() {}, LAUNCH_GROUP,
        isNullOrValidJson(new TypeReference<EnvVarsSettings>() {}));
    public static final ObjectPreference<List<DockerMount>> DOCKER_IN_DOCKER_MOUNTS = new ObjectPreference<>(
            "launch.dind.mounts", null, new TypeReference<List<DockerMount>>() {},
            LAUNCH_GROUP, isNullOrValidJson(new TypeReference<List<DockerMount>>() {}));
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
            "launch.container.cpu.resource", 0, LAUNCH_GROUP, isGreaterThan(-1));
    public static final StringPreference LAUNCH_CONTAINER_MEMORY_RESOURCE_POLICY = new StringPreference(
            "launch.container.memory.resource.policy", ContainerMemoryResourcePolicy.NO_LIMIT.name(),
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

    //DTS submission
    public static final StringPreference DTS_LAUNCH_CMD_TEMPLATE = new StringPreference("dts.launch.cmd",
            "sleep infinity", DTS_GROUP, pass);
    public static final StringPreference DTS_LAUNCH_URL = new StringPreference("dts.launch.script.url",
            "", DTS_GROUP, pass);
    public static final StringPreference DTS_DISTRIBUTION_URL = new StringPreference("dts.dist.url",
            "", DTS_GROUP, pass);

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

    // UI_GROUP
    public static final StringPreference UI_PROJECT_INDICATOR = new StringPreference("ui.project.indicator",
                                                                                     "type=project", UI_GROUP, pass);
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
            "Cloud Pipeline", UI_GROUP, PreferenceValidators.isNotBlank);
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

    // BASE_URLS_GROUP
    public static final StringPreference BASE_API_HOST = new StringPreference("base.api.host", null,
                                                                  BASE_URLS_GROUP, PreferenceValidators.isValidUrl);
    public static final StringPreference BASE_API_HOST_EXTERNAL = new StringPreference("base.api.host.external", null,
                                                              BASE_URLS_GROUP, PreferenceValidators.isValidUrlSyntax);
    public static final StringPreference BASE_PIPE_DISTR_URL = new StringPreference("base.pipe.distributions.url",
                                                        null, BASE_URLS_GROUP, PreferenceValidators.isValidUrl);
    public static final StringPreference BASE_DAV_AUTH_URL = new StringPreference("base.dav.auth.url",
            null, BASE_URLS_GROUP, pass);

    //Data sharing
    public static final StringPreference BASE_API_SHARED = new StringPreference("data.sharing.base.api", null,
            DATA_SHARING_GROUP, PreferenceValidators.isEmptyOrValidUrlSyntax);
    public static final StringPreference DATA_SHARING_DISCLAIMER = new StringPreference("data.sharing.disclaimer", null,
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
     * Controls the amount of pod logs to be loaded
     */
    public static final IntPreference SYSTEM_LIMIT_LOG_LINES = new IntPreference(
            "system.log.line.limit", 8000, SYSTEM_GROUP, isGreaterThan(0));

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
    /**
     * Controls how long resource monitoring stats are being kept, in days
     */
    public static final IntPreference SYSTEM_RESOURCE_MONITORING_STATS_RETENTION_PERIOD = new IntPreference(
        "system.resource.monitoring.stats.retention.period", 3, SYSTEM_GROUP, isGreaterThanOrEquals(0));
    /**
     * Specifies if interactive run ssh sessions should use root as a default user.
     */
    public static final BooleanPreference SYSTEM_SSH_DEFAULT_ROOT_USER_ENABLED = new BooleanPreference(
            "system.ssh.default.root.user.enabled", true, SYSTEM_GROUP, pass);
    /**
     * Controls which instance types will be excluded from notification list.
     */
    public static final StringPreference SYSTEM_NOTIFICATIONS_EXCLUDE_INSTANCE_TYPES = new StringPreference(
            "system.notifications.exclude.instance.types", null, SYSTEM_GROUP, pass);

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
            null, FIRECLOUD_GROUP, PreferenceValidators.isNotBlank);
    public static final StringPreference FIRECLOUD_BILLING_PROJECT = new StringPreference("firecloud.billing.project",
            null, FIRECLOUD_GROUP, PreferenceValidators.isNotBlank);
    public static final StringPreference FIRECLOUD_INSTANCE_TYPE = new StringPreference("firecloud.instance.type",
            "m4.xlarge", FIRECLOUD_GROUP, pass);
    public static final IntPreference FIRECLOUD_INSTANCE_DISK = new IntPreference("firecloud.instance.disk",
            50, FIRECLOUD_GROUP, isGreaterThan(0));

    // Misc
    public static final IntPreference MISC_MAX_TOOL_ICON_SIZE_KB = new IntPreference("misc.max.tool.icon.size.kb", 50,
                                                                                     MISC_GROUP, isGreaterThan(0));
    public static final StringPreference MISC_SYSTEM_EVENTS_CONFIRMATION_METADATA_KEY = new StringPreference(
            "system.events.confirmation.metadata.key", "confirmed_notifications", MISC_GROUP,
            PreferenceValidators.isNotBlank);

    // Search
    public static final StringPreference SEARCH_ELASTIC_SCHEME = new StringPreference("search.elastic.scheme",
            null, SEARCH_GROUP, pass);
    public static final StringPreference SEARCH_ELASTIC_HOST = new StringPreference("search.elastic.host",
            null, SEARCH_GROUP, pass);
    public static final IntPreference SEARCH_ELASTIC_PORT = new IntPreference("search.elastic.port",
            null, SEARCH_GROUP, pass);
    public static final StringPreference SEARCH_ELASTIC_CP_INDEX_PREFIX = new StringPreference(
            "search.elastic.index.common.prefix", null, SEARCH_GROUP, pass);
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


    //GCP
    public static final ObjectPreference<List<String>> GCP_REGION_LIST = new ObjectPreference<>(
            "gcp.regions.list", null, new TypeReference<List<String>>() {}, GCP_GROUP,
            isNullOrValidJson(new TypeReference<List<String>>() {}));
    public static final ObjectPreference<Map<String, GCPResourceMapping>> GCP_SKU_MAPPING = new ObjectPreference<>(
            "gcp.sku.mapping", null, new TypeReference<Map<String, GCPResourceMapping>>() {}, GCP_GROUP,
            isNullOrValidJson(new TypeReference<Map<String, GCPResourceMapping>>() {}));

    // Billing Reports
    public static final StringPreference BILLING_USER_NAME_ATTRIBUTE = new StringPreference(
            "billing.reports.user.name.attribute", null, BILLING_GROUP, pass);
    public static final BooleanPreference BILLING_REPORTS_ENABLED = new BooleanPreference(
            "billing.reports.enabled", true, BILLING_GROUP, pass);
    public static final BooleanPreference BILLING_REPORTS_ENABLED_ADMINS = new BooleanPreference(
            "billing.reports.enabled.admins", true, BILLING_GROUP, pass);


    private static final Pattern GIT_VERSION_PATTERN = Pattern.compile("(\\d)\\.(\\d)");

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
