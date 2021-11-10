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
import {DownOutlined, FileOutlined, FolderOutlined, PlusOutlined} from '@ant-design/icons';
import {Dropdown, Menu, Table, Spin, Button, Input, Modal, Row} from 'antd';
import classNames from 'classnames';
import UploadButton from '../../../../special/UploadButton';
import VSTableNavigation from './vs-table-navigation';
import roleModel from '../../../../../utils/roleModel';
import PipelineFileUpdate from '../../../../../models/pipelines/PipelineFileUpdate';
import checkFileExistence from '../utils';
import getColumns from './columns';
import TABLE_MENU_KEYS from './table-menu-keys';
import DOCUMENT_TYPES from '../document-types';
import styles from './table.css';
import '../../../../../staticStyles/vs-storage.css';

function typeSorter (a, b) {
  return b.type.localeCompare(a.type);
}

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
}

class VersionedStorageTable extends React.Component {
  state = {
    comment: '',
    deletingDocument: null
  }

  get data () {
    const {
      contents,
      showNavigateBack,
      versionedStorage
    } = this.props;
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
        ...content.git_object,
        mask: versionedStorage
          ? versionedStorage.mask
          : 0
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

  get uploadPath () {
    const {pipelineId, path} = this.props;
    return PipelineFileUpdate.uploadUrl(
      pipelineId,
      path || '/',
      {trimTrailingSlash: !!path}
    );
  }

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
        <div>
          {`Are you sure you want to delete ${type} '${record.name}'?`}
        </div>
        {type === 'folder' && (
          <div>
            All child folders and files will be removed.
          </div>
        )}
        <Input.TextArea
          placeholder="Please type a comment"
          rows={4}
          onChange={this.onCommentChange}
        />
      </div>
    );
    Modal.confirm({
      title: `Remove ${type}`,
      content: content,
      okType: 'danger',
      onOk: () => callback && callback(record, comment)
    });
  };

  onRowClick = (record) => {
    const {onRowClick} = this.props;
    if (!record) {
      return;
    }
    return onRowClick && onRowClick(record);
  };

  onCreateActionSelect = (action) => {
    const {onTableActionClick} = this.props;
    onTableActionClick && onTableActionClick(action);
  };

  onUploadFinished = (uploadedFiles) => {
    const {afterUpload} = this.props;
    afterUpload && afterUpload(uploadedFiles);
  };

  checkFilesExistence = async (files) => {
    const {
      pipelineId,
      path
    } = this.props;
    return Promise.all(files.map((file) => checkFileExistence(
      pipelineId,
      `${path || ''}${file.name}`
    ).then((exist) => (exist ? file : undefined))
    ));
  };

  askUserAboutConflicts = async (files, duplicateFiles) => {
    const singleFile = duplicateFiles.length === 1;
    const getModalTitle = (contentFiles) => {
      return singleFile
        ? (
          <span
            style={{wordBreak: 'break-word'}}
          >
            File {contentFiles[0]?.name || ''} already exist. Overwrite?
          </span>
        ) : (
          <span>
            Some files already exist:
          </span>
        );
    };
    const getModalContent = (contentFiles) => {
      if (!contentFiles.length || singleFile) {
        return null;
      }
      return (
        <div className={styles.conflictModal}>
          <span className={styles.conflictTitle}>
            {`Duplicate ${singleFile ? 'file' : 'files'}:`}
          </span>
          <ul className={styles.conflictList}>
            {contentFiles.map(file => (
              <li key={file.name}>
                {file.name}
              </li>
            ))}
          </ul>
        </div>
      );
    };
    return new Promise((resolve) => {
      if (duplicateFiles.length) {
        Modal.confirm({
          title: getModalTitle(duplicateFiles),
          style: {
            wordWrap: 'break-word'
          },
          onCancel: () => {
            resolve([]);
          },
          onOk: () => {
            resolve(files);
          },
          okText: `Overwrite`,
          cancelText: 'Cancel',
          content: getModalContent(duplicateFiles)
        });
      } else {
        resolve(files);
      }
    });
  };

  validateAndFilter = async (files) => {
    const duplicateFiles = await this.checkFilesExistence(files)
      .then(files => files.filter(Boolean));
    let uploadFiles = files;
    if (duplicateFiles.length) {
      uploadFiles = await this.askUserAboutConflicts(files, duplicateFiles);
    }
    return uploadFiles;
  };

  renderTableControls = () => {
    const {
      controlsEnabled,
      versionedStorage,
      path,
      onNavigate
    } = this.props;
    if (roleModel.writeAllowed(versionedStorage)) {
      return (
        <div className={styles.tableControlsContainer}>
          <VSTableNavigation
            path={path}
            onNavigate={onNavigate}
          />
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
                    <FolderOutlined /> Folder
                  </Menu.Item>
                  <Menu.Item
                    key={TABLE_MENU_KEYS.file}
                    disabled={!controlsEnabled}
                  >
                    <FileOutlined /> File
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
                <PlusOutlined />
                Create
                <DownOutlined />
              </Button>
            </Dropdown>
            <UploadButton
              multiple
              synchronous
              onRefresh={this.onUploadFinished}
              validateAndFilter={this.validateAndFilter}
              title={'Upload'}
              action={this.uploadPath}
            />
          </div>
        </div>
      );
    }
    return null;
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
          Cancel
        </Button>
        <Button
          danger
          onClick={() => {
            onDeleteDocument && onDeleteDocument(deletingDocument, comment);
            this.hideDeleteDialog();
          }}
        >
          Delete
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
          <div style={{paddingLeft: '15px'}}>
            {`Are you sure you want to delete ${type} '${deletingDocument.name}'?`}
          </div>
          {type === 'folder' && (
            <div style={{textAlign: 'center'}}>
              All child folders and files will be removed.
            </div>
          )}
          <div
            style={{padding: '15px', display: 'flex', flexWrap: 'nowrap'}}
          >
            <span style={{marginRight: '15px'}}>
              Comment:
            </span>
            <Input.TextArea
              rows={1}
              value={comment}
              onChange={this.onCommentChange}
            />
          </div>
        </div>
      </Modal>
    );
  };

  render () {
    const {
      className,
      pending,
      style
    } = this.props;
    if (!this.data) {
      return <Spin />;
    }
    return (
      <div
        className={
          classNames(
            styles.tableContainer,
            className
          )
        }
        style={style}
      >
        {this.renderTableControls()}
        <Table
          className={
            classNames(
              styles.table,
              'versioned-storage-contents'
            )
          }
          columns={getColumns(this.actions)}
          rowKey={(record) => record.name}
          dataSource={this.data}
          size="small"
          onRow={item => ({
            onClick: () => this.onRowClick(item)
          })}
          pagination={false}
          rowClassName={() => styles.tableRow}
          loading={pending}
        />
        {this.renderDeleteDialog()}
      </div>
    );
  }
}

VersionedStorageTable.propTypes = {
  className: PropTypes.string,
  contents: PropTypes.array,
  onRowClick: PropTypes.func,
  showNavigateBack: PropTypes.bool,
  pending: PropTypes.bool,
  controlsEnabled: PropTypes.bool,
  onTableActionClick: PropTypes.func,
  onDeleteDocument: PropTypes.func,
  onRenameDocument: PropTypes.func,
  onDownloadFile: PropTypes.func,
  pipelineId: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  path: PropTypes.string,
  afterUpload: PropTypes.func,
  style: PropTypes.object,
  versionedStorage: PropTypes.object
};

export default VersionedStorageTable;
