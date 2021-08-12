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
const SEARCH_PAGE_SECTIONS = 'ui-search-document-types';

function parseAttributes (data, ignore = {}) {
  const {
    dashboard: ignoreDashboardSettings = false,
    pages: ignorePagesSettings = false,
    homePage: ignoreHomePageSettings = false,
    searchDocumentTypes: ignoreSearchDocumentTypes = false
  } = ignore;
  let pages;
  let dashboard;
  let homePage;
  let searchDocumentTypes;
  if (!ignorePagesSettings && data.hasOwnProperty(UI_PAGES_ATTRIBUTE)) {
    pages = (data[UI_PAGES_ATTRIBUTE].value || '').split(',').map(o => o.trim());
  }
  if (!ignoreDashboardSettings && data.hasOwnProperty(DASHBOARD_CONFIGURATION_ATTRIBUTE)) {
    try {
      dashboard = JSON.parse(data[DASHBOARD_CONFIGURATION_ATTRIBUTE].value);
    } catch (e) {
      console.warn(`Error parsing dashboard settings: ${e.message}`);
    }
  }
  if (!ignoreHomePageSettings && data.hasOwnProperty(HOME_PAGE_ATTRIBUTE)) {
    homePage = (data[HOME_PAGE_ATTRIBUTE].value || '');
  }
  if (!ignoreSearchDocumentTypes && data.hasOwnProperty(SEARCH_PAGE_SECTIONS)) {
    searchDocumentTypes = (data[SEARCH_PAGE_SECTIONS].value || '').split(',').map(o => o.trim());
  }
  if (pages && pages.length === 0) {
    pages = undefined;
  }
  return {pages, dashboard, homePage, searchDocumentTypes};
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

function fetchRolesAttributes (roles, options = {}) {
  if (!Object.values(options || {}).some(o => !o)) {
    return Promise.resolve(options);
  }
  return new Promise((resolve) => {
    const request = new MetadataMultiLoad(
      roles.map(role => ({entityId: role.id, entityClass: ROLE_CLASS}))
    );
    request.fetch()
      .then(() => {
        if (request.loaded) {
          let {
            pages = [],
            dashboard,
            homePage,
            searchDocumentTypes
          } = options || {};
          (request.value || [])
            .forEach(({data}) => {
              const {
                pages: rolesPages,
                dashboard: rolesDashboard,
                homePage: rolesHomePage,
                searchDocumentTypes: rolesSearchDocumentTypes
              } = parseAttributes(
                data,
                options
              );
              if (rolesPages && rolesPages.length) {
                const current = new Set(pages);
                pages = [];
                rolesPages
                  .forEach(page => {
                    if (current.has(page)) {
                      pages.push(page);
                    }
                  });
              }
              if (rolesDashboard && !dashboard) {
                dashboard = rolesDashboard;
              }
              if (rolesHomePage && !homePage) {
                homePage = rolesHomePage;
              }
              if (rolesSearchDocumentTypes && !searchDocumentTypes) {
                searchDocumentTypes = rolesSearchDocumentTypes;
              }
            });
          if (pages && pages.length === 0) {
            pages = undefined;
          }
          resolve({
            pages,
            dashboard,
            homePage,
            searchDocumentTypes
          });
        } else {
          resolve(options);
        }
      })
      .catch(() => resolve(options));
  });
}

function fetchAttributes (user) {
  const {
    id,
    roles = []
  } = user;
  const fetchRolesAttributesFn = roles => o => fetchRolesAttributes(roles, o);
  return new Promise((resolve) => {
    fetchUserAttributes(id)
      .then(fetchRolesAttributesFn(roles))
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
  @observable searchDocumentTypes;
  @observable _loaded;

  constructor (authenticatedUserInfo) {
    this.user = authenticatedUserInfo;
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
    const homePageKey = this.homePage || Pages.dashboard;
    const available = this.navigationItems.find(item => item.key === homePageKey);
    if (available) {
      return available.path;
    }
    const any = this.navigationItems.find(item => item.key === Pages.dashboard) ||
      this.navigationItems[0];
    if (any) {
      return any.path;
    }
    return '/dashboard';
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
            return fetchAttributes(this.user.value);
          }
          return Promise.resolve();
        })
        .then(({pages, dashboard, homePage, searchDocumentTypes} = {}) => {
          const {
            admin = false
          } = this.user.loaded ? (this.user.value || {}) : {};
          this.userPages = !admin && pages ? pages.slice() : undefined;
          this.dashboard = dashboard;
          this.homePage = homePage;
          this.searchDocumentTypes = searchDocumentTypes;
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

  fetchSearchDocumentTypes = () => {
    return new Promise((resolve) => {
      this.fetch()
        .then(() => resolve(this.searchDocumentTypes));
    });
  };

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
