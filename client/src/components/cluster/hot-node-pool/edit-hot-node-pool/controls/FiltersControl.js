/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Provider} from 'mobx-react';
import classNames from 'classnames';
// Aliased so this component's own name doesn't collide with the sibling default export.
import BaseFiltersControl from '../../filters-control';
import styles from '../../edit-hot-node-pool.css';

function FiltersControl ({
  roles,
  disabled,
  filters,
  error,
  touched,
  onChange
}) {
  return (
    <Provider roles={roles}>
      <div style={{marginTop: 5}}>
        <div>
          <BaseFiltersControl
            disabled={disabled}
            filters={filters}
            onChange={onChange}
          />
        </div>
        {
          error && touched && (
            <div className={classNames(styles.formRow, styles.error, 'cp-error')}>
              {error}
            </div>
          )
        }
      </div>
    </Provider>
  );
}

FiltersControl.propTypes = {
  roles: PropTypes.object,
  disabled: PropTypes.bool,
  filters: PropTypes.object,
  error: PropTypes.string,
  touched: PropTypes.bool,
  onChange: PropTypes.func
};

export default FiltersControl;
