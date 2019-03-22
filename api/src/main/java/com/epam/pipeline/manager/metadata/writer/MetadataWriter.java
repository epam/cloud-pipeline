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

package com.epam.pipeline.manager.metadata.writer;

import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MetadataWriter {

    private final MetadataFileFormat format;
    private final CSVWriter csvWriter;

    public MetadataWriter(final MetadataFileFormat format, final Writer writer) {
        this.format = format;
        this.csvWriter = new CSVWriter(writer, format.separator(), CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.NO_ESCAPE_CHARACTER);
    }

    public void writeEntities(@NonNull final String metadataClass, @NonNull final List<MetadataEntity> entities) {
        final List<MetadataFileColumn> columns = retrieveEntityColumns(entities);
        addLine(headerLine(metadataClass, columns));
        entities.stream()
                .flatMap(entity -> entityLines(entity, columns))
                .forEach(this::addLine);
    }

    private List<MetadataFileColumn> retrieveEntityColumns(final List<MetadataEntity> entities) {
        if (entities.isEmpty()) {
            throw new MetadataWriterException("There are no metadata entities to write");
        }
        return entities.stream()
                .flatMap(entity -> MapUtils.emptyIfNull(entity.getData()).entrySet().stream())
                .map(entry -> MetadataFileColumn.from(entry.getKey(), entry.getValue().getType()))
                .distinct()
                .sorted(Comparator.comparing(MetadataFileColumn::getName))
                .collect(Collectors.toList());
    }

    private List<String> headerLine(final String metadataClass, final List<MetadataFileColumn> columns) {
        return Stream.concat(Stream.of(MetadataFileColumn.id(metadataClass)), columns.stream())
                .map(MetadataFileColumn::getName)
                .collect(Collectors.toList());
    }

    private void addLine(final List<String> record) {
        final String[] escapedValues = record.stream()
                .map(value -> value.contains(format.separatorAsString())
                        ? String.format("%s%s%s", format.quoteSymbol(), value, format.quoteSymbol())
                        : value
                )
                .toArray(String[]::new);
        csvWriter.writeNext(escapedValues);
    }

    private Stream<List<String>> entityLines(final MetadataEntity entity, final List<MetadataFileColumn> columns) {
        final List<MetadataFileColumn> membershipColumns = columns.stream()
                .filter(column -> column.getType() == MetadataFileColumnType.MEMBERSHIP)
                .collect(Collectors.toList());
        return membershipColumns.isEmpty()
                ? streamOfPlainEntity(entity, columns)
                : streamOfMultilineEntities(entity, columns, membershipColumns);
    }

    private Stream<List<String>> streamOfPlainEntity(final MetadataEntity entity,
                                                     final List<MetadataFileColumn> columns) {
        final List<String> values = columns.stream()
                .map(column -> column.getValueOf(entity))
                .collect(Collectors.toList());
        return Stream.of(listOf(entity.getExternalId(), values));
    }

    private Stream<List<String>> streamOfMultilineEntities(final MetadataEntity entity,
                                                           final List<MetadataFileColumn> columns,
                                                           final List<MetadataFileColumn> membershipColumns) {
        final Map<MetadataFileColumn, List<String>> membershipColumnsValues = membershipColumns.stream()
                .map(column -> new ImmutablePair<>(column, splitStringArray(column.getValueOf(entity))))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        final List<Map<MetadataFileColumn, String>> membershipColumnsValuesPermutations =
                permuteValues(membershipColumnsValues);

        return membershipColumnsValuesPermutations.stream()
                .map(membershipColumnsValuesCombination -> {
                    final List<String> values = columns.stream()
                            .map(column -> membershipColumnsValuesCombination.containsKey(column)
                                    ? membershipColumnsValuesCombination.get(column)
                                    : column.getValueOf(entity)
                            )
                            .collect(Collectors.toList());
                    return listOf(entity.getExternalId(), values);
                });
    }

    private List<String> splitStringArray(String stringArray) {
        try {
            return new ObjectMapper().readValue(stringArray, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            throw new MetadataWriterException(
                    String.format("Given metadata entity array value %s cannot be parsed", stringArray), e);
        }
    }

    private <T> List<T> listOf(final T element, final List<T> elements) {
        final ArrayList<T> list = new ArrayList<>();
        list.add(element);
        list.addAll(elements);
        return list;
    }

    private List<Map<MetadataFileColumn, String>> permuteValues(
            final Map<MetadataFileColumn, List<String>> membershipColumnsValues) {
        final List<Map<MetadataFileColumn, String>> initialList = Collections.singletonList(new HashMap<>());
        final SimpleReference<List<Map<MetadataFileColumn, String>>> listReference = new SimpleReference<>(initialList);
        membershipColumnsValues.forEach((column, columnValues) -> {
            final List<Map<MetadataFileColumn, String>> listWithAddedCurrentColumnValues =
                    columnValues.stream()
                            .distinct()
                            .flatMap(value -> listReference.getValue().stream()
                                    .map(columnsMap -> mapOf(column, value, columnsMap)))
                            .collect(Collectors.toList());
            listReference.setValue(listWithAddedCurrentColumnValues);
        });
        return listReference.getValue();
    }

    private <K, V> Map<K, V> mapOf(final K key, final V value, final Map<K, V> map) {
        final Map<K, V> newMap = new HashMap<>(map);
        newMap.put(key, value);
        return newMap;
    }

    @AllArgsConstructor
    @Getter
    @Setter
    private class SimpleReference<VALUE_TYPE> {
        private VALUE_TYPE value;
    }
}
