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
import types, {QuotaTypeIcon} from './quota-types';
import {splitRoleName} from '../../../settings/user-management/utilities';
import UserName from '../../../special/UserName';

function QuotaTarget (
  {
    addonAfter,
    className,
    quota,
    roles,
    style
  }
) {
  if (!quota) {
    return null;
  }
  const {
    type,
    subject
  } = quota;
  if (type === types.overall) {
    return null;
  }
  let name = subject;
  switch (type) {
    case types.group:
      if (roles && roles.loaded) {
        const role = (roles.value || []).find(o => o.name === subject);
        name = splitRoleName(role) || subject;
      }
      break;
    case types.user:
      name = (<UserName userName={subject} />);
      break;
  }
  return (
    <div
      className={className}
      style={Object.assign({display: 'inline-flex', alignItems: 'center'}, style)}
    >
      <QuotaTypeIcon type={type} />
      <span>{name}</span>
      {addonAfter}
    </div>
  );
}

QuotaTarget.propTypes = {
  className: PropTypes.string,
  quota: PropTypes.shape({
    subject: PropTypes.string,
    type: PropTypes.string
  }),
  style: PropTypes.object
};

export default QuotaTarget;
