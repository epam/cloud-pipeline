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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component public class CommandBuilder {

    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\[(.*?)]");

    private static final String PARAMETER_PLACEHOLDER = "[%s]";
    private static final String PARAMETER_BASH_PLACEHOLDER = "$%s";
    private static final String PARAMETER_TEMPLATE = "--%s %s";
    private static final String DASH = "-";
    private static final String UNDERSCORE = "_";
    private static final String EMPTY = "";

    @Autowired
    private MessageHelper messageHelper;

    private enum ParamsGroup {
        USER_PARAMS("[user-params]"), SYS_PARAMS("[sys-params]");

        private String groupName;

        ParamsGroup(String groupName) {
            this.groupName = groupName;
        }

        public String getGroupName() {
            return groupName;
        }
    }

    public String build(PipelineConfiguration configuration, Map<SystemParams, String> sysParams) {
        String cmdTemplate = getTemplateWithFiledPipeConfiguration(configuration);

        Map<String, String> matchedSysParams = new LinkedHashMap<>();
        sysParams
            .entrySet()
            .stream()
            .filter(systemParam -> !systemParam.getKey().isSecure() && !systemParam.getKey().isHidden())
            .forEach(systemParam ->
                matchedSysParams.put(systemParam.getKey().getOptionName(), systemParam.getValue())
            );

        cmdTemplate = replaceParametersByValue(
                ParamsGroup.SYS_PARAMS, matchedSysParams, cmdTemplate
        );

        Map<String, String> matchedUserParams = new LinkedHashMap<>();
        configuration.getParameters()
                .forEach((key, value) -> matchedUserParams.put(key, value.getValue()));

        cmdTemplate = replaceParametersByBashPlaceholder(
                ParamsGroup.USER_PARAMS, matchedUserParams, cmdTemplate
        );
        checkForUnfilledParams(cmdTemplate);
        return cmdTemplate;
    }

    private String getTemplateWithFiledPipeConfiguration(PipelineConfiguration configuration) {
        String filledTemplate = configuration.getCmdTemplate();
        for (Entry<String, String> param : configuration.getEnvironmentParams().entrySet()) {
            filledTemplate = replaceParameterIfRequired(
                    filledTemplate, param.getValue(),
                    String.format(PARAMETER_PLACEHOLDER, param.getKey()));
        }

        return filledTemplate;
    }

    private String replaceParameterIfRequired(String template, String value,
            String placeholder) {
        String filledTemplate = template;
        if (!StringUtils.isEmpty(value) && template.contains(placeholder)) {
            filledTemplate = template.replace(placeholder, value);
        }
        return filledTemplate;
    }

    private String replaceParametersByValue(ParamsGroup group, Map<String, String> params,
                                                      String cmdTemplate) {
        if (cmdTemplate.contains(group.getGroupName())) {
            cmdTemplate = replaceGroupByValues(group, params, cmdTemplate);
        }
        cmdTemplate = replaceParametersByValues(params, cmdTemplate);
        return cmdTemplate;
    }

    private String replaceGroupByValues(ParamsGroup group, Map<String, String> params, String cmdTemplate) {
        String parametersString = params.entrySet()
                .stream()
                .map(param -> String.format(PARAMETER_TEMPLATE, param.getKey(), param.getValue()))
                .collect(Collectors.joining(" "));
        cmdTemplate = cmdTemplate.replace(group.getGroupName(), parametersString);
        return cmdTemplate;
    }

    private String replaceParametersByValues(Map<String, String> params, String cmdTemplate) {
        for (Entry<String, String> parameter : params.entrySet()) {
            if (parameter.getKey() == null || parameter.getValue() == null) {
                continue;
            }
            cmdTemplate = cmdTemplate.replace(String.format(PARAMETER_PLACEHOLDER, parameter.getKey()),
                    parameter.getValue());
        }
        return cmdTemplate;
    }

    private String replaceParametersByBashPlaceholder(ParamsGroup group, Map<String, String> params,
                                                      String cmdTemplate) {
        if (cmdTemplate.contains(group.getGroupName())) {
            cmdTemplate = replaceGroupByBashPlaceholders(group, params, cmdTemplate);
        }
        cmdTemplate = replaceParametersByValues(params, cmdTemplate);
        return cmdTemplate;
    }

    private String replaceGroupByBashPlaceholders(ParamsGroup group, Map<String, String> params, String cmdTemplate) {
        String parametersString = params.entrySet()
                .stream()
                .map(param -> {
                    String bashPlaceholder = getBashPlaceholder(param.getKey(), param.getValue().isEmpty());
                    return String.format(PARAMETER_TEMPLATE, param.getKey(), bashPlaceholder);
                })
                .collect(Collectors.joining(" "));
        cmdTemplate = cmdTemplate.replace(group.getGroupName(), parametersString);
        return cmdTemplate;
    }

    private String getBashPlaceholder(String param, boolean isFlag) {
        // will return bash placeholder ($<paramName>), or empty string if the parameter is flag
        return isFlag ? EMPTY : String.format(PARAMETER_BASH_PLACEHOLDER, param)
                .replace(DASH, UNDERSCORE);
    }

    private void checkForUnfilledParams(String cmdTemplate) {
        Matcher matcher = PARAMS_PATTERN.matcher(cmdTemplate);
        Set<String> unfilled = new HashSet<>();
        if (!matcher.find()) {
            return;
        }
        for (int i = 0; i < matcher.groupCount(); i++) {
            unfilled.add(matcher.group(i));
        }
        if (!unfilled.isEmpty()) {
            throw new IllegalArgumentException(
                    messageHelper.getMessage(MessageConstants.ERROR_PARAMETER_REQUIRED, unfilled.toArray())
            );
        }
    }

}
