/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React, {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Button, Space} from 'antd';
import {DataGrid, renderTextEditor} from 'react-data-grid';
import 'react-data-grid/lib/styles.css';
import {
  ROW_NUMBER_KEY,
  array2DToRows,
  cloneRows,
  columnKey,
  getColumnCount,
  rowsToArray2D,
} from '../../../utils/tabular-array';
import TabularGridContextMenu, {
  TABULAR_GRID_ACTIONS,
  buildTabularGridMenuItems,
} from './context-menu';
import styles from './TabularDataGrid.module.css';

const HISTORY_LIMIT = 50;
const DEFAULT_ROW_HEIGHT = 35;
const MIN_COLUMN_COUNT = 1;

function createEmptyRow(rowIndex, columnCount) {
  const row = {id: rowIndex, [ROW_NUMBER_KEY]: rowIndex + 1};
  for (let col = 0; col < columnCount; col++) {
    row[columnKey(col)] = '';
  }
  return row;
}

function reindexRows(rows) {
  return rows.map((row, index) => ({
    ...row,
    id: index,
    [ROW_NUMBER_KEY]: index + 1,
  }));
}

function insertColumn(rows, columnCount, insertAt) {
  const nextCount = columnCount + 1;
  const nextRows = rows.map((row) => {
    const nextRow = {...row};
    for (let col = nextCount - 1; col > insertAt; col--) {
      nextRow[columnKey(col)] = row[columnKey(col - 1)] ?? '';
    }
    nextRow[columnKey(insertAt)] = '';
    return nextRow;
  });
  return {rows: nextRows, columnCount: nextCount};
}

function removeColumn(rows, columnCount, removeAt) {
  if (columnCount <= MIN_COLUMN_COUNT) {
    return {rows, columnCount};
  }
  const nextCount = columnCount - 1;
  const nextRows = rows.map((row) => {
    const nextRow = {...row};
    for (let col = removeAt; col < nextCount; col++) {
      nextRow[columnKey(col)] = row[columnKey(col + 1)] ?? '';
    }
    delete nextRow[columnKey(nextCount)];
    return nextRow;
  });
  return {rows: nextRows, columnCount: nextCount};
}

function dataColumnIndex(columnIdx) {
  return Math.max(0, columnIdx - 1);
}

