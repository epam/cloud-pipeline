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
import {computed} from 'mobx';
import classNames from 'classnames';
import {
  Alert,
  Input,
  Row,
  Button,
  Icon,
  Table,
  message,
  Modal
} from 'antd';
import FileSaver from 'file-saver';
import VersionFile from '../../../../models/pipelines/VersionFile';
import PipelineGenerateFile from '../../../../models/pipelines/PipelineGenerateFile';
import PipelineFileUpdate from '../../../../models/pipelines/PipelineFileUpdate';
import PipelineFileDelete from '../../../../models/pipelines/PipelineFileDelete';
import CodeFileCommitForm from '../code/forms/CodeFileCommitForm';
import UploadButton from '../../../special/UploadButton';
import WorkflowGraph from '../graph/WorkflowGraph';
import LoadingView from '../../../special/LoadingView';
import Markdown from '../../../special/markdown';
import PipelineCodeSourceNameDialog from '../code/forms/PipelineCodeSourceNameDialog';
import roleModel from '../../../../utils/roleModel';
import download from '../utilities/download-pipeline-file';
import * as styles from './PipelineDocuments.css';

function correctFolderPath (folder) {
  if (!folder) {
    return folder;
  }
  if (folder === '/') {
    return folder;
  }
  let result = folder;
  if (result.startsWith('/')) {
    result = result.slice(1);
  }
  if (result.endsWith('/')) {
    result = result.slice(0, -1);
  }
  return result;
}

function getDefaultDocument (documents = []) {
  const readme = documents.find((document) => /^readme\.md$/i.test(document.name));
  if (readme) {
    return readme;
  }
  return documents.find((document) => /\.md$/i.test(document.name));
}

function increaseToken (token) {
  return (token || 0) + 1;
}

@inject(({pipelines, routing}, {onReloadTree, params}) => ({
  onReloadTree,
  pipelineId: params.id,
  pipeline: pipelines.getPipeline(params.id),
  version: params.version,
  pipelineVersions: pipelines.versionsForPipeline(params.id),
  routing,
  pipelines
}))
@observer
export default class PipelineDocuments extends Component {
  state = {
    renameFile: null,
    error: undefined,
    pending: false,
    defaultFile: undefined,
    editMode: false,
    defaultFilePending: true,
    defaultFileError: undefined,
    defaultFileContent: undefined,
    defaultFileModifiedContent: undefined,
    commitMessageForm: false,
    graphReady: false
  };

  fetchToken;
  fetchFileToken;

  componentDidMount () {
    this.updateDocsPath();
    this.updateDocuments();
  }

  componentDidUpdate (prevProps, prevState) {
    const docsPathChanged = this.updateDocsPath();
    if (
      prevProps.version !== this.props.version ||
      prevProps.pipelineId !== this.props.pipelineId ||
      docsPathChanged
    ) {
      this.updateDocuments(docsPathChanged);
    }
    this.redirectBitBucketPipelineToCode();
  }

  componentWillUnmount () {
    // to prevent setState of the fetch methods (updateDocuments / setDefaultFile)
    this.fetchToken = increaseToken(this.fetchToken);
    this.fetchFileToken = increaseToken(this.fetchFileToken);
  }

  @computed
  get docsFolder () {
    const {pipeline} = this.props;
    if (pipeline && pipeline.loaded) {
      const {docsPath} = pipeline.value;
      return correctFolderPath(docsPath);
    }
    return undefined;
  }

  updateDocsPath = () => {
    const {
      pipeline
    } = this.props;
    if (pipeline && pipeline.loaded) {
      const {docsPath} = pipeline.value;
      if (this._docsPath !== docsPath) {
        this._docsPath = docsPath;
        return true;
      }
    }
    return false;
  };

