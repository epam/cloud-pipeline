package com.epam.pipeline.manager.utils;

import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class GlobalSearchElasticHelper {

    private final PreferenceManager preferenceManager;


    public RestHighLevelClient buildClient() {
        return new RestHighLevelClient(buildLowLevelClient());
    }

    public RestClient buildLowLevelClient() {
        final String host = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_HOST);
        final Integer port = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_PORT);
        final String schema = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_SCHEME);

        Assert.isTrue(Objects.nonNull(host) && Objects.nonNull(port) && Objects.nonNull(schema),
                "One or more of the following parameters is not configured: "
                        + SystemPreferences.SEARCH_ELASTIC_HOST.getKey() + ", "
                        + SystemPreferences.SEARCH_ELASTIC_PORT.getKey() + ", "
                        + SystemPreferences.SEARCH_ELASTIC_SCHEME.getKey()
        );
        return RestClient.builder(new HttpHost(host, port, schema)).build();
    }

}
