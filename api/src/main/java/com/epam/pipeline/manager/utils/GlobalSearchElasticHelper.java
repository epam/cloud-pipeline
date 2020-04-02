package com.epam.pipeline.manager.utils;

import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class GlobalSearchElasticHelper {

    private final PreferenceManager preferenceManager;


    public RestHighLevelClient buildClient() {
        return new RestHighLevelClient(buildLowLevelClient());
    }

    private RestClient buildLowLevelClient() {
        return RestClient.builder(new HttpHost(
                preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_HOST),
                preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_PORT),
                preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_SCHEME)))
                .build();
    }

}
