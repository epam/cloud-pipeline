/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
  Alert
} from 'antd';
import UserName from '../../../special/UserName';

function isPersonalGroup (group) {
  const {
    name = '',
    owner: ownerName = ''
  } = group || {};
  const owner = (ownerName || '')
    .replace(/[^a-zA-Z0-9-]/g, '-')
    .toLowerCase();
  return name.toLowerCase() === owner;
}

function PersonalToolWarning (
  {
    className,
    style,
    preferences,
    dockerRegistries,
    docker,
    showIcon
  }
) {
  if (
    dockerRegistries &&
    dockerRegistries.loaded &&
    docker &&
    preferences &&
    preferences.loaded &&
    preferences.uiPersonalToolsLaunchWarningEnabled
  ) {
    const [registryName = '', groupName = '', toolName = ''] = docker.split('/');
    const {registries = []} = dockerRegistries.value || {};
    const registry = registries
      .find((aRegistry) => (aRegistry.path || '').toLowerCase() === registryName.toLowerCase());
    if (registry) {
      const {
        groups = []
      } = registry;
      const group = groups
        .find((aGroup) => (aGroup.name || '').toLowerCase() === groupName.toLowerCase());
      if (
        group &&
        !group.privateGroup &&
        isPersonalGroup(group)
      ) {
        return (
          <Alert
            type="warning"
            className={className}
            style={style}
            showIcon={showIcon}
            message={(
              <div>
                <div>
                  The
                  <b style={{margin: '0 5px'}}>
                    {[groupName, toolName].join('/')}
                  </b>
                  tool is not managed by the platform support team.
                </div>
                <div>
                  It may contain errors/issues, please contact
                  <UserName
                    userName={group.owner}
                    style={{margin: '0 5px', fontWeight: 'bold'}}
                  />
                  ({group.owner}) for support in case of any trouble.
                </div>
              </div>
            )}
          />
        );
      }
    }
  }
  return null;
}

PersonalToolWarning.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  docker: PropTypes.string,
  showIcon: PropTypes.bool
};

export default inject('preferences', 'dockerRegistries')(
  observer(PersonalToolWarning)
);
