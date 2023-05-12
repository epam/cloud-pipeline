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

import Pages from './pages';

export default [
  {
    key: Pages.dashboard,
    title: 'Home',
    icon: 'home',
    path: '/dashboard',
    keys: ['dashboard'],
    isDefault: false,
    isLink: true
  },
  {
    key: Pages.library,
    title: 'Library',
    icon: 'fork',
    path: '/library',
    keys: [
      'library',
      'storages',
      'pipelines',
      'folder',
      'storage',
      'configuration',
      'metadata',
      'metadataFolder',
      'vs'
    ],
    isDefault: true,
    isLink: true
  },
  {
    key: Pages.cluster,
    title: 'Cluster state',
    icon: 'bars',
    path: '/cluster',
    keys: ['cluster'],
    isDefault: false,
    isLink: true
  },
  {
    key: Pages.tools,
    title: 'Tools',
    icon: 'tool',
    path: '/tools',
    keys: ['tools', 'tool'],
    isDefault: false,
    isLink: true
  },
  {
    key: Pages.runs,
    title: 'Runs',
    icon: 'play-circle',
    path: '/runs',
    keys: ['runs'],
    isDefault: false,
    isLink: true
  },
  {
    key: Pages.settings,
    title: 'Settings',
    icon: 'setting',
    path: '/settings',
    keys: [
      'settings',
      'cli',
      'events',
      'user',
      'email',
      'preferences',
      'regions',
      'logs',
      'dictionaries',
      'profile'
    ],
    isDefault: false,
    isLink: true
  },
  {
    key: Pages.search,
    title: 'Search',
    icon: 'search',
    isDefault: false,
    isLink: false
  },
  {
    key: Pages.billing,
    title: 'Billing',
    icon: 'area-chart',
    path: '/billing',
    isDefault: false,
    isLink: true
  },
  {
    key: 'divider',
    isDivider: true,
    static: true
  },
  {
    key: 'logout',
    visible: props => !(props && props.impersonation && props.impersonation.isImpersonated),
    title: 'Log out',
    icon: 'poweroff',
    path: '/logout',
    isDefault: false,
    static: true
  },
  {
    key: 'stop-impersonation',
    visible: props => props && props.impersonation && props.impersonation.isImpersonated,
    title: (props) => props && props.impersonation && props.impersonation.isImpersonated
      ? `Stop impersonation as ${props.impersonation.impersonatedUserName}`
      : undefined,
    icon: 'user-delete',
    isDefault: false,
    static: true,
    action: (props) => props && props.impersonation
      ? props.impersonation.stopImpersonation()
      : undefined
  },
  {
    key: 'launch',
    keys: ['launch'],
    static: true,
    hidden: true
  },
  {
    key: Pages.miew,
    keys: ['miew'],
    static: true,
    hidden: true
  },
  {
    key: Pages.run,
    keys: ['run'],
    static: true,
    hidden: true
  }
];
