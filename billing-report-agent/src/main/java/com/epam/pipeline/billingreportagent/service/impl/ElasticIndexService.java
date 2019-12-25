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

package com.epam.pipeline.billingreportagent.service.impl;

import com.epam.pipeline.billingreportagent.exception.ElasticClientException;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

@Service
@Slf4j
@RequiredArgsConstructor
public class ElasticIndexService {

    private final ElasticsearchServiceClient elasticsearchServiceClient;

    public void createIndexIfNotExists(final String indexName, final String settingsFilePath)
        throws ElasticClientException {
        try {
            if (!elasticsearchServiceClient.isIndexExists(indexName)) {
                final String mappingsJson = IOUtils.toString(openJsonMapping(settingsFilePath),
                                                             Charset.defaultCharset());
                if (elasticsearchServiceClient.isIndexExists(indexName)) {
                    log.debug("Index {} exists already!", indexName);
                } else {
                    elasticsearchServiceClient.createIndex(indexName, mappingsJson);
                }
            }
        } catch (IOException e) {
            throw new ElasticClientException("Failed to create elasticsearch index with name " + indexName, e);
        }
    }

    private InputStream openJsonMapping(final String path) throws FileNotFoundException {
        if (path.startsWith(ResourceUtils.CLASSPATH_URL_PREFIX)) {
            final InputStream classPathResource = getClass()
                .getResourceAsStream(path.substring(ResourceUtils.CLASSPATH_URL_PREFIX.length()));
            Assert.notNull(classPathResource, String.format("Failed to resolve path: %s", path));
            return classPathResource;
        }
        if (path.startsWith(ResourceUtils.FILE_URL_PREFIX)) {
            return new FileInputStream(path.substring(ResourceUtils.FILE_URL_PREFIX.length()));
        }
        throw new IllegalArgumentException("Unsupported mapping file: " + path);
    }
}
