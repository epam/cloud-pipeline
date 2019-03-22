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

package com.epam.pipeline.entity.pipeline;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents docker registry notification about registry events (push/pull)
 * Events can be presented in JSON as described here: https://docs.docker.com/registry/notifications/#events
 * */
@Getter
@Setter
public class DockerRegistryEvent {

    private String id;
    private String timestamp;
    private String action;
    private Target target;
    private Request request;
    private Source source;
    private Actor actor;

    @Getter
    @Setter
    public static class Target {
        private String mediaType;
        private int size;
        private int length;
        private String digest;
        private String repository;
        private String url;
        private String tag;

    }

    @Getter
    @Setter
    public static class Request {
        private String id;
        private String addr;
        private String host;
        private String method;
        private String useragent;
    }

    @Getter
    @Setter
    public static class Source {
        private String addr;
        private String instanceID;
    }

    @Getter
    @Setter
    public static class Actor {
        private String name;
    }
}


