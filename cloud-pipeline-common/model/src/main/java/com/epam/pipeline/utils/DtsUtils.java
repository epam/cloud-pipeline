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

package com.epam.pipeline.utils;

import com.epam.pipeline.config.Constants;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

public interface DtsUtils {

    Logger log = LoggerFactory.getLogger(DtsUtils.class);

    static LocalDateTime now() {
        return LocalDateTime.now(Clock.systemUTC());
    }

    static Date currentDate() {
        return toDate(now());
    }

    static Date toDate(LocalDateTime time) {
        return Date.from(time.toInstant(ZoneOffset.UTC));
    }

    static <T> List<T> iterableToList(Iterable<T> iterable) {
        ArrayList<T> result = new ArrayList<>();
        iterable.forEach(result::add);
        return result;
    }

    static boolean fileExists(Path path) {
        return path.toFile().exists();
    }

    static void checkLocalPathReadability(String path) {
        Assert.isTrue(StringUtils.isNotBlank(path), "Path must be specified.");
        Assert.isTrue(Paths.get(path).toFile().exists(), String.format("Specified path %s does not exists.", path));
        Assert.isTrue(Paths.get(path).toFile().canRead(), String.format("Cannot read path %s.", path));
    }

    static boolean isLocalPathReadable(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        File file = Paths.get(path).toFile();
        return file.exists() && file.canRead();
    }

    static Pair<String, String> getBucketNameAndKey(String path) {
        URI pathURI;
        try {
            pathURI = new URI(path);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        String bucketName = pathURI.getHost();
        String key = StringUtils.strip(pathURI.getPath(), Constants.PATH_DELIMITER);
        return new ImmutablePair<>(bucketName, key);
    }

    static String expandParameterWithEnvVars(String paramValue, Map<String, String> envVars) {
        if (StringUtils.isBlank(paramValue)) {
            return paramValue;
        }
        String expanded = paramValue;
        for (Map.Entry<String, String> env : envVars.entrySet()) {
            Pattern pattern = Pattern.compile(String.format("[$]%s[^a-zA-Z0-9_]", env.getKey()));
            expanded = replaceEnvVarInParameter(pattern, env.getKey(), env.getValue(), expanded);
        }
        return expanded;
    }

    static String replaceEnvVarInParameter(Pattern pattern, String envVarName,
                                           String envVarValue, String parameter) {
        if (StringUtils.isBlank(parameter) || StringUtils.isBlank(envVarName) || StringUtils.isBlank(envVarValue)) {
            return parameter;
        }
        try {
            String resolvedParameter = parameter;
            Matcher matcher = pattern.matcher(resolvedParameter);
            while (matcher.find()) {
                char lastSymbol = matcher.group().toCharArray()[matcher.group().length() - 1];
                resolvedParameter = matcher.replaceAll(envVarValue + lastSymbol);
            }
            return resolvedParameter.replaceAll(String.format("[$]%s$|[$][{]%s[}]",
                    envVarName, envVarName), envVarValue);
        } catch (IllegalArgumentException e) {
            //TODO : why do we need this?
            log.trace(e.getMessage(), e);
            return parameter;
        }
    }

    static String replaceParametersInTemplate(String template, Map<String, String> parameters) {
        return new StringSubstitutor(parameters)
                .setVariablePrefix("$[")
                .setVariableSuffix("]")
                .replace(template);
    }

}
