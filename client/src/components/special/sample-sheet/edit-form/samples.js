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
import PropTypes from 'prop-types';
import HotTable from 'react-handsontable';
import classNames from 'classnames';
import styles from './sample-sheet-edit-form.css';

const ROW_HEIGHT = 23;

function samplesAreEqual (a = [], b = []) {
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    const aLine = a[i] || [];
    const bLine = b[i] || [];
    if (aLine.length !== bLine.length) {
      return false;
    }
    for (let j = 0; j < aLine.length; j++) {
      if (aLine[j] !== bLine[j]) {
        return false;
      }
    }
  }
  return true;
}

const EDITABLE_MENU = [
  'row_above',
  'row_below',
  '---------',
  'col_left',
  'col_right',
  '---------',
  'remove_row',
  'remove_col',
  '---------',
  'undo',
  'redo',
  '---------',
  'cut',
  'copy'
];

const READ_ONLY_MENU = [
  'cut',
  'copy'
];

class SamplesEditor extends React.PureComponent {
  initializeEditor = (editor) => {
    this.editor = editor;
  };

  onChange = () => {
    if (this.editor && this.editor.hotInstance) {
      const newData = this.editor.hotInstance.getData();
      if (!samplesAreEqual(newData, this.props.data)) {
        const {onChange, editable} = this.props;
        if (onChange && editable) {
          onChange(newData);
        }
      }
    }
  };

  render () {
    const {
      readOnly: disabled,
      editable,
      className,
      style,
      data = []
    } = this.props;
    const readOnly = disabled || !editable;
    return (
      <div
        className={
          classNames(
            styles.samples,
            className
          )
        }
        style={style}
      >
        <div className={styles.title}>
          Samples:
        </div>
        <HotTable
          root="hot"
          height={Math.max(2, data.length + 1) * ROW_HEIGHT}
          ref={this.initializeEditor}
          data={data.map(o => o.slice())}
          readOnly={readOnly}
          readOnlyCellClassName={classNames('readonly-cell', 'cp-table-cell')}
          manualColumnResize
          manualRowResize
          contextMenu={readOnly ? READ_ONLY_MENU : EDITABLE_MENU}
          afterChange={this.onChange}
          afterCreateRow={this.onChange}
          afterCreateCol={this.onChange}
          afterRemoveRow={this.onChange}
          afterRemoveCol={this.onChange}
        />
      </div>
    );
  }
}

SamplesEditor.propTypes = {
  className: PropTypes.string,
  editable: PropTypes.bool,
  readOnly: PropTypes.bool,
  style: PropTypes.object,
  onChange: PropTypes.func,
  data: PropTypes.array
};

export default SamplesEditor;
