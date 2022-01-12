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
import {Icon} from 'antd';

const types = {
  overall: 'OVERALL',
  billingCenter: 'BILLING_CENTER',
  user: 'USER',
  group: 'GROUP'
};

function QuotaTypeIcon ({type}) {
  switch (type) {
    case types.billingCenter: return (<Icon type="solution" />);
    case types.user: return (<Icon type="user" />);
    case types.group: return (<Icon type="team" />);
  }
  return null;
}

QuotaTypeIcon.propTypes = {
  type: PropTypes.string
};

const ordered = [
  types.overall,
  types.billingCenter,
  types.user,
  types.group
];

const names = {
  [types.overall]: 'Overall',
  [types.billingCenter]: 'Billing centers',
  [types.user]: 'Users',
  [types.group]: 'Groups'
};

const singleNames = {
  [types.overall]: 'Overall',
  [types.billingCenter]: 'Billing center',
  [types.user]: 'User',
  [types.group]: 'Group'
};

export {
  ordered as orderedQuotaTypes,
  names as quotaNames,
  singleNames as quotaSubjectName,
  QuotaTypeIcon
};
export default types;
