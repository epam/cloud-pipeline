package com.epam.pipeline.utils;

import org.apache.commons.collections4.IteratorUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GroupingIteratorTest {

    @ParameterizedTest(name = "[{index}] Groups: {1}")
    @MethodSource("provideData")
    void iteratorShouldGroupObjects(Integer[] data, Integer expectedSize, Integer[] groupSizes) {
        final GroupingIterator<Integer> groupingIterator = new GroupingIterator<>(
                IteratorUtils.arrayIterator(data), Integer::compareTo);
        final List<List<Integer>> result = new ArrayList<>();
        groupingIterator.forEachRemaining(result::add);

        assertEquals(expectedSize.intValue(), result.size(), "Group count mismatch");
        for (int i = 0; i < result.size(); i++) {
            assertEquals(groupSizes[i].intValue(), result.get(i).size(),
                    String.format("Mismatch at group %d", i));
        }
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> provideData() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        new Integer[]{1, 1, 1, 2, 3}, 3, new Integer[]{3, 1, 1}),
                org.junit.jupiter.params.provider.Arguments.of(
                        new Integer[]{1, 2, 2, 3}, 3, new Integer[]{1, 2, 1}),
                org.junit.jupiter.params.provider.Arguments.of(
                        new Integer[]{1, 2, 3}, 3, new Integer[]{1, 1, 1}),
                org.junit.jupiter.params.provider.Arguments.of(
                        new Integer[]{}, 0, new Integer[]{0})
        );
    }
}
