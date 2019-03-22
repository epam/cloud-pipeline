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

package com.epam.pipeline.dts.submission.service.execution.impl;

import com.epam.pipeline.dts.security.service.JwtTokenVerifier;
import com.epam.pipeline.dts.submission.model.execution.RunParameter;
import com.epam.pipeline.dts.submission.model.execution.Submission;
import com.epam.pipeline.dts.submission.model.execution.SubmissionTemplate;
import com.epam.pipeline.dts.submission.service.execution.SubmissionConverter;
import com.epam.pipeline.dts.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class SubmissionConverterImpl implements SubmissionConverter {

    private static final String ANALYSIS_DIR = "ANALYSIS_DIR";
    public static final String CP_CAP = "CP_CAP";
    private static final String PARAM_TYPE_SUFFIX = "_PARAM_TYPE";
    private static final String S3_SCHEME = "s3://";
    private static final String CP_SCEME = "cp://";

    private Set<String> outputTypes = Collections.singleton(RunParameter.OUTPUT_TYPE);
    private Set<String> inputTypes = new HashSet<>(
            Arrays.asList(RunParameter.INPUT_TYPE, RunParameter.COMMON_TYPE));

    private final JwtTokenVerifier jwtTokenVerifier;

    @Override
    public SubmissionTemplate convertToTemplate(final Submission submission) {
        return SubmissionTemplate.builder()
                .api(submission.getApi())
                .token(submission.getToken())
                .dockerImage(submission.getDockerImage())
                .command(submission.getCommand())
                .parameters(getParameters(submission.getParameters()))
                .username(parseNameFromToken(submission.getToken()))
                .dockerHost(parseHostFromDockerImage(submission.getDockerImage()))
                .inputs(getInputs(submission.getParameters()))
                .outputs(filterParameters(submission.getParameters(), outputTypes))
                .build();
    }


    @Override
    public void validate(Submission submission) {
        Assert.isTrue(submission.getRunId() != null, "RunId is required");
        Assert.isTrue(StringUtils.isNotBlank(submission.getRunName()), "Run name is required");
        Assert.isTrue(StringUtils.isNotBlank(submission.getCommand()), "Command is required");
        Assert.isTrue(StringUtils.isNotBlank(submission.getApi()), "API is required");
        Assert.isTrue(StringUtils.isNotBlank(submission.getToken()), "Token is required");
        Assert.isTrue(StringUtils.isNotBlank(submission.getDockerImage()), "DockerImage is required");
        Assert.isTrue(StringUtils.isNotBlank(parseNameFromToken(submission.getToken())),
                "Invalid token");
        Assert.isTrue(StringUtils.isNotBlank(parseHostFromDockerImage(submission.getDockerImage())),
                "Invalid docker image");
    }

    private List<String> getInputs(List<RunParameter> parameters) {
        Map<String, String> envVars = ListUtils.emptyIfNull(parameters)
                .stream()
                .collect(Collectors.toMap(RunParameter::getName, RunParameter::getValue, (p1, p2) -> p2));
        return ListUtils.emptyIfNull(parameters)
                .stream()
                .filter(p -> inputTypes.contains(p.getType()) && !isCloudPath(p))
                .filter(p -> Utils.isLocalPathReadable(
                        Utils.expandParameterWithEnvVars(p.getValue(), envVars)))
                .map(RunParameter::getValue)
                .collect(Collectors.toList());
    }

    private List<RunParameter> getParameters(List<RunParameter> parameters) {
        List<RunParameter> result = ListUtils.emptyIfNull(parameters)
                .stream()
                .filter(p -> !p.getName().startsWith(CP_CAP))
                .collect(Collectors.toList());

        // If local outputs are present use first of them as ANALYSIS_DIR
        ListUtils.emptyIfNull(result).stream()
                .filter(p -> outputTypes.contains(p.getType()) && !isCloudPath(p))
                .findFirst()
                .ifPresent(param -> result.add(RunParameter.builder()
                        .name(ANALYSIS_DIR)
                        .value(param.getValue())
                        .build()));

        List<RunParameter> typeParameters = ListUtils.emptyIfNull(parameters)
                .stream()
                .filter(p -> outputTypes.contains(p.getType()) || inputTypes.contains(p.getType()))
                .filter(this::isCloudPath)
                .map(this::getAdditionalTypeParameter)
                .collect(Collectors.toList());
        result.addAll(typeParameters);
        return result;
    }

    private RunParameter getAdditionalTypeParameter(RunParameter parameter) {
        return RunParameter.builder()
                .name(parameter.getName() + PARAM_TYPE_SUFFIX)
                .value(parameter.getType())
                .build();
    }

    private boolean isCloudPath(RunParameter p) {
        if (StringUtils.isBlank(p.getValue())) {
            return false;
        }
        return p.getValue().startsWith(CP_SCEME) || p.getValue().startsWith(S3_SCHEME);
    }

    private List<String> filterParameters(final List<RunParameter> parameters,
                                          final Set<String> types) {
        return ListUtils.emptyIfNull(parameters).stream()
                .filter(p -> types.contains(p.getType()) && !isCloudPath(p))
                .map(RunParameter::getValue)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private String parseHostFromDockerImage(final String dockerImage) {
        final String[] parts = dockerImage.split("/");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    String.format("Invalid docker images format: %s", dockerImage));
        }
        return parts[0];
    }

    private String parseNameFromToken(final String token) {
        return jwtTokenVerifier.readClaims(token).getUserName();
    }
}
