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
import PropTypes from 'prop-types';

const DEFAULT_STYLE = {
  horisontal: {
    width: '100%',
    height: '1px',
    background: '#d9d9d9',
    margin: '10px 0'
  },
  vertical: {
    width: '1px',
    height: '100%',
    background: '#d9d9d9',
    margin: '0 10px'
  }
};

function Divider ({style, vertical}) {
  const getStyle = () => {
    return vertical
      ? Object.assign(DEFAULT_STYLE.vertical, style)
      : Object.assign(DEFAULT_STYLE.horisontal, style);
  };
  return (
    <div style={getStyle()}>
      {'\u00A0'}
    </div>
  );
};

Divider.PropTypes = {
  style: PropTypes.object,
  vertical: PropTypes.bool
};

export default Divider;
