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

package com.epam.pipeline.manager.pipeline;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface ToolUtils {
    Pattern REPOSITORY_AND_IMAGE = Pattern.compile("^(.*)\\/(.*\\/.*)$");
    String TAG_DELIMITER = ":";

    static String getImageWithoutTag(final String imageWithTag) {
        return imageWithTag.split(TAG_DELIMITER)[0];
    }

    static String getImageTag(final String imageWithTag) {
        return imageWithTag.split(TAG_DELIMITER)[1];
    }

    static Optional<String> getImageWithoutRepository(final String image) {
        return Optional.ofNullable(image)
                .filter(StringUtils::isNotBlank)
                .map(REPOSITORY_AND_IMAGE::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(2));
    }
}