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

package com.epam.pipeline.manager.execution;

import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Build environment variables for kubernetes node from {@link SystemParams} and {@link PipelineConfiguration}
 */
public final class EnvVarsBuilder {

    private static final String DASH = "-";
    private static final String UNDERSCORE = "_";

    private static final String PARAM_TYPE_POSTFIX = "_PARAM_TYPE";

    private EnvVarsBuilder() {
    }

    public static List<EnvVar> buildEnvVars(PipelineRun run, PipelineConfiguration configuration,
                                            Map<SystemParams, String> sysParams,
                                            Map<String, String> externalProperties) {
        Map<String, EnvVar> fullEnvVars = new HashMap<>();
        Map<String, String> envVarsMap = new HashMap<>();

        MapUtils.emptyIfNull(sysParams)
                .forEach((key, value) -> {
                    String name = key.getEnvName();
                    if (!key.isSecure()) {
                        envVarsMap.put(name, value);
                    }
                    fullEnvVars.put(name, new EnvVar(name, value, null));
                });

        MapUtils.emptyIfNull(externalProperties)
                .forEach((key, value) -> {
                    String name = getBashName(key);
                    envVarsMap.put(name, value);
                    fullEnvVars.put(name, new EnvVar(name, value, null));
                });

        MapUtils.emptyIfNull(configuration.getParameters())
                .entrySet()
                .stream()
                .map(parameter -> {
                    String name = parameter.getKey();
                    String value = parameter.getValue().getValue();
                    String type = parameter.getValue().getType();
                    return matchParameterToEnvVars(name, value, type, envVarsMap);
                })
                .flatMap(Arrays::stream)
                .forEach(e -> fullEnvVars.put(e.getName(), e));

        MapUtils.emptyIfNull(configuration.getEnvironmentParams())
                .forEach((name, value) -> {
                    envVarsMap.put(name, value);
                    fullEnvVars.put(name, new EnvVar(name, value, null));
                });

        run.setEnvVars(MapUtils.emptyIfNull(envVarsMap)
                .entrySet()
                .stream()
                .filter(e -> StringUtils.isNotBlank(e.getKey()))
                .filter(e -> SystemParams.SECURED_PREFIXES.stream().noneMatch(prefix -> e.getKey().startsWith(prefix)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2)));
        return new ArrayList<>(fullEnvVars.values());
    }

    private static EnvVar[] matchParameterToEnvVars(String name, String value, String type,
                                                    Map<String, String> envVarsMap) {
        String bashName = getBashName(name);
        envVarsMap.put(bashName, value);
        envVarsMap.put(bashName + PARAM_TYPE_POSTFIX, type);
        return new EnvVar[] {
            new EnvVar(bashName, value, null),
            new EnvVar(bashName + PARAM_TYPE_POSTFIX, type, null)
        };
    }

    private static String getBashName(String param) {
        return param.replace(DASH, UNDERSCORE);
    }
}
