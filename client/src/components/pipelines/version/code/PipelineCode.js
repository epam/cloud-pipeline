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

import React, {Component} from 'react';
import {inject, observer} from 'mobx-react';
import PipelineFileUpdate from '../../../../models/pipelines/PipelineFileUpdate';
import PipelineFileDelete from '../../../../models/pipelines/PipelineFileDelete';
import PipelineFolderUpdate from '../../../../models/pipelines/PipelineFolderUpdate';
import PipelineFolderDelete from '../../../../models/pipelines/PipelineFolderDelete';
import PipelineCodeForm from './forms/PipelineCodeForm';
import PipelineCodeSourceNameDialog from './forms/PipelineCodeSourceNameDialog';
import {
  Breadcrumb,
  Button,
  Col,
  Table,
  Icon,
  Row,
  Modal,
  message} from 'antd';
import styles from './PipelineCode.css';
import parseQueryParameters from '../../../../utils/queryParameters';
import roleModel from '../../../../utils/roleModel';
import LoadingView from '../../../special/LoadingView';
import UploadButton from '../../../special/UploadButton';

@inject(({pipelines, routing}, {onReloadTree, params}) => {
  const queryParameters = parseQueryParameters(routing);
  const path = queryParameters.path ? `src/${parseQueryParameters(routing).path}` : undefined;
  return {
    onReloadTree,
    path: parseQueryParameters(routing).path,
    source: pipelines.getSource(params.id, params.version, path),
    pipeline: pipelines.getPipeline(params.id),
    pipelines,
    pipelineId: params.id,
    version: params.version,
    pipelineVersions: pipelines.versionsForPipeline(params.id),
    routing
  };
})
@observer
export default class PipelineCode extends Component {

  state = {
    createFileDialog: false,
    editFileDialog: false,
    createFolderDialog: false,
    renameFolder: null,
    renameFile: null,
    editFile: null
  };

  scriptsColumns = [
    {
      key: 'type',
      className: styles.sourceItemTypeColumn,
      render: item => this.renderSourceItemType(item)
    },
    {
      dataIndex: 'name',
      key: 'name',
      title: 'Scripts'
    },
    {
      key: 'actions',
      render: item => this.renderSourceItemActions(item)

    }
  ];

  get canModifySources () {
    if (!this.props.pipeline.loaded) {
      return false;
    }
    return roleModel.writeAllowed(this.props.pipeline.value) &&
      this.props.version === this.props.pipeline.value.currentVersion.name;
  };

  renderSourceItemType = (item) => {
    return item.type.toLowerCase() === 'tree'
      ? <Icon type="folder" className={styles.sourceItemType} />
      : <div />;
  };

  renderSourceItemActions = (item) => {
    if (item.editable && this.canModifySources) {
      if (item.type.toLowerCase() === 'tree') {
        return (
          <Row type="flex" justify="end">
            <Button className={styles.sourceItemAction}
                    onClick={(event) => this.openRenameFolderDialog(item.name, event)}
                    size="small">Rename</Button>
            <Button className={styles.sourceItemAction}
                    onClick={(event) => this.deleteFolderConfirm(item, event)}
                    type="danger"
                    size="small"><Icon type="delete" /> Delete</Button>
          </Row>
        );
      } else {
        return (
          <Row type="flex" justify="end">
            <Button className={styles.sourceItemAction}
                    onClick={(event) => this.openRenameFileDialog(item.name, event)}
                    size="small">Rename</Button>
            <Button className={styles.sourceItemAction}
                    onClick={(event) => this.deleteFileConfirm(item, event)}
                    type="danger"
                    size="small"><Icon type="delete" /> Delete</Button>
          </Row>
        );
      }
    } else {
      return <Row />;
    }
  };

  createScriptsTableData = () => {
    let sources = [];
    if (this.props.source.loaded) {
      sources = this.props.source.value || [];
    }

    const tableData = [];

    if (this.props.path !== undefined && this.props.path !== null) {
      const parts = this.props.path.split('/');
      let parentDirectory = '';
      for (let i = 0; i < parts.length - 1; i++) {
        if (i > 0) {
          parentDirectory += `/${parts[i]}`;
        } else {
          parentDirectory = parts[i];
        }
      }
      tableData.push({
        key: 'parent directory',
        name: '..',
        type: 'tree',
        path: parentDirectory,
        editable: false
      });
    }

    for (let source of sources) {
      if (!source) {
        continue;
      }

      tableData.push({
        key: source.name,
        name: source.name,
        type: source.type,
        path: source.path,
        editable: source.name.toLowerCase() !== 'config.json'
      });
    }

    return tableData;
  };

