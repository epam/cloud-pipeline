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

import React, {useState, useRef, useEffect, useCallback, DragEvent} from 'react';
import classNames from 'classnames';
import {Button, Row, Checkbox, Popover} from 'antd';
import type {SizeType} from 'antd/es/config-provider/SizeContext';
import {BarsOutlined} from '@ant-design/icons';
import styles from './DropdownWithMultiselect.module.css';

interface Column {
  key: string;
  selected?: boolean;
}

interface SortableItemProps {
  value: string;
  isSelected: boolean;
  disabled: boolean;
  onColumnSelect?: () => void;
  columnNameFn: (key: string) => React.ReactNode;
  onDragStart: (e: DragEvent<HTMLElement>) => void;
  onDragEnter: () => void;
  onDragEnd: () => void;
}

function SortableItem({
  value,
  isSelected,
  disabled,
  onColumnSelect,
  columnNameFn,
  onDragStart,
  onDragEnter,
  onDragEnd,
}: SortableItemProps) {
  return (
    <Row
      className={classNames(styles.row, 'cp-metadata-dropdown-row')}
      draggable
      onDragStart={onDragStart}
      onDragEnter={onDragEnter}
      onDragEnd={onDragEnd}
      onDragOver={(e) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
      }}
    >
      <span style={{cursor: 'grab'}}>
        <BarsOutlined />
      </span>
      <Checkbox disabled={disabled} checked={isSelected} onChange={onColumnSelect}>
        {columnNameFn(value)}
      </Checkbox>
    </Row>
  );
}

interface DropdownWithMultiselectProps {
  className?: string;
  onColumnSelect?: (key: string) => void;
  onSetOrder?: (columns: Column[]) => void;
  onResetColumns?: () => void;
  columns?: Column[];
  columnNameFn?: (key: string) => React.ReactNode;
  size?: SizeType;
  style?: React.CSSProperties;
}

function DropdownWithMultiselect({
  className,
  style,
  size,
  columns = [],
  onColumnSelect,
  onSetOrder,
  onResetColumns,
  columnNameFn,
}: DropdownWithMultiselectProps): React.ReactElement {
  const [sortedColumns, setSortedColumns] = useState<Column[]>(columns);
  const dragIndex = useRef<number | null>(null);

  useEffect(() => {
    setSortedColumns(columns);
  }, [columns]);

  const selectedColumns = sortedColumns.filter((c) => c.selected);

  const handleDragStart = (index: number) => (e: DragEvent<HTMLElement>) => {
    e.dataTransfer.effectAllowed = 'move';
    dragIndex.current = index;
  };

  const handleDragEnter = (index: number) => () => {
    if (dragIndex.current === null || dragIndex.current === index) return;
    setSortedColumns((prev) => {
      const next = [...prev];
      const [moved] = next.splice(dragIndex.current!, 1);
      next.splice(index, 0, moved);
      dragIndex.current = index;
      return next;
    });
  };

  const handleDragEnd = useCallback(() => {
    onSetOrder?.(sortedColumns);
    dragIndex.current = null;
  }, [sortedColumns, onSetOrder]);

  const resolvedColumnNameFn = columnNameFn ?? ((o: string) => o);

  const content = (
    <div>
      <Button style={{width: '100%', marginBottom: '5px'}} onClick={onResetColumns}>
        Reset Columns
      </Button>
      <div style={{margin: '-2px -10px', width: 250}}>
        {sortedColumns.map(({key}, index) => {
          const isSelected = selectedColumns.some((c) => c.key === key);
          return (
            <SortableItem
              key={key}
              value={key}
              isSelected={isSelected}
              disabled={selectedColumns.length <= 1 && isSelected}
              onColumnSelect={onColumnSelect ? () => onColumnSelect(key) : undefined}
              columnNameFn={resolvedColumnNameFn}
              onDragStart={handleDragStart(index)}
              onDragEnter={handleDragEnter(index)}
              onDragEnd={handleDragEnd}
            />
          );
        })}
      </div>
    </div>
  );

  return (
    <Popover trigger="click" title="Show columns" placement="bottomRight" content={content}>
      <Button
        id="metadata-manage-columns-button"
        className={className}
        style={Object.assign({lineHeight: 1}, style ?? {})}
        size={size}
      >
        <BarsOutlined />
      </Button>
    </Popover>
  );
}

export default DropdownWithMultiselect;
