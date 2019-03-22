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
import {Col, Row} from 'antd';

export default function renderSeparator (text, marginInCols, key, style) {
  return (
    <Row key={key} type="flex" style={style || {margin: 0}}>
      <Col span={marginInCols} />
      <Col span={24 - 2 * marginInCols}>
        <table style={{width: '100%'}}>
          <tbody>
          <tr>
            <td style={{width: '50%'}}>
              <div
                style={{
                  margin: '0 5px',
                  verticalAlign: 'middle',
                  height: 1,
                  backgroundColor: '#ccc'
                }}>{'\u00A0'}</div>
            </td>
            <td style={{width: 1, whiteSpace: 'nowrap'}}><b>{text}</b></td>
            <td style={{width: '50%'}}>
              <div
                style={{
                  margin: '0 5px',
                  verticalAlign: 'middle',
                  height: 1,
                  backgroundColor: '#ccc'
                }}>{'\u00A0'}</div>
            </td>
          </tr>
          </tbody>
        </table>
      </Col>
      <Col span={marginInCols} />
    </Row>
  );
}