  openEditFileForm = (item) => {
    this.setState({editFile: item});
  };

  closeEditFileForm = () => {
    this.setState({editFile: null});
  };

  openCreateFileDialog = () => {
    this.setState({createFileDialog: true});
  };

  closeCreateFileDialog = () => {
    this.setState({createFileDialog: false});
  };

  openRenameFileDialog = (file, event) => {
    event.stopPropagation();
    this.setState({renameFile: file});
  };

  closeRenameFileDialog = () => {
    this.setState({renameFile: null});
  };

  openCreateFolderDialog = () => {
    this.setState({createFolderDialog: true});
  };

  closeCreateFolderDialog = () => {
    this.setState({createFolderDialog: false});
  };

  openRenameFolderDialog = (folder, event) => {
    event.stopPropagation();
    this.setState({renameFolder: folder});
  };

  closeRenameFolderDialog = () => {
    this.setState({renameFolder: null});
  };

  deleteFileConfirm = (item, event) => {
    event.stopPropagation();
    const onDelete = () => {
      this.deleteFile(item);
    };
    Modal.confirm({
      title: `Are you sure you want to delete file ${item.name}`,
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        onDelete();
      }
    });
  };

  deleteFolderConfirm = (item, event) => {
    event.stopPropagation();
    const onDelete = () => {
      this.deleteFolder(item);
    };
    Modal.confirm({
      title: `Are you sure you want to delete folder ${item.name}`,
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        onDelete();
      }
    });
  };

  deleteFile = async({name}) => {
    const fileFullName = this.props.path ? `src/${this.props.path}/${name}` : `src/${name}`;
    const request = new PipelineFileDelete(this.props.pipelineId);
    const hide = message.loading(`Deleting file '${name}'...`, 0);
    await request.send({
      comment: `Deleting a file ${name}`,
      path: fileFullName,
      lastCommitId: this.props.pipeline.value.currentVersion.commitId
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      await this.props.pipeline.fetch();
      await this.props.pipelineVersions.fetch();
      if (this.props.onReloadTree) {
        await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
      }
      this.navigateToNewVersion();
    }
  };

  createFile = async({name, comment}) => {
    this.closeCreateFileDialog();
    const fileFullName = this.props.path ? `src/${this.props.path}/${name}` : `src/${name}`;
    const request = new PipelineFileUpdate(this.props.pipelineId);
    const hide = message.loading(`Creating file '${name}'...`, 0);
    await request.send({
      contents: '',
      path: fileFullName,
      comment: comment,
      lastCommitId: this.props.pipeline.value.currentVersion.commitId
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      await this.props.pipeline.fetch();
      await this.props.pipelineVersions.fetch();
      if (this.props.onReloadTree) {
        await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
      }
      this.navigateToNewVersion();
    }
  };

  saveEditableFile = async (contents, message) => {
    const request = new PipelineFileUpdate(this.props.pipeline.value.id);
    const hide = message.loading('Committing changes...');
    await request.send({
      contents: contents,
      comment: message,
      path: this.state.editFile.path,
      lastCommitId: this.props.pipeline.value.currentVersion.commitId
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
      return false;
    } else {
      this.closeEditFileForm();
      await this.props.pipeline.fetch();
      await this.props.pipelineVersions.fetch();
      if (this.props.onReloadTree) {
        await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
      }
      this.navigateToNewVersion();
      return true;
    }
  };

  createFolder = async ({name, comment}) => {
    const folderFullName = this.props.path ? `src/${this.props.path}/${name}` : `src/${name}`;
    const request = new PipelineFolderUpdate(this.props.pipelineId);
    const hide = message.loading(`Creating folder '${name}'...`, 0);
    await request.send({
      lastCommitId: this.props.pipeline.value.currentVersion.commitId,
      path: folderFullName,
      comment: comment
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeCreateFolderDialog();
      await this.props.pipeline.fetch();
      await this.props.pipelineVersions.fetch();
      if (this.props.onReloadTree) {
        await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
      }
      this.navigateToNewVersion();
    }
  };

  renameFolder = async ({name, comment}) => {
    const folderFullName = this.props.path
      ? `src/${this.props.path}/${name}`
      : `src/${name}`;
    const folderPreviousFullName = this.props.path
      ? `src/${this.props.path}/${this.state.renameFolder}`
      : `src/${this.state.renameFolder}`;
    const request = new PipelineFolderUpdate(this.props.pipelineId);
    const hide = message.loading('Renaming folder...', 0);
    await request.send({
      lastCommitId: this.props.pipeline.value.currentVersion.commitId,
      path: folderFullName,
      previousPath: folderPreviousFullName,
      comment: comment
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeRenameFolderDialog();
      await this.props.pipeline.fetch();
      await this.props.pipelineVersions.fetch();
      if (this.props.onReloadTree) {
        await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
      }
      this.navigateToNewVersion();
    }
  };

  renameFile = async ({name, comment}) => {
    const fileFullName = this.props.path
      ? `src/${this.props.path}/${name}`
      : `src/${name}`;
    const filePreviousFullName = this.props.path
      ? `src/${this.props.path}/${this.state.renameFile}`
      : `src/${this.state.renameFile}`;
    const request = new PipelineFileUpdate(this.props.pipelineId);
    const hide = message.loading('Renaming file...', 0);
    await request.send({
      comment: comment,
      path: fileFullName,
      previousPath: filePreviousFullName,
      lastCommitId: this.props.pipeline.value.currentVersion.commitId
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeRenameFileDialog();
      await this.props.pipeline.fetch();
      await this.props.pipelineVersions.fetch();
      if (this.props.onReloadTree) {
        await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
      }
      this.navigateToNewVersion();
    }
  };

  deleteFolder = async({name}) => {
    const folderFullName = this.props.path ? `src/${this.props.path}/${name}` : `src/${name}`;
    const request = new PipelineFolderDelete(this.props.pipelineId);
    const hide = message.loading(`Deleting folder '${name}'...`, 0);
    await request.send({
      comment: `Deleting a folder ${name}`,
      lastCommitId: this.props.pipeline.value.currentVersion.commitId,
      path: folderFullName
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      await this.props.pipeline.fetch();
      await this.props.pipelineVersions.fetch();
      if (this.props.onReloadTree) {
        await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
      }
      this.navigateToNewVersion();
    }
  };

  didSelectSourceItem = (item) => {
    if (item.type.toLowerCase() === 'tree') {
      this.navigateToFolder(item);
    } else {
      this.openEditFileForm(item);
    }
  };

  navigateToFolder = (folder) => {
    const rootPath = `${this.props.pipeline.value.id}/${this.props.version}/code`;
    if (folder.path && folder.path.startsWith('src/')) {
      folder.path = folder.path.substring('src/'.length);
    }
    if (folder.path) {
      this.props.routing.push(`${rootPath}?path=${folder.path}`);
    } else {
      this.props.routing.push(rootPath);
    }
  };

  renderNavigationControl = () => {
    const navigationParts = [
      {
        name: 'Root',
        path: undefined,
        isCreateNewFolder: false,
        isCurrent: !this.props.path
      }
    ];
    if (this.props.path) {
      const parts = this.props.path.split('/');
      let path = '';
      for (let i = 0; i < parts.length; i++) {
        if (i > 0) {
          path += `/${parts[i]}`;
        } else {
          path = parts[i];
        }
        navigationParts.push({
          name: parts[i],
          path: path,
          isCreateNewFolder: false,
          isCurrent: i === (parts.length - 1)
        });
      }
    }
    if (this.canModifySources) {
      navigationParts.push({
        name: 'add folder',
        isCreateNewFolder: true,
        isCurrent: false
      });
    }
    return (
      <Breadcrumb className={styles.navigationItem}>
        {navigationParts.map((part, index) => {
          let breadcrumbContent = <a onClick={() => this.navigateToFolder(part)}>{part.name}</a>;
          if (part.isCurrent) {
            breadcrumbContent = <b>{part.name}</b>;
          } else if (part.isCreateNewFolder) {
            breadcrumbContent = (
              <Button onClick={() => this.openCreateFolderDialog()} size="small">
                <Icon type="plus" />
              </Button>
            );
          }
          return (
            <Breadcrumb.Item key={`navigation_${index}`}>
              {breadcrumbContent}
            </Breadcrumb.Item>);
        })}
      </Breadcrumb>
    );
  };

  navigateToLastPipelineVersion = async () => {
    await this.props.pipeline.fetch();
    let rootPath = `${this.props.pipeline.value.id}/${this.props.pipeline.value.currentVersion.name}/code`;
    if (this.props.path) {
      this.props.routing.push(`${rootPath}?path=${this.props.path}`);
    } else {
      this.props.routing.push(rootPath);
    }
  };

  navigateToNewVersion = () => {
    let rootPath = `${this.props.pipeline.value.id}/${this.props.pipeline.value.currentVersion.name}/code`;
    if (this.props.path) {
      this.props.routing.push(`${rootPath}?path=${this.props.path}`);
    } else {
      this.props.routing.push(rootPath);
    }
  };

  validateUploadFiles = (files) => {
    let sources;
    if (this.props.source.loaded) {
      sources = (this.props.source.value || []).map(s => s);
    }
    let result = true;
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if (sources.filter(s => s.name === file.name).length > 0) {
        result = false;
        message.error(`File '${file.name}' already exists`);
        break;
      }
    }
    return result;
  };

  render () {
    if (this.props.pipeline.pending && !this.props.pipeline.loaded) {
      return <LoadingView />;
    }

    const header = (
      <Row type="flex" justify="space-between">
        <Col span={16}>
          <Row type="flex" className={styles.navigationControl}>
            {this.renderNavigationControl()}
          </Row>
        </Col>
        <Col span={8}>
          {
            this.canModifySources &&
            <Row type="flex" justify="end" className={styles.actionButtonsContainer}>
              <Button
                type="primary"
                onClick={this.openCreateFileDialog} size="small">
                <Icon type="plus" />NEW FILE
              </Button>
              <UploadButton
                multiple={true}
                synchronous={true}
                validate={this.validateUploadFiles}
                onRefresh={this.navigateToLastPipelineVersion}
                title={'Upload'}
                action={PipelineFileUpdate.uploadUrl(this.props.pipelineId, this.props.path ? `src/${this.props.path}` : 'src')} />
            </Row>
          }
        </Col>
      </Row>
    );

    let sources;
    if (this.props.source.loaded) {
      sources = (this.props.source.value || []).map(s => s);
    }

    return (
      <div className={styles.container} style={{overflowY: 'auto'}}>
        <Table
          className={styles.table}
          columns={this.scriptsColumns}
          loading={this.props.source.pending}
          dataSource={this.createScriptsTableData()}
          pagination={false}
          showHeader={false}
          onRowClick={this.didSelectSourceItem}
          rowKey={file => file.path}
          rowClassName={() => styles.sourceItemRow}
          title={() => header}
          size="small" />
        <PipelineCodeForm file={this.state.editFile}
                          pipeline={this.props.pipeline}
                          version={this.props.version}
                          cancel={this.closeEditFileForm}
                          save={this.saveEditableFile} />
        <PipelineCodeSourceNameDialog visible={this.state.createFileDialog}
                                      title="Create new file"
                                      sources={sources}
                                      onSubmit={this.createFile}
                                      onCancel={this.closeCreateFileDialog}
                                      pending={false}/>
        <PipelineCodeSourceNameDialog visible={this.state.createFolderDialog}
                                      title="Create new folder"
                                      sources={sources}
                                      onSubmit={this.createFolder}
                                      onCancel={this.closeCreateFolderDialog}
                                      pending={false} />
        <PipelineCodeSourceNameDialog visible={this.state.renameFolder !== null}
                                      title="Rename folder"
                                      sources={sources}
                                      name={this.state.renameFolder}
                                      onSubmit={this.renameFolder}
                                      onCancel={this.closeRenameFolderDialog}
                                      pending={false} />
        <PipelineCodeSourceNameDialog visible={this.state.renameFile !== null}
                                      title="Rename file"
                                      sources={sources}
                                      name={this.state.renameFile}
                                      onSubmit={this.renameFile}
                                      onCancel={this.closeRenameFileDialog}
                                      pending={false} />
      </div>
    );
  }
}
