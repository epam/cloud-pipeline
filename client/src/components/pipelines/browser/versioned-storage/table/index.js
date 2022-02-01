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
  Button,
  Icon,
  Input,
  Modal,
  Row, message
} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import Dropdown from 'rc-dropdown';
import classNames from 'classnames';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import UploadButton from '../../../../special/UploadButton';
import VSTableNavigation from './vs-table-navigation';
import roleModel from '../../../../../utils/roleModel';
import PipelineFileUpdate from '../../../../../models/pipelines/PipelineFileUpdate';
import checkFileExistence from '../utils/check-file-existence';
import GitIgnore from '../utils/git-ignore-utils';
import COLUMNS from './columns';
import TABLE_MENU_KEYS from './table-menu-keys';
import DOCUMENT_TYPES from '../document-types';
import downloadPipelineFile from '../../../version/utilities/download-pipeline-file';
import PipelineCodeForm from '../../../version/code/forms/PipelineCodeForm';
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

function isFolder (document) {
  if (!document || !document.type) {
    return false;
  }
  return document.type.toLowerCase() === DOCUMENT_TYPES.tree;
}

@inject((stores, props) => {
  const {
    pipelineId,
    lastCommit
  } = props;
  return {
    gitIgnore: GitIgnore.getGitIgnore(pipelineId, lastCommit)
  };
})
@observer
class VersionedStorageTable extends React.Component {
  state = {
    comment: '',
    deletingDocument: null,
    gitIgnoreDialogVisible: false
  }

