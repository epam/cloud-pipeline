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
import {observer, Provider} from 'mobx-react';
import Timeline from './timeline';
import DrawContainer from './draw-container';

@observer
class Plot extends React.PureComponent {
  canvas;

  canvasRef = (element) => {
    this.canvas = element;
  };

  render () {
    const {
      data,
      from,
      height,
      instanceFrom,
      instanceTo,
      to,
      width
    } = this.props;
    return (
      <Provider plot={this} data={data}>
        <svg
          ref={this.canvasRef}
          width={width}
          height={height}
        >
          <Timeline
            from={from || instanceFrom}
            to={to || instanceTo}
            interactiveArea={this.canvas}
          >
            <DrawContainer />
          </Timeline>
        </svg>
      </Provider>
    );
  }
}

Plot.propTypes = {
  data: PropTypes.object,
  width: PropTypes.number.isRequired,
  height: PropTypes.number.isRequired,
  instanceFrom: PropTypes.number.isRequired,
  instanceTo: PropTypes.number.isRequired,
  from: PropTypes.number,
  to: PropTypes.number,
  chartArea: PropTypes.shape({
    left: PropTypes.number,
    right: PropTypes.number,
    top: PropTypes.number,
    bottom: PropTypes.number
  })
};

Plot.defaultProps = {
  chartArea: {
    left: 75,
    top: 0,
    right: 75,
    bottom: 30
  }
};

export default Plot;
