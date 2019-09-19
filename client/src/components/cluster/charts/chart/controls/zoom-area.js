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
import {inject, observer} from 'mobx-react';

@inject('plotContext')
@observer
class ZoomArea extends React.Component {
  render () {
    const {
      plotContext,
      from,
      to,
      xAxis
    } = this.props;
    if (plotContext && from !== undefined && to !== undefined) {
      const start = Math.min(from, to);
      const end = Math.max(from, to);
      const axis = plotContext.getAxis(xAxis);
      if (axis) {
        const xStart = axis.getCanvasCoordinate(start);
        const xEnd = axis.getCanvasCoordinate(end);
        const top = plotContext.top;
        const bottom = plotContext.height - plotContext.bottom;
        return (
          <g>
            <rect
              x={xStart} y={top}
              width={xEnd - xStart}
              height={bottom - top}
              fill={'#999'}
              opacity={0.25}
            />
            <path
              d={`M ${xStart},${top} L ${xStart},${bottom} M ${xEnd},${top} L ${xEnd},${bottom}`}
              stroke={'#999'}
              strokeWidth={1}
            />
          </g>
        );
      }
    }
    return null;
  }
}

ZoomArea.propTypes = {
  from: PropTypes.number,
  to: PropTypes.number,
  xAxis: PropTypes.string
};

ZoomArea.defaultProps = {
  xAxis: 'x'
};

export default ZoomArea;