const TabularDataGrid = forwardRef(function TabularDataGrid(
  {data = [], readOnly = false, onChange, className, style, height, rowHeight = DEFAULT_ROW_HEIGHT},
  ref,
) {
  const gridRef = useRef(null);
  const [snapshots, setSnapshots] = useState(() => [
    {rows: array2DToRows(data), columnCount: Math.max(getColumnCount(data), MIN_COLUMN_COUNT)},
  ]);
  const [snapshotIndex, setSnapshotIndex] = useState(0);
  const [rows, setRows] = useState(() => array2DToRows(data));
  const [columnCount, setColumnCount] = useState(() =>
    Math.max(getColumnCount(data), MIN_COLUMN_COUNT),
  );
  const [selectedCell, setSelectedCell] = useState({rowIdx: 0, colIdx: 1});
  const [contextMenu, setContextMenu] = useState({open: false, x: 0, y: 0});
  const dataSignatureRef = useRef(JSON.stringify(data));

  useEffect(() => {
    const signature = JSON.stringify(data);
    if (signature !== dataSignatureRef.current) {
      dataSignatureRef.current = signature;
      const nextColumnCount = Math.max(getColumnCount(data), MIN_COLUMN_COUNT);
      const nextRows = array2DToRows(data, nextColumnCount);
      setRows(nextRows);
      setColumnCount(nextColumnCount);
      setSnapshots([{rows: nextRows, columnCount: nextColumnCount}]);
      setSnapshotIndex(0);
    }
  }, [data]);

  const notifyChange = useCallback(
    (nextRows, nextColumnCount) => {
      if (onChange) {
        onChange(rowsToArray2D(nextRows, nextColumnCount));
      }
    },
    [onChange],
  );

  const recordSnapshot = useCallback(
    (nextRows, nextColumnCount) => {
      setSnapshots((prev) => {
        const trimmed = prev.slice(0, snapshotIndex + 1);
        const snapshot = {rows: cloneRows(nextRows), columnCount: nextColumnCount};
        const nextSnapshots = [...trimmed, snapshot];
        if (nextSnapshots.length > HISTORY_LIMIT) {
          nextSnapshots.shift();
          setSnapshotIndex(nextSnapshots.length - 1);
          return nextSnapshots;
        }
        setSnapshotIndex(nextSnapshots.length - 1);
        return nextSnapshots;
      });
    },
    [snapshotIndex],
  );

  const applyState = useCallback(
    (nextRows, nextColumnCount) => {
      const reindexed = reindexRows(nextRows);
      recordSnapshot(reindexed, nextColumnCount);
      setRows(reindexed);
      setColumnCount(nextColumnCount);
      notifyChange(reindexed, nextColumnCount);
      return reindexed;
    },
    [notifyChange, recordSnapshot],
  );

  const restoreSnapshot = useCallback(
    (index) => {
      const snapshot = snapshots[index];
      if (!snapshot) {
        return;
      }
      const restoredRows = cloneRows(snapshot.rows);
      setSnapshotIndex(index);
      setRows(restoredRows);
      setColumnCount(snapshot.columnCount);
      notifyChange(restoredRows, snapshot.columnCount);
    },
    [notifyChange, snapshots],
  );

  useImperativeHandle(
    ref,
    () => ({
      getData() {
        return rowsToArray2D(rows, columnCount);
      },
    }),
    [rows, columnCount],
  );

  const columns = useMemo(() => {
    const dataColumns = Array.from({length: columnCount}, (_, index) => ({
      key: columnKey(index),
      name: String(index + 1),
      resizable: true,
      editable: !readOnly,
      renderEditCell: readOnly ? undefined : renderTextEditor,
      width: 120,
    }));
    return [
      {
        key: ROW_NUMBER_KEY,
        name: '',
        frozen: true,
        width: 48,
        resizable: false,
        editable: false,
        cellClass: 'cp-tabular-row-header',
      },
      ...dataColumns,
    ];
  }, [columnCount, readOnly]);

  const onRowsChange = useCallback(
    (nextRows) => {
      const reindexed = reindexRows(nextRows);
      recordSnapshot(reindexed, columnCount);
      setRows(reindexed);
      notifyChange(reindexed, columnCount);
    },
    [columnCount, notifyChange, recordSnapshot],
  );

  const onCellClick = useCallback((args) => {
    setSelectedCell({rowIdx: args.rowIdx, colIdx: args.column.idx});
  }, []);

  const onCellContextMenu = useCallback((args, event) => {
    event.preventGridDefault?.();
    event.preventDefault();
    setSelectedCell({rowIdx: args.rowIdx, colIdx: args.column.idx});
    setContextMenu({open: true, x: event.clientX, y: event.clientY});
  }, []);

  const copySelection = useCallback(async () => {
    const dataCol = dataColumnIndex(selectedCell.colIdx);
    const row = rows[selectedCell.rowIdx];
    const value = row ? (row[columnKey(dataCol)] ?? '') : '';
    try {
      await navigator.clipboard.writeText(String(value));
    } catch {
      // Clipboard may be unavailable; native grid copy still works via keyboard.
    }
  }, [rows, selectedCell.colIdx, selectedCell.rowIdx]);

  const handleAction = useCallback(
    (action) => {
      const dataCol = dataColumnIndex(selectedCell.colIdx);
      const rowIdx = Math.max(0, Math.min(selectedCell.rowIdx, Math.max(rows.length - 1, 0)));

      switch (action) {
        case TABULAR_GRID_ACTIONS.COPY:
          copySelection();
          break;
        case TABULAR_GRID_ACTIONS.UNDO: {
          if (snapshotIndex > 0) {
            restoreSnapshot(snapshotIndex - 1);
          }
          break;
        }
        case TABULAR_GRID_ACTIONS.REDO: {
          if (snapshotIndex + 1 < snapshots.length) {
            restoreSnapshot(snapshotIndex + 1);
          }
          break;
        }
        case TABULAR_GRID_ACTIONS.INSERT_ROW_ABOVE: {
          const nextRows = [...rows];
          nextRows.splice(rowIdx, 0, createEmptyRow(rowIdx, columnCount));
          applyState(nextRows, columnCount);
          break;
        }
        case TABULAR_GRID_ACTIONS.INSERT_ROW_BELOW: {
          const nextRows = [...rows];
          nextRows.splice(rowIdx + 1, 0, createEmptyRow(rowIdx + 1, columnCount));
          applyState(nextRows, columnCount);
          break;
        }
        case TABULAR_GRID_ACTIONS.INSERT_COL_LEFT: {
          const result = insertColumn(rows, columnCount, dataCol);
          applyState(result.rows, result.columnCount);
          break;
        }
        case TABULAR_GRID_ACTIONS.INSERT_COL_RIGHT: {
          const result = insertColumn(rows, columnCount, dataCol + 1);
          applyState(result.rows, result.columnCount);
          break;
        }
        case TABULAR_GRID_ACTIONS.REMOVE_ROW: {
          if (rows.length <= 1) {
            break;
          }
          const nextRows = rows.filter((_, index) => index !== rowIdx);
          applyState(nextRows, columnCount);
          break;
        }
        case TABULAR_GRID_ACTIONS.REMOVE_COL: {
          const result = removeColumn(rows, columnCount, dataCol);
          applyState(result.rows, result.columnCount);
          break;
        }
        default:
          break;
      }
      setContextMenu({open: false, x: 0, y: 0});
    },
    [
      applyState,
      columnCount,
      copySelection,
      restoreSnapshot,
      rows,
      selectedCell.colIdx,
      selectedCell.rowIdx,
      snapshotIndex,
      snapshots.length,
    ],
  );

  const menuItems = buildTabularGridMenuItems({
    readOnly,
    canRemoveRow: rows.length > 1,
    canRemoveCol: columnCount > MIN_COLUMN_COUNT,
    canUndo: snapshotIndex > 0,
    canRedo: snapshotIndex + 1 < snapshots.length,
    onAction: handleAction,
  });

  const gridStyle = height ? {height} : {height: '100%', minHeight: (rows.length + 2) * rowHeight};

  return (
    <div
      className={classNames(styles.container, 'cp-tabular-data-grid', className, {
        [styles.readonly]: readOnly,
        'cp-tabular-data-grid-readonly': readOnly,
      })}
      style={style}
    >
      {!readOnly ? (
        <Space wrap size={4} className={styles.toolbar}>
          <Button size="small" onClick={() => handleAction(TABULAR_GRID_ACTIONS.INSERT_ROW_ABOVE)}>
            Row above
          </Button>
          <Button size="small" onClick={() => handleAction(TABULAR_GRID_ACTIONS.INSERT_ROW_BELOW)}>
            Row below
          </Button>
          <Button size="small" onClick={() => handleAction(TABULAR_GRID_ACTIONS.INSERT_COL_LEFT)}>
            Col left
          </Button>
          <Button size="small" onClick={() => handleAction(TABULAR_GRID_ACTIONS.INSERT_COL_RIGHT)}>
            Col right
          </Button>
          <Button
            size="small"
            disabled={rows.length <= 1}
            onClick={() => handleAction(TABULAR_GRID_ACTIONS.REMOVE_ROW)}
          >
            Remove row
          </Button>
          <Button
            size="small"
            disabled={columnCount <= MIN_COLUMN_COUNT}
            onClick={() => handleAction(TABULAR_GRID_ACTIONS.REMOVE_COL)}
          >
            Remove col
          </Button>
          <Button
            size="small"
            disabled={snapshotIndex <= 0}
            onClick={() => handleAction(TABULAR_GRID_ACTIONS.UNDO)}
          >
            Undo
          </Button>
          <Button
            size="small"
            disabled={snapshotIndex + 1 >= snapshots.length}
            onClick={() => handleAction(TABULAR_GRID_ACTIONS.REDO)}
          >
            Redo
          </Button>
        </Space>
      ) : null}
      <div className={styles.body} style={gridStyle} ref={gridRef}>
        <DataGrid
          className="rdg-light"
          columns={columns}
          rows={rows}
          rowKeyGetter={(row) => row.id}
          onRowsChange={readOnly ? undefined : onRowsChange}
          onCellClick={onCellClick}
          onCellContextMenu={onCellContextMenu}
          rowHeight={rowHeight}
          headerRowHeight={rowHeight}
        />
      </div>
      <TabularGridContextMenu
        open={contextMenu.open}
        x={contextMenu.x}
        y={contextMenu.y}
        items={menuItems}
        onClose={() => setContextMenu({open: false, x: 0, y: 0})}
      />
    </div>
  );
});

TabularDataGrid.propTypes = {
  data: PropTypes.array,
  readOnly: PropTypes.bool,
  onChange: PropTypes.func,
  className: PropTypes.string,
  style: PropTypes.object,
  height: PropTypes.number,
  rowHeight: PropTypes.number,
};

export default TabularDataGrid;
