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

package com.epam.pipeline.manager.pipeline.documents.templates.structure;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
public class Table {
    private List<TableRow> rows;
    private List<TableColumn> columns;

    private boolean containsHeaderRow;
    private boolean containsHeaderColumn;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Map<String, Object> data;

    public Table() {
        rows = new ArrayList<>();
        columns = new ArrayList<>();
        data = new HashMap<>();
        this.setContainsHeaderColumn(false);
        this.setContainsHeaderRow(false);
    }

    public TableRow addRow(String name) {
        TableRow row = new TableRow(name);
        row.setIndex(this.rows.size());
        this.rows.add(row);
        return row;
    }

    public TableColumn addColumn(String name) {
        TableColumn column = new TableColumn(name);
        column.setIndex(this.columns.size());
        this.columns.add(column);
        return column;
    }

    public Object getData(int row, int column) {
        return this.data.get(this.getKey(row, column));
    }

    public void setData(int row, int column, Object data) {
        this.data.put(this.getKey(row, column), data);
    }

    public void setData(String rowName, String columnName, Object data) {
        Optional<TableRow> tableRow = this.rows.stream()
                .filter(r -> r.getName().toLowerCase().equals(rowName.toLowerCase()))
                .findFirst();
        Optional<TableColumn> tableColumn = this.columns.stream()
                .filter(r -> r.getName().toLowerCase().equals(columnName.toLowerCase()))
                .findFirst();
        if (tableRow.isPresent() && tableColumn.isPresent()) {
            this.data.put(this.getKey(tableRow.get().getIndex(), tableColumn.get().getIndex()), data);
        }
    }

    private String getKey(int row, int column) {
        return String.format("[%d][%d]", row, column);
    }
}
