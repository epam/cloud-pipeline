/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Collapse,
  Checkbox,
  Modal
} from 'antd';
import {DocumentColumns} from '../../utilities';
import styles from './export.css';

function columnsAreEqual (a, b) {
  const aa = [...new Set((a || []).map((column) => column.key))].sort();
  const bb = [...new Set((b || []).map((column) => column.key))].sort();
  if (aa.length !== bb.length) {
    return false;
  }
  for (let i = 0; i < aa.length; i++) {
    if (aa[i] !== bb[i]) {
      return false;
    }
  }
  return true;
}

function isMainColumn (columnKey) {
  return DocumentColumns.find((column) => column.key === columnKey);
}

const GROUPS = {
  main: 'main',
  other: 'other'
};

class ExportConfigurationModal extends React.Component {
  state = {
    configuration: [],
    expanded: ['main']
  };

  componentDidMount () {
    this.updateDefaultConfiguration();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      (prevProps.visible !== this.props.visible && this.props.visible) ||
      !columnsAreEqual(prevProps.columns, this.props.columns)
    ) {
      this.updateDefaultConfiguration();
    }
  }

  get groupedColumns () {
    const {
      columns = []
    } = this.props;
    return {
      [GROUPS.main]: columns.filter((column) => isMainColumn(column.key)),
      [GROUPS.other]: columns.filter((column) => !isMainColumn(column.key))
    };
  }

  updateDefaultConfiguration = () => {
    const {
      columns = []
    } = this.props;
    this.setState({
      configuration: columns.map((column) => column.key),
      expanded: ['main']
    });
  };

  handleExportClick = () => {
    const {
      onExport
    } = this.props;
    if (typeof onExport === 'function') {
      const {
        configuration = []
      } = this.state;
      onExport(configuration);
    }
  };

  onChangeExpandedKeys = (keys) => this.setState({expanded: keys});

  columnSelected = (columnKey) => {
    const {configuration = []} = this.state;
    return configuration.includes(columnKey);
  };

  changeColumnSelection = (columnKey, selected) => {
    let columnIsSelected = selected;
    if (columnIsSelected === undefined) {
      columnIsSelected = !this.columnSelected(columnKey);
    }
    const {configuration = []} = this.state;
    if (columnIsSelected) {
      this.setState({
        configuration: [
          ...configuration.filter((key) => key !== columnKey),
          columnKey
        ]
      });
    } else {
      this.setState({
        configuration: [
          ...configuration.filter((key) => key !== columnKey)
        ]
      });
    }
  };

  onCheckboxChange = (columnKey) => (event) =>
    this.changeColumnSelection(columnKey, event.target.checked);

  renderColumnsList = (columns = []) => (
    <div className={styles.columns}>
      {
        columns.map((column) => (
          <div
            key={column.key}
            className={styles.column}
          >
            <Checkbox
              checked={this.columnSelected(column.key)}
              onChange={this.onCheckboxChange(column.key)}
            >
              {column.name || column.key}
            </Checkbox>
          </div>
        ))
      }
    </div>
  );

  clearSelection = () => this.setState({configuration: []});

  selectAll = () => {
    const {
      columns = []
    } = this.props;
    this.setState({
      configuration: columns.map((column) => column.key)
    });
  }

  onToggleGroupSelection = (event, group, selectAll) => {
    const {configuration} = this.state;
    if (!this.groupedColumns[group]) {
      return;
    }
    const groupKeys = this.groupedColumns[group].map((column) => column.key);
    event && event.stopPropagation();
    if (selectAll) {
      return this.setState({
        configuration: [...new Set([...configuration, ...groupKeys])]
      });
    }
    return this.setState({
      configuration: configuration.filter(key => !groupKeys.includes(key))
    });
  };

  renderCollapseHeader = (title, group) => {
    const allGroupSelected = !this.groupedColumns[group]
      .some(column => !this.columnSelected(column.key));
    const hasSelection = this.groupedColumns[group]
      .some(column => this.columnSelected(column.key));
    return (
      <div>
        <Checkbox
          checked={allGroupSelected}
          indeterminate={!allGroupSelected && hasSelection}
          onClick={evt => this.onToggleGroupSelection(evt, group, !allGroupSelected)}
          style={{margin: '0 7px 0 3px'}}
        />
        {title}
      </div>
    );
  };

  render () {
    const {
      visible,
      onCancel,
      columns = []
    } = this.props;
    const {
      configuration = [],
      expanded = ['main']
    } = this.state;
    const allSelected = columns.length > 0 &&
      !columns.some((column) => !this.columnSelected(column.key));
    const selectedCount = columns.filter((column) => this.columnSelected(column.key)).length;
    return (
      <Modal
        visible={visible}
        title="Configure export"
        onCancel={onCancel}
        footer={(
          <div
            className={styles.exportFooter}
          >
            <Button
              onClick={onCancel}
            >
              CANCEL
            </Button>
            <Button
              disabled={configuration.length === 0}
              type="primary"
              onClick={this.handleExportClick}
            >
              EXPORT
            </Button>
          </div>
        )}
      >
        <div style={{marginBottom: 5}}>
          <Button
            disabled={selectedCount === 0}
            style={{marginRight: 5}}
            onClick={this.clearSelection}
            size="small"
          >
            Clear selection
          </Button>
          <Button
            disabled={allSelected}
            style={{marginRight: 5}}
            onClick={this.selectAll}
            size="small"
          >
            Select all
          </Button>
        </div>
        <Collapse
          activeKey={expanded}
          onChange={this.onChangeExpandedKeys}
          bordered
        >
          <Collapse.Panel
            key="main"
            header={this.renderCollapseHeader('Main columns', GROUPS.main)}
          >
            {this.renderColumnsList(this.groupedColumns.main)}
          </Collapse.Panel>
          {
            this.groupedColumns.other.length > 0 && (
              <Collapse.Panel
                key="other"
                header={this.renderCollapseHeader('Other columns', GROUPS.other)}
              >
                {this.renderColumnsList(this.groupedColumns.other)}
              </Collapse.Panel>
            )
          }
        </Collapse>
      </Modal>
    );
  }
}

ExportConfigurationModal.propTypes = {
  visible: PropTypes.bool,
  onCancel: PropTypes.func,
  onExport: PropTypes.func,
  columns: PropTypes.arrayOf(PropTypes.shape({
    key: PropTypes.string,
    name: PropTypes.string
  }))
};

export default ExportConfigurationModal;
