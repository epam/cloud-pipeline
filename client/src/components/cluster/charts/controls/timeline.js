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

@inject('plot', 'data')
@observer
class Timeline extends React.PureComponent {
  render () {
    const {children, data} = this.props;
    if (!data || !data.newData) {
      return null;
    }
    const {newData} = data;
    return (
      <Provider x={this}>
        <g transform={'translate(30, 40)'}>
          {children}
        </g>
      </Provider>
    );
  }
}

Timeline.propTypes = {
  data: PropTypes.object,
  end: PropTypes.number,
  maximum: PropTypes.number,
  minimum: PropTypes.number,
  start: PropTypes.number,
  width: PropTypes.number,
  height: PropTypes.number
};

export default Timeline;
