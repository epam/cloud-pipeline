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

import {action, computed, observable} from 'mobx';
import Pages from './pages';
import NavigationItems from './navigation-items';
import MetadataMultiLoad from '../../models/metadata/MetadataMultiLoad';

const USER_CLASS = 'PIPELINE_USER';
const ROLE_CLASS = 'ROLE';

const UI_PAGES_ATTRIBUTE = 'ui-pages';
const DASHBOARD_CONFIGURATION_ATTRIBUTE = 'ui-dashboard';
const HOME_PAGE_ATTRIBUTE = 'ui-home-page';
const LIBRARY_EXPANDED_ATTRIBUTE = 'ui-library-expanded';

const LIBRARY_EXPANDED_STORAGE_KEY = 'library_expanded';

function parseAttributes (data, ignore = {}) {
  const {
    dashboard: ignoreDashboardSettings = false,
    pages: ignorePagesSettings = false,
    homePage: ignoreHomePageSettings = false,
    libraryExpanded: libraryExpandedSetting
  } = ignore;
  const ignoreLibraryExpandedSetting = libraryExpandedSetting !== undefined;
  let pages;
  let dashboard;
  let homePage;
  let libraryExpanded;
  if (!ignorePagesSettings && data.hasOwnProperty(UI_PAGES_ATTRIBUTE)) {
    pages = (data[UI_PAGES_ATTRIBUTE].value || '').split(',').map(o => o.trim());
  }
  if (!ignoreDashboardSettings && data.hasOwnProperty(DASHBOARD_CONFIGURATION_ATTRIBUTE)) {
    try {
      const value = data[DASHBOARD_CONFIGURATION_ATTRIBUTE].value;
      dashboard = typeof value === 'string' ? JSON.parse(value) : value;
    } catch (e) {
      console.warn(`Error parsing dashboard settings: ${e.message}`);
    }
  }
  if (!ignoreHomePageSettings && data.hasOwnProperty(HOME_PAGE_ATTRIBUTE)) {
    homePage = (data[HOME_PAGE_ATTRIBUTE].value || '');
  }
  if (!ignoreLibraryExpandedSetting && data.hasOwnProperty(LIBRARY_EXPANDED_ATTRIBUTE)) {
    libraryExpanded = `${data[LIBRARY_EXPANDED_ATTRIBUTE].value || ''}`.toLowerCase() === 'true';
  }
  if (pages && pages.length === 0) {
    pages = undefined;
  }
  return {pages, dashboard, homePage, libraryExpanded};
}

function fetchUserAttributes (userId) {
  return new Promise((resolve) => {
    const request = new MetadataMultiLoad([{entityId: userId, entityClass: USER_CLASS}]);
    request.fetch()
      .then(() => {
        if (request.loaded) {
          const {data} = (request.value || [])[0] || {};
          resolve(parseAttributes(data));
        } else {
          resolve({});
        }
      })
      .catch(() => resolve({}));
  });
}

function joinAttributes (values, options) {
  let {
    pages = [],
    dashboard,
    homePage,
    libraryExpanded
  } = options || {};
  (values || [])
    .forEach((data) => {
      const {
        pages: parsedPages,
        dashboard: parsedDashboard,
        homePage: parsedHomePage,
        libraryExpanded: parsedLibraryExpanded
      } = parseAttributes(
        data,
        options
      );
      if (parsedPages && parsedPages.length) {
        const current = new Set(pages);
        pages = [];
        parsedPages
          .forEach(page => {
            if (current.size === 0 || current.has(page)) {
              pages.push(page);
            }
          });
      }
      if (parsedDashboard && !dashboard) {
        dashboard = parsedDashboard;
      }
      if (parsedHomePage && !homePage) {
        homePage = parsedHomePage;
      }
      if (parsedLibraryExpanded !== undefined) {
        libraryExpanded = parsedLibraryExpanded;
      }
    });
  if (pages && pages.length === 0) {
    pages = undefined;
  }
  return {
    pages,
    dashboard,
    homePage,
    libraryExpanded
  };
}

function fetchRolesAttributes (roles, options = {}) {
  return new Promise((resolve) => {
    const request = new MetadataMultiLoad(
      roles.map(role => ({entityId: role.id, entityClass: ROLE_CLASS}))
    );
    request.fetch()
      .then(() => {
        if (request.loaded) {
          resolve(
            joinAttributes(
              (request.value || []).map(({data}) => data).filter(Boolean),
              options
            )
          );
        } else {
          resolve(options);
        }
      })
      .catch(() => resolve(options));
  });
}

function fetchGroupsAttributes (preferences, groups, options = {}) {
  return new Promise((resolve) => {
    preferences.fetchIfNeededOrWait()
      .then(() => {
        const groupsPreferences = Object
          .entries(preferences.groupsUIPreferences || {})
          .filter(([group]) => (groups || []).includes(group))
          .map(([, groupPreferences]) => groupPreferences)
          .map(o => Object.entries(o || {})
            .map(([key, value]) => ({[key]: {value}}))
            .reduce((r, c) => ({...r, ...c}))
          );
        resolve(
          joinAttributes(
            groupsPreferences,
            options
          )
        );
      })
      .catch(() => resolve(options));
  });
}

