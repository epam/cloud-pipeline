/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline.documents.templates.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TableTest {

    private static final String DATA = "data";

    @Test
    public void setDataShouldFindTheRowAndTheColumnByNameInAnyLetterCase() {
        final Table table = table();

        table.setData("SECOND ROW", "second column", DATA);

        assertEquals(DATA, table.getData(1, 1));
        assertNull(table.getData(0, 0));
    }

    @Test
    public void setDataShouldStoreNothingForAnUnknownName() {
        final Table table = table();

        table.setData("Third row", "First column", DATA);
        table.setData("First row", "Third column", DATA);

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                assertNull(table.getData(row, column));
            }
        }
    }

    private static Table table() {
        final Table table = new Table();
        table.addRow("First row");
        table.addRow("Second row");
        table.addColumn("First column");
        table.addColumn("Second column");
        return table;
    }
}
