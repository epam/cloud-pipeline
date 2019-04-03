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
package com.epam.pipeline.autotests.utils;

import java.io.FileInputStream;
import java.io.IOException;
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
    }

    public static final int DEFAULT_TIMEOUT;
    public static final int COMMIT_APPEARING_TIMEOUT;
    public static final int SSH_APPEARING_TIMEOUT;
    public static final int COMMITTING_TIMEOUT;
    public static final int COMPLETION_TIMEOUT;
    public static final int BUCKETS_MOUNTING_TIMEOUT;

    public static final String LOGIN;
    public static final String PASSWORD;
    public static final String DOWNLOAD_FOLDER;

    public static final String ROOT_ADDRESS;

    public static final String ANOTHER_LOGIN;
    public static final String ANOTHER_PASSWORD;
    public static final String DEFAULT_REGISTRY;
    public static final String DEFAULT_REGISTRY_IP;
    public static final String DEFAULT_GROUP;

    public static final String CLEAN_HISTORY_LOGIN;
    public static final String CLEAN_HISTORY_PASSWORD;

    public static final String TESTING_TOOL_NAME;
    public static final String TOOL_WITHOUT_DEFAULT_SETTINGS;

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

    public static final String DEFAULT_INSTANCE;
    public static final String DEFAULT_INSTANCE_PRICE_TYPE;
    public static final String CLOUD_PROVIDER;
}
