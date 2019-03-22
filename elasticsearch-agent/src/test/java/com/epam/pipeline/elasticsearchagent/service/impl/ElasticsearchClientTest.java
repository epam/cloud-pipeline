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
package com.epam.pipeline.elasticsearchagent.service.impl;

import com.epam.pipeline.elasticsearchagent.AbstractSpringBootApplicationTest;
import com.epam.pipeline.entity.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.*;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Slf4j
public class ElasticsearchClientTest extends AbstractSpringBootApplicationTest {

    private static final DateTimeFormatter DATE_FORMATTER =DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String PIPELINE_TOKEN = "s3-";
    private static final int SIZE_NAMBER = 115;
    private static final int ONE_AND_A_HALF_SEC = 1500;
    private static final int STATUS_RESPONSE = 201;
    private static final int SMALL_PAUSE = 500;
    private static final String BUCKET = "bucket";
    private static final int ELASTICSEARCH_DEFAULT_PORT = 9200;

    private static Node node = null;

    private Client client;
    private static CreateIndexResponse createIndexResponse;
    private static String indexName;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Settings settings = Settings.builder()
                .put("path.home", "target/elasticsearch")
                .put("transport.type", "local")
                .put("http.enabled", true)
                .build();

        node = new PluginConfigurableNode(settings, Collections.singletonList(Netty4Plugin.class)).start();
        Client client = node.client();

        indexName = PIPELINE_TOKEN + DATE_FORMATTER.format(DateUtils.nowUTC());

        tryDelete(indexName, client);

        URL fileUrl = Thread.currentThread().getContextClassLoader()
                .getResource("templates/s3.json");
        String mappingsJson = Files.readAllLines(Paths.get(fileUrl.toURI())).stream().collect(Collectors.joining());
        while (true) {
            try {
                createIndexResponse = client.admin().indices().prepareCreate(indexName)
                        .addMapping(BUCKET, mappingsJson, XContentType.JSON).get();
                System.out.println(createIndexResponse.index());
                break;
            } catch (ResourceAlreadyExistsException e) {
                tryDelete(indexName, client);
                Thread.sleep(SMALL_PAUSE);
            }
        }

        Thread.sleep(ONE_AND_A_HALF_SEC); // Wait till all stuff is indexed
    }

    private static void tryDelete(String indexName, Client client) {
        try {
            client.admin().indices().prepareDelete(indexName).get();
        } catch (IndexNotFoundException e) {
            log.debug(e.getMessage());
        }

    }

    @Before
    public void setUp() throws Exception {
        client = node.client();
        RestClientBuilder lowLevelClient = RestClient.builder(
                new HttpHost("localhost", ELASTICSEARCH_DEFAULT_PORT, "http"));
        RestHighLevelClient highLevelClient = new RestHighLevelClient(lowLevelClient);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        node.close();
    }

    @Test
    public void testLoadDocument() throws IOException {
        IndexResponse indexResponse = client.prepareIndex(indexName, BUCKET, null)
                .setSource(XContentFactory.jsonBuilder()
                        .startObject()
                        .field("lastModified", "2018-10-22 18:20:20")
                        .field("size", SIZE_NAMBER)
                        .field("path", "s3://path/to/file")
                        .field("tags")
                        .startObject()
                        .field("key", "keyValue")
                        .field("value", "valueValue")
                        .endObject()
                        .endObject())
                .get();
        Assert.assertEquals(STATUS_RESPONSE, indexResponse.status().getStatus());

    }

    private static final class PluginConfigurableNode extends Node {
        private PluginConfigurableNode(Settings settings, Collection<Class<? extends Plugin>> classpathPlugins) {
            super(InternalSettingsPreparer.prepareEnvironment(settings, null), classpathPlugins);
        }
    }
}
