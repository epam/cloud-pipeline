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

import React from 'react';
import PropTypes from 'prop-types';
import {Dropdown} from 'antd';

export const TABULAR_GRID_ACTIONS = {
  INSERT_ROW_ABOVE: 'insertRowAbove',
  INSERT_ROW_BELOW: 'insertRowBelow',
  INSERT_COL_LEFT: 'insertColLeft',
  INSERT_COL_RIGHT: 'insertColRight',
  REMOVE_ROW: 'removeRow',
  REMOVE_COL: 'removeCol',
  UNDO: 'undo',
  REDO: 'redo',
  COPY: 'copy',
};

export function buildTabularGridMenuItems({
  readOnly,
  canRemoveRow,
  canRemoveCol,
  canUndo,
  canRedo,
  onAction,
}) {
  if (readOnly) {
    return [
      {
        key: TABULAR_GRID_ACTIONS.COPY,
        label: 'Copy',
        onClick: () => onAction(TABULAR_GRID_ACTIONS.COPY),
      },
    ];
  }
  return [
    {
      key: TABULAR_GRID_ACTIONS.INSERT_ROW_ABOVE,
      label: 'Insert row above',
      onClick: () => onAction(TABULAR_GRID_ACTIONS.INSERT_ROW_ABOVE),
    },
    {
      key: TABULAR_GRID_ACTIONS.INSERT_ROW_BELOW,
      label: 'Insert row below',
      onClick: () => onAction(TABULAR_GRID_ACTIONS.INSERT_ROW_BELOW),
    },
    {type: 'divider'},
    {
      key: TABULAR_GRID_ACTIONS.INSERT_COL_LEFT,
      label: 'Insert column left',
      onClick: () => onAction(TABULAR_GRID_ACTIONS.INSERT_COL_LEFT),
    },
    {
      key: TABULAR_GRID_ACTIONS.INSERT_COL_RIGHT,
      label: 'Insert column right',
      onClick: () => onAction(TABULAR_GRID_ACTIONS.INSERT_COL_RIGHT),
    },
    {type: 'divider'},
    {
      key: TABULAR_GRID_ACTIONS.REMOVE_ROW,
      label: 'Remove row',
      disabled: !canRemoveRow,
      onClick: () => onAction(TABULAR_GRID_ACTIONS.REMOVE_ROW),
    },
    {
      key: TABULAR_GRID_ACTIONS.REMOVE_COL,
      label: 'Remove column',
      disabled: !canRemoveCol,
      onClick: () => onAction(TABULAR_GRID_ACTIONS.REMOVE_COL),
    },
    {type: 'divider'},
    {
      key: TABULAR_GRID_ACTIONS.UNDO,
      label: 'Undo',
      disabled: !canUndo,
      onClick: () => onAction(TABULAR_GRID_ACTIONS.UNDO),
    },
    {
      key: TABULAR_GRID_ACTIONS.REDO,
      label: 'Redo',
      disabled: !canRedo,
      onClick: () => onAction(TABULAR_GRID_ACTIONS.REDO),
    },
    {type: 'divider'},
    {
      key: TABULAR_GRID_ACTIONS.COPY,
      label: 'Copy',
      onClick: () => onAction(TABULAR_GRID_ACTIONS.COPY),
    },
  ];
}

function TabularGridContextMenu({open, x, y, items, onClose}) {
  if (!open) {
    return null;
  }
  return (
    <>
      <div
        style={{position: 'fixed', inset: 0, zIndex: 1000}}
        onClick={onClose}
        onContextMenu={(event) => {
          event.preventDefault();
          onClose();
        }}
      />
      <Dropdown open menu={{items, onClick: onClose}} trigger={['contextMenu']}>
        <div style={{position: 'fixed', left: x, top: y, width: 1, height: 1, zIndex: 1001}} />
      </Dropdown>
    </>
  );
}

TabularGridContextMenu.propTypes = {
  open: PropTypes.bool,
  x: PropTypes.number,
  y: PropTypes.number,
  items: PropTypes.array,
  onClose: PropTypes.func,
};

export default TabularGridContextMenu;
