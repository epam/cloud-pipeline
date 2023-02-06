package com.epam.pipeline.utils;

import org.apache.commons.collections4.IteratorUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class GroupingIteratorTest {

    private final Integer[] data;
    private final Integer expectedSize;
    private final Integer[] groupSizes;

    public GroupingIteratorTest(final Integer[] data, final Integer expectedSize, final Integer[] groupSizes) {
        this.data = data;
        this.expectedSize = expectedSize;
        this.groupSizes = groupSizes;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> provideData() {
        return Arrays.asList(new Object[][] {
            {
                new Integer[]{1, 1, 1, 2, 3},
                3,
                new Integer[]{3, 1, 1}
            },
            {
                new Integer[]{1, 2, 2, 3},
                3,
                new Integer[]{1, 2, 1}
            },
            {
                new Integer[]{1, 2, 3},
                3,
                new Integer[]{1, 1, 1}
            },
            {
                new Integer[]{},
                0,
                new Integer[]{0}
            }
        });
    }

    @Test
    public void iteratorShouldGroupObjects() {
        final GroupingIterator<Integer> groupingIterator = new GroupingIterator<>(
                IteratorUtils.arrayIterator(data), Integer::compareTo);
        final List<List<Integer>> result = new ArrayList<>();
        groupingIterator.forEachRemaining(result::add);
        Assert.assertEquals(expectedSize.intValue(), result.size());
        for (int i = 0; i < result.size(); i++) {
            Assert.assertEquals(groupSizes[i].intValue(), result.get(i).size());
        }
    }

}
