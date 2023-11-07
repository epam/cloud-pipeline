/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Application properties
 */
public class C {

    public static final String CONF_PATH_PROPERTY = "com.epam.bfx.e2e.ui.property.path";
    static {
        String propFilePath = System.getProperty(CONF_PATH_PROPERTY, "default.conf");

        Properties conf = new Properties();
        try {
            conf.load(new FileInputStream(propFilePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        DEFAULT_TIMEOUT = Integer.parseInt(conf.getProperty("e2e.ui.default.timeout"));
        COMMIT_APPEARING_TIMEOUT = Integer.parseInt(conf.getProperty("e2e.ui.commit.appearing.timeout"));
        SSH_APPEARING_TIMEOUT = Integer.parseInt(conf.getProperty("e2e.ui.ssh.appearing.timeout"));
        COMMITTING_TIMEOUT = Integer.parseInt(conf.getProperty("e2e.ui.committing.timeout"));
        COMPLETION_TIMEOUT = Integer.parseInt(conf.getProperty("e2e.ui.run.completion.timeout"));
        BUCKETS_MOUNTING_TIMEOUT = Integer.parseInt(conf.getProperty("e2e.ui.buckets.mounting.timeout"));
        ENDPOINT_INITIALIZATION_TIMEOUT = Integer.parseInt(conf.getProperty("e2e.ui.endpoint.initialization.timeout"));
        SEARCH_TIMEOUT = Integer.parseInt(conf.getProperty("e2e.ui.search.timeout.in.minutes"));
        LOGIN_DELAY_TIMEOUT = Integer.parseInt(conf.getProperty("e2e.ui.login.delay.timeout"));
        SHARING_TIMEOUT = Integer.parseInt(conf.getProperty("e2e.ui.run.sharing.timeout.in.seconds"));
        VALID_ENDPOINT = conf.getProperty("e2e.ui.valid.endpoint");
        LOGIN = conf.getProperty("e2e.ui.login");
        PASSWORD = conf.getProperty("e2e.ui.password");
        ROOT_ADDRESS = conf.getProperty("e2e.ui.root.address");
        DOWNLOAD_FOLDER = conf.getProperty("e2e.ui.download.folder");
        ANOTHER_LOGIN = conf.getProperty("e2e.ui.another.login");
        ANOTHER_PASSWORD = conf.getProperty("e2e.ui.another.password");
        DEFAULT_REGISTRY = conf.getProperty("e2e.ui.default.registry");
        DEFAULT_REGISTRY_IP = conf.getProperty("e2e.ui.default.registry.ip");
        DEFAULT_GROUP = conf.getProperty("e2e.ui.default.group");
        CLEAN_HISTORY_LOGIN = conf.getProperty("e2e.ui.clean.history.login");
        CLEAN_HISTORY_PASSWORD = conf.getProperty("e2e.ui.clean.history.password");
        TESTING_TOOL_NAME = conf.getProperty("e2e.ui.testing.tool");
        TOOL_WITHOUT_DEFAULT_SETTINGS = conf.getProperty("e2e.ui.tool.without.default.settings");
        REGISTRY_LOGIN_FOR_TOOL = conf.getProperty("e2e.ui.registry.login");
        REGISTRY_PASSWORD_FOR_TOOL = conf.getProperty("e2e.ui.registry.password");
        REGISTRY_PATH_FOR_TOOL = conf.getProperty("e2e.ui.tool.registry.path");
        INVALID_REGISTRY_PATH_FOR_TOOL = conf.getProperty("e2e.ui.tool.invalid.registry.path");
        REPOSITORY = conf.getProperty("e2e.ui.repository");
        TOKEN = conf.getProperty("e2e.ui.token");
        LUIGI_IMAGE = conf.getProperty("e2e.ui.image.luigi");
        STORAGE_PREFIX = conf.getProperty("e2e.ui.storage.prefix");
        DEFAULT_INSTANCE = conf.getProperty("e2e.ui.default.instance.type");
        NFS_PREFIX = conf.getProperty("e2e.ui.nfs.prefix");
        DEFAULT_INSTANCE_PRICE_TYPE = conf.getProperty("e2e.ui.default.instance.price.type");
        CLOUD_PROVIDER = conf.getProperty("e2e.ui.cloud.provider");
        SPOT_PRICE_NAME = conf.getProperty("e2e.ui.spot.price.name");
        AUTH_TOKEN = conf.getProperty("e2e.ui.auth.token");
        STORAGE_NAME_PREFIX = conf.getProperty("e2e.ui.storage.name.prefix");
        SEARCH_PREFIX = conf.getProperty("e2e.ui.search.prefix");
        ANOTHER_INSTANCE = conf.getProperty("e2e.ui.another.instance.type");
        DEFAULT_INSTANCE_FAMILY_NAME = conf.getProperty("e2e.ui.default.instance.family.name");
        ANOTHER_TESTING_TOOL_NAME = conf.getProperty("e2e.ui.another.testing.tool");
        LDAP_SERVER_TEST_TOOL = conf.getProperty("e2e.ui.ldap.server.test.tool");
        ANOTHER_GROUP = conf.getProperty("e2e.ui.another.group");
        PLATFORM_NAME = conf.getProperty("e2e.ui.platform.name");
        ANONYMOUS_NAME = conf.getProperty("e2e.ui.anonymous.name");
        ANONYMOUS_TOKEN = conf.getProperty("e2e.ui.anonymous.token");
        ANOTHER_ADMIN_TOKEN = conf.getProperty("e2e.ui.another.admin.token");
        DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES = conf.getProperty("e2e.ui.default.cluster.allowed.instance.types");
        DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER = conf.getProperty(
                "e2e.ui.default.cluster.allowed.instance.types.docker");
        CLUSTER_ALLOWED_MASKS = conf.getProperty("e2e.ui.cluster.allowed.masks");
        DEFAULT_CLUSTER_ALLOWED_PRICE_TYPES = conf.getProperty("e2e.ui.default.cluster.allowed.price.types");
        TEST_DOCKER_IMAGE = conf.getProperty("e2e.ui.test.docker.image");
        ANOTHER_CLOUD_REGION = conf.getProperty("e2e.ui.another.cloud.region");
        SYNC_STORAGE_NAME = conf.getProperty("e2e.ui.sync.storage.name");
        SYNC_STORAGE_TIMEOUT = Integer.parseInt(conf.getProperty("e2e.ui.sync.storage.timeout.in.seconds"));
        SYNC_STORAGE_PERMISSION_NAME = conf.getProperty("e2e.ui.sync.storage.permission.name");
        SYNC_STORAGE_PERMISSIONS = conf.getProperty("e2e.ui.sync.storage.permissions");
        ROLE_USER = conf.getProperty("e2e.ui.role.user");
        SUPPORT_CONTENT = conf.getProperty("e2e.ui.help.content");
        LUSTRE_MOUNT_OPTIONS = conf.getProperty("e2e.ui.lustre.fs.mount.options");
        LAUNCH_SYSTEM_PARAMETERS_CONFIG_PATH = conf.getProperty("e2e.ui.launch.system.parameters.path");
        LAUNCH_CONTAINER_CPU_RESOURCES_VALUE = conf.getProperty("e2e.ui.launch.container.cpu.resource");
        PIPE_OPERATION_SYSTEM = conf.getProperty("e2e.ui.pipe.operation.system");
        PIPE_INSTALLATION_CONTENT = conf.getProperty("e2e.ui.pipe.installation.content");
        PIPE_CONFIG_CONTENT_PATH = conf.getProperty("e2e.ui.pipe.config.content.path");
        IMPERSONATE_AUTH = conf.getProperty("e2e.ui.impersonate.auth");
        EXTENSION_PATH = conf.getProperty("e2e.ui.extension.path");
        INVALID_EXTENSION_PATH = conf.getProperty("e2e.ui.invalid.extension.path");
        ANONYM_EXTENSION_PATH = conf.getProperty("e2e.ui.anonym.extension.path");
        ADMIN_TOKEN_IS_SERVICE = conf.getProperty("e2e.ui.login.isservice");
        WEBDAV_ADDRESS = conf.getProperty("e2e.ui.webdav.address");
        NAT_PROXY_SERVICE_PREFIX = conf.getProperty("e2e.ui.nat.proxy.service.prefix");
        NAT_PROXY_SERVER_NAMES = Arrays.asList(conf.getProperty("e2e.ui.nat.proxy.service.names")
                .split("\\s*,\\s*"));
        LDAP_URLS = conf.getProperty("e2e.ui.ldap.server.urls");
        LDAP_BASE_PATH = conf.getProperty("e2e.ui.ldap.server.base.path");
        LDAP_USERNAME = conf.getProperty("e2e.ui.ldap.server.username");
        LDAP_PASSWORD = conf.getProperty("e2e.ui.ldap.server.password");
        SYSTEM_MONITOR_DELAY = conf.getProperty("e2e.ui.system.monitor.delay");

        BACKUP_STORAGE_NAMES = Arrays.asList(conf.getProperty("e2e.ui.backup.storage.names")
                .split(","));
        BACKUP_STORAGE_PATH = conf.getProperty("e2e.ui.backup.storage.path");
        BACKUP_STORAGE_OFFSET = Integer.parseInt(conf.getProperty("e2e.ui.backup.storage.offset"));
        DEFAULT_CLUSTER_AWS_EBS_TYPE = conf.getProperty("e2e.ui.cluster.aws.ebs.type");
        TEST_RUN_TAG = conf.getProperty("e2e.ui.test.run.tag");
        DEFAULT_INSTANCE_PRICE_TYPE_TOOL = conf.getProperty("e2e.ui.default.instance.price.type.tool");
    }

    public static final int DEFAULT_TIMEOUT;
    public static final int COMMIT_APPEARING_TIMEOUT;
    public static final int SSH_APPEARING_TIMEOUT;
    public static final int COMMITTING_TIMEOUT;
    public static final int COMPLETION_TIMEOUT;
    public static final int BUCKETS_MOUNTING_TIMEOUT;
    public static final int ENDPOINT_INITIALIZATION_TIMEOUT;
    public static final int LOGIN_DELAY_TIMEOUT;
    public static final int SEARCH_TIMEOUT;
    public static final int SHARING_TIMEOUT;

    public static final String LOGIN;
    public static final String PASSWORD;
    public static final String DOWNLOAD_FOLDER;

    public static final String ROOT_ADDRESS;
    public static final String WEBDAV_ADDRESS;

    public static final String ANOTHER_LOGIN;
    public static final String ANOTHER_PASSWORD;
    public static final String DEFAULT_REGISTRY;
    public static final String DEFAULT_REGISTRY_IP;
    public static final String DEFAULT_GROUP;
    public static final String ANOTHER_GROUP;
    public static final String ROLE_USER;

    public static final String CLEAN_HISTORY_LOGIN;
    public static final String CLEAN_HISTORY_PASSWORD;

    public static final String TESTING_TOOL_NAME;
    public static final String TOOL_WITHOUT_DEFAULT_SETTINGS;
    public static final String ANOTHER_TESTING_TOOL_NAME;
    public static final String LDAP_SERVER_TEST_TOOL;

    public static final String REGISTRY_PATH_FOR_TOOL;
    public static final String INVALID_REGISTRY_PATH_FOR_TOOL;
    public static final String REGISTRY_LOGIN_FOR_TOOL;
    public static final String REGISTRY_PASSWORD_FOR_TOOL;

    public static final String REPOSITORY;
    public static final String TOKEN;

    public static final String VALID_ENDPOINT;

    public static final String LUIGI_IMAGE;
    public static final String STORAGE_PREFIX;
    public static final String NFS_PREFIX;
    public static final String TEST_DOCKER_IMAGE;

    public static final String DEFAULT_INSTANCE;
    public static final String DEFAULT_INSTANCE_PRICE_TYPE;
    public static final String DEFAULT_INSTANCE_PRICE_TYPE_TOOL;
    public static final String CLOUD_PROVIDER;
    public static final String ANOTHER_INSTANCE;
    public static final String DEFAULT_INSTANCE_FAMILY_NAME;
    public static final String DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES;
    public static final String DEFAULT_CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER;
    public static final String CLUSTER_ALLOWED_MASKS;
    public static final String DEFAULT_CLUSTER_ALLOWED_PRICE_TYPES;
    public static final String DEFAULT_CLUSTER_AWS_EBS_TYPE;

    public static final String SPOT_PRICE_NAME;
    public static final String AUTH_TOKEN;
    public static final String STORAGE_NAME_PREFIX;
    public static final String SEARCH_PREFIX;
    public static final String PLATFORM_NAME;
    public static final String ANOTHER_CLOUD_REGION;

    public static final String ANONYMOUS_NAME;
    public static final String ANONYMOUS_TOKEN;
    public static final String ANOTHER_ADMIN_TOKEN;

    public static final String SYNC_STORAGE_NAME;
    public static final int SYNC_STORAGE_TIMEOUT;
    public static final String SYNC_STORAGE_PERMISSION_NAME;
    public static final String SYNC_STORAGE_PERMISSIONS;

    public static final String SUPPORT_CONTENT;
    public static final String LUSTRE_MOUNT_OPTIONS;
    public static final String LAUNCH_SYSTEM_PARAMETERS_CONFIG_PATH;
    public static final String LAUNCH_CONTAINER_CPU_RESOURCES_VALUE;
    public static final String PIPE_OPERATION_SYSTEM;
    public static final String PIPE_INSTALLATION_CONTENT;
    public static final String PIPE_CONFIG_CONTENT_PATH;

    public static final String IMPERSONATE_AUTH;
    public static final String EXTENSION_PATH;
    public static final String INVALID_EXTENSION_PATH;
    public static final String ANONYM_EXTENSION_PATH;
    public static final String ADMIN_TOKEN_IS_SERVICE;

    public static final String NAT_PROXY_SERVICE_PREFIX;
    public static final List<String> NAT_PROXY_SERVER_NAMES;

    public static final String LDAP_URLS;
    public static final String LDAP_BASE_PATH;
    public static final String LDAP_USERNAME;
    public static final String LDAP_PASSWORD;
    public static final String SYSTEM_MONITOR_DELAY;

    public static final List<String> BACKUP_STORAGE_NAMES;
    public static final String BACKUP_STORAGE_PATH;
    public static final int BACKUP_STORAGE_OFFSET;
    public static final String TEST_RUN_TAG;
}
