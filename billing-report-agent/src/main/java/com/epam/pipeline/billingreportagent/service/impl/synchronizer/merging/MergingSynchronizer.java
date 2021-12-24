package com.epam.pipeline.billingreportagent.service.impl.synchronizer.merging;

import com.epam.pipeline.billingreportagent.model.EntityDocument;
import com.epam.pipeline.billingreportagent.service.DocumentMapper;
import com.epam.pipeline.billingreportagent.service.ElasticsearchMergingSynchronizer;
import com.epam.pipeline.billingreportagent.service.ElasticsearchServiceClient;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.ElasticsearchMergingFrame;
import com.epam.pipeline.billingreportagent.service.impl.ElasticIndexService;
import com.epam.pipeline.billingreportagent.service.impl.IndexRequestContainer;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.utils.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class MergingSynchronizer implements ElasticsearchMergingSynchronizer {

    private static final String INDEX_TYPE = "_doc";

    private final String name;
    private final String indexMappingFile;
    private final String indexPrefix;
    private final String indexName;
    private final int bulkInsertSize;
    private final ElasticsearchServiceClient elasticsearchServiceClient;
    private final ElasticIndexService elasticIndexService;
    private final EntityDocumentLoader loader;
    private final DocumentMapper mapper;
    private final ElasticsearchMergingFrame frame;

    @Override
    public String name() {
        return name;
    }

    @Override
    public ElasticsearchMergingFrame frame() {
        return frame;
    }

    @Override
    public void synchronize(final LocalDateTime from, final LocalDateTime to) {
        final LocalDate fromDate = Optional.ofNullable(from).map(Optional::of)
                .orElseGet(this::latestBillingIndex)
                .orElseGet(DateUtils::nowUTC)
                .toLocalDate();
        final LocalDate toDate = Optional.ofNullable(to)
                .orElseGet(DateUtils::nowUTC)
                .toLocalDate();
        frame.periods(fromDate, toDate).forEach(this::synchronize);
    }

    private Optional<LocalDateTime> latestBillingIndex() {
        return elasticsearchServiceClient.indices()
                .filter(index -> index.startsWith(indexPrefix + indexName))
                .map(this::fromIndexToDate)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted()
                .findFirst();
    }

    private Optional<LocalDateTime> fromIndexToDate(final String index) {
        try {
            final String commonIndexPrefix = getIndexName(indexPrefix + indexName, StringUtils.EMPTY);
            final String indexDateString = index.substring(commonIndexPrefix.length());
            return Optional.of(LocalDate.parse(indexDateString, EntityToBillingRequestConverter.SIMPLE_DATE_FORMAT)
                    .atStartOfDay());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private void synchronize(final Temporal period) {
        log.debug("{} - Merging {} period billings...", name(), period);
        final String indexAlias = getIndexName(indexPrefix + indexName, frame.nameOf(period));
        final String creatingIndex = getIndexName(PasswordGenerator.generateRandomString(5).toLowerCase(), indexAlias);
        try {
            final String existingIndex = elasticsearchServiceClient.getIndexNameByAlias(indexAlias);
            elasticIndexService.createIndexIfNotExists(creatingIndex, indexMappingFile);

            final String[] subIndices = frame.subPeriodNamesOf(period)
                    .map(childPeriodName -> getIndexName(indexPrefix + indexName, childPeriodName))
                    .toArray(String[]::new);
            synchronize(frame.startOf(period), frame.endOf(period), creatingIndex, subIndices);

            elasticsearchServiceClient.createIndexAlias(creatingIndex, indexAlias);
            if (StringUtils.isNotBlank(existingIndex)) {
                elasticsearchServiceClient.deleteIndex(existingIndex);
            }
            log.debug("{} - Successfully merged {} period billings...", name(), period);
        } catch (Exception e) {
            log.error(String.format("%s - Failed merging %s period billings.", name(), period), e);
            if (elasticsearchServiceClient.isIndexExists(creatingIndex))  {
                elasticsearchServiceClient.deleteIndex(creatingIndex);
            }
        }
    }

    private String getIndexName(final String... tokens) {
        return String.join("-", tokens);
    }

    private void synchronize(final LocalDate from, final LocalDate to,
                             final String index,
                             final String[] subIndices) {
        try (IndexRequestContainer requestContainer = getRequestContainer(bulkInsertSize)) {
            loader.documents(from, to, subIndices)
                    .map(document -> getRequest(index, document))
                    .forEach(requestContainer::add);
        }
    }

    private IndexRequest getRequest(final String index, final EntityDocument document) {
        return new IndexRequest(index, INDEX_TYPE)
                .id(document.getId())
                .source(mapper.map(document));
    }

    private IndexRequestContainer getRequestContainer(final int bulkInsertSize) {
        return new IndexRequestContainer(elasticsearchServiceClient::sendRequests, bulkInsertSize);
    }

}
