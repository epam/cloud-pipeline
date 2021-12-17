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
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import BillingNavigation from './navigation';
import Quotas from './quotas';
import * as Reports from './reports';
import roleModel from '../../utils/roleModel';

function billing ({children, location, router, preferences, authenticatedUserInfo}) {
  const isBillingPrivilegedUser = authenticatedUserInfo && authenticatedUserInfo.loaded &&
    roleModel.isManager.billing(this);
  if (
    !authenticatedUserInfo ||
    !authenticatedUserInfo.loaded ||
    !preferences ||
    !preferences.loaded
  ) {
    return null;
  }
  if (
    (!isBillingPrivilegedUser && !preferences.billingEnabled) ||
    (isBillingPrivilegedUser && !preferences.billingAdminsEnabled)
  ) {
    return null;
  }
  return (
    <BillingNavigation
      className={
        classNames(
          'cp-panel',
          'cp-panel-no-hover',
          'cp-panel-borderless'
        )
      }
      location={location}
      router={router}
    >
      {children}
    </BillingNavigation>
  );
}

export {
  Quotas as BillingQuotas,
  Reports as BillingReports
};
export default inject('preferences')(roleModel.authenticationInfo(observer(billing)));
