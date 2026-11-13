/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import PropTypes from 'prop-types';
import {Icon, Tooltip} from 'antd';

// A tooltip is 1060 and a dropdown 1050, so this only just clears the menu.
// Stated rather than inherited, since the margin is one step.
const ABOVE_DROPDOWN = 1071;

/**
 * A selection action's hint, behind an info icon.
 *
 * Mounted on `document.body` rather than beside the icon, so the menu it sits
 * in can neither clip it nor trap it in its own stacking context, and raised
 * above the dropdown. The click is swallowed so it does not reach the menu.
 */
export default function ActionHint ({id, hint}) {
  if (!hint) {
    return null;
  }
  return (
    <Tooltip
      title={hint}
      trigger="hover"
      placement="left"
      getPopupContainer={() => document.body}
      overlayStyle={{zIndex: ABOVE_DROPDOWN}}
    >
      <span
        id={id}
        style={{marginLeft: 5, display: 'inline-flex', pointerEvents: 'auto'}}
        onClick={(event) => event.stopPropagation()}
      >
        <Icon type="info-circle" />
      </span>
    </Tooltip>
  );
}

ActionHint.propTypes = {
  id: PropTypes.string,
  hint: PropTypes.node
};
