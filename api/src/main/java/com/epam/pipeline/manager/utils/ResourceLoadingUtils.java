/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.utils;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.springframework.util.ResourceUtils;

import java.io.FileReader;
import java.io.InputStream;

public final class ResourceLoadingUtils {

    private ResourceLoadingUtils() {
        //no-op
    }

    @SneakyThrows
    public static String readResource(final String path) {
        if (path.startsWith(ResourceUtils.CLASSPATH_URL_PREFIX)) {
            try (InputStream classPathResource = ResourceLoadingUtils.class
                    .getResourceAsStream(path.substring(ResourceUtils.CLASSPATH_URL_PREFIX.length()))) {
                return IOUtils.toString(classPathResource);
            }
        } else {
            return IOUtils.toString(new FileReader(ResourceUtils.getFile(path)));
        }
    }
}
