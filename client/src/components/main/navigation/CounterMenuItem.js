/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Icon, Button, Tooltip} from 'antd';

function CounterMenuItem (props) {
  const {
    className,
    id,
    count = 0,
    maxCount = 99,
    tooltip,
    onClick,
    icon
  } = props;
  const renderCount = () => {
    if (count > maxCount) {
      return `${maxCount}+`;
    }
    return count;
  };
  return (
    <Tooltip
      overlay={tooltip}
      placement="right"
      mouseEnterDelay={0.5}
    >
      <Button
        id={id}
        className={className}
        onClick={onClick}
      >
        <Icon
          type={icon}
        />
        {
          count > 0 &&
          <span>
            {renderCount()}
          </span>
        }
      </Button>
    </Tooltip>
  );
}

CounterMenuItem.propTypes = {
  onClick: PropTypes.func,
  id: PropTypes.string.isRequired,
  icon: PropTypes.string,
  className: PropTypes.string,
  tooltip: PropTypes.string,
  count: PropTypes.number,
  maxCount: PropTypes.number
};

export default CounterMenuItem;
