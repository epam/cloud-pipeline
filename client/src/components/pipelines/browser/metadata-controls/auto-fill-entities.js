/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import {
  Button,
  Dropdown,
  Icon,
  Menu
} from 'antd';
import styles from './auto-fill-entities.css';
import MetadataEntitySave from '../../../../models/folderMetadata/MetadataEntitySave';

function cellIsSelected (area, column, row) {
  return area &&
    area.start.column <= column && column <= area.end.column &&
    area.start.row <= row && row <= area.end.row;
}

function isLeftSideCell (area, column, row) {
  return area && cellIsSelected(area, column, row) && column === area.start.column;
}
function isRightSideCell (area, column, row) {
  return area && cellIsSelected(area, column, row) && column === area.end.column;
}
function isTopSideCell (area, column, row) {
  return area && cellIsSelected(area, column, row) && row === area.start.row;
}
function isBottomSideCell (area, column, row) {
  return area && cellIsSelected(area, column, row) && row === area.end.row;
}
function isRightCornerCell (area, column, row) {
  return isBottomSideCell(area, column, row) && isRightSideCell(area, column, row);
}
function isAutoFillEntitiesMarker (element) {
  return element && element.dataset && element.dataset.hasOwnProperty('autoFillEntities');
}
function mapItem (item, classId, className, parentId) {
  if (!item) {
    return undefined;
  }
  return {
    classId,
    className,
    parentId,
    entityId: item.rowKey ? item.rowKey.value : undefined,
    externalId: item.ID ? item.ID.value : undefined,
    data: Object.entries(item)
      .filter(([key]) => !/^(ID|createdDate|rowKey)$/i.test(key))
      .map(([key, value]) => ({[key]: value}))
      .reduce((r, c) => ({...r, ...c}), {})
  };
}
function mapItemSaveOperation (item, options) {
  const {
    classId,
    className,
    parentId
  } = options || {};
  const request = new MetadataEntitySave();
  return new Promise((resolve, reject) => {
    request
      .send(mapItem(item, classId, className, parentId))
      .then(() => {
        if (request.error) {
          throw new Error(request.error);
        }
        resolve(item);
      })
      .catch(reject);
  });
}
function buildUndoOperation (options) {
  const {
    backup = []
  } = options || {};
  return {
    title: 'Revert',
    loadingMessage: 'Reverting...',
    action: () => new Promise((resolve, reject) => {
      Promise.all(backup.map(item => mapItemSaveOperation(item, options)))
        .then(resolve)
        .catch(reject);
    })
  };
}
function buildClearPropertiesAction (options) {
  const {
    targetItems,
    removedItems,
    targetColumns,
    removedColumns
  } = options;
  if (removedItems.length > 0) {
    const processItem = (item) => {
      const payload = {...item.item};
      for (let c = 0; c < targetColumns.length; c++) {
        const column = targetColumns[c].key;
        delete payload[column];
      }
      return mapItemSaveOperation(payload, options);
    };
    return {
      title: 'Clear cells',
      loadingMessage: 'Clearing...',
      action: () => new Promise((resolve, reject) => {
        Promise
          .all(removedItems.map(processItem))
          .then(resolve)
          .catch(reject);
      })
    };
  }
  if (removedColumns.length > 0) {
    const processItem = (item) => {
      const payload = {...item.item};
      for (let c = 0; c < removedColumns.length; c++) {
        const column = removedColumns[c].key;
        delete payload[column];
      }
      return mapItemSaveOperation(payload, options);
    };
    return {
      title: 'Clear cells',
      loadingMessage: 'Clearing...',
      action: () => new Promise((resolve, reject) => {
        Promise
          .all(targetItems.map(processItem))
          .then(resolve)
          .catch(reject);
      })
    };
  }
  return undefined;
}
function buildCopyAction (options) {
  const {
    sourceItems,
    targetItems,
    insertedItems,
    removedItems,
    sourceColumns,
    targetColumns,
    removedColumns,
    insertedColumns
  } = options;
  if (removedItems.length > 0 || removedColumns.length > 0) {
    return undefined;
  }
  if (insertedItems.length > 0) {
    const processItem = (item, index) => {
      const source = sourceItems[index % sourceItems.length].item;
      const payload = {...item.item};
      for (let c = 0; c < targetColumns.length; c++) {
        const column = targetColumns[c].key;
        const value = source.hasOwnProperty(column)
          ? source[column].value
          : undefined;
        const type = source.hasOwnProperty(column)
          ? source[column].type
          : undefined;
        if (value === undefined) {
          delete payload[column];
        } else {
          payload[column] = {
            value,
            type: type || 'string'
          };
        }
      }
      return mapItemSaveOperation(payload, options);
    };
    return {
      title: 'Copy cells',
      loadingMessage: 'Copying...',
      action: () => new Promise((resolve, reject) => {
        Promise
          .all(insertedItems.map(processItem))
          .then(resolve)
          .catch(reject);
      })
    };
  }
  if (insertedColumns.length > 0) {
    const processItem = (item) => {
      const payload = {...item.item};
      for (let c = 0; c < insertedColumns.length; c++) {
        const sourceColumn = sourceColumns[c % sourceColumns.length].key;
        const targetColumn = insertedColumns[c].key;
        const value = payload.hasOwnProperty(sourceColumn)
          ? payload[sourceColumn].value
          : undefined;
        const type = payload.hasOwnProperty(sourceColumn)
          ? payload[sourceColumn].type
          : undefined;
        if (value === undefined) {
          delete payload[targetColumn];
        } else {
          payload[targetColumn] = {
            value,
            type: type || 'string'
          };
        }
      }
      return mapItemSaveOperation(payload, options);
    };
    return {
      title: 'Copy cells',
      loadingMessage: 'Copying...',
      action: () => new Promise((resolve, reject) => {
        Promise
          .all(targetItems.map(processItem))
          .then(resolve)
          .catch(reject);
      })
    };
  }
  return undefined;
}
function buildAutoFillAction (options) {
  const {
    sourceItems,
    targetItems,
    insertedItems,
    removedItems,
    sourceColumns,
    targetColumns,
    removedColumns,
    insertedColumns
  } = options;
  if (removedItems.length > 0 || removedColumns.length > 0) {
    return undefined;
  }
  const getNumberShift = value => {
    if (!value) {
      return 0;
    }
    const exec = /^[\\-]?(0+[\d]+)$/.exec(`${value}`);
    if (exec && exec.length === 2) {
      return exec[1].length;
    }
    return 0;
  };
  const getShiftedNumber = (number, shift = 0) => {
    const string = `${Math.abs(number)}`;
    const sign = number < 0 ? '-' : '';
    const appendZeroCount = Math.max(0, shift - string.length);
    if (appendZeroCount > 0) {
      return `${sign}${(new Array(appendZeroCount)).fill('0').join('')}${string}`;
    }
    return `${sign}${string}`;
  };
  const splitValue = value => {
    if (!value) {
      return {
        value: undefined
      };
    }
    if (!Number.isNaN(Number(value))) {
      return {
        value,
        number: +value,
        string: '',
        shift: getNumberShift(value)
      };
    }
    const exec = /^([^\d]*)([\d]+)$/.exec(`${value}`);
    if (exec && exec.length === 3) {
      return {
        value: exec[0],
        string: exec[1],
        number: +exec[2],
        shift: getNumberShift(exec[2])
      };
    }
    return {
      value
    };
  };
  const getValuesDiff = values => {
    if (values.length === 0) {
      return undefined;
    }
    if (values.some(value => value.number === undefined)) {
      return undefined;
    }
    if ((new Set(values.map(value => value.string))).size > 1) {
      return undefined;
    }
    if (values.length === 1) {
      return Math.sign(values[0].number);
    }
    const diff = values[1].number - values[0].number;
    for (let i = 2; i < values.length; i++) {
      if (values[i].number - values[i - 1].number !== diff) {
        return undefined;
      }
    }
    return diff;
  };
  const getValuesShift = values => {
    if (values.length === 0) {
      return 0;
    }
    if (values.some(value => value.number === undefined)) {
      return 0;
    }
    if ((new Set(values.map(value => value.string))).size > 1) {
      return 0;
    }
    return Math.max(...values.map(value => value.shift || 0));
  };
  const valuesAreAutoIncrementable = values => !!getValuesDiff(values);
  const values = [];
  for (let sRow = 0; sRow < sourceItems.length; sRow++) {
    const row = [];
    for (let sColumn = 0; sColumn < sourceColumns.length; sColumn++) {
      const source = sourceItems[sRow].item;
      const column = sourceColumns[sColumn].key;
      const value = source.hasOwnProperty(column)
        ? source[column].value
        : undefined;
      row.push(splitValue(value));
    }
    values.push(row);
  }
  const rotatedValues = [];
  for (let sColumn = 0; sColumn < sourceColumns.length; sColumn++) {
    const row = [];
    for (let sRow = 0; sRow < sourceItems.length; sRow++) {
      const source = sourceItems[sRow].item;
      const column = sourceColumns[sColumn].key;
      const value = source.hasOwnProperty(column)
        ? source[column].value
        : undefined;
      row.push(splitValue(value));
    }
    rotatedValues.push(row);
  }
  const getIncrementedValue = table => {
    const diffs = table.map(getValuesDiff);
    const shifts = table.map(getValuesShift);
    return (source, incrementRatio) => {
      if (source < 0 || source >= table.length) {
        return undefined;
      }
      const row = table[source];
      if (row.length === 0) {
        return undefined;
      }
      const last = row[row.length - 1];
      const diff = diffs[source];
      const shift = shifts[source] || 0;
      if (diff === undefined || last.number === undefined) {
        return undefined;
      }
      const number = last.number + incrementRatio * diff;
      const shiftedNumber = getShiftedNumber(
        !last.string
          ? number
          : Math.abs(number),
        shift
      );
      return `${last.string || ''}${shiftedNumber}`;
    };
  };
  if (insertedItems.length > 0 && rotatedValues.some(valuesAreAutoIncrementable)) {
    const fn = getIncrementedValue(rotatedValues);
    const processItem = (item, index) => {
      const source = sourceItems[index % sourceItems.length].item;
      const payload = {...item.item};
      for (let c = 0; c < targetColumns.length; c++) {
        const column = targetColumns[c].key;
        const value = fn(c, index + 1);
        const type = source.hasOwnProperty(column)
          ? source[column].type
          : undefined;
        if (value === undefined) {
          delete payload[column];
        } else {
          payload[column] = {
            value,
            type: type || 'string'
          };
        }
      }
      return mapItemSaveOperation(payload, options);
    };
    return {
      title: 'Fill cells',
      loadingMessage: 'Filling...',
      action: () => new Promise((resolve, reject) => {
        Promise
          .all(insertedItems.map(processItem))
          .then(resolve)
          .catch(reject);
      })
    };
  }
  if (insertedColumns.length > 0 && values.some(valuesAreAutoIncrementable)) {
    const fn = getIncrementedValue(values);
    const processItem = (item, index) => {
      const payload = {...item.item};
      for (let c = 0; c < insertedColumns.length; c++) {
        const sourceColumn = sourceColumns[c % sourceColumns.length].key;
        const targetColumn = insertedColumns[c].key;
        const value = fn(index, c + 1);
        const type = payload.hasOwnProperty(sourceColumn)
          ? payload[sourceColumn].type
          : undefined;
        if (value === undefined) {
          delete payload[targetColumn];
        } else {
          payload[targetColumn] = {
            value,
            type: type || 'string'
          };
        }
      }
      return mapItemSaveOperation(payload, options);
    };
    return {
      title: 'Fill cells',
      loadingMessage: 'Filling...',
      action: () => new Promise((resolve, reject) => {
        Promise
          .all(targetItems.map(processItem))
          .then(resolve)
          .catch(reject);
      })
    };
  }
  return undefined;
}
function buildAutoFillActions (elements, columns, source, target, backup, options) {
  if (!elements || !columns || !source || !target) {
    return undefined;
  }
  const sourceItems = elements
    .filter(item => item.row >= source.start.row && item.row <= source.end.row);
  const targetItems = elements
    .filter(item => item.row >= target.start.row && item.row <= target.end.row);
  const insertedItems = targetItems
    .filter(item => sourceItems.indexOf(item) === -1);
  if (target.start.row < source.start.row) {
    insertedItems.reverse();
  }
  const removedItems = sourceItems
    .filter(item => targetItems.indexOf(item) === -1);
  const sourceColumns = columns
    .filter(item => item.column >= source.start.column && item.column <= source.end.column)
    .filter(item => !/^(ID|createdDate)$/i.test(item.key));
  const targetColumns = columns
    .filter(item => item.column >= target.start.column && item.column <= target.end.column)
    .filter(item => !/^(ID|createdDate)$/i.test(item.key));
  const insertedColumns = targetColumns
    .filter(item => sourceColumns.indexOf(item) === -1);
  if (target.start.column < source.start.column) {
    insertedColumns.reverse();
  }
  const removedColumns = sourceColumns
    .filter(item => targetColumns.indexOf(item) === -1);
  if (sourceColumns.length === 0 || targetColumns.length === 0) {
    return [];
  }
  const actionOptions = {
    sourceItems,
    sourceColumns,
    targetItems,
    targetColumns,
    insertedItems,
    insertedColumns,
    removedItems,
    removedColumns,
    backup,
    ...options
  };
  const actions = [
    buildClearPropertiesAction(actionOptions),
    buildAutoFillAction(actionOptions),
    buildCopyAction(actionOptions)
  ]
    .filter(Boolean);
  if (actions.length > 0) {
    const undo = buildUndoOperation(actionOptions);
    actions.forEach(action => {
      action.revert = undo.action;
    });
    actions.push(undo);
  }
  return actions;
}