  @computed
  get data () {
    const {
      contents,
      showNavigateBack,
      versionedStorage,
      gitIgnore
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
          : 0,
        isFolder: isFolder(content.git_object),
        ignored: gitIgnore.pathIsIgnored(content.git_object.path, isFolder(content.git_object)),
        hasExplicitIgnoreRule: gitIgnore.hasPathRule(
          content.git_object.path,
          isFolder(content.git_object)
        ),
        showGitIgnoreActions: !/\.gitignore$/i.test(content.git_object.path) &&
          !this.currentFolderIgnored
      })).sort(typeSorter);
    return showNavigateBack ? [navigateBack, ...content] : content;
  };

  get actions () {
    const {
      pipelineId,
      lastCommit,
      onRenameDocument
    } = this.props;
    const onDownloadFile = (record) => downloadPipelineFile(pipelineId, lastCommit, record.path);
    return {
      delete: (record) => this.showDeleteDialog(record),
      edit: (record) => onRenameDocument && onRenameDocument(record),
      download: onDownloadFile,
      addGitIgnore: (record) => this.ignoreItem(record.path, record.isFolder),
      removeGitIgnore: (record) => this.stopIgnoreItem(record.path, record.isFolder)
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

  get currentFolderIgnored () {
    const {
      gitIgnore,
      path
    } = this.props;
    return gitIgnore.folderIsIgnored(path);
  }

  updateFileContent = async (path, contents, comment) => {
    const {pipelineId, onRefresh} = this.props;
    if (!path) {
      return;
    }
    const request = new PipelineFileUpdate(pipelineId);
    const hide = message.loading('Committing changes...');
    await request.send({
      contents: contents,
      comment,
      path: path,
      lastCommitId: this.lastCommitId
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      onRefresh && onRefresh();
    }
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

  openGitIgnoreDialog = () => {
    this.setState({
      gitIgnoreDialogVisible: true
    });
  };

  closeGitIgnoreDialog = () => {
    this.setState({
      gitIgnoreDialogVisible: false
    });
  };

  saveGitIgnoreContent = async (content, commitMessage) => {
    await this.updateFileContent('.gitignore', content, commitMessage);
    this.closeGitIgnoreDialog();
  };

  ignoreItem = (path, itemIsFolder = false) => {
    const {gitIgnore} = this.props;
    const content = itemIsFolder
      ? gitIgnore.ignoreFolder(path).join('\n')
      : gitIgnore.ignoreFile(path).join('\n');
    return this.updateFileContent(
      '.gitignore',
      content,
      `Ignoring ${path || '/'} ${itemIsFolder ? 'folder' : 'file'}`
    );
  };

  stopIgnoreItem = (path, itemIsFolder = false) => {
    const {gitIgnore} = this.props;
    const content = itemIsFolder
      ? gitIgnore.stopIgnoreFolder(path).join('\n')
      : gitIgnore.stopIgnoreFile(path).join('\n');
    return this.updateFileContent(
      '.gitignore',
      content,
      `Tracking ${path || '/'} ${itemIsFolder ? 'folder' : 'file'}`
    );
  };

  renderGitIgnoreActions = () => {
    const {
      controlsEnabled,
      path,
      gitIgnore
    } = this.props;
    const items = [
      (
        <MenuItem
          key="configure"
          disabled={!controlsEnabled}
        >
          Configure
        </MenuItem>
      )
    ];
    if (
      path &&
      !this.currentFolderIgnored
    ) {
      items.push((
        <MenuItem
          key="ignore-current"
          disabled={!controlsEnabled}
        >
          Ignore current folder
        </MenuItem>
      ));
    }
    if (
      path &&
      this.currentFolderIgnored &&
      gitIgnore.hasFolderRule(path)
    ) {
      items.push((
        <MenuItem
          key="stop-ignore-current"
          disabled={!controlsEnabled}
        >
          Track current folder
        </MenuItem>
      ));
    }
    if (!path || items.length === 1) {
      return (
        <Button
          id="ignore-button"
          size="small"
          className={styles.tableControl}
          disabled={!controlsEnabled}
          onClick={this.openGitIgnoreDialog}
        >
          <Icon type="setting" />
          Ignored files
        </Button>
      );
    }
    const onHeaderGitActionSelect = ({key}) => {
      switch (key) {
        case 'configure':
          this.openGitIgnoreDialog();
          break;
        case 'ignore-current':
          this.ignoreItem(path, true);
          break;
        case 'stop-ignore-current':
          this.stopIgnoreItem(path, true);
          break;
      }
    };
    return (
      <Dropdown
        placement="bottomRight"
        trigger={['hover']}
        key="git actions"
        overlay={
          <Menu
            selectedKeys={[]}
            onClick={onHeaderGitActionSelect}
          >
            {items}
          </Menu>
        }
      >
        <Button
          id="ignore-button"
          size="small"
          className={styles.tableControl}
          disabled={!controlsEnabled}
        >
          <Icon type="setting" />
          Ignored files
          <Icon type="down" />
        </Button>
      </Dropdown>
    );
  }

  renderTableControls = () => {
    const {
      controlsEnabled,
      versionedStorage,
      path,
      onNavigate
    } = this.props;
    const writeAllowed = roleModel.writeAllowed(versionedStorage);
    return (
      <div
        className={
          classNames(
            styles.tableControlsContainer,
            'cp-versioned-storage-table-header'
          )
        }
      >
        <VSTableNavigation
          path={path}
          onNavigate={onNavigate}
        />
        <div className={styles.tableControls}>
          {
            writeAllowed && (
              <Dropdown
                placement="bottomRight"
                trigger={['hover']}
                overlay={
                  <Menu
                    selectedKeys={[]}
                    onClick={this.onCreateActionSelect}
                    style={{width: 200}}>
                    <MenuItem
                      key={TABLE_MENU_KEYS.folder}
                      disabled={!controlsEnabled}
                    >
                      <Icon type="folder" /> Folder
                    </MenuItem>
                    <MenuItem
                      key={TABLE_MENU_KEYS.file}
                      disabled={!controlsEnabled}
                    >
                      <Icon type="file" /> File
                    </MenuItem>
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
            )
          }
          {writeAllowed && this.renderGitIgnoreActions()}
          {
            writeAllowed && (
              <UploadButton
                multiple
                synchronous
                onRefresh={this.onUploadFinished}
                validateAndFilter={this.validateAndFilter}
                title={'Upload'}
                action={this.uploadPath}
              />
            )
          }
        </div>
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
          Cancel
        </Button>
        <Button
          type="danger"
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
          <Row style={{paddingLeft: '15px'}}>
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
      style,
      pipelineId,
      lastCommit
    } = this.props;
    const {
      gitIgnoreDialogVisible
    } = this.state;
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
          columns={COLUMNS}
          rowKey={(record) => record.name}
          dataSource={this.data}
          size="small"
          onRowClick={this.onRowClick}
          pagination={false}
          rowClassName={(record) => classNames(
            styles.tableRow,
            {
              'cp-disabled': record.ignored
            }
          )}
          loading={pending}
        />
        {this.renderDeleteDialog()}
        <PipelineCodeForm
          pipelineId={pipelineId}
          version={lastCommit}
          path=".gitignore"
          visible={gitIgnoreDialogVisible}
          cancel={this.closeGitIgnoreDialog}
          save={this.saveGitIgnoreContent}
          editable
          ignoreError
          editMode
        />
      </div>
    );
  }
}

VersionedStorageTable.PropTypes = {
  className: PropTypes.string,
  contents: PropTypes.object,
  onRowClick: PropTypes.func,
  showNavigateBack: PropTypes.bool,
  pending: PropTypes.bool,
  lastCommit: PropTypes.string,
  controlsEnabled: PropTypes.bool,
  onTableActionClick: PropTypes.func,
  onDeleteDocument: PropTypes.func,
  onRenameDocument: PropTypes.func,
  onRefresh: PropTypes.func,
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
