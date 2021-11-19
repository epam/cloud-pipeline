/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Icon, Popover} from 'antd';

function ToolLink ({link, style}) {
  if (!link) {
    return null;
  }
  return (
    <Popover
      content={(
        <div>
          This is a tool link
        </div>
      )}
    >
      <Icon type="link" style={style} />
    </Popover>
  );
}

ToolLink.propTypes = {
  link: PropTypes.oneOfType([PropTypes.bool, PropTypes.number, PropTypes.string]),
  style: PropTypes.object
};

export default ToolLink;
