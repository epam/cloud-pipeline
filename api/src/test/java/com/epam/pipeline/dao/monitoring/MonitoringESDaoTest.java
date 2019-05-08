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

package com.epam.pipeline.dao.monitoring;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.epam.pipeline.entity.cluster.monitoring.ELKUsageMetric;
import org.apache.http.HttpHost;
import org.elasticsearch.ResourceAlreadyExistsException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.junit.*;

import com.epam.pipeline.entity.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitoringESDaoTest {
    private static Node node = null;
    private static final String POD1_NAME = "pod1";
    private static final String POD2_NAME = "pod2";
    private static final String HEAPSTER_TOKEN = "heapster-";
    private static final String CPU_TYPE = "cpu";
    private static final int HALF_AN_HOUR = 30;
    private static final int TWENTY_MINUTES = 20;
    private static final int ONE_AND_A_HALF_SEC = 1500;
    private static final int SMALL_PAUSE = 500;
    private static final int ONE_HUNDRED = 100;
    private static final double TEST_DELTA = 0.0001;
    private static final int ELASTICSEARCH_DEFAULT_PORT = 9200;

    private MonitoringESDao monitoringESDao;

    private static final LocalDateTime NOW = DateUtils.nowUTC();
    private Client client;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Settings settings = Settings.builder()
            .put("path.home", "target/elasticsearch")
            .put("transport.type", "local")
            .put("http.enabled", true)
            .build();

        node = new PluginConfigurableNode(settings, Collections.singletonList(Netty4Plugin.class)).start();
        Client client = node.client();

        String indexName = HEAPSTER_TOKEN + MonitoringESDao.DATE_FORMATTER.format(DateUtils.nowUTC());

        tryDelete(indexName, client);

        URL fileUrl = Thread.currentThread().getContextClassLoader()
                .getResource("templates/heapster-type-mapping.json");
        String mappingsJson = Files.readAllLines(Paths.get(fileUrl.toURI())).stream().collect(Collectors.joining());
        while (true) {
            try {
                client.admin().indices().prepareCreate(indexName)
                    .addMapping(CPU_TYPE, mappingsJson, XContentType.JSON).get();
                break;
            } catch (ResourceAlreadyExistsException e) {
                tryDelete(indexName, client);
                Thread.sleep(SMALL_PAUSE);
            }
        }

        Map<String, Object> record1 = new HashMap<>();
        record1.put("CpuMetricsTimestamp", NOW.minusMinutes(HALF_AN_HOUR));

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("cpu/usage_rate", Collections.singletonMap("value", 4));
        record1.put("Metrics", metrics);

        Map<String, Object> metricsTags = new HashMap<>();
        metricsTags.put("pod_name", POD1_NAME);
        metricsTags.put("type", "pod_container");
        metricsTags.put("namespace_name", "default");
        record1.put("MetricsTags", metricsTags);

        client.prepareIndex(indexName, CPU_TYPE).setSource(record1).get();

        Map<String, Object> record2 = new HashMap<>(record1);
        record2.put("CpuMetricsTimestamp", NOW.minusMinutes(TWENTY_MINUTES));
        ((Map<String, Object>) record2.get("Metrics")).put("cpu/usage_rate", Collections.singletonMap("value", 2));

        client.prepareIndex(indexName, CPU_TYPE).setSource(record2).get();

        Map<String, Object> record3 = new HashMap<>(record1);
        ((Map<String, Object>) record3.get("MetricsTags")).put("pod_name", POD2_NAME);
        client.prepareIndex(indexName, CPU_TYPE).setSource(record3).get();

        Map<String, Object> record4 = new HashMap<>(record2);
        ((Map<String, Object>) record4.get("MetricsTags")).put("pod_name", POD2_NAME);
        ((Map<String, Object>) record4.get("Metrics")).put("cpu/usage_rate", Collections.singletonMap("value", 4));
        client.prepareIndex(indexName, CPU_TYPE).setSource(record4).get();

        Thread.sleep(ONE_AND_A_HALF_SEC); // Wait till all stuff is indexed

        while (true) { // wait additionally if needed
            SearchResponse searchResponse = client.prepareSearch(indexName)
                    .setQuery(QueryBuilders.matchAllQuery()).get();
            if (searchResponse.getHits().totalHits == 4) {
                break;
            }

            Thread.sleep(ONE_HUNDRED);
        }
    }

    @Before
    public void setUp() throws Exception {
        client = node.client();
        RestClient lowLevelClient = RestClient.builder(
            new HttpHost("localhost", ELASTICSEARCH_DEFAULT_PORT, "http")).build();
        RestHighLevelClient highLevelClient = new RestHighLevelClient(lowLevelClient);
        monitoringESDao = new MonitoringESDao(highLevelClient, lowLevelClient);
    }

    private static void tryDelete(String indexName, Client client) {
        try {
            client.admin().indices().prepareDelete(indexName).get();
        } catch (IndexNotFoundException e) {
            log.debug(e.getMessage());
        }

    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        node.close();
    }

    @Test
    public void testLoadCpuUsageRateMetrics() {
        Map<String, Double> stats = monitoringESDao.loadMetrics(ELKUsageMetric.CPU,
                Arrays.asList(POD1_NAME, POD2_NAME), NOW.minusMinutes(HALF_AN_HOUR), NOW);

        Assert.assertEquals(2, stats.size());
        Assert.assertEquals(3, stats.get(POD1_NAME), TEST_DELTA);
        Assert.assertEquals(3, stats.get(POD2_NAME), TEST_DELTA);
    }

    @Test
    public void testDeleteIndices() {
        IntStream.range(1, 6)
            .mapToObj(NOW::minusDays)
            .map(date -> HEAPSTER_TOKEN + MonitoringESDao.DATE_FORMATTER.format(date))
            .forEach(indexName -> {
                tryDelete(indexName, client);
                client.admin().indices().prepareCreate(indexName).get();
            });

        monitoringESDao.deleteIndices(3);

        String[] indices = client.admin().cluster()
            .prepareState().get()
            .getState().metaData().getConcreteAllIndices();

        Assert.assertEquals(4, Arrays.stream(indices).filter(i -> i.startsWith(HEAPSTER_TOKEN)).count());
    }

    private static final class PluginConfigurableNode extends Node {
        private PluginConfigurableNode(Settings settings, Collection<Class<? extends Plugin>> classpathPlugins) {
            super(InternalSettingsPreparer.prepareEnvironment(settings, null), classpathPlugins);
        }
    }
}