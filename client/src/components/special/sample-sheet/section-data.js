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
import {isSequenceValue} from './utilities';
import styles from './sample-sheet.css';

function SectionData (
  {
    children,
    className,
    style
  }
) {
  return (
    <table
      className={
        classNames(
          className,
          styles.sampleSheetSectionData
        )
      }
      style={style}
    >
      <tbody>
        {children}
      </tbody>
    </table>
  );
}

SectionData.propTypes = {
  children: PropTypes.node,
  className: PropTypes.string,
  style: PropTypes.object
};

function SectionDataRow (
  {
    className,
    data,
    style
  }
) {
  if (!data) {
    return null;
  }
  const {
    key,
    value
  } = data;
  return (
    <tr
      className={
        classNames(
          styles.sampleSheetSectionRow,
          {
            [styles.sequence]: isSequenceValue(key, value)
          },
          className
        )
      }
      style={style}
    >
      <th>
        <span>{key}</span>
      </th>
      <td>
        <span>{value}</span>
      </td>
    </tr>
  );
}

SectionDataRow.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  data: PropTypes.shape({
    key: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    data: PropTypes.arrayOf(PropTypes.oneOfType([PropTypes.string, PropTypes.number]))
  })
};

SectionData.Row = SectionDataRow;

export default SectionData;
