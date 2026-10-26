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

// A plain `<a>` to the /downloads page, and deliberately NOT react-router's
// `<Link>`: both of this feature's toasts carry one, and an antd `message`
// renders into a portal detached from the application's element tree, where
// `<Link>` finds no router in context and throws.
//
// A bare hash href is enough because the application runs on `hashHistory`
// (`Root.js`: `syncHistoryWithStore(hashHistory, routing)`), so this is
// in-application navigation - the router picks the hash change up, nothing
// reloads, and no absolute path is written anywhere (client/AGENTS.md:
// `PUBLIC_URL` makes an absolute path wrong behind the API).
export const DOWNLOADS_PAGE_HREF = '#/downloads';

export default function DownloadsPageLink ({children}) {
  return (
    <a href={DOWNLOADS_PAGE_HREF}>
      {children}
    </a>
  );
}

DownloadsPageLink.propTypes = {
  children: PropTypes.node
};

DownloadsPageLink.defaultProps = {
  children: 'Downloads page'
};
