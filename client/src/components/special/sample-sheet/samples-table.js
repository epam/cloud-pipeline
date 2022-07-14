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
import classNames from 'classnames';
import styles from './sample-sheet.css';

function asArray (o) {
  if (o && !Array.isArray(o)) {
    return [o];
  }
  return (o || [])
    .map(oo => asArray(oo))
    .reduce((r, c) => ([...r, ...c]), []);
}

function SamplesTableRow (
  {
    children,
    header
  }
) {
  const Cell = ({children: child}) => header
    ? <th>{child}</th>
    : <td>{child}</td>;
  return (
    <tr
      className="cp-even-odd-element"
    >
      {
        children.map((child, index) => (
          <Cell key={`samples-table-header-${index}`}>
            <span>
              {child}
            </span>
          </Cell>
        ))
      }
    </tr>
  );
}

SamplesTableRow.propTypes = {
  children: PropTypes.oneOfType([PropTypes.node, PropTypes.arrayOf(PropTypes.node)]),
  header: PropTypes.bool
};

function SamplesTableHeader (
  {
    children
  }
) {
  return (
    <SamplesTableRow header>
      {children}
    </SamplesTableRow>
  );
}

SamplesTableHeader.propTypes = {
  children: PropTypes.oneOfType([PropTypes.node, PropTypes.arrayOf(PropTypes.node)])
};

function SamplesTableData (
  {
    children
  }
) {
  return (
    <SamplesTableRow>
      {children}
    </SamplesTableRow>
  );
}

SamplesTableData.propTypes = {
  children: PropTypes.oneOfType([PropTypes.node, PropTypes.arrayOf(PropTypes.node)])
};

function SamplesTable (
  {
    className,
    children: raw,
    style
  }
) {
  const children = asArray(raw);
  const headers = children.filter(o => o.type === SamplesTableHeader);
  const body = children.filter(o => o.type !== SamplesTableHeader);
  return (
    <div
      className={
        classNames(
          className,
          styles.samplesTableContainer
        )
      }
      style={style}
    >
      <table
        className={
          classNames(
            styles.samplesTable,
            'cp-sample-sheet-table'
          )
        }
      >
        {
          headers.length > 0 && (
            <thead>{headers}</thead>
          )
        }
        <tbody>{body}</tbody>
      </table>
    </div>
  );
}

SamplesTable.Header = SamplesTableHeader;
SamplesTable.Sample = SamplesTableData;

SamplesTable.propTypes = {
  className: PropTypes.string,
  children: PropTypes.oneOfType([PropTypes.node, PropTypes.arrayOf(PropTypes.node)]),
  style: PropTypes.object,
  headers: PropTypes.arrayOf(PropTypes.string)
};

export default SamplesTable;