function fetchAttributes (user, preferences) {
  const {
    id,
    roles = [],
    groups = []
  } = user;
  const groupsAndRoles = [
    ...(
      new Set([...groups, ...roles.map(o => o.name)])
    )
  ];
  const fetchRolesAttributesFn = o => fetchRolesAttributes(roles, o);
  const fetchGroupsAttributesFn = o => fetchGroupsAttributes(preferences, groupsAndRoles, o);
  return new Promise((resolve) => {
    fetchUserAttributes(id)
      .then(fetchRolesAttributesFn)
      .then(fetchGroupsAttributesFn)
      .then(resolve);
  });
}

const allPages = Object.values(Pages);

class UINavigation {
  static testPage (router) {
    const path = (router.location.pathname || '')
      .split('/')
      .slice(1)
      .shift();
    const pathRegExp = Number.isNaN(Number(path))
      ? new RegExp(`^${path}$`, 'i')
      : /^library$/i;
    return NavigationItems
      .find(item => (item.keys || [item.key]).find(key => pathRegExp.test(key)));
  }

  @observable userPages;
  @observable dashboard;
  @observable homePage;
  @observable supportTemplate;
  @observable _libraryExpanded;
  @observable _loaded;

  constructor (authenticatedUserInfo, preferences) {
    this.user = authenticatedUserInfo;
    this.preferences = preferences;
    this.fetch();
  }

  @computed
  get navigationItems () {
    if (!this._loaded) {
      return [];
    }
    const pages = new Set((this.userPages || allPages).map(p => p.toLowerCase()));
    return NavigationItems
      .filter(page => page.static || pages.has(page.key));
  }

  @computed
  get loaded () {
    return this._loaded;
  }

  @computed
  get home () {
    let homePageKey = this.homePage || Pages.dashboard;
    if (homePageKey === Pages.search) {
      homePageKey = Pages.dashboard;
    }
    const available = this.navigationItems.find(item => item.key === homePageKey);
    if (available) {
      return available.path;
    }
    return homePageKey;
  }

  @computed
  get libraryExpanded () {
    if (this._libraryExpanded === undefined) {
      try {
        const storageValue = JSON.parse(localStorage.getItem(LIBRARY_EXPANDED_STORAGE_KEY));
        if (typeof storageValue === 'boolean') {
          return storageValue;
        }
      } catch (_) {}
      return true;
    }
    return this._libraryExpanded;
  }

  set libraryExpanded (value) {
    this._libraryExpanded = value;
    if (value !== undefined) {
      localStorage.setItem(LIBRARY_EXPANDED_STORAGE_KEY, JSON.stringify(value));
    }
  }

  parseSupportTemplate () {
    if (this.preferences && this.preferences.loaded && this.user && this.user.loaded) {
      const template = this.preferences.getPreferenceValue('ui.support.template');
      if (template) {
        try {
          const parsed = JSON.parse(template);
          if (typeof parsed === 'object') {
            const {
              roles = [],
              groups: adGroups = []
            } = this.user.value;
            const keys = Object.keys(parsed);
            const groups = roles
              .map(r => r.name)
              .concat(...adGroups);
            const groupKey = keys.find(key => groups.includes(key));
            if (groupKey) {
              this.supportTemplate = parsed[groupKey];
            } else {
              this.supportTemplate = parsed._default;
            }
          } else if (typeof parsed === 'string') {
            this.supportTemplate = parsed;
          } else {
            this.supportTemplate = undefined;
          }
        } catch (_) {
          if (typeof template === 'string') {
            this.supportTemplate = template;
          }
        }
      }
    }
  }

  @action
  fetch () {
    if (this.loaded) {
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      this.user.fetchIfNeededOrWait()
        .then(() => {
          if (this.user.loaded) {
            return fetchAttributes(this.user.value, this.preferences);
          }
          return Promise.resolve();
        })
        .then(({pages, dashboard, homePage, libraryExpanded} = {}) => {
          const {
            admin = false
          } = this.user.loaded ? (this.user.value || {}) : {};
          this.userPages = !admin && pages ? pages.slice() : undefined;
          this.dashboard = dashboard;
          this.homePage = homePage;
          this.libraryExpanded = libraryExpanded;
          this.parseSupportTemplate();
          this._loaded = true;
        })
        .then(resolve);
    });
  }

  fetchDashboard = () => {
    return new Promise((resolve) => {
      this.fetch()
        .then(() => resolve(this.dashboard));
    });
  }

  pageIsUnavailable (pageKey) {
    return !this.navigationItems.find(item => item.key === pageKey);
  }

  searchEnabled () {
    return !this.pageIsUnavailable(Pages.search);
  }

  getActivePage (router) {
    const page = UINavigation.testPage(router);
    const active = page
      ? this.navigationItems.find(item => item.key === page.key)
      : undefined;
    return active ? active.key : undefined;
  }

  redirectIfPageIsUnavailable (router) {
    const page = UINavigation.testPage(router);
    if (
      page &&
      !this.getActivePage(router) &&
      router.location.pathname !== this.home &&
      this.loaded
    ) {
      router.push(this.home);
    }
  }
}

export {Pages};
export default UINavigation;
