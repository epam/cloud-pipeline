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
import {computed} from 'mobx';
import {inject, observer, Provider} from 'mobx-react';
import Timeline from './timeline';

@observer
class Plot extends React.PureComponent {
  state = {
    start: undefined,
    end: undefined
  };
  render () {
    const {data, width, height} = this.props;
    const {start, end} = this.state;
    return (
      <Provider plot={this} data={data}>
        <svg width={width} height={height}>
          <Timeline
            start={start}
            end={end}
            width={width}
            height={height}
          >
            <g>
              <rect x={10} y={10} width={10} height={10} fill={'red'} />
            </g>
          </Timeline>
        </svg>
      </Provider>
    );
  }
}

Plot.propTypes = {
  data: PropTypes.object,
  width: PropTypes.number,
  height: PropTypes.number
};

export default Plot;