  updateDocuments = (force = false) => {
    const {
      pipelineId,
      version,
      pipelines
    } = this.props;
    if (pipelineId && version) {
      const fetchToken = this.fetchToken = increaseToken(this.fetchToken);
      const commitState = (state, cb) => {
        if (this.fetchToken === fetchToken) {
          this.setState(state, cb);
        }
      };
      this.setState({
        pending: true,
        error: undefined
      }, async () => {
        const state = {
          pending: false
        };
        try {
          const request = pipelines.getDocuments(pipelineId, version);
          await (force ? request.fetch() : request.fetchIfNeededOrWait());
          if (request.error) {
            throw new Error(request.error);
          }
          if (!request.loaded) {
            throw new Error('Error fetching documents');
          }
          state.documents = request.value || [];
        } catch (error) {
          state.error = error.message;
        } finally {
          commitState(
            state,
            () => this.setDefaultFile(getDefaultDocument(this.state.documents))
          );
        }
      });
    } else {
      this.setState({
        pending: false,
        error: 'Pipeline id or version is not specified'
      });
    }
  };

  updateAndNavigateToNewVersion = async () => {
    const {
      pipeline,
      pipelineVersions,
      onReloadTree
    } = this.props;
    await Promise.all([
      pipeline.fetch(),
      pipelineVersions.fetch()
    ]);
    if (typeof onReloadTree === 'function') {
      await onReloadTree(!pipeline.value.parentFolderId);
    }
    this.navigateToNewVersion();
  };

  navigateToNewVersion = () => {
    const {routing, pipeline} = this.props;
    const {value} = pipeline;
    routing
      .push(`${value?.id}/${value?.currentVersion?.name}/documents`);
  };

  setDefaultFile = (defaultFile) => {
    const fetchFileToken = this.fetchFileToken = increaseToken(this.fetchFileToken);
    const commitState = (state) => {
      if (this.fetchFileToken === fetchFileToken) {
        this.setState(state);
      }
    };
    const {
      pipelineId,
      version
    } = this.props;
    this.setState(
      {
        defaultFile,
        editMode: false,
        defaultFilePending: true,
        defaultFileError: undefined,
        defaultFileContent: undefined,
        defaultFileModifiedContent: undefined
      },
      async () => {
        const state = {
          defaultFilePending: false
        };
        try {
          if (defaultFile) {
            const request = new VersionFile(
              pipelineId,
              defaultFile.path,
              version
            );
            await request.fetch();
            if (request.error) {
              throw new Error(request.error);
            }
            state.defaultFileContent = atob(request.response);
            state.defaultFileModifiedContent = state.defaultFileContent;
          }
        } catch (error) {
          state.defaultFileError = error.message;
        } finally {
          commitState(state);
        }
      }
    );
  };

  onDefaultFileContentChange = (content) => {
    this.setState({defaultFileModifiedContent: content});
  };

  toggleEditMode = (mode) => {
    const editMode = mode !== undefined
      ? mode
      : !this.state.editMode;
    this.setState({editMode});
  };

  onSaveDefaultFile = () => {
    if (
      this.state.defaultFileModifiedContent !== this.state.defaultFileContent
    ) {
      this.setState({commitMessageForm: true});
    } else {
      this.toggleEditMode(false);
    }
  };

  doCommit = (options) => {
    this.setState({
      editMode: false
    }, () => {
      this.closeCommitForm();
      (this.saveEditableFile)(options.message);
    });
  };

  closeCommitForm = () => {
    this.setState({commitMessageForm: false});
  };

  saveEditableFile = async (comment) => {
    const {
      pipelineId,
      pipeline,
      pipelineVersions,
      onReloadTree
    } = this.props;
    if (
      !this.state.defaultFile ||
      !pipeline ||
      !pipeline.value ||
      !pipeline.value.currentVersion
    ) {
      return;
    }
    const request = new PipelineFileUpdate(pipelineId);
    const hide = message.loading('Committing changes...');
    await request.send({
      contents: this.state.defaultFileModifiedContent,
      comment,
      path: this.state.defaultFile.path,
      lastCommitId: pipeline.value.currentVersion.commitId
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
      return false;
    } else {
      await pipeline.fetch();
      await pipelineVersions.fetch();
      if (typeof onReloadTree === 'function') {
        await onReloadTree(!pipeline.value.parentFolderId);
      }
      this.navigateToNewVersion();
      return true;
    }
  };

  actionsRenderer = (text, file) => {
    const {graphReady} = this.state;
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
                <Icon type="delete" />
                Delete
              </Button>
              <Button
                size="small" onClick={(e) => this.openRenameFileDialog(file, e)}>
                <Icon type="edit" />
                Rename
              </Button>
              <span className="ant-divider" />
            </span>
          )
        }
        <Button
          onClick={(e) => this.downloadPipelineFile(file, e)}
          size="small"
        >
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

