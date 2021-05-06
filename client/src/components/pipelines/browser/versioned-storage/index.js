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
import VersionedStorageHeader from './header';
import VersionedStorageTable from './table';
import localization from '../../../../utils/localization';
import HiddenObjects from '../../../../utils/hidden-objects';
import LoadingView from '../../../special/LoadingView';
import UpdatePipeline from '../../../../models/pipelines/UpdatePipeline';
import PipelineFolderUpdate from '../../../../models/pipelines/PipelineFolderUpdate';
import PipelineFileUpdate from '../../../../models/pipelines/PipelineFileUpdate';
import PipelineFileDelete from '../../../../models/pipelines/PipelineFileDelete';
import PipelineFolderDelete from '../../../../models/pipelines/PipelineFolderDelete';
import VersionedStorageListWithInfo from '../../../../models/versioned-storages/list-with-info';
import EditItemForm from '../forms/EditItemForm';
import TABLE_MENU_KEYS from './table/table-menu-keys';
import DOCUMENT_TYPES from './document-types';
import styles from './versioned-storage.css';

const PAGE_SIZE = 20;

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
    showHistoryPanel: false
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
      pipeline.loaded &&
      !pipeline.pending
    ) {
      return pipeline.value.currentVersion.commitId;
    }
    return null;
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

  openHistoryPanel = () => {
    this.setState({showHistoryPanel: true});
  };

  closeHistoryPanel = () => {
    this.setState({showHistoryPanel: false});
  };

  openEditStorageDialog = () => {
    this.setState({editStorageDialog: true});
  };

  openCreateDocumentDialog = (type) => {
    if (type) {
      this.setState({createDocument: type});
    }
  };

  closeCreateDocumentDialog = () => {
    this.setState({createDocument: undefined});
  };

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
        comment: comment || `${document.name} deleted`
      });
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        parentFolderId
          ? folders.invalidateFolder(parentFolderId)
          : pipelinesLibrary.invalidateCache();
      }
      this.pathWasChanged();
    }
  };

  createFolder = async ({name, content}) => {
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
        comment: content
      });
      hide();
      if (request.error) {
        message.error(request.error, 5);
      } else {
        parentFolderId
          ? folders.invalidateFolder(parentFolderId)
          : pipelinesLibrary.invalidateCache();
        this.closeCreateDocumentDialog();
      }
      this.pathWasChanged();
    }
  };

  createFile = async ({name, content}) => {
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
        comment: content,
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
      }
      this.pathWasChanged();
    }
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

  onFileClick = (document) => {

  };

  onRowClick = (document = {}) => {
    if (document.type && document.type.toLowerCase() === 'navback') {
      return this.navigate(this.parentPath);
    }
    if (document.type && document.type.toLowerCase() === DOCUMENT_TYPES.tree) {
      return this.onFolderClick(document);
    }
    if (document.type && document.type.toLowerCase() === DOCUMENT_TYPES.blob) {
      return this.onFileClick(document);
    }
  };

  render () {
    const {
      pipeline,
      pipelineId,
      readOnly,
      path
    } = this.props;
    const {
      contents,
      error,
      lastPage,
      page,
      createDocument
    } = this.state;
    const {
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
    return (
      <div className={styles.vsContainer}>
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
        <VersionedStorageTable
          contents={contents}
          onRowClick={this.onRowClick}
          showNavigateBack={path}
          pending={pending}
          controlsEnabled={this.lastCommitId && (pipeline.loaded && !pipeline.pending)}
          onTableActionClick={this.onTableActionClick}
          onDeleteDocument={this.onDeleteDocument}
        />
        <EditItemForm
          pending={false}
          includeFileContentField
          title={`Create ${createDocument}`}
          contentPlaceholder="Comment"
          visible={!!createDocument}
          onCancel={this.closeCreateDocumentDialog}
          onSubmit={this.onCreateDocument}
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
