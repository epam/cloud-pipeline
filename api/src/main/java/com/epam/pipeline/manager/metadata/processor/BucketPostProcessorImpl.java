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

package com.epam.pipeline.manager.metadata.processor;

import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

@Component
@Slf4j
public class BucketPostProcessorImpl implements MetadataPostProcessor {

    private static final String BUCKET_TYPE = "bucket";
    private static final  String[] SCHEMES = {"s3://", "cp://"};
    private static final String SCHEME_DELIMITER = "://";

    @Override
    public void process(String name, PipeConfValue parameter) {
        String value = parameter.getValue();
        if (StringUtils.hasText(value)) {
            String possibleUrl = value.toLowerCase();
            if (Arrays.stream(SCHEMES).anyMatch(possibleUrl::startsWith)) {
                replacePathValue(parameter, possibleUrl);
            } else {
                parameter.setValue(S3bucketDataStorage.normalizeBucketName(value));
            }
        }
    }

    @Override
    public String supportedType() {
        return BUCKET_TYPE;
    }

    private void replacePathValue(PipeConfValue parameter, String possibleUrl) {
        try {
            URI uri = new URI(possibleUrl);
            String bucketName = S3bucketDataStorage.normalizeBucketName(uri.getAuthority());
            parameter.setValue(uri.getScheme() + SCHEME_DELIMITER + bucketName + uri.getPath());
        } catch (URISyntaxException e) {
            log.debug(e.getMessage(), e);
        }
    }
}
