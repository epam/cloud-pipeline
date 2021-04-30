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
import {
  Table,
  Spin,
  Dropdown,
  Button,
  Menu
} from 'antd';
import COLUMNS from './columns';
import styles from './table.css';

function typeSorter (a, b) {
  return b.type.localeCompare(a.type);
};

class VersionedStorageTable extends React.Component {
  get data () {
    const {contents} = this.props;
    if (!contents) {
      return null;
    }
    return contents
      .map(content => ({
        ...content.commit,
        ...content.git_object
      })).sort(typeSorter);
  }

  get actions () {
    return {
      delete: (record) => console.log('delete', record),
      edit: (record) => console.log('edit', record),
      download: (record) => console.log('download', record)
    };
  }

  onRowClick = (record, index, event) => {
    if (event && event.target.dataset.action) {
      const buttonAction = event.target.dataset.action;
      this.actions[buttonAction] && this.actions[buttonAction](record);
    }
  }

  onCreateMenuClick = (event) => {
    console.log(`create ${event.key}`);
  }

  onCreate = (event) => {
    event && event.stopPropagation();
  }

  onUpload = (event) => {
    event && event.stopPropagation();
  }

  render () {
    if (!this.data) {
      return <Spin />;
    }
    const menu = (
      <Menu onClick={this.onCreateMenuClick}>
        <Menu.Item key="file">File</Menu.Item>
        <Menu.Item key="folder">Folder</Menu.Item>
      </Menu>
    );
    return (
      <div className={styles.tableContainer}>
        <div className={styles.tableControls}>
          <Dropdown.Button
            onClick={this.onCreate}
            overlay={menu}
            className={styles.tableControl}
            type="primary"
          >
            Create
          </Dropdown.Button>
          <Button
            className={styles.tableControl}
            onClick={this.onUpload}
          >
            Upload
          </Button>
        </div>
        <Table
          columns={COLUMNS}
          rowKey={(record) => record.id}
          dataSource={this.data}
          size="small"
          onRowClick={this.onRowClick}
        />
      </div>
    );
  }
}

VersionedStorageTable.PropTypes = {
  contents: PropTypes.object
};

export default VersionedStorageTable;
