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

package com.epam.pipeline.manager.cloud.commands;

import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractClusterCommand {

    public static final String EXECUTABLE = "python";
    protected static final String RUN_ID_PARAMETER = "--run_id";
    protected static final String REGION_PARAMETER = "--region_id";
    protected static final String INTERNAL_IP_PARAMETER = "--internal_ip";
    protected static final String NODE_NAME_PARAMETER = "--node_name";
    private static final String ARG_DELIMITER = " ";

    public String getCommand() {
        return buildCommandArguments().stream()
                .collect(Collectors.joining(ARG_DELIMITER));
    }

    protected abstract List<String> buildCommandArguments();
}
