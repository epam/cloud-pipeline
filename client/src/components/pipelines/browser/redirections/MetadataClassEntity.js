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
import {Alert} from 'antd';
import LoadingView from '../../../special/LoadingView';
import MetadataEntityLoad from '../../../../models/folderMetadata/MetadataEntityLoad';

class MetadataClassEntity extends React.PureComponent {
  state = {
    error: undefined,
    folder: undefined,
    entity: undefined
  };

  componentDidMount () {
    this.navigate();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    const {
      routeParams = {}
    } = this.props;
    const {
      folder: propFolder,
      entity: propEntity
    } = routeParams;
    const {
      folder,
      entity
    } = this.state;
    if (+propFolder !== +folder || propEntity !== entity) {
      this.navigate();
    }
  }

  navigate = () => {
    const {
      routeParams = {},
      router
    } = this.props || {};
    const {
      folder,
      entity
    } = routeParams;
    let error;
    if (!folder) {
      error = 'Unknown metadata folder';
    } else if (!entity) {
      error = 'Unknown entity';
    }
    this.setState({
      folder,
      entity,
      error
    }, () => {
      if (folder && entity) {
        const metadataRequest = new MetadataEntityLoad(entity);
        metadataRequest
          .fetch()
          .then(() => {
            if (folder === this.state.folder && entity === this.state.entity) {
              if (metadataRequest.error || !metadataRequest.loaded) {
                this.setState({
                  error: metadataRequest.error
                });
              } else {
                const result = metadataRequest.value || {};
                const {
                  classEntity
                } = result;
                if (!classEntity) {
                  router.push(`/metadataFolder/${folder}`);
                } else {
                  const {
                    name
                  } = classEntity;
                  if (!name) {
                    router.push(`/metadataFolder/${folder}`);
                  } else {
                    router.push(`/metadata/${folder}/${name}`);
                  }
                }
              }
            }
          });
      }
    });
  };

  render () {
    const {
      error
    } = this.state;
    if (error) {
      return (
        <Alert type="error" message={error} />
      );
    }
    return (
      <LoadingView />
    );
  }
}

export default inject('pipelines')(observer(MetadataClassEntity));
