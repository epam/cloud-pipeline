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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Alert,
  message,
  Pagination
} from 'antd';
import FileSaver from 'file-saver';
import VersionedStorageHeader from './header';
import VersionedStorageTable from './table';
import {SplitPanel} from '../../../special/splitPanel';
import localization from '../../../../utils/localization';
import HiddenObjects from '../../../../utils/hidden-objects';
import LoadingView from '../../../special/LoadingView';
import UpdatePipeline from '../../../../models/pipelines/UpdatePipeline';
import PipelineFolderUpdate from '../../../../models/pipelines/PipelineFolderUpdate';
import PipelineFile from '../../../../models/pipelines/PipelineFile';
import PipelineFileUpdate from '../../../../models/pipelines/PipelineFileUpdate';
import PipelineFileDelete from '../../../../models/pipelines/PipelineFileDelete';
import PipelineFolderDelete from '../../../../models/pipelines/PipelineFolderDelete';
import VersionedStorageListWithInfo
  from '../../../../models/versioned-storage/vs-contents-with-info';
import DeletePipeline from '../../../../models/pipelines/DeletePipeline';
import InfoPanel from './info-panel';
import PipelineCodeForm from '../../version/code/forms/PipelineCodeForm';
import UpdatePipelineToken from '../../../../models/pipelines/UpdatePipelineToken';
import CreateItemForm from './forms/create-item-form';
import EditPipelineForm from '../../version/forms/EditPipelineForm';
import TABLE_MENU_KEYS from './table/table-menu-keys';
import DOCUMENT_TYPES from './document-types';
import styles from './versioned-storage.css';

const PAGE_SIZE = 20;

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

function checkForBlobErrors (blob) {
  return new Promise(resolve => {
    const fr = new FileReader();
    fr.onload = function () {
      const status = JSON.parse(this.result)?.status?.toLowerCase();
      resolve(status === 'error');
    };
    fr.readAsText(blob);
  });
}

const RESTRICTED_FILES = [
  /.gitkeep$/i
];

function filterByRestrictedNames (content = {}) {
  return !RESTRICTED_FILES.some(restriction => {
    if (!content.git_object || !content.git_object.name) {
      return false;
    }
    return restriction.test(content.git_object.name);
  });
}

@localization.localizedComponent
@HiddenObjects.checkPipelines(p => (p.params ? p.params.id : p.id))
@HiddenObjects.injectTreeFilter
@inject('pipelines', 'folders', 'pipelinesLibrary')
@inject(({pipelines}, params) => {
  const {location} = params;
  let path;
  if (location && location.query.path) {
    path = location.query.path;
  }
  let componentParameters = params;
  if (params.params) {
    componentParameters = params.params;
  }
  return {
    path,
    pipelineId: componentParameters.id,
    pipeline: pipelines.getPipeline(componentParameters.id)
  };
})
@observer
class VersionedStorage extends localization.LocalizedReactComponent {
  state = {
    contents: [],
    editStorageDialog: false,
    error: undefined,
    lastPage: 0,
    page: 0,
    pending: false,
    showHistoryPanel: false,
    selectedFile: null,
    editSelectedFile: false
  };

  updateVSRequest = new UpdatePipeline();

  componentDidMount () {
    this.pathWasChanged();
  };

  componentDidUpdate (prevProps) {
    if (
      prevProps.path !== this.props.path ||
      prevProps.pipelineId !== this.props.pipelineId
    ) {
      this.pathWasChanged();
    }
  };

  @computed
  get lastCommitId () {
    const {pipeline} = this.props;
    if (
      pipeline &&
      pipeline.value &&
      pipeline.loaded
    ) {
      return pipeline.value.currentVersion.commitId;
    }
    return null;
  };

  get filteredContents () {
    const {contents} = this.state;
    if (!contents || !contents.length) {
      return [];
    }
    return contents.filter(filterByRestrictedNames);
  };

