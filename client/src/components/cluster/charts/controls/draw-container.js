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
import {inject} from 'mobx-react';

@inject('timeline')
class DrawContainer extends React.PureComponent {
  render () {
    const {timeline} = this.props;
    console.log('render draw-container');
    return (
      <DrawContainerWithOffset
        offset={timeline.offset}
        ratio={timeline.plotToCanvasRatio}
      />
    );
  }
}

class DrawContainerWithOffset extends React.PureComponent {
  render () {
    const {offset, ratio} = this.props;
    console.log('render draw-container-with-offset', offset, ratio);
    return (
      <g transform={`translate(${offset || 0}, 0)`}>
        <line x1={10} y1={10} x2={20} y2={20} stroke={'red'} />
      </g>
    );
  }
}

DrawContainerWithOffset.propTypes = {
  offset: PropTypes.number,
  ratio: PropTypes.number
};

export default DrawContainer;
