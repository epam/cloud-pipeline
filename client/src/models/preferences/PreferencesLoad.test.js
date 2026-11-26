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

/* eslint-env jest */

import {stubApi} from '@test';
import preferences, {DEFAULT_NODE_UP_RETRY_COUNT} from './PreferencesLoad';
import whoAmI from '../user/WhoAmI';

describe('allowNodeUpRetryCount', () => {
  it('is false when the preference is absent', async () => {
    stubApi({'/preferences': []});
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.allowNodeUpRetryCount).toBe(false);
  });

  it('is true only when the preference is the string "true"', async () => {
    stubApi({
      '/preferences': [
        {name: 'ui.launch.allow.nodeup.count', value: 'true'}
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.allowNodeUpRetryCount).toBe(true);
  });

  it('is false for any other value', async () => {
    stubApi({
      '/preferences': [
        {name: 'ui.launch.allow.nodeup.count', value: 'false'}
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.allowNodeUpRetryCount).toBe(false);
  });
});

describe('defaultNodeUpRetryCount', () => {
  it('falls back to the default when the preference is absent', async () => {
    stubApi({'/preferences': []});
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.defaultNodeUpRetryCount).toBe(DEFAULT_NODE_UP_RETRY_COUNT);
  });

  it('reads the numeric value of cluster.nodeup.retry.count', async () => {
    stubApi({
      '/preferences': [
        {name: 'cluster.nodeup.retry.count', value: '7'}
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.defaultNodeUpRetryCount).toBe(7);
  });
});

describe('launchCapabilities', () => {
  const capabilitiesWithPermissions = {
    cap1: {
      name: 'Capability 1',
      permissions: [
        {name: 'ROLE_ADMIN', principal: false},
        {name: 'USER1', principal: true}
      ]
    },
    cap2: {
      name: 'Capability 2'
    }
  };

  const whoAmIPayload = (overrides) => Object.assign({
    userName: 'user2',
    roles: [{name: 'ROLE_USER'}],
    groups: ['GROUP1']
  }, overrides);

  const stub = (user) => stubApi({
    '/preferences': [
      {name: 'launch.capabilities', value: JSON.stringify(capabilitiesWithPermissions)}
    ],
    '/whoami': user
  });

  const load = async () => {
    preferences.invalidateCache();
    whoAmI.invalidateCache();
    await preferences.fetch();
    await whoAmI.fetch();
  };

  // Runs before any other case in this block fetches whoAmI: the singleton's
  // `loaded` starts false, and `invalidateCache` alone does not clear it back.
  it('is hidden while the current user is not loaded yet', async () => {
    stubApi({
      '/preferences': [
        {name: 'launch.capabilities', value: JSON.stringify(capabilitiesWithPermissions)}
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    const names = preferences.launchCapabilities.map(c => c.name);
    expect(names).not.toContain('Capability 1');
    expect(names).toContain('Capability 2');
  });

  it('stays visible to everyone when permissions is absent or empty', async () => {
    stub(whoAmIPayload());
    await load();

    const names = preferences.launchCapabilities.map(c => c.name);
    expect(names).toContain('Capability 2');
  });

  it('is hidden from a user not listed, directly or through a role or group', async () => {
    stub(whoAmIPayload());
    await load();

    const names = preferences.launchCapabilities.map(c => c.name);
    expect(names).not.toContain('Capability 1');
  });

  it('is shown to the user named directly', async () => {
    stub(whoAmIPayload({userName: 'user1'}));
    await load();

    const names = preferences.launchCapabilities.map(c => c.name);
    expect(names).toContain('Capability 1');
  });

  it('is shown to a user via a listed role', async () => {
    stub(whoAmIPayload({roles: [{name: 'ROLE_ADMIN'}]}));
    await load();

    const names = preferences.launchCapabilities.map(c => c.name);
    expect(names).toContain('Capability 1');
  });

  it('is shown to a user via a listed group', async () => {
    stub(whoAmIPayload({groups: ['ROLE_ADMIN']}));
    await load();

    const names = preferences.launchCapabilities.map(c => c.name);
    expect(names).toContain('Capability 1');
  });

  it('hides a restricted parent together with its children', async () => {
    const restrictedParent = {
      parent: {
        name: 'Parent',
        permissions: [{name: 'ROLE_ADMIN', principal: false}],
        capabilities: {
          child: {name: 'Child'}
        }
      }
    };
    stubApi({
      '/preferences': [
        {name: 'launch.capabilities', value: JSON.stringify(restrictedParent)}
      ],
      '/whoami': whoAmIPayload()
    });
    preferences.invalidateCache();
    whoAmI.invalidateCache();
    await preferences.fetch();
    await whoAmI.fetch();

    expect(preferences.launchCapabilities.map(c => c.name)).not.toContain('Parent');
  });

  it('filters a restricted child on its own, keeping the visible parent', async () => {
    const restrictedChild = {
      parent: {
        name: 'Parent',
        capabilities: {
          child: {
            name: 'Child',
            permissions: [{name: 'ROLE_ADMIN', principal: false}]
          },
          other: {name: 'Other child'}
        }
      }
    };
    stubApi({
      '/preferences': [
        {name: 'launch.capabilities', value: JSON.stringify(restrictedChild)}
      ],
      '/whoami': whoAmIPayload()
    });
    preferences.invalidateCache();
    whoAmI.invalidateCache();
    await preferences.fetch();
    await whoAmI.fetch();

    const [parent] = preferences.launchCapabilities;
    expect(parent.name).toBe('Parent');
    const childNames = parent.capabilities.map(c => c.name);
    expect(childNames).not.toContain('Child');
    expect(childNames).toContain('Other child');
  });

  it('hides a parent whose children are all restricted', async () => {
    const restrictedChildren = {
      parent: {
        name: 'Parent',
        capabilities: {
          child: {
            name: 'Child',
            permissions: [{name: 'ROLE_ADMIN', principal: false}]
          }
        }
      }
    };
    stubApi({
      '/preferences': [
        {name: 'launch.capabilities', value: JSON.stringify(restrictedChildren)}
      ],
      '/whoami': whoAmIPayload()
    });
    preferences.invalidateCache();
    whoAmI.invalidateCache();
    await preferences.fetch();
    await whoAmI.fetch();

    expect(preferences.launchCapabilities.map(c => c.name)).not.toContain('Parent');
  });
});
