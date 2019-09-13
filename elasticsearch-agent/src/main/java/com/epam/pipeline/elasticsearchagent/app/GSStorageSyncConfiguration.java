package com.epam.pipeline.elasticsearchagent.app;

import com.epam.pipeline.elasticsearchagent.dao.PipelineEventDao;
import com.epam.pipeline.elasticsearchagent.model.DataStorageDoc;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import com.epam.pipeline.elasticsearchagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.elasticsearchagent.service.impl.BulkRequestSender;
import com.epam.pipeline.elasticsearchagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.elasticsearchagent.service.impl.ElasticIndexService;
import com.epam.pipeline.elasticsearchagent.service.impl.EntitySynchronizer;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.EventToRequestConverterImpl;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.DataStorageIndexCleaner;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.DataStorageLoader;
import com.epam.pipeline.elasticsearchagent.service.impl.converter.storage.DataStorageMapper;
import com.epam.pipeline.entity.search.SearchDocumentType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
@ConditionalOnProperty(value = "sync.gs-storage.disable", matchIfMissing = true, havingValue = "false")
public class GSStorageSyncConfiguration {

    @Value("${sync.index.common.prefix}")
    private String commonIndexPrefix;

    @Bean
    public DataStorageMapper gsStorageMapper() {
        return new DataStorageMapper(SearchDocumentType.GS_STORAGE);
    }

    @Bean
    public DataStorageLoader gsStorageLoader(final CloudPipelineAPIClient apiClient) {
        return new DataStorageLoader(apiClient);
    }

    @Bean
    public DataStorageIndexCleaner gsEventProcessor(
            final @Value("${sync.gs-file.index.name}") String gsFileIndexName,
            final ElasticsearchServiceClient serviceClient) {
        return new DataStorageIndexCleaner(commonIndexPrefix, gsFileIndexName, serviceClient);
    }

    @Bean
    public EventToRequestConverterImpl<DataStorageDoc> gsEventConverter(
            final @Qualifier("gsStorageMapper") DataStorageMapper gsStorageMapper,
            final @Qualifier("gsStorageLoader") DataStorageLoader gsStorageLoader,
            final @Qualifier("gsEventProcessor") DataStorageIndexCleaner indexCleaner,
            final @Value("${sync.gs-storage.index.name}") String indexName) {
        return new EventToRequestConverterImpl<>(
                commonIndexPrefix, indexName, gsStorageLoader, gsStorageMapper,
                Collections.singletonList(indexCleaner));
    }

    @Bean
    public EntitySynchronizer dataStorageGsSynchronizer(
            final @Qualifier("gsEventConverter") EventToRequestConverterImpl<DataStorageDoc> gsEventConverter,
            final PipelineEventDao eventDao,
            final ElasticIndexService indexService,
            final BulkRequestSender requestSender,
            final @Value("${sync.gs-storage.index.mapping}") String gsStorageMapping) {
        return new EntitySynchronizer(eventDao,
                PipelineEvent.ObjectType.GS,
                gsStorageMapping,
                gsEventConverter,
                indexService,
                requestSender);
    }
}
