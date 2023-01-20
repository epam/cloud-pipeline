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
import {inject, observer} from 'mobx-react';
import {VERSION} from '../../../config';

function ApplicationVersion (
  {
    className,
    style,
    preferences,
    applicationInfo
  }
) {
  const deploymentName = (preferences.loaded ? preferences.deploymentName : undefined) ||
    'EPAM Cloud Pipeline';
  let versionDescription = VERSION;
  if (applicationInfo.loaded) {
    const {
      version = VERSION,
      prettyName
    } = (applicationInfo.value || {});
    if (version && prettyName) {
      versionDescription = `${prettyName} (${version})`;
    } else if (prettyName) {
      versionDescription = prettyName;
    } else if (version) {
      versionDescription = version;
    }
  }
  return (
    <div
      className={className}
      style={style}
    >
      <div>
        <b>{deploymentName}</b>
      </div>
      {
        versionDescription && (
          <div>
            <b style={{marginRight: 5}}>Version:</b>
            {versionDescription}
          </div>
        )
      }
    </div>
  );
}

ApplicationVersion.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object
};

export default inject('preferences', 'applicationInfo')(observer(ApplicationVersion));