function AutoFillEntitiesMarker ({visible, handleSize = 10, markerSize = 5}) {
  const padding = Math.max(0, handleSize - markerSize);
  return (
    <div
      className={styles.autoFillEntitiesMarker}
      data-auto-fill-entities="true"
      style={{
        width: handleSize,
        height: handleSize,
        visibility: visible ? 'visible' : 'hidden',
        padding: `${padding}px 0px 0px ${padding}px`
      }}
    >
      <div
        data-auto-fill-entities="true"
        className={styles.marker}
      >
        {'\u00A0'}
      </div>
    </div>
  );
}

class AutoFillEntitiesActions extends React.Component {
  state = {
    visible: false
  };

  handleDropdownVisibility = visible => {
    this.setState({visible});
  };

  render () {
    const {
      actions,
      callback,
      className
    } = this.props;
    if (actions && actions.length > 0) {
      const handleMenuItem = ({key}) => {
        const index = +key;
        const action = actions[index];
        callback && callback(action);
      };
      return (
        <div
          className={classNames(styles.actions, className)}
        >
          <Dropdown
            overlay={(
              <Menu
                style={{
                  width: 200
                }}
                onClick={handleMenuItem}
              >
                {
                  actions.map((action, index) => (
                    <Menu.Item key={`${index}`}>
                      {action.title}
                    </Menu.Item>
                  ))
                }
              </Menu>
            )}
            trigger={['click']}
            onVisibleChange={this.handleDropdownVisibility}
          >
            <Button
              type="primary"
              size="small"
              style={{
                padding: '0 3px',
                margin: 5
              }}
              onMouseDown={e => e.stopPropagation()}
              onClick={e => e.stopPropagation()}
            >
              <Icon type="edit" />
              <Icon type="down" />
            </Button>
          </Dropdown>
        </div>
      );
    }
    return null;
  }
}

export {
  AutoFillEntitiesMarker,
  AutoFillEntitiesActions,
  buildAutoFillActions,
  isAutoFillEntitiesMarker,
  cellIsSelected,
  isLeftSideCell,
  isRightSideCell,
  isTopSideCell,
  isBottomSideCell,
  isRightCornerCell
};
