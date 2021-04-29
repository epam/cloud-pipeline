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
import {inject, observer} from 'mobx-react';
import {
  Alert
} from 'antd';
import localization from '../../../../utils/localization';
import HiddenObjects from '../../../../utils/hidden-objects';
import LoadingView from '../../../special/LoadingView';

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
  render () {
    const {pipeline} = this.props;
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
      <div
        style={{display: 'flex', flexDirection: 'column', height: '100%'}}
      >
        <Alert type="info" message="Under construction" />
      </div>
    );
  }
}

export default VersionedStorage;
