/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import classNames from 'classnames';
import HcsControlGrid from './hcs-control-grid';

class HcsImage extends React.PureComponent {
  componentDidMount () {
    this.prepare();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
  }

  prepare = () => {
  };

  render () {
    const {
      className,
      style
    } = this.props;
    return (
      <div
        className={
          classNames(
            className
          )
        }
        style={Object.assign({
          display: 'flex',
          flexDirection: 'column',
          height: '100vh'
        }, style)}
      >
        <div>
          HCS Image Preview header
        </div>
        <div
          style={{
            display: 'flex',
            height: '100%'
          }}
        >
          <div style={{flex: '1 0 auto'}}>
            HCS Image Preview
          </div>
          <div
            style={{
              width: '30vw',
              height: '100%',
              borderLeft: '1px solid #dfdfdf'
            }}
          >
            HCS Image Preview controls
            <HcsControlGrid
              style={{width: '100%', height: '300px'}}
              rows={32}
              columns={48}
            />
          </div>
        </div>
      </div>
    );
  }
}

HcsImage.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object
};

export default HcsImage;
