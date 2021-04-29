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
  message
} from 'antd';
import VersionedStorageHeader from './header';
import localization from '../../../../utils/localization';
import HiddenObjects from '../../../../utils/hidden-objects';
import LoadingView from '../../../special/LoadingView';
import UpdatePipeline from '../../../../models/pipelines/UpdatePipeline';
import styles from './versioned-storage.css';

@localization.localizedComponent
@HiddenObjects.checkPipelines(p => (p.params ? p.params.id : p.id))
@HiddenObjects.injectTreeFilter
@inject('pipelines', 'folders', 'pipelinesLibrary')
@inject(({pipelines}, params) => {
  let componentParameters = params;
  if (params.params) {
    componentParameters = params.params;
  }
  return {
    pipelineId: componentParameters.id,
    pipeline: pipelines.getPipeline(componentParameters.id)
  };
})
@observer
class VersionedStorage extends localization.LocalizedReactComponent {
  state = {
    editStorageDialog: false,
    showHistoryPanel: false
  };

  get actions () {
    return {
      openHistoryPanel: this.openHistoryPanel,
      closeHistoryPanel: this.closeHistoryPanel,
      openEditSorageDialog: this.openEditSorageDialog
    };
  }

  updateVSRequest = new UpdatePipeline();

  openHistoryPanel = () => {
    this.setState({showHistoryPanel: true});
  };

  closeHistoryPanel = () => {
    this.setState({showHistoryPanel: false});
  };

  openEditSorageDialog = () => {
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
      parentFolderId: pipeline.value.parentFolderId
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
      readOnly,
      listingMode
    } = this.props;
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
          listingMode={listingMode}
          historyPanelOpen={showHistoryPanel}
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
  listingMode: PropTypes.bool,
  readOnly: PropTypes.bool,
  onReloadTree: PropTypes.func,
  folders: PropTypes.object,
  pipelinesLibrary: PropTypes.object
};

export default VersionedStorage;
