/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Provider} from 'mobx-react';
import HcsImage from '../../../special/hcs-image';

const themes = {
  addThemeChangedListener: () => {},
  removeThemeChangedListener: () => {}
};

class HCSPreview extends React.Component {
  componentDidMount () {
    const {onPreviewLoaded} = this.props;
    if (onPreviewLoaded) {
      onPreviewLoaded({large: true});
    }
  }

  render () {
    const {
      children,
      storage,
      file
    } = this.props;
    return (
      <Provider themes={themes}>
        <HcsImage
          storage={storage}
          path={file}
          style={{
            height: 'calc(100vh - 75px)'
          }}
        >
          {children}
        </HcsImage>
      </Provider>
    );
  }
}

HCSPreview.propTypes = {
  className: PropTypes.string,
  children: PropTypes.node,
  file: PropTypes.string,
  storage: PropTypes.object,
  onPreviewLoaded: PropTypes.func
};

export default HCSPreview;
