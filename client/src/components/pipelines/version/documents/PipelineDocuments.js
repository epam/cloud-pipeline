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
import {observable} from 'mobx';
import {Input, Row, Button, Icon, Table, message, Modal} from 'antd';
import FileSaver from 'file-saver';
import PipelineFile from '../../../../models/pipelines/PipelineFile';
import VersionFile from '../../../../models/pipelines/VersionFile';
import PipelineGenerateFile from '../../../../models/pipelines/PipelineGenerateFile';
import PipelineFileUpdate from '../../../../models/pipelines/PipelineFileUpdate';
import PipelineFileDelete from '../../../../models/pipelines/PipelineFileDelete';
import CodeFileCommitForm from '../code/forms/CodeFileCommitForm';
import UploadButton from '../../../special/UploadButton';
import * as styles from './PipelineDocuments.css';
import WorkflowGraph from '../graph/WorkflowGraph';
import LoadingView from '../../../special/LoadingView';
import PipelineCodeSourceNameDialog from '../code/forms/PipelineCodeSourceNameDialog';
import roleModel from '../../../../utils/roleModel';
import Remarkable from 'remarkable';
import hljs from 'highlight.js';

const MarkdownRenderer = new Remarkable('full', {
  html: true,
  xhtmlOut: true,
  breaks: false,
  langPrefix: 'language-',
  linkify: true,
  linkTarget: '',
  typographer: true,
  highlight: function (str, lang) {
    lang = lang || 'bash';
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(lang, str).value;
      } catch (__) {}
    }
    try {
      return hljs.highlightAuto(str).value;
    } catch (__) {}
    return '';
  }
});

@inject(({pipelines, routing}, {onReloadTree, params}) => ({
  onReloadTree,
  pipelineId: params.id,
  pipeline: pipelines.getPipeline(params.id),
  version: params.version,
  pipelineVersions: pipelines.versionsForPipeline(params.id),
  docs: pipelines.getDocuments(params.id, params.version),
  routing
}))
@observer
export default class PipelineDocuments extends Component {

  state = {
    renameFile: null,
    managingMdFile: null,
    editManagingMdFileMode: false,
    commitMessageForm: false,
    mdModifiedContent: null
  };

  @observable
  _mdFileRequest = null;
  @observable
  _mdOriginalContent = '';

  updateAndNavigateToNewVersion = async () => {
    await this.props.pipeline.fetch();
    await this.props.pipelineVersions.fetch();
    if (this.props.onReloadTree) {
      await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
    }
    this.navigateToNewVersion();
  };

  navigateToNewVersion = () => {
    this.props.routing.push(`${this.props.pipeline.value.id}/${this.props.pipeline.value.currentVersion.name}/documents`);
  };

  isMdFile = file => file && file.name.endsWith('.md');

  setManagingMdFile = (managingMdFile) => {
    this.setState({managingMdFile, editManagingMdFileMode: false});
  };

  onMdFileChanged = (mdModifiedContent) => {
    this.setState({mdModifiedContent});
  };

  toggleEditManagingMdFileMode = (mode) => {
    const editManagingMdFileMode = mode !== undefined
      ? mode
      : !this.state.editManagingMdFileMode;
    this.setState({
      editManagingMdFileMode,
      mdModifiedContent: editManagingMdFileMode ? this._mdOriginalContent : null
    });
  };

  onMdSave = () => {
    if (this.state.mdModifiedContent && this.state.mdModifiedContent !== this._mdOriginalContent) {
      this.setState({commitMessageForm: true});
    } else {
      this.toggleEditManagingMdFileMode(false);
    }
  };

  doCommit = (options) => {
    this.setState({
      editManagingMdFileMode: false
    }, () => {
      this.closeCommitForm();
      this.saveEditableFile(this.state.mdModifiedContent, options.message);
    });
  };

  closeCommitForm = () => {
    this.setState({commitMessageForm: false});
  };