  get actions () {
    return {
      openHistoryPanel: this.openHistoryPanel,
      closeHistoryPanel: this.closeHistoryPanel,
      openEditStorageDialog: this.openEditStorageDialog
    };
  };

  get parentPath () {
    const {path} = this.props;
    let parentPath;
    if (path) {
      parentPath = path.split('/').filter(Boolean).slice(0, -1).join('/');
    }
    if (parentPath && parentPath.length > 0 && !parentPath.endsWith('/')) {
      parentPath = `${parentPath}/`;
    }
    return parentPath || '';
  };

  folderOperationWrapper = (operation) => (...props) => {
    this.setState({
      pending: true
    }, async () => {
      await operation(...props);
      this.setState({
        pending: false
      });
    });
  };

  pathWasChanged = () => {
    this.fetchPage(0);
  };

  fetchPage = (page) => {
    const {
      path,
      pipelineId
    } = this.props;
    this.setState({
      pending: true
    }, () => {
      const resolve = (result = {}) => {
        this.setState({
          page,
          pending: false,
          ...result
        });
      };
      const request = new VersionedStorageListWithInfo(
        pipelineId,
        {
          page,
          pageSize: PAGE_SIZE,
          path
        }
      );
      request
        .fetch()
        .then(() => {
          if (request.error || !request.loaded) {
            resolve({error: request.error || 'Error fetching versioned storage contents'});
          } else {
            const {
              listing = [],
              max_page: lastPage
            } = request.value;
            resolve({contents: listing.slice(), lastPage});
          }
        })
        .catch(e => resolve({error: e.message}));
    });
  };

  clearSelectedFile = () => {
    this.setState({selectedFile: undefined});
  };

  refreshSelectedFile = () => {
    const {selectedFile} = this.state;
    if (selectedFile) {
      const updatedFile = this.filteredContents
        .find(contentFile => contentFile.git_object.id === selectedFile.id);
      updatedFile && this.setState({selectedFile: {
        ...updatedFile.commit,
        ...updatedFile.git_object
      }});
    }
  };

  openHistoryPanel = () => {
    this.setState({showHistoryPanel: true});
  };

  closeHistoryPanel = () => {
    this.setState({showHistoryPanel: false, selectedFile: undefined});
  };

  openHistoryPanelWithFileInfo = (file) => {
    this.setState({
      selectedFile: file,
      showHistoryPanel: true
    });
  };

  openEditStorageDialog = () => {
    this.setState({editStorageDialog: true});
  };

  closeEditStorageDialog = () => {
    this.setState({editStorageDialog: false});
  };

  openCreateDocumentDialog = (type) => {
    if (type) {
      this.setState({createDocument: type});
    }
  };

  closeCreateDocumentDialog = () => {
    this.setState({createDocument: undefined});
  };

  openRenameDocumentDialog = (document) => {
    if (document) {
      this.setState({renameDocument: document});
    }
  };

  closeRenameDocumentDialog = () => {
    this.setState({renameDocument: undefined});
  };

  editVersionedStorage = async ({name, description, token}) => {
    const {
      folders,
      onReloadTree,
      pipeline,
      pipelinesLibrary
    } = this.props;
    if (pipeline && pipeline.loaded) {
      const {
        id,
        parentFolderId
      } = pipeline.value;
      const hide = message.loading(
        `Updating ${this.localizedString('versioned storage')} ${name}...`,
        0
      );
      const updatePipelineRequest = new UpdatePipeline();
      await updatePipelineRequest.send({
        id: id,
        name: name,
        description: description,
        parentFolderId: parentFolderId
      });
      if (updatePipelineRequest.error) {
        hide();
        message.error(updatePipelineRequest.error, 5);
      } else {
        if (token !== undefined) {
          const updatePipelineTokenRequest = new UpdatePipelineToken();
          await updatePipelineTokenRequest.send({
            id: id,
            repositoryToken: token
          });
          hide();
          if (updatePipelineTokenRequest.error) {
            message.error(updatePipelineTokenRequest.error, 5);
          } else {
            this.closeEditStorageDialog();
            await pipeline.fetch();
            if (parentFolderId) {
              folders.invalidateFolder(parentFolderId);
            } else {
              pipelinesLibrary.invalidateCache();
            }
            if (onReloadTree) {
              onReloadTree(!parentFolderId);
            }
          }
        } else {
          hide();
          this.closeEditStorageDialog();
          await pipeline.fetch();
          if (parentFolderId) {
            folders.invalidateFolder(parentFolderId);
          } else {
            pipelinesLibrary.invalidateCache();
          }
          if (onReloadTree) {
            onReloadTree(!parentFolderId);
          }
        }
      }
    }
  }

