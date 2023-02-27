/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportProperties;
import com.epam.pipeline.entity.billing.BillingChartDetails;
import com.epam.pipeline.entity.billing.StorageBilling;
import com.epam.pipeline.entity.billing.StorageBillingChartCostDetails;
import com.epam.pipeline.entity.billing.StorageBillingMetrics;
import com.epam.pipeline.manager.billing.billingdetails.StorageBillingCostDetailsLoader;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.pipeline.entity.billing.StorageBillingChartCostDetails.StorageBillingDetails;

public class StorageBillingWriter implements BillingWriter<StorageBilling> {

    private static final String TABLE_NAME = "Storages";

    private final PeriodBillingWriter<StorageBilling, StorageBillingMetrics> writer;

    public StorageBillingWriter(final Writer writer,
                                final LocalDate from,
                                final LocalDate to,
                                final BillingExportProperties exportProperties) {
        this.writer = new PeriodBillingWriter<>(writer, from, to, TABLE_NAME,
                Arrays.asList(
                    BillingUtils.STORAGE_COLUMN,
                    BillingUtils.OWNER_COLUMN,
                    BillingUtils.BILLING_CENTER_COLUMN,
                    BillingUtils.TYPE_COLUMN,
                    BillingUtils.REGION_COLUMN,
                    BillingUtils.PROVIDER_COLUMN,
                    BillingUtils.CREATED_COLUMN
                ),
                getPeriodColumns(exportProperties),
                Arrays.asList(
                    StorageBilling::getName,
                    StorageBilling::getOwner,
                    StorageBilling::getBillingCenter,
                    b -> BillingUtils.asString(b.getType()),
                    StorageBilling::getRegion,
                    StorageBilling::getProvider,
                    b -> BillingUtils.asString(b.getCreated())
                ),
                getPeriodDataExtractors(exportProperties)
        );
    }

    private static List<Function<StorageBillingMetrics, String>> getPeriodDataExtractors(
            final BillingExportProperties properties) {
        final List<String> requestedStorageClasses = Optional.ofNullable(properties)
                .map(BillingExportProperties::getIncludeStorageClasses)
                .orElse(Collections.emptyList())
                .stream().filter(StorageBillingCostDetailsLoader.STORAGE_CLASSES::containsKey)
                .collect(Collectors.toList());
        final List<Function<StorageBillingMetrics, String>> extractors = new ArrayList<>();
        extractors.add(metrics -> BillingUtils.asCostString(metrics.getCost()));
        requestedStorageClasses
                .stream()
                .flatMap(sc -> getReportValueExtractors(
                        sc, properties.isIncludeStorageOldVersions(),
                        StorageBillingDetails::getCost,
                        StorageBillingDetails::getOldVersionCost,
                        BillingUtils::asCostString
                    )
                )
                .forEachOrdered(extractors::add);

        extractors.add(metrics -> BillingUtils.asVolumeString(metrics.getAverageVolume()));
        requestedStorageClasses
                .stream()
                .flatMap(sc -> getReportValueExtractors(
                                sc, properties.isIncludeStorageOldVersions(),
                                StorageBillingDetails::getAvgSize,
                                StorageBillingDetails::getOldVersionAvgSize,
                                BillingUtils::asVolumeString
                        )
                )
                .forEachOrdered(extractors::add);
        extractors.add(metrics -> BillingUtils.asVolumeString(metrics.getCurrentVolume()));
        requestedStorageClasses
                .stream()
                .flatMap(sc -> getReportValueExtractors(
                                sc, properties.isIncludeStorageOldVersions(),
                                StorageBillingDetails::getSize,
                                StorageBillingDetails::getOldVersionSize,
                                BillingUtils::asVolumeString
                        )
                )
                .forEachOrdered(extractors::add);
        return extractors;
    }

    private static Function<StorageBillingMetrics, StorageBillingDetails> getStorageClassDetails(final String sc) {
        return (StorageBillingMetrics metrics) -> {
            final BillingChartDetails details = metrics.getDetails();
            if (details instanceof StorageBillingChartCostDetails) {
                return ((StorageBillingChartCostDetails) details).getTiers()
                        .stream().filter(d -> d.getStorageClass().equals(sc))
                        .findFirst().orElse(StorageBillingDetails.empty(sc));
            }
            return StorageBillingDetails.empty(sc);
        };
    }

    private static Stream<Function<StorageBillingMetrics, String>> getReportValueExtractors(
            final String sc, final boolean showOV,
            final Function<StorageBillingDetails, Long> dataFetcher,
            final Function<StorageBillingDetails, Long> dataOVFetcher,
            final Function<Long, String> converter) {
        if (showOV) {
            return Stream.of(
                (StorageBillingMetrics metrics) ->
                        converter.apply(dataFetcher.apply(getStorageClassDetails(sc).apply(metrics))),
                (StorageBillingMetrics metrics) ->
                        converter.apply(dataOVFetcher.apply(getStorageClassDetails(sc).apply(metrics)))
            );
        } else {
            return Stream.of(
                (StorageBillingMetrics metrics) -> {
                    final StorageBillingDetails details = getStorageClassDetails(sc).apply(metrics);
                    return converter.apply(dataFetcher.apply(details) + dataOVFetcher.apply(details));
                }
            );
        }
    }

    private static List<String> getPeriodColumns(BillingExportProperties properties) {
        final List<String> requestedStorageClasses = Optional.ofNullable(properties)
                .map(BillingExportProperties::getIncludeStorageClasses)
                .orElse(Collections.emptyList())
                .stream().map(StorageBillingCostDetailsLoader.STORAGE_CLASSES::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        final boolean includeStorageOV = Optional.ofNullable(properties)
                .map(BillingExportProperties::isIncludeStorageOldVersions).orElse(false);
        final List<String> columns = new ArrayList<>();
        columns.add(BillingUtils.COST_COLUMN);
        requestedStorageClasses.forEach(sc -> {
            columns.add(String.format(BillingUtils.DETAILED_COST_COLUMN, sc));
            if (includeStorageOV) {
                columns.add(String.format(BillingUtils.DETAILED_OV_COST_COLUMN, sc));
            }
        });
        columns.add(BillingUtils.AVERAGE_VOLUME_COLUMN);
        requestedStorageClasses.forEach(sc -> {
            columns.add(String.format(BillingUtils.DETAILED_AVERAGE_VOLUME_COLUMN, sc));
            if (includeStorageOV) {
                columns.add(String.format(BillingUtils.DETAILED_OV_AVERAGE_VOLUME_COLUMN, sc));
            }
        });
        columns.add(BillingUtils.CURRENT_VOLUME_COLUMN);
        requestedStorageClasses.forEach(sc -> {
            columns.add(String.format(BillingUtils.DETAILED_CURRENT_VOLUME_COLUMN, sc));
            if (includeStorageOV) {
                columns.add(String.format(BillingUtils.DETAILED_OV_CURRENT_VOLUME_COLUMN, sc));
            }
        });
        return columns;
    }

    @Override
    public void writeHeader() {
        writer.writeHeader();
    }

    @Override
    public void write(final StorageBilling billing) {
        writer.write(billing);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}
