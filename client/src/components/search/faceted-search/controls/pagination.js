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
import {Pagination} from 'antd';
import classNames from 'classnames';
import styles from './controls.css';
import '../../../../staticStyles/faceted-filters-pagination.css';

function SearchResults (
  {
    className,
    disabled,
    style,
    total,
    page,
    pageSize,
    onChangePage
  }
) {
  const onChangePagination = (p, ps) => {
    if (onChangePage) {
      onChangePage(p, ps);
    }
  };
  return (
    <div
      className={classNames(styles.pagination, className)}
      style={style}
    >
      {
        !!total && (
          <Pagination
            className="faceted-filters-pagination"
            disabled={disabled}
            current={page}
            total={total}
            pageSize={pageSize}
            onChange={onChangePagination}
            size="small"
          />
        )
      }
      {
        !total && '\u00A0'
      }
    </div>
  );
}

SearchResults.propTypes = {
  className: PropTypes.string,
  disabled: PropTypes.bool,
  onChangePage: PropTypes.func,
  page: PropTypes.number,
  pageSize: PropTypes.number,
  style: PropTypes.object,
  total: PropTypes.number
};

SearchResults.defaultProps = {
  page: 1,
  pageSize: 20,
  total: 0
};

export default SearchResults;
