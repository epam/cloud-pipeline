/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Icon} from 'antd';
import classNames from 'classnames';
import AddDockerRegistryControl from '../../add-docker-registry-control';
import styles from '../../edit-hot-node-pool.css';

function DockerImagesControl ({
  dockerImages,
  disabled,
  readOnly,
  error,
  touched,
  onChange,
  onRemove,
  onAdd
}) {
  const images = (dockerImages || [])
    .filter(o => !o.removed)
    .map(o => (o.image || '').trim())
    .filter(Boolean);
  const isDuplicateImage = (image) => {
    return images.filter(o => o === image).length > 1;
  };
  const filteredDockerImages = dockerImages.filter(o => !o.removed);
  return (
    <div>
      <div
        className={classNames(styles.formRow, styles.multiRow)}
      >
        <span className={styles.label}>
          Docker images:
        </span>
        <div className={styles.column}>
          {
            filteredDockerImages.map((image) => (
              <AddDockerRegistryControl
                key={image.id}
                disabled={disabled || readOnly}
                duplicate={isDuplicateImage(image.image)}
                docker={image.image}
                onChange={onChange(image.id)}
                onRemove={onRemove(image.id)}
              />
            ))
          }
          {
            !readOnly && (
              <div>
                <Button
                  disabled={disabled}
                  onClick={onAdd}
                  type="dashed"
                >
                  <Icon type="plus" />
                  Add docker image
                </Button>
              </div>
            )
          }
          {readOnly && filteredDockerImages.length === 0 ? (
            <span>Not specified</span>
          ) : null}
        </div>
      </div>
      {
        error && touched && (
          <div className={classNames(styles.formRow, styles.error, 'cp-error')}>
            {error}
          </div>
        )
      }
    </div>
  );
}

DockerImagesControl.propTypes = {
  dockerImages: PropTypes.array,
  disabled: PropTypes.bool,
  readOnly: PropTypes.bool,
  error: PropTypes.string,
  touched: PropTypes.bool,
  onChange: PropTypes.func,
  onRemove: PropTypes.func,
  onAdd: PropTypes.func
};

export default DockerImagesControl;
