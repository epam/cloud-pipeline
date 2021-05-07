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
  Icon,
  Input,
  Modal,
  Row
} from 'antd';
import COLUMNS from './columns';
import TABLE_MENU_KEYS from './table-menu-keys';
import DOCUMENT_TYPES from '../document-types';
import styles from './table.css';

function typeSorter (a, b) {
  return b.type.localeCompare(a.type);
};

function getDocumentType (document) {
  if (!document || !document.type) {
    return;
  }
  let type;
  switch (document.type.toLowerCase()) {
    case DOCUMENT_TYPES.tree:
      type = 'folder';
      break;
    case DOCUMENT_TYPES.blob:
      type = 'file';
      break;
  }
  return type;
};

class VersionedStorageTable extends React.Component {
  state = {
    comment: '',
    deletingDocument: null
  }

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
  };

  get actions () {
    const {
      onRenameDocument,
      onDownloadFile
    } = this.props;
    return {
      delete: (record) => this.showDeleteDialog(record),
      edit: (record) => onRenameDocument && onRenameDocument(record),
      download: (record) => onDownloadFile && onDownloadFile(record)
    };
  };

  onCommentChange = (event) => {
    if (event) {
      this.setState({comment: event.target.value});
    }
  };

  onCommentClear = () => {
    this.setState({comment: undefined});
  };

  showDeleteDialog = (record) => {
    this.setState({deletingDocument: record});
  };

  hideDeleteDialog = () => {
    this.setState({comment: '', deletingDocument: null});
  };

  confirmDelete = (record, callback) => {
    this.setState({shofDeleteDialog: true});
    const {comment} = this.state;
    const type = getDocumentType(record);
    if (!record || !callback || !type) {
      return;
    }
    const content = (
      <div>
        <Row>
          {`Are you sure you want to delete ${type} '${record.name}'?`}
        </Row>
        {type === 'folder' && (
          <Row>
            All child folders and files will be removed.
          </Row>
        )}
        <Input
          type="textarea"
          placeholder="Please type a comment"
          rows={4}
          onChange={this.onCommentChange}
        />
      </div>
    );
    Modal.confirm({
      title: `Remove ${type}`,
      content: content,
      style: {wordWrap: 'break-word'},
      onOk: () => callback && callback(record, comment)
    });
  };

  onRowClick = (record, index, event) => {
    const {onRowClick} = this.props;
    if (!record) {
      return;
    }
    if (event && event.target.dataset.action) {
      const buttonAction = event.target.dataset.action;
      return this.actions[buttonAction] && this.actions[buttonAction](record);
    }
    return onRowClick && onRowClick(record);
  };

  onCreateActionSelect = (action) => {
    const {onTableActionClick} = this.props;
    onTableActionClick && onTableActionClick(action);
  };

  onUpload = (event) => {
    event && event.stopPropagation();
  };

  renderTableControls = () => {
    const {controlsEnabled} = this.props;
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
                key={TABLE_MENU_KEYS.folder}
                disabled={!controlsEnabled}
              >
                <Icon type="folder" /> Folder
              </Menu.Item>
              <Menu.Item
                key={TABLE_MENU_KEYS.file}
                disabled={!controlsEnabled}
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
            disabled={!controlsEnabled}
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
          disabled={!controlsEnabled}
        >
          Upload
        </Button>
      </div>
    );
  };

  renderDeleteDialog = () => {
    const {deletingDocument, comment} = this.state;
    const {onDeleteDocument} = this.props;
    const type = getDocumentType(deletingDocument);
    if (!deletingDocument || !type) {
      return;
    }
    const footer = (
      <Row type="flex" justify="space-between">
        <Button
          onClick={this.hideDeleteDialog}
        >
          CANCEL
        </Button>
        <Button
          type="danger"
          onClick={() => {
            onDeleteDocument && onDeleteDocument(deletingDocument, comment);
            this.hideDeleteDialog();
          }}
        >
          DELETE
        </Button>
      </Row>
    );
    return (
      <Modal
        visible={!!deletingDocument}
        title={`Remove ${type}`}
        onCancel={this.hideDeleteDialog}
        footer={footer}
        width="500px"
      >
        <div>
          <Row style={{textAlign: 'center'}}>
            {`Are you sure you want to delete ${type} '${deletingDocument.name}'?`}
          </Row>
          {type === 'folder' && (
            <Row style={{textAlign: 'center'}}>
              All child folders and files will be removed.
            </Row>
          )}
          <div
            style={{padding: '15px', display: 'flex', flexWrap: 'nowrap'}}
          >
            <span style={{marginRight: '15px'}}>
              Comment:
            </span>
            <Input
              type="textarea"
              rows={2}
              value={comment}
              onChange={this.onCommentChange}
            />
          </div>
        </div>
      </Modal>
    );
  };

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
          rowKey={(record) => record.name}
          dataSource={this.data}
          size="small"
          onRowClick={this.onRowClick}
          pagination={false}
          rowClassName={() => styles.tableRow}
          loading={pending}
        />
        {this.renderDeleteDialog()}
      </div>
    );
  }
}

VersionedStorageTable.PropTypes = {
  contents: PropTypes.object,
  onRowClick: PropTypes.func,
  showNavigateBack: PropTypes.bool,
  pending: PropTypes.bool,
  controlsEnabled: PropTypes.bool,
  onTableActionClick: PropTypes.func,
  onDeleteDocument: PropTypes.func,
  onRenameDocument: PropTypes.func,
  onDownloadFile: PropTypes.func
};

export default VersionedStorageTable;
