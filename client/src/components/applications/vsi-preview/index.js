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
import {inject} from 'mobx-react';
import styles from './vsi-preview.css';
import previewStyles from '../../search/preview/preview.css';
import VSIPreview from '../../search/preview/vsi-preview';
import parseQueryParameters from '../../../utils/queryParameters';

@inject(({routing}) => {
  const queryParameters = parseQueryParameters(routing);
  const getNumber = o => !Number.isNaN(Number(o)) ? Number(o) : undefined;
  return {
    storageId: queryParameters.storage,
    file: decodeURIComponent(queryParameters.file),
    zoom: getNumber(queryParameters.zoom),
    roll: getNumber(queryParameters.roll),
    x: getNumber(queryParameters.x),
    y: getNumber(queryParameters.y)
  };
})
class VSIPreviewPage extends React.Component {
  onCameraChanged = (opts) => {
    const {router, storageId, file} = this.props;
    const {
      zoom,
      roll,
      x,
      y
    } = opts || {};
    // eslint-disable-next-line
    router.push(`/wsi?storage=${storageId}&file=${encodeURIComponent(file)}&roll=${roll}&zoom=${zoom}&x=${x}&y=${y}`);
  };

  shouldComponentUpdate (nextProps, nextState, nextContext) {
    return nextProps.file !== this.props.file || nextProps.storageId !== this.props.storageId;
  }

  render () {
    const {
      storageId,
      file,
      zoom,
      roll,
      x,
      y
    } = this.props;
    if (storageId && file) {
      return (
        <div className={styles.container}>
          <VSIPreview
            className={previewStyles.wsiPage}
            storageId={storageId}
            file={file}
            fullScreenAvailable={false}
            shareAvailable={false}
            zoom={zoom}
            roll={roll}
            x={x}
            y={y}
            onCameraChanged={this.onCameraChanged}
          />
        </div>
      );
    }
    return null;
  }
}

export default VSIPreviewPage;
