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
import {Icon, Row, Tooltip} from 'antd';
import {getRunStatusIcon} from './run-status-iconset';
import DefaultStyles from './run-status-styles';
import StatusTooltips from './run-status-tooltips';
import {getStatus} from './run-statuses';

const StatusIcon = (props) => {
  const status = getStatus(props);
  const icon = getRunStatusIcon(status, props.iconSet);
  const className = [props.className, DefaultStyles[status]].filter(Boolean).join(' ');

  let iconStyle = {verticalAlign: 'middle', fontWeight: 'normal'};
  if (props.small) {
    iconStyle.fontSize = 'small';
  }
  if (props.additionalStyle) {
    iconStyle = Object.assign(iconStyle, props.additionalStyle);
  }
  if (props.additionalStyleByStatus &&
    props.additionalStyleByStatus.hasOwnProperty(status) &&
    !!props.additionalStyleByStatus[status]) {
    iconStyle = Object.assign(iconStyle, props.additionalStyleByStatus[status]);
  }

  const result = (
    <Icon
      className={className}
      type={icon}
      style={iconStyle} />
  );
  if (props.displayTooltip && StatusTooltips.hasOwnProperty(status)) {
    const {description, title} = StatusTooltips[status];
    if (!!title || !!description) {
      const tooltip = (
        <Row>
          {!!title && <Row>{title}</Row>}
          {!!description && <Row>{description}</Row>}
        </Row>
      );
      return (
        <Tooltip title={tooltip} mouseEnterDelay={1} placement={props.tooltipPlacement}>
          {result}
        </Tooltip>
      );
    }
  }
  return result;
};

StatusIcon.propTypes = {
  additionalStyle: PropTypes.object,
  additionalStyleByStatus: PropTypes.object,
  className: PropTypes.string,
  displayTooltip: PropTypes.bool,
  iconSet: PropTypes.object,
  tooltipPlacement: PropTypes.oneOf([
    'top',
    'left',
    'right',
    'bottom',
    'topLeft',
    'topRight',
    'bottomLeft',
    'bottomRight',
    'leftTop',
    'leftBottom',
    'rightTop',
    'rightBottom'
  ]),
  run: PropTypes.object,
  status: PropTypes.string,
  small: PropTypes.bool
};

StatusIcon.defaultProps = {
  displayTooltip: true
};

export default StatusIcon;
