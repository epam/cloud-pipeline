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
  Menu,
  Icon
} from 'antd';
import COLUMNS from './columns';
import styles from './table.css';

function typeSorter (a, b) {
  return b.type.localeCompare(a.type);
};

class VersionedStorageTable extends React.Component {
  get data () {
    const {contents, showNavigateBack} = this.props;
    if (!contents) {
      return null;
    }
    const navigateBack = {
      name: '..',
      type: 'navback'
    };
    const content = contents
      .map(content => ({
        ...content.commit,
        ...content.git_object
      })).sort(typeSorter);
    return showNavigateBack ? [navigateBack, ...content] : content;
  }

  get actions () {
    return {
      delete: (record) => console.log('delete', record),
      edit: (record) => console.log('edit', record),
      download: (record) => console.log('download', record)
    };
  }

  onRowClick = (document, index, event) => {
    const {onRowClick} = this.props;
    if (!document) {
      return;
    }
    if (event && event.target.dataset.action) {
      const buttonAction = event.target.dataset.action;
      return this.actions[buttonAction] && this.actions[buttonAction](document);
    }
    return onRowClick && onRowClick(document);
  }

  onCreateActionSelect = (event) => {
    console.log(event);
  }

  onUpload = (event) => {
    event && event.stopPropagation();
  }

  renderTableControls = () => {
    return (
      <div className={styles.tableControls}>
        <Dropdown
          placement="bottomRight"
          trigger={['hover']}
          overlay={
            <Menu
              selectedKeys={[]}
              onClick={this.onCreateActionSelect}
              style={{width: 200}}>
              <Menu.Item
                key="folder"
              >
                <Icon type="folder" /> Folder
              </Menu.Item>
              <Menu.Item
                key="file"
              >
                <Icon type="file" /> File
              </Menu.Item>
            </Menu>
          }
          key="create actions">
          <Button
            type="primary"
            id="create-button"
            size="small"
            className={styles.tableControl}
          >
            <Icon type="plus" />
            Create
            <Icon type="down" />
          </Button>
        </Dropdown>
        <Button
          className={styles.tableControl}
          onClick={this.onUpload}
          size="small"
        >
          Upload
        </Button>
      </div>
    );
  }

  render () {
    const {pending} = this.props;
    if (!this.data) {
      return <Spin />;
    }
    return (
      <div className={styles.tableContainer}>
        {this.renderTableControls()}
        <Table
          columns={COLUMNS}
          rowKey={(record) => record.id}
          dataSource={this.data}
          size="small"
          onRowClick={this.onRowClick}
          pagination={false}
          rowClassName={() => styles.tableRow}
          loading={pending}
        />
      </div>
    );
  }
}

VersionedStorageTable.PropTypes = {
  contents: PropTypes.object,
  onRowClick: PropTypes.func,
  showNavigateBack: PropTypes.boolean,
  pending: PropTypes.boolean
};

export default VersionedStorageTable;
