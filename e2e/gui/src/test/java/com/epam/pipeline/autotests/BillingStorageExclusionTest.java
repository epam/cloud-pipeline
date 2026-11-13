/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests;

import com.codeborne.selenide.ex.UIAssertionError;
import com.epam.pipeline.autotests.ao.StorageContentAO;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.PlatformApi;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.codeborne.selenide.Condition.appear;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static java.lang.String.format;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Case 2580: the data storages that have the attribute "Billing status" = "Exclude" are not shown in the billing
 * reports.
 * <p>
 * The billing service leaves these storages out when it writes the billing data of a day (StorageLoader). It runs
 * once a day and keeps the data of the days before, so a test can't set the attribute and see the effect in the same
 * run. This test checks every storage that has the attribute, in the report of the last 7 days. That proves the
 * exclusion only if:
 * <ul>
 *     <li>a storage has the attribute;</li>
 *     <li>one of them shows "Storage size" and has no sharing enabled: the billing service bills only storages with
 *     indexed files, and never a shared one, so this one would be in the report without the attribute;</li>
 *     <li>every row of the report is a storage of the platform: the rows are what the storages are compared with;</li>
 *     <li>the report shows a storage without the attribute of the same type (S3, AZ, GS or NFS) as one of the
 *     storages that would be billed: the billing of that type works in the period. The billing service bills each
 *     type separately, but checks the attribute for all types at once before that (StorageLoader), so one type is
 *     enough.</li>
 * </ul>
 * The test fails when one of them doesn't hold. The storages are found through the API, as the GUI can't search by
 * an attribute; the call is the one the billing service makes.
 */
public class BillingStorageExclusionTest extends AbstractBfxPipelineTest implements Navigation {

    private static final String EXCLUDE_KEY = "Billing status";
    private static final String EXCLUDE_VALUE = "Exclude";
    private static final int REPORT_DAYS = 7;
    // The size is loaded after the page, from the search index of the storage files
    private static final Duration STORAGE_SIZE_TIMEOUT = Duration.ofSeconds(30);
    // The row of a storage whose details the API can't load, e.g. a deleted one: the report shows its id
    private static final Pattern STORAGE_ID_ROW = Pattern.compile("\\d+");