  deleteVersionedStorage = async (keepRepository) => {
    const {
      folders,
      onReloadTree,
      pipeline,
      pipelinesLibrary
    } = this.props;
    if (pipeline && pipeline.loaded) {
      const {
        id,
        name,
        parentFolderId
      } = pipeline.value;
      const request = new DeletePipeline(id, keepRepository);
      const hide = message.loading(
        `Deleting ${this.localizedString('versioned storage')} ${name}...`,
        0);
      await request.fetch();
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        this.closeEditStorageDialog();
        if (parentFolderId) {
          folders.invalidateFolder(parentFolderId);
        } else {
          pipelinesLibrary.invalidateCache();
        }
        if (onReloadTree) {
          onReloadTree(!parentFolderId);
        }
        if (parentFolderId) {
          this.props.router.push(`/folder/${parentFolderId}`);
        } else {
          this.props.router.push('/library');
        }
      }
    }
  }

  renameVersionedStorage = async (name) => {
    const {pipeline, folders, pipelinesLibrary, onReloadTree} = this.props;
    if (!pipeline || !pipeline.value) {
      return;
    }
    const hide = message.loading(`Renaming versioned-storage ${name}...`, -1);
    await this.updateVSRequest.send({
      id: pipeline.value.id,
      name: name,
      description: pipeline.value.description,
      parentFolderId: pipeline.value.parentFolderId,
      pipelineType: pipeline.value.pipelineType
    });
    if (this.updateVSRequest.error) {
      hide();
      message.error(this.updateVSRequest.error, 5);
    } else {
      hide();
      const parentFolderId = pipeline.value.parentFolderId;
      if (parentFolderId) {
        folders.invalidateFolder(parentFolderId);
      } else {
        pipelinesLibrary.invalidateCache();
      }
      await pipeline.fetch();
      if (onReloadTree) {
        onReloadTree(!pipeline.value.parentFolderId);
      }
    }
  };

  onTableActionClick = (action) => {
    if (action && TABLE_MENU_KEYS[action.key]) {
      this.openCreateDocumentDialog(action.key);
    }
  };

  onCreateDocument = (values) => {
    const {createDocument} = this.state;
    if (createDocument && createDocument === TABLE_MENU_KEYS.folder) {
      return this.createFolder(values);
    }
    if (createDocument && createDocument === TABLE_MENU_KEYS.file) {
      return this.createFile(values);
    }
  };

  onDeleteDocument = async (document, comment) => {
    if (this.lastCommitId) {
      const {
        pipeline,
        pipelineId,
        folders,
        pipelinesLibrary
      } = this.props;
      let request;
      if (document.type.toLowerCase() === DOCUMENT_TYPES.blob) {
        request = new PipelineFileDelete(pipelineId);
      }
      if (document.type.toLowerCase() === DOCUMENT_TYPES.tree) {
        request = new PipelineFolderDelete(pipelineId);
      }
      if (!request) {
        return;
      }
      const parentFolderId = pipeline.value.parentFolderId;
      const hide = message.loading(`Deleting '${document.name}'...`, 0);
      await request.send({
        lastCommitId: this.lastCommitId,
        path: document.path,
        comment: comment || `Removing ${document.name}`
      });
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        parentFolderId
          ? folders.invalidateFolder(parentFolderId)
          : pipelinesLibrary.invalidateCache();
        await pipeline.fetch();
      }
      this.pathWasChanged();
    }
  };

  onRenameDocument = async ({name, content}) => {
    const {renameDocument} = this.state;
    const {path} = this.props;
    if (this.lastCommitId) {
      const {
        pipeline,
        pipelineId,
        folders,
        pipelinesLibrary
      } = this.props;
      let request;
      if (renameDocument.type.toLowerCase() === DOCUMENT_TYPES.blob) {
        request = new PipelineFileUpdate(pipelineId);
      }
      if (renameDocument.type.toLowerCase() === DOCUMENT_TYPES.tree) {
        request = new PipelineFolderUpdate(pipelineId);
      }
      if (!request) {
        return;
      }
      const parentFolderId = pipeline.value.parentFolderId;
      const hide = message.loading(`Renaming '${renameDocument.name}'...`, 0);
      await request.send({
        lastCommitId: this.lastCommitId,
        path: `${path || ''}${name}`,
        previousPath: renameDocument.path,
        comment: content || `Renaming ${renameDocument.name}`
      });
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        parentFolderId
          ? folders.invalidateFolder(parentFolderId)
          : pipelinesLibrary.invalidateCache();
        await pipeline.fetch();
      }
      this.pathWasChanged();
      this.closeRenameDocumentDialog();
    }
  };

  createFolder = async ({name, comment}) => {
    if (this.lastCommitId && name) {
      const {
        pipeline,
        pipelineId,
        folders,
        pipelinesLibrary
      } = this.props;
      const request = new PipelineFolderUpdate(pipelineId);
      const parentFolderId = pipeline.value.parentFolderId;
      let path = this.props.path || '';
      if (path.length > 0 && !path.endsWith('/')) {
        path = `${path}/`;
      }
      const hide = message.loading(`Creating folder '${name}'...`, 0);
      await request.send({
        lastCommitId: this.lastCommitId,
        path: `${path}${name.trim()}`,
        comment: comment || `Creating folder ${path}${name.trim()}`
      });
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        parentFolderId
          ? folders.invalidateFolder(parentFolderId)
          : pipelinesLibrary.invalidateCache();
        await pipeline.fetch();
        this.closeCreateDocumentDialog();
        this.pathWasChanged();
      }
    }
  };

  createFile = async ({name, comment}) => {
    if (this.lastCommitId && name) {
      const {
        pipeline,
        pipelineId,
        folders,
        pipelinesLibrary
      } = this.props;
      const request = new PipelineFileUpdate(pipelineId);
      const parentFolderId = pipeline.value.parentFolderId;
      let path = this.props.path || '';
      if (path.length > 0 && !path.endsWith('/')) {
        path = `${path}`;
      }
      const hide = message.loading(`Creating folder '${name}'...`, 0);
      await request.send({
        lastCommitId: this.lastCommitId,
        path: `${path}${name.trim()}`,
        comment: comment || `Creating file ${path}${name.trim()}`,
        contents: ''
      });
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        parentFolderId
          ? folders.invalidateFolder(parentFolderId)
          : pipelinesLibrary.invalidateCache();
        this.closeCreateDocumentDialog();
        await pipeline.fetch();
        this.pathWasChanged();
      }
    }
  };

  downloadSingleFile = async (document) => {
    const {pipelineId} = this.props;
    const pipelineFile = new PipelineFile(pipelineId, this.lastCommitId, document.path);
    let res;
    await pipelineFile.fetch();
    res = pipelineFile.response;
    if (!res) {
      return;
    }
    if (res.type?.includes('application/json') && res instanceof Blob) {
      checkForBlobErrors(res)
        .then(error => error
          ? message.error('Error downloading file', 5)
          : FileSaver.saveAs(res, document.name)
        );
    } else if (res) {
      FileSaver.saveAs(res, document.name);
    }
  };

  afterUpload = async () => {
    const {
      pipeline,
      folders,
      pipelinesLibrary
    } = this.props;
    const parentFolderId = pipeline.value.parentFolderId;
    const hide = message.loading('Updating folder content', 0);
    parentFolderId
      ? folders.invalidateFolder(parentFolderId)
      : pipelinesLibrary.invalidateCache();
    await pipeline.fetch();
    this.pathWasChanged();
    hide();
  };

  navigate = (path) => {
    const {router, pipelineId} = this.props;
    if (!router) {
      return;
    }
    if (path) {
      router.push({
        pathname: `/vs/${pipelineId}`,
        search: `?path=${path}`
      });
    } else {
      router.push(`/vs/${pipelineId}`);
    }
  };

  onFolderClick = (document) => {
    let path = document.path || '';
    if (!path.endsWith('/')) {
      path = `${path}/`;
    }
    this.navigate(path);
  };

  onFileClick = (file) => {
    this.openHistoryPanelWithFileInfo(file);
  };

  onRowClick = (document = {}) => {
    if (document.type && document.type.toLowerCase() === 'navback') {
      this.clearSelectedFile();
      return this.navigate(this.parentPath);
    }
    if (document.type && document.type.toLowerCase() === DOCUMENT_TYPES.tree) {
      this.clearSelectedFile();
      return this.onFolderClick(document);
    }
    if (document.type && document.type.toLowerCase() === DOCUMENT_TYPES.blob) {
      return this.onFileClick(document);
    }
  };

  openEditFileForm = () => {
    const {selectedFile} = this.state;
    if (selectedFile) {
      this.setState({editSelectedFile: true});
    }
  };

  closeEditFileForm = () => {
    this.setState({editSelectedFile: false});
  };

  saveEditFileForm = async (contents, comment) => {
    const {pipelineId, pipeline} = this.props;
    const {selectedFile} = this.state;
    if (!selectedFile) {
      return;
    }
    const request = new PipelineFileUpdate(pipelineId);
    const hide = message.loading('Committing file changes...');
    await request.send({
      contents: contents,
      comment,
      path: selectedFile.path,
      lastCommitId: this.lastCommitId
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeEditFileForm();
      await pipeline.fetch();
      this.pathWasChanged();
      this.refreshSelectedFile();
    }
  };

  renderEditItemForm = () => {
    const {createDocument, renameDocument} = this.state;
    return (
      <div>
        <CreateItemForm
          pending={false}
          title={`Create ${createDocument}`}
          visible={!!createDocument}
          onCancel={this.closeCreateDocumentDialog}
          onSubmit={this.onCreateDocument}
        />
        <CreateItemForm
          pending={false}
          title={`Rename ${getDocumentType(renameDocument)}`}
          visible={!!renameDocument}
          name={renameDocument && renameDocument.name}
          onCancel={this.closeRenameDocumentDialog}
          onSubmit={this.onRenameDocument}
        />
      </div>
    );
  };

  renderEditFileContent = () => {
    const {editSelectedFile, selectedFile} = this.state;
    const {pipeline} = this.props;
    return (
      <PipelineCodeForm
        file={editSelectedFile ? selectedFile : undefined}
        pipeline={pipeline}
        version={this.lastCommitId}
        cancel={this.closeEditFileForm}
        save={this.saveEditFileForm}
        vsStorage
      />
    );
  };

  render () {
    const {
      pipeline,
      pipelineId,
      readOnly,
      path
    } = this.props;
    const {
      error,
      lastPage,
      page,
      selectedFile
    } = this.state;
    const {
      editStorageDialog,
      showHistoryPanel,
      pending
    } = this.state;
    if (!pipeline.loaded && pipeline.pending) {
      return (
        <LoadingView />
      );
    }
    if (pipeline.error) {
      return (
        <Alert type="error" message={pipeline.error} />
      );
    }
    const contentInfo = [{
      key: 'vstable',
      containerStyle: {
        height: '100%'
      },
      size: {
        priority: 0,
        percentMinimum: 33,
        percentDefault: 75
      }
    },
    {
      key: 'vsinfo',
      containerStyle: {
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'auto'
      },
      title: 'Information',
      closable: true,
      size: {
        keepPreviousSize: true,
        priority: 0,
        percentDefault: 25,
        pxMinimum: 200
      }
    }];
    return (
      <div className={styles.vsContainer} style={{height: 'calc(100vh - 30px)'}}>
        <VersionedStorageHeader
          pipeline={pipeline}
          pipelineId={pipelineId}
          readOnly={readOnly}
          onRenameStorage={this.renameVersionedStorage}
          actions={this.actions}
          historyPanelOpen={showHistoryPanel}
          controlsEnabled={this.lastCommitId && (pipeline.loaded && !pipeline.pending)}
        />
        {
          error && (
            <Alert
              message={error}
              type="error"
            />
          )
        }
        <SplitPanel
          style={{flex: 1, overflow: 'auto', width: 'inherited', height: 'auto'}}
          onPanelClose={this.closeHistoryPanel}
          contentInfo={contentInfo}
        >
          <div
            style={{width: '100%', height: '100%', display: 'flex', flexDirection: 'column'}}
            key="vstable"
          >
            <VersionedStorageTable
              style={{height: 'auto'}}
              contents={this.filteredContents}
              onRowClick={this.onRowClick}
              showNavigateBack={path}
              pending={pending}
              controlsEnabled={this.lastCommitId && (pipeline.loaded && !pipeline.pending)}
              onTableActionClick={this.onTableActionClick}
              onDeleteDocument={this.onDeleteDocument}
              onRenameDocument={this.openRenameDocumentDialog}
              onDownloadFile={this.downloadSingleFile}
              pipelineId={pipelineId}
              path={path}
              afterUpload={this.afterUpload}
            />
            <div
              className={styles.paginationRow}
            >
              {lastPage >= 0 && (
                <Pagination
                  current={page + 1}
                  total={(lastPage + 1) * PAGE_SIZE}
                  pageSize={PAGE_SIZE}
                  size="small"
                  onChange={
                    newPage => newPage === (page + 1)
                      ? undefined
                      : this.fetchPage(newPage - 1)
                  }
                />
              )}
            </div>
          </div>
          {
            showHistoryPanel && (
              <div
                key="vsinfo"
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  overflow: 'auto',
                  flex: 1
                }}
              >
                <InfoPanel
                  file={selectedFile}
                  path={path}
                  pipelineId={pipelineId}
                  lastCommitId={this.lastCommitId}
                  pending={pending}
                  onFileEdit={this.openEditFileForm}
                  onFileDownload={this.downloadSingleFile}
                  onGoBack={this.clearSelectedFile}
                />
              </div>
            )
          }
        </SplitPanel>
        {this.renderEditFileContent()}
        <EditPipelineForm
          onSubmit={this.folderOperationWrapper(this.editVersionedStorage)}
          onCancel={this.closeEditStorageDialog}
          onDelete={this.folderOperationWrapper(this.deleteVersionedStorage)}
          visible={editStorageDialog}
          pending={pending}
          pipeline={pipeline.value}
        />
        {this.renderEditItemForm()}
      </div>
    );
  }
}

VersionedStorage.propTypes = {
  pipeline: PropTypes.object,
  pipelineId: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  readOnly: PropTypes.bool,
  onReloadTree: PropTypes.func,
  folders: PropTypes.object,
  pipelinesLibrary: PropTypes.object,
  path: PropTypes.string
};

export default VersionedStorage;
