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
import {Icon, Popover} from 'antd';

// antd puts a popover (1030) below a dropdown (1050), so one inside a menu
// needs this override - the same number capability.js uses.
const ABOVE_DROPDOWN = 1071;

/**
 * The reason a disabled action is disabled, behind an info icon.
 *
 * Mounted on `document.body` rather than beside the icon, so the menu it sits
 * in cannot clip it or trap it in its own stacking context. The `<span>` gives
 * the hover a target of its own, and swallows the click so it does not reach
 * the menu.
 */
export default function DisabledActionHint ({id, reason}) {
  if (!reason) {
    return null;
  }
  return (
    <Popover
      content={<div style={{maxWidth: 300}}>{reason}</div>}
      trigger="hover"
      placement="left"
      getPopupContainer={() => document.body}
      overlayStyle={{zIndex: ABOVE_DROPDOWN}}
    >
      <span
        id={id}
        style={{marginLeft: 5, display: 'inline-flex'}}
        onClick={(event) => event.stopPropagation()}
      >
        <Icon type="info-circle" />
      </span>
    </Popover>
  );
}

DisabledActionHint.propTypes = {
  id: PropTypes.string,
  reason: PropTypes.node
};
