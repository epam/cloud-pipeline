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

package com.epam.pipeline.manager.execution;

import java.util.HashSet;
import java.util.Set;

public enum SystemParams {

    API("api", "API"),
    API_EXTERNAL("api-external", "API_EXTERNAL"),
    DISTRIBUTION_URL("distribution-url", "DISTRIBUTION_URL"),
    GLOBAL_DISTRIBUTION_URL("global-distribution-url", "GLOBAL_DISTRIBUTION_URL", false),
    PIPELINE_VERSION("pipeline-version", "PIPELINE_VERSION"),
    NAMESPACE("namespace", "NAMESPACE"),
    PARENT("parent", "PARENT"),
    PIPELINE_NAME("pipeline-name", "PIPELINE_NAME"),
    RUN_DATE("run-date", "RUN_DATE"),
    RUN_TIME("run-time", "RUN_TIME"),
    RUN_ID("run-id", "RUN_ID"),
    PIPELINE_ID("pipeline-id", "PIPELINE_ID"),
    AUTOSCALING_ENABLED("autoscaling-enabled", "AUTOSCALING_ENABLED"),
    CLOUD_PROVIDER("cloud-provider", "CLOUD_PROVIDER", false),
    CLOUD_REGION("cloud-region", "CLOUD_REGION", false),
    CLOUD_REGION_ID("cloud-region-id", "CLOUD_REGION_ID", false),
    //AWS SPECIFIC
    AWS_ACCESS_KEY_ID("aws-access-key-id", "AWS_ACCESS_KEY_ID", true),
    AWS_SECRET_ACCESS_KEY("aws-secret-access-key", "AWS_SECRET_ACCESS_KEY", true),
    AWS_DEFAULT_REGION("aws-default-region", "AWS_DEFAULT_REGION", true),
    AWS_REGION("aws-region", "AWS_REGION", true),
    API_TOKEN("api-token", "API_TOKEN", false, true),
    CLUSTER_NAME("cluster-name", "CLUSTER_NAME", true),
    BUCKETS("buckets", "BUCKETS", true),
    MOUNT_OPTIONS("mount-options", "MOUNT_OPTIONS", true),
    MOUNT_POINTS("mount-points", "MOUNT_POINTS", true),
    OWNER("owner", "OWNER", true),
    SSH_PASS("ssh-pass", "SSH_PASS", true),
    GIT_USER("git-user", "GIT_USER", true),
    GIT_TOKEN("git-token", "GIT_TOKEN", true),
    RUN_ON_PARENT_NODE("run-on-parent", "RUN_ON_PARENT_NODE"),
    SECURE_ENV_VARS("secure-env-vars", "SECURE_ENV_VARS", true),
    RUN_CONFIG_NAME("run-config-name", "RUN_CONFIG_NAME", false),
    ALLOWED_USERS("allowed-users", "ALLOWED_USERS"),
    ALLOWED_GROUPS("allowed-groups", "ALLOWED_GROUPS"),
    GS_OAUTH_REFRESH_TOKEN("google-refresh-token", "GS_OAUTH_REFRESH_TOKEN", true),
    GS_OAUTH_CLIENT_ID("google-client-id", "GS_CLIENT_ID", true),
    GS_OAUTH_CLIENT_SECRET("google-client-secret", "GS_CLIENT_SECRET", true),
    PARENT_ID("parent-id", "parent_id", false),
    RESUMED_RUN("resumed-run", "RESUMED_RUN", false, true),
    FSBROWSER_ENABLED("cp-fsbrowser-enabled", "CP_FSBROWSER_ENABLED", false, true),
    FSBROWSER_PORT("cp-fsbrowser-port", "CP_FSBROWSER_PORT", false, true),
    FSBROWSER_WD("cp-fsbrowser-wd", "CP_FSBROWSER_WD", false, true),
    FSBROWSER_TMP("cp-fsbrowser-tmp", "CP_FSBROWSER_TMP", false, true),
    FSBROWSER_STORAGE("cp-fsbrowser-storage", "CP_FSBROWSER_STORAGE", false, true),
    FSBROWSER_BLACK_LIST("cp-fsbrowser-black-list", "CP_FSBROWSER_BLACK_LIST", false, true),
    CONTAINER_CPU_RESOURCE("container-cpu-resource", "CP_CONTAINER_CPU_RESOURCE", false, true),
    CONTAINER_MEMORY_RESOURCE_POLICY("container-memory-resource-policy",
            "CP_CONTAINER_MEMORY_RESOURCE_POLICY", false, true),
    CP_SENSITIVE_RUN("cp-sensitive-run", "CP_SENSITIVE_RUN", false, false),
    CP_EXEC_TIMEOUT("cp-exec-timeout", "CP_EXEC_TIMEOUT");

    public static final String CLOUD_REGION_PREFIX = "CP_ACCOUNT_REGION_";
    public static final String CLOUD_ACCOUNT_PREFIX = "CP_ACCOUNT_ID_";
    public static final String CLOUD_ACCOUNT_KEY_PREFIX = "CP_ACCOUNT_KEY_";
    public static final String CLOUD_ACCOUNT_TOKEN_PREFIX = "CP_ACCOUNT_TOKEN_";
    public static final String CLOUD_PROVIDER_PREFIX = "CP_CLOUD_PROVIDER_";
    public static final String CLOUD_CREDENTIALS_FILE_CONTENT_PREFIX = "CP_CREDENTIALS_FILE_CONTENT_";

    public static final Set<String> SECURED_PREFIXES = new HashSet<>();

    static {
        SECURED_PREFIXES.add(CLOUD_ACCOUNT_PREFIX);
        SECURED_PREFIXES.add(CLOUD_ACCOUNT_KEY_PREFIX);
        SECURED_PREFIXES.add(CLOUD_ACCOUNT_TOKEN_PREFIX);
        SECURED_PREFIXES.add(CLOUD_CREDENTIALS_FILE_CONTENT_PREFIX);
    }

    private String optionName;
    private String envName;
    // secure params are not passed as global ENV vars and not added to console params
    private boolean secure;
    // hidden params and not added to console params
    private boolean hidden;

    SystemParams(String optionName, String envName) {
        this(optionName, envName, false);
    }

    SystemParams(String optionName, String envName, boolean secure) {
        this(optionName, envName, secure, false);
    }

    SystemParams(String optionName, String envName, boolean secure, boolean hidden) {
        this.optionName = optionName;
        this.envName = envName;
        this.secure = secure;
        this.hidden = hidden;
    }



    public String getOptionName() {
        return optionName;
    }

    public String getEnvName() {
        return envName;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean isHidden() {
        return hidden;
    }
}
