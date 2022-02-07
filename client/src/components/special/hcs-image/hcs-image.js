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
  state = {
    selectedCell: null,
    dataCells: [
      {row: 3, column: 6},
      {row: 3, column: 7},
      {row: 3, column: 8},
      {row: 4, column: 6},
      {row: 4, column: 7},
      {row: 4, column: 8},
      {row: 5, column: 6},
      {row: 5, column: 7},
      {row: 5, column: 8}
    ]
  }

  componentDidMount () {
    this.prepare();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
  }

  prepare = () => {
  };

  onSelectCell = (cell) => {
    const {dataCells} = this.state;
    const dataCellSelected = dataCells
      .find(c => c.row === cell.row && c.column === cell.column);
    if (dataCellSelected) {
      this.setState({selectedCell: cell});
    }
  };

  render () {
    const {
      className,
      style
    } = this.props;
    const {selectedCell, dataCells} = this.state;
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
              onClick={this.onSelectCell}
              selectedCell={selectedCell}
              dataCells={dataCells}
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
