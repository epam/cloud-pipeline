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

package com.epam.pipeline.entity.utils;

import com.epam.pipeline.entity.configuration.PipeConfValueVO;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class DefaultSystemParameter {

    private String name;
    private String type;
    private String description;
    private String defaultValue;
    private boolean passToWorkers;
    private boolean prefix;
    private Set<String> roles;

    public DefaultSystemParameter() {
        this.type = PipeConfValueVO.DEFAULT_TYPE;
        this.passToWorkers = false;
        this.prefix = false;
    }
}
