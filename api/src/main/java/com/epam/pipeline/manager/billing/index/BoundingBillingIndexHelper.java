package com.epam.pipeline.manager.billing.index;

import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.billing.BillingUtils;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class BoundingBillingIndexHelper implements BillingIndexHelper {

    private final BillingIndexHelper inner;
    private final GlobalSearchElasticHelper elasticHelper;
    private final String commonIndexPrefix;
    private final String runIndexPrefix;
    private final String storageIndexPrefix;
    private final AtomicReference<LocalDate> firstBillingDate;

    public BoundingBillingIndexHelper(final BillingIndexHelper inner,
                                      final GlobalSearchElasticHelper elasticHelper,
                                      final String commonIndexPrefix,
                                      final String runIndexName,
                                      final String storageIndexName) {
        this.inner = inner;
        this.elasticHelper = elasticHelper;
        this.commonIndexPrefix = commonIndexPrefix;
        this.runIndexPrefix = commonIndexPrefix + runIndexName;
        this.storageIndexPrefix = commonIndexPrefix + storageIndexName;
        this.firstBillingDate = new AtomicReference<>();
    }

    @Override
    public String[] dailyIndicesBetween(final LocalDate from, final LocalDate to) {
        return inner.dailyIndicesBetween(normalizedFrom(from), normalizedTo(to));
    }

    @Override
    public String[] dailyRunIndicesBetween(final LocalDate from, final LocalDate to) {
        return inner.dailyRunIndicesBetween(normalizedFrom(from), normalizedTo(to));
    }

    @Override
    public String[] dailyStorageIndicesBetween(final LocalDate from, final LocalDate to) {
        return inner.dailyStorageIndicesBetween(normalizedFrom(from), normalizedTo(to));
    }

    @Override
    public String[] monthlyIndicesBetween(final LocalDate from, final LocalDate to) {
        return inner.monthlyIndicesBetween(normalizedFrom(from), normalizedTo(to));
    }

    @Override
    public String[] monthlyRunIndicesBetween(final LocalDate from, final LocalDate to) {
        return inner.monthlyRunIndicesBetween(normalizedFrom(from), normalizedTo(to));
    }

    @Override
    public String[] monthlyStorageIndicesBetween(final LocalDate from, final LocalDate to) {
        return inner.monthlyStorageIndicesBetween(normalizedFrom(from), normalizedTo(to));
    }

    @Override
    public String[] yearlyIndicesBetween(final LocalDate from, final LocalDate to) {
        return inner.yearlyIndicesBetween(normalizedFrom(from), normalizedTo(to));
    }

    @Override
    public String[] yearlyRunIndicesBetween(final LocalDate from, final LocalDate to) {
        return inner.yearlyRunIndicesBetween(normalizedFrom(from), normalizedTo(to));
    }

    @Override
    public String[] yearlyStorageIndicesBetween(final LocalDate from, final LocalDate to) {
        return inner.yearlyStorageIndicesBetween(normalizedFrom(from), normalizedTo(to));
    }

    private LocalDate normalizedFrom(final LocalDate from) {
        final LocalDate firstBillingDate = getOrResolveFirstBillingDate().orElse(LocalDate.MIN);
        return from.isBefore(firstBillingDate) ? Year.from(firstBillingDate).atDay(1) : from;
    }

    private LocalDate normalizedTo(final LocalDate to) {
        final LocalDate lastBillingDate = DateUtils.nowUTC().toLocalDate().minusDays(1);
        return to.isAfter(lastBillingDate) ? lastBillingDate : to;
    }

    private Optional<LocalDate> getOrResolveFirstBillingDate() {
        return Optional.ofNullable(firstBillingDate.get())
                .map(Optional::of)
                .orElseGet(this::resolveAndSaveFirstBillingDate);
    }

    private Optional<LocalDate> resolveAndSaveFirstBillingDate() {
        final Optional<LocalDate> resolvedFirstBillingDate = resolveFirstBillingDate();
        resolvedFirstBillingDate.ifPresent(date -> firstBillingDate.compareAndSet(null, date));
        return resolvedFirstBillingDate;
    }

    private Optional<LocalDate> resolveFirstBillingDate() {
        return indices()
                .map(this::fromIndexToDate)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted()
                .findFirst();
    }

    private Stream<String> indices() {
        try (final RestHighLevelClient client = elasticHelper.buildClient()) {
            final GetIndexRequest request = new GetIndexRequest(commonIndexPrefix + BillingUtils.ES_WILDCARD)
                    .indicesOptions(IndicesOptions.strictExpandOpen());
            final GetIndexResponse response = client.indices().get(request, RequestOptions.DEFAULT);
            return Arrays.stream(response.getIndices());
        } catch (IOException e) {
            throw new ElasticsearchException("Failed to list indices.", e);
        }
    }

    private Optional<LocalDate> fromIndexToDate(final String index) {
        try {
            final String indexPrefix = index.startsWith(runIndexPrefix) ? runIndexPrefix
                    : index.startsWith(storageIndexPrefix) ? storageIndexPrefix
                    : null;
            return Optional.ofNullable(indexPrefix)
                    .map(prefix -> index.substring(prefix.length() + 1))
                    .map(dateString -> LocalDate.parse(dateString, BillingUtils.ELASTIC_DATE_FORMATTER));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

}
