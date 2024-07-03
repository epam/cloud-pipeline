/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import HcsImage from '../../../special/hcs-image';

class HCSPreview extends React.Component {
  componentDidMount () {
    const {
      onHideInfo,
      onPreviewLoaded
    } = this.props;
    if (onHideInfo) {
      onHideInfo(true);
    }
    if (onPreviewLoaded) {
      onPreviewLoaded({requireMaximumSpace: true});
    }
  }

  render () {
    const {
      className,
      storageId,
      file,
      children,
      detailsTitle,
      detailsButtonTitle
    } = this.props;
    return (
      <HcsImage
        className={className}
        path={file}
        storageId={storageId}
        style={{
          height: 'calc(100vh - 75px)'
        }}
        detailsTitle={detailsTitle}
        detailsButtonTitle={detailsButtonTitle}
      >
        {children}
      </HcsImage>
    );
  };
}

HCSPreview.propTypes = {
  className: PropTypes.string,
  file: PropTypes.string,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  fullScreenAvailable: PropTypes.bool,
  shareAvailable: PropTypes.bool,
  x: PropTypes.number,
  y: PropTypes.number,
  zoom: PropTypes.number,
  roll: PropTypes.number,
  onCameraChanged: PropTypes.func,
  onPreviewLoaded: PropTypes.func,
  fullscreen: PropTypes.bool,
  onFullScreenChange: PropTypes.func,
  onHideInfo: PropTypes.func,
  detailsTitle: PropTypes.string,
  detailsButtonTitle: PropTypes.string
};

HCSPreview.defaultProps = {
  fullScreenAvailable: true,
  shareAvailable: true
};

export default HCSPreview;
