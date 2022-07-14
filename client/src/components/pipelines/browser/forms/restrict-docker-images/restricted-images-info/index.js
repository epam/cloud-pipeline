/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import PropTypes from 'prop-types';
import {
  Icon,
  Popover
} from 'antd';
import classNames from 'classnames';
import DockerImageDetails from '../../../../../cluster/hot-node-pool/docker-image-details';
import styles from './restricted-images-info.css';
import FSMountStatus, {MountStatus} from '../../../../../special/fs-mount-status';

const getToolVersions = (tool) => {
  const {versions} = tool;
  if (versions && versions.length) {
    return `(${tool.versions.map(o => o.version).join(', ')})`;
  }
  return '';
};

const getDockerImage = (tool) => {
  return `${tool.registry}/${tool.image}`;
};

function RestrictedImagesInfo ({
  toolsToMount,
  status
}) {
  const renderContent = () => {
    const displayStatus = status && status !== MountStatus.active;
    return (
      <div
        className={styles.popoverContainer}
      >
        {
          displayStatus && (
            <div>
              Storage status is: <FSMountStatus status={status} />
            </div>
          )
        }
        {
          toolsToMount && toolsToMount.length > 0 && (
            <div
              className={
                classNames(
                  styles.title,
                  {
                    'cp-divider': displayStatus,
                    'top': displayStatus
                  }
                )
              }
            >
              Storage is automatically mounted to:
            </div>
          )
        }
        {
          (toolsToMount || []).map(tool => (
            <span
              key={tool.id}
              className={styles.toolRow}
            >
              <span className={styles.toolName}>
                <DockerImageDetails
                  docker={getDockerImage(tool)}
                />
              </span>
              {getToolVersions(tool)}
            </span>
          ))
        }
      </div>
    );
  };
  if (
    (!toolsToMount || toolsToMount.length === 0) &&
    (!status || status === MountStatus.active)
  ) {
    return null;
  }
  return (
    <div className={styles.container}>
      <Popover
        content={renderContent()}
        overlayClassName={styles.overlay}
      >
        <Icon
          type="exclamation-circle-o"
          className={classNames('cp-icon-larger', 'cp-danger')}
        />
      </Popover>
    </div>
  );
}

RestrictedImagesInfo.PropTypes = {
  toolsToMount: PropTypes.arrayOf(PropTypes.shape({
    id: PropTypes.number,
    image: PropTypes.string,
    registry: PropTypes.string,
    versions: PropTypes.oneOfType([PropTypes.array, PropTypes.object])
  })),
  status: PropTypes.string
};

export default RestrictedImagesInfo;
