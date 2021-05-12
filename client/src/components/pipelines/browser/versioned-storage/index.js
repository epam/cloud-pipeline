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
import PipelineFileUpdate from '../../../../models/pipelines/PipelineFileUpdate';
import PipelineFileDelete from '../../../../models/pipelines/PipelineFileDelete';
import PipelineFolderDelete from '../../../../models/pipelines/PipelineFolderDelete';
import VersionedStorageListWithInfo
from '../../../../models/versioned-storage/vs-contents-with-info';
import DeletePipeline from '../../../../models/pipelines/DeletePipeline';
import PipelineGenerateReport from '../../../../models/pipelines/PipelineGenerateReport';
import InfoPanel from './info-panel';
import LaunchVSForm from './forms/launch-vs-form';
import getToolLaunchingOptions from '../../launch/utilities/get-tool-launching-options';
import UpdatePipelineToken from '../../../../models/pipelines/UpdatePipelineToken';
import {PipelineRunner} from '../../../../models/pipelines/PipelineRunner';
import PipelineFileInfo from '../../../../models/pipelines/PipelineFileInfo';
import CreateItemForm from './forms/create-item-form';
import EditPipelineForm from '../../version/forms/EditPipelineForm';
import GenerateReportDialog from './dialogs/generate-report';
import TABLE_MENU_KEYS from './table/table-menu-keys';
import DOCUMENT_TYPES from './document-types';
import roleModel from '../../../../utils/roleModel';
import styles from './versioned-storage.css';

const PAGE_SIZE = 20;
const ID_PREFIX = 'vs';

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
      try {
        const json = JSON.parse(this.result);
        const {
          status,
          message
        } = json || {};
        if (/^error$/i.test(status)) {
          resolve(message || `Error downloading file`);
          return;
        }
      } catch (e) {}
      resolve(false);
    };
    fr.readAsText(blob);
  });
}

function generateItemsFilter (preferences) {
  const ignorePatterns = (preferences.versionStorageIgnoredFiles || [])
    .map(pattern => pattern.startsWith('/')
      ? new RegExp(`^${pattern}`, 'i')
      : new RegExp(`${pattern}$`, 'i')
    );
  const getPath = (content) => {
    if (content.git_object && content.git_object.path) {
      const {path} = content.git_object;
      if (path.startsWith('/')) {
        return path;
      }
      return `/${path}`;
    }
    return undefined;
  };
  return (content) => {
    const path = getPath(content);
    if (path) {
      return !(ignorePatterns.some(pattern => pattern.test(path)));
    }
    return true;
  };
}

