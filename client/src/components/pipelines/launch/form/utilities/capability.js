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
import {Icon, Tooltip} from 'antd';

const Capability = ({capability}) => {
  if (!capability) {
    return null;
  }
  const {
    name,
    disabled,
    os = [],
    cloud = []
  } = capability;
  if (disabled && (os.length > 0 || cloud.length > 0)) {
    return (
      <Tooltip
        title={(
          <div>
            <div
              style={{marginBottom: 10}}
            >
              <b>This capability is not allowed</b>
            </div>
            {
              os.length > 0 && (
                <div>
                  Supported OS versions:
                </div>
              )
            }
            {
              os.length > 0 && (
                <ul style={{listStyle: 'disc inside'}}>
                  {
                    os.map((o, i) => (
                      <li key={`${o}-${i}`}>
                        {o}
                      </li>
                    ))
                  }
                </ul>
              )
            }
            {
              cloud.length > 0 && (
                <div>
                  Supported Cloud Providers:
                </div>
              )
            }
            {
              cloud.length > 0 && (
                <ul style={{listStyle: 'disc inside'}}>
                  {
                    cloud.map((o, i) => (
                      <li
                        key={`${o}-${i}`}
                      >
                        {o.toUpperCase()}
                      </li>
                    ))
                  }
                </ul>
              )
            }
          </div>
        )}
        trigger={['hover']}
        overlayStyle={{zIndex: 1051}}
        placement="left"
      >
        <span>{name}</span>
        <Icon
          type="question-circle-o"
          style={{marginLeft: 5}}
        />
      </Tooltip>
    );
  }
  return (
    <span>
      {name}
    </span>
  );
};

export default Capability;
