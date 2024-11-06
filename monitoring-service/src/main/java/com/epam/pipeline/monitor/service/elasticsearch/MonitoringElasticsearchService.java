/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.monitor.service.elasticsearch;

import com.epam.pipeline.monitor.model.node.GpuUsages;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.IOUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MonitoringElasticsearchService {

    private static final DateTimeFormatter INDEX_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final ElasticsearchService elasticsearchService;
    private final String indexNamePrefix;
    private final String indexMappingsFile;

    public MonitoringElasticsearchService(final ElasticsearchService elasticsearchService,
                                          @Value("${es.gpu.monitor.index.prefix:cp-gpu-monitor-}")
                                         final String indexNamePrefix,
                                          @Value("${es.gpu.monitor.index.mappings}") final String indexMappingsFile) {
        this.elasticsearchService = elasticsearchService;
        this.indexNamePrefix = indexNamePrefix;
        this.indexMappingsFile = indexMappingsFile;
    }

    public void saveGpuUsages(final List<GpuUsages> usages) {
        if (CollectionUtils.isEmpty(usages)) {
            return;
        }
        final String indexName = indexNamePrefix + LocalDateTime.now().format(INDEX_FORMATTER);
        final String mappingsJson = readMappingsJson();
        elasticsearchService.createIndexIfNotExists(indexName, mappingsJson);
        final List<IndexRequest> indexRequests = ListUtils.emptyIfNull(usages).stream()
                .flatMap(usage -> GpuMonitorIndexHelper.buildIndexRequests(indexName, usage).stream())
                .collect(Collectors.toList());
        elasticsearchService.insertBulkDocuments(indexRequests);
    }

    private String readMappingsJson() {
        try {
            return IOUtils.toString(openJsonMapping(indexMappingsFile), Charset.defaultCharset());
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read mappings file: " + indexMappingsFile);
        }
    }

    private InputStream openJsonMapping(final String path) throws IOException {
        if (path.startsWith(ResourceUtils.CLASSPATH_URL_PREFIX)) {
            final InputStream classPathResource = getClass().getResourceAsStream(path
                    .substring(ResourceUtils.CLASSPATH_URL_PREFIX.length()));
            Assert.notNull(classPathResource, String.format("Failed to resolve path: %s", path));
            return classPathResource;
        }
        if (path.startsWith(ResourceUtils.FILE_URL_PREFIX)) {
            return Files.newInputStream(Paths.get(path.substring(ResourceUtils.FILE_URL_PREFIX.length())));
        }
        throw new IllegalArgumentException("Unsupported mapping file: " + path);
    }
}
