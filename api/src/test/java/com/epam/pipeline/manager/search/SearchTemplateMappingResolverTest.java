/*
 * Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.search;

import com.epam.pipeline.entity.search.SearchDocument;
import com.epam.pipeline.entity.search.SearchTemplateExportColumnData;
import com.epam.pipeline.entity.search.SearchTemplateExportSheetMapping;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

public class SearchTemplateMappingResolverTest {
    private static final String FACET_1 = "Study name";
    private static final String FACET_2 = "Stain";
    private static final String FACET_1_VALUE_1 = "Facet11";
    private static final String FACET_1_VALUE_2 = "Facet12";
    private static final String FACET_1_VALUE_3 = "Facet13";
    private static final String FACET_2_VALUE_1 = "Facet21";
    private static final String FACET_2_VALUE_2 = "Facet22";
    private static final String FACET_2_VALUE_3 = "Facet23";
    private static final String UNRESOLVED_FACET_2 = placeholder(FACET_2);
    private static final String TEXT = "/";
    private static final String DOC_NAME = "file1.txt";
    private static final String ATTR_NAME = "file2.txt";
    private static final String DOC_USER = "user1";
    private static final String ATTR_USER = "user2";
    private static final String CLOUD_PATH = "/cloud/path";

    private final SearchTemplateMappingResolver resolver = new SearchTemplateMappingResolver();

    @Test
    public void shouldResolveColumnValues() {
        final List<SearchDocument> documents = documents();

        final SearchTemplateExportSheetMapping mapping = mapping(placeholder(FACET_1));

        final String[] expectedResolvedValues = {
            FACET_1_VALUE_1,
            FACET_1_VALUE_2,
            FACET_1_VALUE_3,
            FACET_1_VALUE_2,
            FACET_1_VALUE_1,
            FACET_1_VALUE_1
        };

        final SearchTemplateExportColumnData resolved = resolver.prepareColumnData(documents, mapping);

        assertColumn(expectedResolvedValues, resolved);
    }

    @Test
    public void shouldResolveUniqueColumnValues() {
        final List<SearchDocument> documents = documents();

        final SearchTemplateExportSheetMapping mapping = mapping(placeholder(FACET_1));
        mapping.setUnique(true);

        final String[] expectedResolvedValues = {FACET_1_VALUE_1, FACET_1_VALUE_2, FACET_1_VALUE_3};

        final SearchTemplateExportColumnData resolved = resolver.prepareColumnData(documents, mapping);

        assertColumn(expectedResolvedValues, resolved);
    }

    @Test
    public void shouldResolveMultipleColumnValues() {
        final List<SearchDocument> documents = documents();

        final SearchTemplateExportSheetMapping mapping = mapping(
                TEXT + placeholder(FACET_1) + TEXT + placeholder(FACET_2));

        final String[] expectedResolvedValues = {
            TEXT + FACET_1_VALUE_1 + TEXT + FACET_2_VALUE_1,
            TEXT + FACET_1_VALUE_2 + TEXT + FACET_2_VALUE_2,
            TEXT + FACET_1_VALUE_3 + TEXT + FACET_2_VALUE_3,
            TEXT + FACET_1_VALUE_2 + TEXT + FACET_2_VALUE_1,
            TEXT + FACET_1_VALUE_1 + TEXT + FACET_2_VALUE_1,
            TEXT + FACET_1_VALUE_1 + TEXT
        };

        final SearchTemplateExportColumnData resolved = resolver.prepareColumnData(documents, mapping);

        assertColumn(expectedResolvedValues, resolved);
    }

    @Test
    public void shouldResolveMultipleUniqueColumnValues() {
        final List<SearchDocument> documents = documents();

        final SearchTemplateExportSheetMapping mapping = mapping(
                TEXT + placeholder(FACET_1) + TEXT + placeholder(FACET_2));
        mapping.setUnique(true);

        final String[] expectedResolvedValues = {
            TEXT + FACET_1_VALUE_1 + TEXT + FACET_2_VALUE_1,
            TEXT + FACET_1_VALUE_2 + TEXT + FACET_2_VALUE_2,
            TEXT + FACET_1_VALUE_3 + TEXT + FACET_2_VALUE_3,
            TEXT + FACET_1_VALUE_2 + TEXT + FACET_2_VALUE_1,
            TEXT + FACET_1_VALUE_1 + TEXT
        };

        final SearchTemplateExportColumnData resolved = resolver.prepareColumnData(documents, mapping);

        assertColumn(expectedResolvedValues, resolved);
    }

    @Test
    public void shouldResolveMultipleColumnValuesWithUnresolved() {
        final List<SearchDocument> documents = documents();

        final SearchTemplateExportSheetMapping mapping = mapping(
                TEXT + placeholder(FACET_1) + TEXT + placeholder(FACET_2));
        mapping.setKeepUnresolved(true);

        final String[] expectedResolvedValues = {
            TEXT + FACET_1_VALUE_1 + TEXT + FACET_2_VALUE_1,
            TEXT + FACET_1_VALUE_2 + TEXT + FACET_2_VALUE_2,
            TEXT + FACET_1_VALUE_3 + TEXT + FACET_2_VALUE_3,
            TEXT + FACET_1_VALUE_2 + TEXT + FACET_2_VALUE_1,
            TEXT + FACET_1_VALUE_1 + TEXT + FACET_2_VALUE_1,
            TEXT + FACET_1_VALUE_1 + TEXT + UNRESOLVED_FACET_2
        };

        final SearchTemplateExportColumnData resolved = resolver.prepareColumnData(documents, mapping);

        assertColumn(expectedResolvedValues, resolved);
    }

    @Test
    public void shouldResolveMultipleUniqueColumnValuesWithUnresolved() {
        final List<SearchDocument> documents = documents();

        final SearchTemplateExportSheetMapping mapping = mapping(
                TEXT + placeholder(FACET_1) + TEXT + placeholder(FACET_2));
        mapping.setUnique(true);
        mapping.setKeepUnresolved(true);

        final String[] expectedResolvedValues = {
            TEXT + FACET_1_VALUE_1 + TEXT + FACET_2_VALUE_1,
            TEXT + FACET_1_VALUE_2 + TEXT + FACET_2_VALUE_2,
            TEXT + FACET_1_VALUE_3 + TEXT + FACET_2_VALUE_3,
            TEXT + FACET_1_VALUE_2 + TEXT + FACET_2_VALUE_1,
            TEXT + FACET_1_VALUE_1 + TEXT + UNRESOLVED_FACET_2
        };

        final SearchTemplateExportColumnData resolved = resolver.prepareColumnData(documents, mapping);

        assertColumn(expectedResolvedValues, resolved);
    }

    @Test
    public void shouldResolveNamePlaceholderFromDocument() {
        final SearchDocument document = SearchDocument.builder()
                .name(DOC_NAME)
                .attributes(Collections.singletonMap("name", ATTR_NAME))
                .build();

        final SearchTemplateExportSheetMapping mapping = mapping(placeholder("Name"));

        final String[] expectedResolvedValues = { DOC_NAME };

        final SearchTemplateExportColumnData resolved = resolver.prepareColumnData(
                Collections.singletonList(document), mapping);

        assertColumn(expectedResolvedValues, resolved);
    }

    @Test
    public void shouldResolveNamePlaceholderFromDocumentAttributes() {
        final SearchDocument document = SearchDocument.builder()
                .attributes(Collections.singletonMap("name", ATTR_NAME))
                .build();

        final SearchTemplateExportSheetMapping mapping = mapping(placeholder("Name"));

        final String[] expectedResolvedValues = { ATTR_NAME };

        final SearchTemplateExportColumnData resolved = resolver
                .prepareColumnData(Collections.singletonList(document), mapping);

        assertColumn(expectedResolvedValues, resolved);
    }

    @Test
    public void shouldResolveOwnerPlaceholderFromDocument() {
        final SearchDocument document = SearchDocument.builder()
                .owner(DOC_USER)
                .attributes(Collections.singletonMap("owner", ATTR_USER))
                .build();

        final SearchTemplateExportSheetMapping mapping = mapping(placeholder("Owner"));

        final String[] expectedResolvedValues = { DOC_USER };

        final SearchTemplateExportColumnData resolved = resolver
                .prepareColumnData(Collections.singletonList(document), mapping);

        assertColumn(expectedResolvedValues, resolved);
    }

    @Test
    public void shouldResolveOwnerPlaceholderFromDocumentAttributes() {
        final SearchDocument document = SearchDocument.builder()
                .attributes(Collections.singletonMap("ownerUserName", ATTR_USER))
                .build();

        final SearchTemplateExportSheetMapping mapping = mapping(placeholder("Owner"));

        final String[] expectedResolvedValues = { ATTR_USER };

        final SearchTemplateExportColumnData resolved = resolver
                .prepareColumnData(Collections.singletonList(document), mapping);

        assertColumn(expectedResolvedValues, resolved);
    }

    @Test
    public void shouldResolveCloudPathPlaceholder() {
        final SearchDocument document = SearchDocument.builder()
                .attributes(Collections.singletonMap("cloud_path", CLOUD_PATH))
                .build();

        final SearchTemplateExportSheetMapping mapping = mapping(placeholder("Cloud path"));

        final String[] expectedResolvedValues = { CLOUD_PATH };

        final SearchTemplateExportColumnData resolved = resolver
                .prepareColumnData(Collections.singletonList(document), mapping);

        assertColumn(expectedResolvedValues, resolved);
    }

    private static List<SearchDocument> documents() {
        final List<SearchDocument> documents = new ArrayList<>();
        documents.add(SearchDocument.builder()
                .attributes(new HashMap<String, String>() {{
                        put(FACET_1, FACET_1_VALUE_1);
                        put(FACET_2, FACET_2_VALUE_1);
                    }})
                .build());
        documents.add(SearchDocument.builder()
                .attributes(new HashMap<String, String>() {{
                        put(FACET_1, FACET_1_VALUE_2);
                        put(FACET_2, FACET_2_VALUE_2);
                    }})
                .build());
        documents.add(SearchDocument.builder()
                .attributes(new HashMap<String, String>() {{
                        put(FACET_1, FACET_1_VALUE_3);
                        put(FACET_2, FACET_2_VALUE_3);
                    }})
                .build());
        documents.add(SearchDocument.builder()
                .attributes(new HashMap<String, String>() {{
                        put(FACET_1, FACET_1_VALUE_2);
                        put(FACET_2, FACET_2_VALUE_1);
                    }})
                .build());
        documents.add(SearchDocument.builder()
                .attributes(new HashMap<String, String>() {{
                        put(FACET_1, FACET_1_VALUE_1);
                        put(FACET_2, FACET_2_VALUE_1);
                    }})
                .build());
        documents.add(SearchDocument.builder()
                .attributes(Collections.singletonMap(FACET_1, FACET_1_VALUE_1))
                .build());
        return documents;
    }

    private static void assertColumn(final String[] expected, final SearchTemplateExportColumnData actual) {
        assertThat(actual.getRowStartIndex(), equalTo(1));
        assertThat(actual.getColumnStringIndex(), equalTo("A"));
        assertThat(actual.getColumnValues().length, equalTo(expected.length));
        assertThat(Arrays.asList(actual.getColumnValues()), containsInAnyOrder(expected));
    }

    private static String placeholder(final String value) {
        return "{" + value + "}";
    }

    private static SearchTemplateExportSheetMapping mapping(final String value) {
        return SearchTemplateExportSheetMapping.builder()
                .column("a")
                .startRow(2)
                .value(value)
                .build();
    }
}