@localization.localizedComponent
@HiddenObjects.checkPipelines(p => (p.params ? p.params.id : p.id))
@HiddenObjects.injectTreeFilter
@inject('pipelines', 'folders', 'pipelinesLibrary', 'preferences', 'awsRegions')
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
    generateReportDialog: false,
    error: undefined,
    lastPage: 0,
    page: 0,
    pending: false,
    showHistoryPanel: false,
    selectedFile: null,
    launchVSFormVisible: false
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
      this.clearSelectedFile();
      this.pathWasChanged();
    }
  };

  @computed
  get lastCommitId () {
    const {pipeline} = this.props;
    if (
      pipeline &&
      pipeline.value &&
      pipeline.loaded &&
      pipeline.value.currentVersion
    ) {
      return pipeline.value.currentVersion.commitId;
    }
    return null;
  };

  @computed
  get writeAllowed () {
    const {pipeline} = this.props;
    if (
      pipeline &&
      pipeline.value &&
      pipeline.loaded
    ) {
      return roleModel.writeAllowed(pipeline.value);
    }
    return false;
  };

  get filteredContents () {
    const {contents} = this.state;
    if (!contents || !contents.length) {
      return [];
    }
    return contents.filter(generateItemsFilter(this.props.preferences));
  };

  get actions () {
    return {
      openHistoryPanel: this.openHistoryPanel,
      closeHistoryPanel: this.closeHistoryPanel,
      openEditStorageDialog: this.openEditStorageDialog,
      runVersionedStorage: this.runVersionedStorage,
      openGenerateReportDialog: this.openGenerateReportDialog
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
          error: undefined,
          ...result
        });
      };
      const reject = error => {
        this.setState({
          page,
          pending: false,
          error: error || 'Error fetching versioned storage contents',
          contents: []
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
            reject(request.error);
          } else {
            const {
              listing = [],
              max_page: lastPage
            } = request.value;
            resolve({contents: listing.slice(), lastPage});
          }
        })
        .catch(e => reject(e.message));
    });
  };

  clearSelectedFile = () => {
    this.setState({selectedFile: undefined});
  };

  refreshSelectedFile = (path) => {
    if (!path) {
      const {selectedFile} = this.state;
      if (selectedFile) {
        path = selectedFile.path;
      }
    }
    if (path) {
      const request = new PipelineFileInfo(
        this.props.pipelineId,
        undefined,
        path
      );
      request
        .fetch()
        .then(() => {
          if (request.loaded) {
            this.setState({
              selectedFile: {
                ...(request.value)
              }
            });
          }
        })
        .catch(() => {
          this.setState({selectedFile: undefined});
        });
    }
  };

  openHistoryPanel = () => {
    this.setState({showHistoryPanel: true});
  };

  closeHistoryPanel = () => {
    this.setState({showHistoryPanel: false, selectedFile: undefined});
  };

  runVersionedStorage = () => {
    this.openLaunchVSForm();
  };

  openLaunchVSForm = () => {
    this.setState({
      launchVSFormVisible: true
    });
  };

  openLaunchVSAdvancedForm = (tool, tag) => {
    const {
      router,
      pipelineId,
      pipeline
    } = this.props;
    if (router && pipelineId && pipeline.loaded && pipeline.value.currentVersion) {
      const options = [
        `vs=true`,
        tool && `tool=${tool.id}`,
        tool && tag && `version=${tag}`
      ]
        .filter(Boolean);
      router.push(
        `/launch/${pipelineId}/${pipeline.value.currentVersion.name}/default?${options.join('&')}`
      );
    }
  };

  closeLaunchVSForm = () => {
    this.setState({
      launchVSFormVisible: false
    });
  };

  launchVS = (tool, tag) => {
    const {
      awsRegions,
      preferences,
      pipeline,
      pipelineId,
      router
    } = this.props;
    this.closeLaunchVSForm();
    const hide = message.loading('Launching Versioned Storage...', 0);
    const {
      currentVersion = {}
    } = pipeline.value || {};
    const request = new PipelineRunner();
    getToolLaunchingOptions({awsRegions, preferences}, tool, tag)
      .then((launchPayload) => {
        const payload = {
          ...launchPayload,
          pipelineId: +pipelineId,
          version: currentVersion.name
        };
        return request.send({...payload, force: true});
      })
      .then(() => {
        if (request.error) {
          throw new Error(request.error);
        } else if (request.loaded) {
          const {id} = request.value;
          return Promise.resolve(+id);
        }
      })
      .catch(e => {
        message.error(e.message, 5);
        return Promise.resolve();
      })
      .then((runId) => {
        hide();
        if (typeof runId === 'number') {
          router.push(`/run/${runId}`);
        }
      });
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
    const {pipeline} = this.props;
    this.setState({editStorageDialog: false}, () => pipeline.fetch());
  };

  openGenerateReportDialog = () => {
    this.setState({generateReportDialog: true});
  };

  closeGenerateReportDialog = () => {
    this.setState({generateReportDialog: false});
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
      const {selectedFile} = this.state;
      const {
        pipeline,
        pipelineId,
        folders,
        pipelinesLibrary
      } = this.props;
      let request;
      const isFolder = document.type.toLowerCase() === DOCUMENT_TYPES.tree;
      if (isFolder) {
        request = new PipelineFolderDelete(pipelineId);
      } else {
        request = new PipelineFileDelete(pipelineId);
      }
      if (!request) {
        return;
      }
      const parentFolderId = pipeline.value.parentFolderId;
      const hide = message.loading(`Deleting '${document.name}'...`, 0);
      await request.send({
        lastCommitId: this.lastCommitId,
        path: document.path,
        comment: comment || `Removing ${isFolder ? 'folder' : 'file'} ${document.path}`
      });
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        parentFolderId
          ? folders.invalidateFolder(parentFolderId)
          : pipelinesLibrary.invalidateCache();
        await pipeline.fetch();
        if (selectedFile && selectedFile.path === document.path) {
          this.clearSelectedFile();
        }
      }
      this.pathWasChanged();
    }
  };

  onRenameDocument = async ({name, content}) => {
    const {
      renameDocument,
      selectedFile
    } = this.state;
    const {path} = this.props;
    if (this.lastCommitId) {
      const {
        pipeline,
        pipelineId,
        folders,
        pipelinesLibrary
      } = this.props;
      let request;
      const isFolder = renameDocument.type.toLowerCase() === DOCUMENT_TYPES.tree;
      if (isFolder) {
        request = new PipelineFolderUpdate(pipelineId);
      } else {
        request = new PipelineFileUpdate(pipelineId);
      }
      if (!request) {
        return;
      }
      const parentFolderId = pipeline.value.parentFolderId;
      const hide = message.loading(`Renaming '${renameDocument.name}'...`, 0);
      const newPath = `${path || ''}${name}`;
      await request.send({
        lastCommitId: this.lastCommitId,
        path: newPath,
        previousPath: renameDocument.path,
        comment: content ||
          `Renaming ${isFolder ? 'folder' : 'file'} ${renameDocument.path} to ${newPath}`
      });
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        parentFolderId
          ? folders.invalidateFolder(parentFolderId)
          : pipelinesLibrary.invalidateCache();
        await pipeline.fetch();
        if (selectedFile && selectedFile.path === renameDocument.path) {
          this.refreshSelectedFile(newPath);
        }
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
      const hide = message.loading(`Creating file '${name}'...`, 0);
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

  afterUpload = async (files = []) => {
    const {
      pipeline,
      folders,
      pipelinesLibrary,
      path
    } = this.props;
    const {
      selectedFile
    } = this.state;
    const pathCorrected = path && path.endsWith('/') ? path.slice(0, -1) : path;
    const uploadedFiles = files
      .map(file => pathCorrected && pathCorrected.length ? `${pathCorrected}/${file}` : file);
    const parentFolderId = pipeline.value.parentFolderId;
    const hide = message.loading('Updating folder content', 0);
    parentFolderId
      ? folders.invalidateFolder(parentFolderId)
      : pipelinesLibrary.invalidateCache();
    await pipeline.fetch();
    if (selectedFile && uploadedFiles.indexOf(selectedFile.path) >= 0) {
      this.refreshSelectedFile();
    }
    this.pathWasChanged();
    hide();
  };

  generateReport = async (settings = {}) => {
    const {
      pipeline,
      pipelineId
    } = this.props;
    const {
      authors,
      extensions,
      dateFrom,
      dateTo,
      includeDiff,
      splitDiffsBy,
      downloadAsArchive
    } = settings;
    const hide = message.loading(`Generating report...`, 0);
    const request = new PipelineGenerateReport(pipelineId);
    await request.send({
      commitsFilter: {
        authors,
        extensions,
        dateFrom,
        dateTo
      },
      includeDiff,
      groupType: splitDiffsBy,
      archive: downloadAsArchive,
      userTimeOffsetInMin: -(new Date()).getTimezoneOffset()
    });
    hide();
    if (request.error) {
      message.error(request.error || 'Error downloading file', 5);
    } else if (request.value instanceof Blob) {
      const fileName = `${pipeline.value.name}-report`;
      const extension = downloadAsArchive ? 'zip' : 'docx';
      if (request.value.type?.includes('application/json')) {
        checkForBlobErrors(request.value)
          .then(error => error
            ? message.error(error, 5)
            : FileSaver.saveAs(request.value, `${fileName}.${extension}`)
          );
      } else {
        FileSaver.saveAs(request.value, `${fileName}.${extension}`);
      }
    } else {
      message.error('Error downloading file', 5);
    }
    this.closeGenerateReportDialog();
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

  onRefresh = async () => {
    const {pipeline} = this.props;
    await pipeline.fetch();
    this.pathWasChanged();
    this.refreshSelectedFile();
  };

  renderEditItemForm = () => {
    const {
      createDocument,
      renameDocument
    } = this.state;
    const {
      pipelineId,
      path
    } = this.props;
    const documentType = createDocument || getDocumentType(renameDocument);
    return (
      <div>
        <CreateItemForm
          pending={false}
          title={`Create ${createDocument}`}
          visible={!!createDocument}
          onCancel={this.closeCreateDocumentDialog}
          onSubmit={this.onCreateDocument}
          pipelineId={pipelineId}
          path={path}
          documentType={documentType}
        />
        <CreateItemForm
          pending={false}
          title={`Rename ${getDocumentType(renameDocument)}`}
          visible={!!renameDocument}
          name={renameDocument && renameDocument.name}
          onCancel={this.closeRenameDocumentDialog}
          onSubmit={this.onRenameDocument}
          pipelineId={pipelineId}
          path={path}
          documentType={documentType}
        />
      </div>
    );
  };

  render () {
    const {
      pipeline,
      pipelineId,
      readOnly,
      path,
      preferences
    } = this.props;
    const {
      error,
      lastPage,
      page,
      selectedFile,
      generateReportDialog,
      launchVSFormVisible
    } = this.state;
    const {
      editStorageDialog,
      showHistoryPanel,
      pending
    } = this.state;
    if (
      (!pipeline.loaded && pipeline.pending) ||
      (!preferences.loaded && preferences.pending)
    ) {
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
              lastCommit={this.lastCommitId}
              controlsEnabled={this.lastCommitId && (pipeline.loaded && !pipeline.pending)}
              onTableActionClick={this.onTableActionClick}
              onDeleteDocument={this.onDeleteDocument}
              onRenameDocument={this.openRenameDocumentDialog}
              onNavigate={this.navigate}
              pipelineId={pipelineId}
              path={path}
              afterUpload={this.afterUpload}
              versionedStorage={pipeline?.value}
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
                  readOnly={!this.writeAllowed}
                  lastCommitId={this.lastCommitId}
                  pending={pending}
                  onRefresh={this.onRefresh}
                  onGoBack={this.clearSelectedFile}
                />
              </div>
            )
          }
        </SplitPanel>
        <EditPipelineForm
          onSubmit={this.folderOperationWrapper(this.editVersionedStorage)}
          onCancel={this.closeEditStorageDialog}
          onDelete={this.folderOperationWrapper(this.deleteVersionedStorage)}
          visible={editStorageDialog}
          pending={pending}
          pipeline={pipeline.value}
        />
        <GenerateReportDialog
          visible={generateReportDialog}
          onCancel={this.closeGenerateReportDialog}
          onOk={this.generateReport}
          idPrefix={ID_PREFIX}
        />
        {this.renderEditItemForm()}
        <LaunchVSForm
          visible={launchVSFormVisible}
          onClose={this.closeLaunchVSForm}
          onLaunch={this.launchVS}
          onLaunchCustom={this.openLaunchVSAdvancedForm}
        />
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
