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
import VersionedStorageListWithInfo from '../../../../models/versioned-storages/list-with-info';
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

  componentDidMount () {
    this.pathWasChanged();
  }

  componentDidUpdate (prevProps) {
    if (
      prevProps.path !== this.props.path ||
      prevProps.pipelineId !== this.props.pipelineId
    ) {
      this.pathWasChanged();
    }
  }

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

  get actions () {
    return {
      openHistoryPanel: this.openHistoryPanel,
      closeHistoryPanel: this.closeHistoryPanel,
      openEditStorageDialog: this.openEditStorageDialog
    };
  }

  updateVSRequest = new UpdatePipeline();

  openHistoryPanel = () => {
    this.setState({showHistoryPanel: true});
  };

  closeHistoryPanel = () => {
    this.setState({showHistoryPanel: false});
  };

  openEditStorageDialog = () => {
    this.setState({editStorageDialog: true});
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

  render () {
    const {
      pipeline,
      pipelineId,
      readOnly
    } = this.props;
    const {
      contents,
      error,
      lastPage,
      page
    } = this.state;
    console.log(contents);
    const {
      showHistoryPanel
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