  onFileClick = (file, _index, event) => {
    if (event.target.type !== 'button') {
      file && /\.md$/i.test(file.name)
        ? this.setDefaultFile(file)
        : this.downloadPipelineFile(file);
    }
  };

  createDocumentsTable = () => {
    const {documents = []} = this.state;
    const columns = [
      {
        dataIndex: 'name',
        key: 'name',
        title: 'Name',
        render: (name) => (
          <span
            className={classNames(styles.documentName, 'cp-primary')}>
            {name}
          </span>
        )
      },
      {
        key: 'actions',
        className: styles.actions,
        render: (text, file) => this.actionsRenderer(text, file)
      }
    ];
    return {
      dataSource: documents.map((document) => ({
        ...document,
        key: document.id
      })),
      columns
    };
  };

  downloadPipelineFile = (file, event) => {
    const {
      pipelineId,
      version
    } = this.props;
    event && event.stopPropagation();
    return download(pipelineId, version, file.path);
  };

  checkForBlobErrors = (blob) => {
    return new Promise(resolve => {
      const fr = new FileReader();
      fr.onload = function () {
        const status = JSON.parse(this.result)?.status?.toLowerCase();
        resolve(status === 'error');
      };
      fr.readAsText(blob);
    });
  }

  generateDocument = async (file) => {
    const {
      pipelineId,
      version
    } = this.props;
    let graph = this._graphComponent ? this._graphComponent.base64Image() : '';
    if (graph.indexOf('data:image/png;base64,') === 0) {
      graph = graph.substring('data:image/png;base64,'.length);
    }
    try {
      const hide = message.loading('Processing...', 0);
      const pipelineGenerateFile = new PipelineGenerateFile(pipelineId, version, file.path);
      let res;
      await pipelineGenerateFile.send({
        luigiWorkflowGraphVO: {
          width: this._graphComponent ? this._graphComponent.imageSize.width : 1,
          height: this._graphComponent ? this._graphComponent.imageSize.height : 1,
          imageData: graph
        }
      });
      res = pipelineGenerateFile.response;
      if (res.type?.includes('application/json') && res instanceof Blob) {
        this.checkForBlobErrors(res)
          .then(error => error
            ? message.error('Error generating document', 5)
            : FileSaver.saveAs(res, file.name)
          );
      } else if (res) {
        FileSaver.saveAs(res, file.name);
      } else {
        message.error('Error generating document', 2);
      }
      hide();
    } catch (e) {
      message.error('Failed to download file', 5);
    }
  };

  _graphComponent;

