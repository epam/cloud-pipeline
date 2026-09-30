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

import {observable} from 'mobx';
import getRunInvestigationUrl from './run-investigation-url';

const URL = 'https://host/run-analyzer-gui/';
const EXPECTED = 'https://host/run-analyzer-gui/?run_id=123';

const preferencesWith = (aiPreferences, loaded = true) => observable({
  loaded,
  miscAIPreferences: aiPreferences
});

const userWith = ({roles = [], groups = [], admin = false} = {}, loaded = true) => observable({
  loaded,
  value: {
    admin,
    roles: roles.map((name) => ({name})),
    groups
  }
});

const failedRun = {id: 123, status: 'FAILURE'};

describe('getRunInvestigationUrl, without roles', () => {
  it('builds the url for a failed run, for any user', () => {
    const preferences = preferencesWith({investigation: {url: URL}});
    expect(getRunInvestigationUrl(preferences, failedRun, userWith())).toBe(EXPECTED);
    expect(getRunInvestigationUrl(preferences, failedRun, undefined)).toBe(EXPECTED);
  });

  it('treats null or empty roles as not set', () => {
    [[], null].forEach((roles) => {
      const preferences = preferencesWith({investigation: {url: URL, roles}});
      expect(getRunInvestigationUrl(preferences, failedRun, userWith())).toBe(EXPECTED);
    });
  });

  it('adds the slash when the url has none', () => {
    const preferences = preferencesWith({
      investigation: {url: 'https://host/run-analyzer-gui'}
    });
    expect(getRunInvestigationUrl(preferences, {id: '7', status: 'FAILURE'}))
      .toBe('https://host/run-analyzer-gui/?run_id=7');
  });

  it('trims the url and collapses repeated trailing slashes', () => {
    const preferences = preferencesWith({
      investigation: {url: '  https://host/run-analyzer-gui//  '}
    });
    expect(getRunInvestigationUrl(preferences, failedRun)).toBe(EXPECTED);
  });
});