  saveEditableFile = async (contents, message) => {
    const request = new PipelineFileUpdate(this.props.pipeline.value.id);
    const hide = message.loading('Committing changes...');
    await request.send({
      contents: contents,
      comment: message,
      path: this.state.managingMdFile.path,
      lastCommitId: this.props.pipeline.value.currentVersion.commitId
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
      return false;
    } else {
      await this.props.pipeline.fetch();
      await this.props.pipelineVersions.fetch();
      if (this.props.onReloadTree) {
        await this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
      }
      this.navigateToNewVersion();
      return true;
    }
  };

  actionsRenderer = (text, file, graphReady) => {
    const parts = file.name.split('.');
    const docx = parts[parts.length - 1].toLowerCase() === 'docx';
    return (
      <span className={styles.rowActionButtonsContainer}>
        {
          this.canModifySources &&
          (
            <span>
              <Button
                size="small"
                type="danger"
                onClick={(e) => this.deleteFileConfirm(file, e)}>
                <Icon type="delete" />Delete
              </Button>
              <Button
                size="small" onClick={(e) => this.openRenameFileDialog(file, e)}>
                <Icon type="edit" />Rename
              </Button>
              <span className="ant-divider" />
            </span>
          )
        }
        <Button
          onClick={() => this.downloadPipelineFile(file)}
          size="small">
          <Icon type="download" />Download
        </Button>
        {
          docx &&
          <Button
            disabled={!graphReady}
            onClick={() => this.generateDocument(file)}
            size="small">
            <Icon type="file-text" />Generate
          </Button>
        }
      </span>
    );
  };

  onFileClick = (file) => {
    if (this.isMdFile(file)) {
      this.setManagingMdFile(file);
    } else {
      this.downloadPipelineFile(file);
    }
  };

  createDocumentsTable = (sources, graphReady) => {
    const dataSource = [];

    const columns = [
      {
        dataIndex: 'name',
        key: 'name',
        title: 'Name',
        render: (name, file) => (
          <span
            className={styles.documentName}>
            {name}
          </span>
        )
      },
      {
        key: 'actions',
        className: styles.actions,
        render: (text, file) => this.actionsRenderer(text, file, graphReady)
      }
    ];

    for (let source of (sources || [])) {
      if (!source) {
        continue;
      }

      dataSource.push({
        key: source.id,
        mode: source.mode,
        name: source.name,
        path: source.path,
        type: source.type
      });
    }

    return {dataSource, columns};
  };

  downloadPipelineFile = async(file) => {
    const {id, version} = this.props.params;

    try {
      const pipelineFile = new PipelineFile(id, version, file.path);
      await pipelineFile.fetch();
      FileSaver.saveAs(pipelineFile.response, file.name);
    } catch (e) {
      message.error('Failed to download file', 5);
    }
  };

  generateDocument = async(file) => {
    const {id, version} = this.props.params;
    let graph = this._graphComponent ? this._graphComponent.base64Image() : '';
    if (graph.indexOf('data:image/png;base64,') === 0) {
      graph = graph.substring('data:image/png;base64,'.length);
    }
    try {
      const hide = message.loading('Processing...', 0);
      const pipelineGenerateFile = new PipelineGenerateFile(id, version, file.path);
      await pipelineGenerateFile.send({
        luigiWorkflowGraphVO: {
          width: this._graphComponent ? this._graphComponent.imageSize.width : 1,
          height: this._graphComponent ? this._graphComponent.imageSize.height : 1,
          imageData: graph
        }
      });
      if (pipelineGenerateFile.response) {
        FileSaver.saveAs(pipelineGenerateFile.response, file.name);
      } else {
        message.error('Error generating document', 2);
      }
      hide();
    } catch (e) {
      message.error('Failed to download file', 5);
    }
  };

  @observable
  _graphReady = false;

  _graphComponent;

  initializeHiddenGraph = (graph) => {
    this._graphComponent = graph;
    this._graphReady = true;
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

  deleteFile = async({name}) => {
    const fileFullName = `docs/${name}`;
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

  openRenameFileDialog = (file, event) => {
    event.stopPropagation();
    this.setState({renameFile: file.name});
  };

  closeRenameFileDialog = () => {
    this.setState({renameFile: null});
  };

  renameFile = async ({name, comment}) => {
    const fileFullName = `docs/${name}`;
    const filePreviousFullName = `docs/${this.state.renameFile}`;
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

  get canModifySources () {
    if (this.props.pipeline.pending) {
      return false;
    }
    return roleModel.writeAllowed(this.props.pipeline.value) &&
      this.props.version === this.props.pipeline.value.currentVersion.name;
  };

  render () {
    if (this.props.docs.pending) {
      return <LoadingView />;
    }
    const tableData = this.createDocumentsTable(this.props.docs.value, this._graphReady);

    const renderMarkdownControls = () => {
      if (!this.canModifySources) {
        return undefined;
      }
      const buttons = [];
      if (this.state.editManagingMdFileMode) {
        buttons.push(
          <Button
            key="cancel"
            size="small"
            onClick={() => this.toggleEditManagingMdFileMode(false)}>
            CANCEL
          </Button>
        );
        buttons.push(
          <Button key="save" size="small" type="primary" onClick={() => this.onMdSave()}>
            SAVE
          </Button>
        );
      } else {
        buttons.push(
          <Button key="edit" size="small" onClick={() => this.toggleEditManagingMdFileMode(true)}>
            EDIT
          </Button>
        );
      }
      return (
        <Row className={styles.mdActions}>
          {buttons}
        </Row>
      );
    };

    const renderMarkdown = () => {
      if (!this.state.managingMdFile) {
        return;
      }
      if (this._mdFileRequest && this._mdFileRequest.pending) {
        return <LoadingView />;
      }
      if (this.state.editManagingMdFileMode) {
        return (
          <Input
            value={this.state.mdModifiedContent}
            onChange={(e) => this.onMdFileChanged(e.target.value)}
            type="textarea"
            onKeyDown={(e) => {
              if (e.key && e.key === 'Escape') {
                this.toggleEditManagingMdFileMode(false);
              }
            }}
            autosize={{minRows: 25}}
            style={{width: '100%', resize: 'none'}} />
        );
      } else {
        if (this._mdOriginalContent && this._mdOriginalContent.trim().length) {
          return (
            <div
              className={styles.mdPreview}
              dangerouslySetInnerHTML={{
                __html: MarkdownRenderer.render(this._mdOriginalContent)
              }}
            />
          );
        } else {
          return <span className={styles.noMdContent}>No content</span>;
        }
      }
    };

    const header = this.canModifySources
      ? (
        <Row
          type="flex"
          justify="end"
          className={styles.actionButtonsContainer}>
          <UploadButton
            multiple={true}
            synchronous={true}
            onRefresh={this.updateAndNavigateToNewVersion}
            title={'Upload'}
            action={PipelineFileUpdate.uploadUrl(this.props.pipelineId, 'docs')} />
        </Row>
      )
    : undefined;

    const docsContent = [
      <Table
        key="docs table"
        rowKey="name"
        onRowClick={(file) => this.onFileClick(file)}
        rowClassName={() => styles.documentRow}
        className={styles.docsTable}
        dataSource={tableData.dataSource}
        columns={tableData.columns}
        pagination={false}
        size="small"
        showHeader={false}
        title={() => header}
        locale={{emptyText: 'No documents attached. Start with uploading new README.md file'}}
      />,
      <WorkflowGraph
        key="docs hidden graph"
        canEdit={false}
        hideError={true}
        pipelineId={this.props.pipelineId}
        version={this.props.version}
        className={styles.graph}
        fitAllSpace={true}
        onGraphReady={this.initializeHiddenGraph} />,
      <PipelineCodeSourceNameDialog
        key="docs name dialog"
        visible={this.state.renameFile !== null}
        title="Rename file"
        name={this.state.renameFile}
        onSubmit={this.renameFile}
        onCancel={this.closeRenameFileDialog}
        pending={false} />
    ];
    if (this.state.managingMdFile) {
      return (
        <div style={{overflowY: 'auto'}}>
          {docsContent}
          <Row
            type="flex"
            align="middle"
            justify="space-between"
            style={{marginTop: 10}}
            className={styles.mdHeader}>
            <span className={styles.mdTitle}>{this.state.managingMdFile.name}</span>
            {renderMarkdownControls()}
          </Row>
          <Row type="flex" className={styles.mdBody} style={{flex: 1, overflowY: 'auto'}}>
            {renderMarkdown()}
          </Row>
          <CodeFileCommitForm
            visible={this.state.commitMessageForm}
            pending={false} onSubmit={this.doCommit}
            onCancel={this.closeCommitForm} />
        </div>
      );
    } else {
      return (
        <div className={styles.container} style={{overflowY: 'auto'}}>
          {docsContent}
        </div>
      );
    }
  }

  componentDidUpdate (prevProps, prevState) {
    if (
      this.state.managingMdFile && (
        prevProps.pipelineId !== this.props.pipelineId ||
        prevProps.version !== this.props.version ||
        (!prevState.managingMdFile && this.state.managingMdFile) ||
        (prevState.managingMdFile &&
          prevState.managingMdFile.path !== this.state.managingMdFile.path)
      )
    ) {
      this._mdFileRequest = new VersionFile(
        this.props.pipelineId,
        this.state.managingMdFile.path,
        this.props.version
      );
      this._mdFileRequest.fetch();
      this._mdOriginalContent = '';
    } else if (!this.state.managingMdFile) {
      this._mdFileRequest = null;
      this._mdOriginalContent = '';
    }
    if (this._mdFileRequest && !this._mdFileRequest.pending && !this._mdOriginalContent) {
      this._mdOriginalContent = atob(this._mdFileRequest.response);
    }
    if (!this.props.docs.pending && this.props.docs.value && !this.state.managingMdFile) {
      const [readme] = this.props.docs.value.filter(source => source.name === 'README.md');
      if (readme) {
        this.setManagingMdFile(readme);
      } else {
        const md = this.props.docs.value.filter(source => this.isMdFile(source)).shift();
        if (md) {
          this.setManagingMdFile(md);
        }
      }
    }
  }

  componentWillUnmount () {
    this._mdFileRequest = null;
    this._mdOriginalContent = '';
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.version !== this.props.version ||
      nextProps.pipelineId !== this.props.pipelineId) {
      this._graphReady = false;
      this.setState({
        managingMdFile: null
      });
    }
  }
}