  initializeHiddenGraph = (graph) => {
    this._graphComponent = graph;
    if (!this.state.graphReady) {
      this.setState({graphReady: true});
    }
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

  deleteFile = async ({name, path}) => {
    const request = new PipelineFileDelete(this.props.pipelineId);
    const hide = message.loading(`Deleting file '${name}'...`, 0);
    await request.send({
      comment: `Deleting a file ${name}`,
      path,
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
    this.setState({renameFile: file});
  };

  closeRenameFileDialog = () => {
    this.setState({renameFile: null});
  };

  renameFile = async ({name, comment}) => {
    const {path = ''} = this.state.renameFile || {};
    const newPath = (path || '').split('/').slice(0, -1).concat(name).join('/');
    const request = new PipelineFileUpdate(this.props.pipelineId);
    const hide = message.loading('Renaming file...', 0);
    await request.send({
      comment: comment,
      path: newPath,
      previousPath: path,
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
      this.props.version === this.props.pipeline.value.currentVersion.name &&
      !!this.docsFolder &&
      this.docsFolder.length;
  };

  renderMarkdownControls = () => {
    if (!this.canModifySources) {
      return undefined;
    }
    const buttons = [];
    if (this.state.editMode) {
      buttons.push(
        <Button
          key="cancel"
          size="small"
          onClick={() => this.toggleEditMode(false)}>
          CANCEL
        </Button>
      );
      buttons.push(
        <Button
          key="save"
          size="small"
          type="primary"
          onClick={this.onSaveDefaultFile}
        >
          SAVE
        </Button>
      );
    } else {
      buttons.push(
        <Button
          key="edit"
          size="small"
          onClick={() => this.toggleEditMode(true)}
        >
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

  renderMarkdown = () => {
    const {
      editMode,
      pending,
      defaultFile,
      defaultFilePending,
      defaultFileError,
      defaultFileContent,
      defaultFileModifiedContent
    } = this.state;
    if (!defaultFile) {
      return;
    }
    if (defaultFilePending || pending) {
      return (
        <LoadingView />
      );
    }
    if (defaultFileError) {
      return (
        <Alert
          message={defaultFileError}
          type="error"
        />
      );
    }
    if (editMode) {
      return (
        <Input
          value={defaultFileModifiedContent}
          onChange={(e) => this.onDefaultFileContentChange(e.target.value)}
          type="textarea"
          onKeyDown={(e) => {
            if (e.key && e.key === 'Escape') {
              this.toggleEditMode(false);
            }
          }}
          autosize={{minRows: 25}}
          style={{width: '100%', resize: 'none'}}
          className={styles.markdownEditor}
        />
      );
    }
    if (defaultFileContent && defaultFileContent.trim().length) {
      return (
        <Markdown
          className={styles.markdown}
          md={defaultFileContent}
        />
      );
    }
    return (
      <span
        className={
          classNames(
            styles.noMdContent,
            'cp-text-not-important'
          )
        }
      >
        No content
      </span>
    );
  };

  render () {
    const {
      pending,
      error,
      defaultFile
    } = this.state;
    const {
      pipelineId,
      version
    } = this.props;
    if (pending) {
      return (
        <LoadingView />
      );
    }
    if (error) {
      return (
        <Alert message={error} type="error" />
      );
    }
    const tableData = this.createDocumentsTable();

    const header = this.canModifySources
      ? (
        <Row
          type="flex"
          justify="end"
          className={styles.actionButtonsContainer}>
          <UploadButton
            multiple
            synchronous
            onRefresh={this.updateAndNavigateToNewVersion}
            title={'Upload'}
            action={PipelineFileUpdate.uploadUrl(this.props.pipelineId, this.docsFolder)} />
        </Row>
      )
      : undefined;

    const docsContent = [
      <Table
        key="docs table"
        rowKey="name"
        onRowClick={(file, index, evt) => this.onFileClick(file, index, evt)}
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
        hideError
        pipelineId={pipelineId}
        version={version}
        className={styles.graph}
        fitAllSpace
        onGraphReady={this.initializeHiddenGraph}
      />,
      <PipelineCodeSourceNameDialog
        key="docs name dialog"
        visible={this.state.renameFile !== null}
        title="Rename file"
        name={this.state.renameFile ? this.state.renameFile.name : undefined}
        onSubmit={this.renameFile}
        onCancel={this.closeRenameFileDialog}
        pending={false}
      />
    ];
    if (defaultFile) {
      return (
        <div style={{overflowY: 'auto'}}>
          {docsContent}
          <Row
            type="flex"
            align="middle"
            justify="space-between"
            style={{marginTop: 10}}
            className={classNames(styles.mdHeader, 'cp-content-panel')}
          >
            <span className={styles.mdTitle}>
              {defaultFile.name}
            </span>
            {this.renderMarkdownControls()}
          </Row>
          <Row
            type="flex"
            className={classNames(styles.mdBody, 'cp-content-panel')}
            style={{flex: 1, overflowY: 'auto'}}
          >
            {this.renderMarkdown()}
          </Row>
          <CodeFileCommitForm
            visible={this.state.commitMessageForm}
            pending={false}
            onSubmit={this.doCommit}
            onCancel={this.closeCommitForm}
          />
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

  redirectBitBucketPipelineToCode () {
    const {
      pipeline
    } = this.props;
    if (pipeline && pipeline.loaded) {
      const {
        repositoryType
      } = pipeline.value;
      if (/^bitbucket$/i.test(repositoryType)) {
        const {
          router,
          pipelineId,
          version
        } = this.props;
        router.push(`/${pipelineId}/${version}/code`);
        return true;
      }
    }
    return false;
  }
}