describe('getRunInvestigationUrl, with roles', () => {
  const preferences = preferencesWith({
    investigation: {url: URL, roles: ['ROLE_ADMIN', 'ROLE_RUN_ANALYZER_USER']}
  });

  it('builds the url for a user with one of the roles', () => {
    expect(getRunInvestigationUrl(preferences, failedRun, userWith({
      roles: ['ROLE_USER', 'ROLE_RUN_ANALYZER_USER']
    }))).toBe(EXPECTED);
    expect(getRunInvestigationUrl(preferences, failedRun, userWith({
      roles: ['ROLE_ADMIN'],
      admin: true
    }))).toBe(EXPECTED);
  });

  it('returns nothing for a user without the roles, admin flag included', () => {
    expect(getRunInvestigationUrl(preferences, failedRun, userWith({
      roles: ['ROLE_USER']
    }))).toBeUndefined();
    expect(getRunInvestigationUrl(preferences, failedRun, userWith({
      roles: ['ROLE_USER'],
      admin: true
    }))).toBeUndefined();
  });

  it('returns nothing until the user is loaded', () => {
    expect(getRunInvestigationUrl(preferences, failedRun, userWith({
      roles: ['ROLE_RUN_ANALYZER_USER']
    }, false))).toBeUndefined();
    expect(getRunInvestigationUrl(preferences, failedRun, undefined)).toBeUndefined();
  });

  it('compares role names exactly', () => {
    expect(getRunInvestigationUrl(preferences, failedRun, userWith({
      roles: ['role_run_analyzer_user']
    }))).toBeUndefined();
    expect(getRunInvestigationUrl(preferences, failedRun, userWith({
      roles: ['RUN_ANALYZER_USER']
    }))).toBeUndefined();
    const withoutPrefix = preferencesWith({
      investigation: {url: URL, roles: ['USER']}
    });
    expect(getRunInvestigationUrl(withoutPrefix, failedRun, userWith({
      roles: ['ROLE_USER']
    }))).toBeUndefined();
  });

  it('matches an external group by its exact name', () => {
    const withGroup = preferencesWith({
      investigation: {url: URL, roles: ['Analyzers']}
    });
    expect(getRunInvestigationUrl(withGroup, failedRun, userWith({
      groups: ['Analyzers']
    }))).toBe(EXPECTED);
    expect(getRunInvestigationUrl(withGroup, failedRun, userWith({
      groups: ['ANALYZERS']
    }))).toBeUndefined();
    expect(getRunInvestigationUrl(preferences, failedRun, userWith({
      groups: ['ADMIN', 'RUN_ANALYZER_USER']
    }))).toBeUndefined();
  });

  it('checks the roles when the user has no external groups', () => {
    const user = observable({
      loaded: true,
      value: {roles: [{name: 'ROLE_RUN_ANALYZER_USER'}], groups: null}
    });
    expect(getRunInvestigationUrl(preferences, failedRun, user)).toBe(EXPECTED);
  });

  it('hides the link for everyone when the roles are malformed', () => {
    const admin = userWith({roles: ['ROLE_ADMIN', 'ROLE_USER'], admin: true});
    [[''], [42], [['ROLE_ADMIN']], {ROLE_ADMIN: true}, 42, ''].forEach((roles) => {
      const malformed = preferencesWith({investigation: {url: URL, roles}});
      expect(getRunInvestigationUrl(malformed, failedRun, admin)).toBeUndefined();
    });
  });

  it('accepts a single role as a string', () => {
    const singleRole = preferencesWith({
      investigation: {url: URL, roles: 'ROLE_RUN_ANALYZER_USER'}
    });
    expect(getRunInvestigationUrl(singleRole, failedRun, userWith({
      roles: ['ROLE_RUN_ANALYZER_USER']
    }))).toBe(EXPECTED);
    expect(getRunInvestigationUrl(singleRole, failedRun, userWith({
      roles: ['ROLE_USER']
    }))).toBeUndefined();
  });
});

describe('getRunInvestigationUrl, not shown', () => {
  it('returns nothing for a run that has not failed', () => {
    const preferences = preferencesWith({investigation: {url: URL}});
    ['RUNNING', 'STOPPED', 'SUCCESS', 'PAUSED', undefined].forEach((status) => {
      expect(getRunInvestigationUrl(preferences, {id: 123, status})).toBeUndefined();
    });
  });

  it('returns nothing when the investigation url is not configured', () => {
    [
      undefined,
      {},
      {api: 'https://host/ai'},
      {investigation: {}},
      {investigation: null},
      {investigation: URL},
      {investigation: {roles: ['ROLE_ADMIN']}},
      {investigation: {url: ''}},
      {investigation: {url: '   '}},
      {investigation: {url: {href: URL}}},
      {investigation_url: URL}
    ].forEach((aiPreferences) => {
      expect(getRunInvestigationUrl(
        preferencesWith(aiPreferences),
        failedRun,
        userWith({roles: ['ROLE_ADMIN'], admin: true})
      )).toBeUndefined();
    });
  });

  it('returns nothing until the preferences are loaded', () => {
    expect(getRunInvestigationUrl(
      preferencesWith({investigation: {url: URL}}, false),
      failedRun
    )).toBeUndefined();
    expect(getRunInvestigationUrl(undefined, failedRun)).toBeUndefined();
  });

  it('returns nothing without a run or its id', () => {
    const preferences = preferencesWith({investigation: {url: URL}});
    expect(getRunInvestigationUrl(preferences, undefined)).toBeUndefined();
    expect(getRunInvestigationUrl(preferences, {status: 'FAILURE'})).toBeUndefined();
    expect(getRunInvestigationUrl(preferences, {id: '', status: 'FAILURE'})).toBeUndefined();
  });
});
