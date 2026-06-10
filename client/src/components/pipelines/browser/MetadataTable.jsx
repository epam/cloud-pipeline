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

import React from 'react';
import PropTypes from 'prop-types';
import {flexRender, getCoreRowModel, useReactTable} from '@tanstack/react-table';
import classNames from 'classnames';
import styles from './Browser.module.css';

function MetadataTable({
  className,
  columns,
  data,
  getTdProps,
  onTableMouseLeave,
  noDataComponent: NoDataComponent,
}) {
  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    enableColumnResizing: true,
    columnResizeMode: 'onEnd',
    defaultColumn: {
      minSize: 60,
      size: 150,
    },
  });

  const rows = table.getRowModel().rows;

  if (rows.length === 0 && NoDataComponent) {
    return (
      <div className={className}>
        <NoDataComponent />
      </div>
    );
  }

  return (
    <div
      className={className}
      style={{
        overflowY: 'hidden',
        userSelect: 'none',
        borderRadius: 5,
      }}
      onMouseLeave={onTableMouseLeave}
    >
      <table
        className={styles.metadataTableElement}
        style={{
          width: table.getCenterTotalSize(),
          minWidth: '100%',
          borderCollapse: 'collapse',
        }}
      >
        <thead>
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => {
                const meta = header.column.columnDef.meta || {};
                return (
                  <th
                    key={header.id}
                    colSpan={header.colSpan}
                    style={{
                      width: header.getSize(),
                      position: 'relative',
                      ...meta.style,
                    }}
                    className={meta.className}
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(header.column.columnDef.header, header.getContext())}
                    {header.column.getCanResize() ? (
                      <div
                        className={styles.metadataColumnResizer}
                        onMouseDown={header.getResizeHandler()}
                        onTouchStart={header.getResizeHandler()}
                      />
                    ) : null}
                  </th>
                );
              })}
            </tr>
          ))}
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              {row.getVisibleCells().map((cell) => {
                const columnDef = cell.column.columnDef;
                const meta = columnDef.meta || {};
                const columnInfo = {
                  id: cell.column.id,
                  index: meta.index,
                  selectable: meta.selectable,
                };
                const rowInfo = {
                  index: row.index,
                  row: {_original: row.original},
                };
                const tdProps = getTdProps ? getTdProps(null, rowInfo, columnInfo) : {};
                const {className: tdClassName, style: tdStyle, ...restTdProps} = tdProps;
                return (
                  <td
                    key={cell.id}
                    style={{
                      padding: 0,
                      width: cell.column.getSize(),
                      ...tdStyle,
                    }}
                    className={classNames(meta.className, tdClassName)}
                    {...restTdProps}
                  >
                    {flexRender(columnDef.cell, cell.getContext())}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

MetadataTable.propTypes = {
  className: PropTypes.string,
  columns: PropTypes.array,
  data: PropTypes.array,
  getTdProps: PropTypes.func,
  onTableMouseLeave: PropTypes.func,
  noDataComponent: PropTypes.elementType,
};

export default MetadataTable;
