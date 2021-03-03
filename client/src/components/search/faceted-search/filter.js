/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Checkbox} from 'antd';
import classNames from 'classnames';
import styles from './filter.css';

class FacetedFilter extends React.Component {
  render () {
    const {
      className,
      name,
      values
    } = this.props;
    if (!values || values.length === 0) {
      return null;
    }
    return (
      <div
        className={
          classNames(
            styles.filter,
            className
          )
        }
      >
        <div className={styles.title}>
          {name}
        </div>
        {
          values.map((v) => (
            <div
              key={v.name}
              className={styles.option}
            >
              <Checkbox>
                {v.name} ({v.count})
              </Checkbox>
            </div>
          ))
        }
      </div>
    );
  }
}

FacetedFilter.propTypes = {
  className: PropTypes.string,
  name: PropTypes.string,
  values: PropTypes.array,
  selection: PropTypes.array
};

export default FacetedFilter;