    @Test
    @TestCase("2580")
    public void storagesWithBillingStatusExcludeAreNotShownInTheBillingReport() {
        final Map<String, JsonNode> storagesById = new LinkedHashMap<>();
        PlatformApi.get("datastorage/loadAll").forEach(storage -> storagesById.put(id(storage), storage));
        final JsonNode entities = PlatformApi.get(format("metadata/search?entityClass=DATA_STORAGE&key=%s&value=%s",
                encode(EXCLUDE_KEY), encode(EXCLUDE_VALUE)));
        assertTrue(entities.size() > 0,
                format("No data storage has the attribute '%s' with value '%s'", EXCLUDE_KEY, EXCLUDE_VALUE));
        final List<JsonNode> excluded = new ArrayList<>();
        for (final JsonNode entity : entities) {
            final JsonNode storage = storagesById.get(entity.path("entityId").asText());
            if (storage == null) {
                System.out.printf("Storage %s has the attribute, but is not among the storages of the platform%n",
                        entity.path("entityId").asText());
            } else {
                excluded.add(storage);
            }
        }
        assertFalse(excluded.isEmpty(), format("None of the %d storages with '%s' = '%s' is among the storages of the "
                + "platform", entities.size(), EXCLUDE_KEY, EXCLUDE_VALUE));

        final List<JsonNode> billable = new ArrayList<>();
        for (final JsonNode storage : excluded) {
            final boolean sized = showsStorageSize(storage);
            // The page shows a "Share" button for a storage with sharing enabled; the flag is read from the API
            final boolean shared = storage.path("shared").asBoolean(false);
            System.out.printf("%s: %s, %s%n", describe(storage), sized ? "Storage size is shown" : "no Storage size",
                    shared ? "sharing enabled" : "no sharing");
            if (sized && !shared) {
                billable.add(storage);
            }
        }
        assertFalse(billable.isEmpty(), format("None of the storages with '%s' = '%s' shows \"Storage size\" and has "
                + "no sharing enabled: the billing service would not bill them without the attribute either",
                EXCLUDE_KEY, EXCLUDE_VALUE));

        final LocalDate today = LocalDate.now(ZoneOffset.UTC);
        final LocalDate from = today.minusDays(REPORT_DAYS);
        final LocalDate to = today.minusDays(1);
        final List<String> rows = billing()
                .storages()
                .customRange(from, to)
                .storageColumn();
        System.out.printf("The billing report of %s - %s shows %d storages: %s%n", from, to, rows.size(), rows);

        // A row is the report name of a storage or, when the API could not load its details, its id (which is no
        // storage of the platform if it has been deleted)
        final Map<String, JsonNode> storagesByRow = storagesById.values().stream()
                .collect(Collectors.toMap(this::reportName, storage -> storage, (first, second) -> first));
        storagesById.forEach(storagesByRow::putIfAbsent);
        final List<String> unknownRows = rows.stream()
                .filter(row -> !storagesByRow.containsKey(row) && !STORAGE_ID_ROW.matcher(row).matches())
                .collect(Collectors.toList());
        assertTrue(unknownRows.isEmpty(), format("The billing report of %s - %s has rows that are not storages of the "
                + "platform, so its rows can't be compared with the storages: %s", from, to, unknownRows));

        // Each storage is looked for by its own report name and id, so that no other storage can hide it
        final List<String> shownExcluded = excluded.stream()
                .filter(storage -> rows.contains(reportName(storage)) || rows.contains(id(storage)))
                .map(this::describe)
                .collect(Collectors.toList());
        assertTrue(shownExcluded.isEmpty(), format("The billing report of %s - %s shows the storages with '%s' = '%s': "
                + "%s", from, to, EXCLUDE_KEY, EXCLUDE_VALUE, shownExcluded));

        final Set<String> excludedIds = excluded.stream().map(this::id).collect(Collectors.toSet());
        final Set<String> billableTypes = billable.stream().map(this::type).collect(Collectors.toSet());
        final Set<String> shownTypes = rows.stream()
                .map(storagesByRow::get)
                .filter(Objects::nonNull)
                .filter(storage -> !excludedIds.contains(id(storage)))
                .map(this::type)
                .collect(Collectors.toSet());
        final Set<String> comparedTypes = new HashSet<>(billableTypes);
        comparedTypes.retainAll(shownTypes);
        System.out.printf("Types of the storages with the attribute that would be billed: %s; of the other storages in "
                + "the report: %s%n", billableTypes, shownTypes);
        assertFalse(comparedTypes.isEmpty(), format("The billing report of %s - %s shows no other storage of the type of "
                + "the storages with '%s' = '%s' that would be billed (%s), only of %s: it can't tell their exclusion "
                + "from no billing of that type", from, to, EXCLUDE_KEY, EXCLUDE_VALUE, billableTypes, shownTypes));
    }

    /**
     * Opens the page of the storage and its attributes, and waits for "Storage size: ..." (the StorageSize component).
     * Until the size has loaded, and if the storage has no indexed files, the attributes show only
     * "Request storage re-index" there. The page is reloaded: StorageSize keeps the size it shows until the size of
     * the next storage has loaded, so the page of a storage without indexed files, opened right after one with them,
     * would show the size of the other one for a while.
     * The text is matched rather than the class name of the component, which is "torage-size__storage-size": the
     * client's CSS module names drop the first letter of a lowercase file name.
     */
    private boolean showsStorageSize(final JsonNode storage) {
        open(format("%s/#/storage/%d", C.ROOT_ADDRESS.replaceAll("/+$", ""), storage.path("id").asLong()));
        Utils.refresh();
        new StorageContentAO()
                .validateHeader(storage.path("name").asText())
                .showMetadata();
        try {
            $(byXpath("//span[starts-with(normalize-space(.), 'Storage size:')]"))
                    .should(appear, STORAGE_SIZE_TIMEOUT);
            return true;
        } catch (UIAssertionError e) {
            return false;
        }
    }

    /**
     * The storage as the "Storage" column of the billing report shows it (StorageBillingDetailsLoader): the name of an
     * NFS storage, the path of any other.
     */
    private String reportName(final JsonNode storage) {
        return "NFS".equals(storage.path("type").asText())
                ? storage.path("name").asText()
                : storage.path("path").asText();
    }

    private String describe(final JsonNode storage) {
        return format("%s (id %s, %s)", storage.path("name").asText(), storage.path("id").asText(),
                reportName(storage));
    }

    private static String encode(final String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String id(final JsonNode storage) {
        return storage.path("id").asText();
    }

    // S3, NFS, AZ or GS: the billing service bills each type separately
    private String type(final JsonNode storage) {
        return storage.path("type").asText();
    }
}
