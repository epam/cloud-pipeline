/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import React, {useMemo} from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Table} from 'antd';
import {buildAntdTableProps} from '../../../utils/tabular-array';

function TabularDataTable({data = [], className, style}) {
  const {columns, dataSource} = useMemo(() => buildAntdTableProps(data), [data]);
  return (
    <Table
      className={classNames(className, 'cp-tabular-data-table')}
      style={style}
      columns={columns}
      dataSource={dataSource}
      pagination={false}
      size="small"
      scroll={{x: 'max-content'}}
      bordered
    />
  );
}

TabularDataTable.propTypes = {
  data: PropTypes.array,
  className: PropTypes.string,
  style: PropTypes.object,
};

export default TabularDataTable;
