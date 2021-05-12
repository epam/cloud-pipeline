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
import {
  Breadcrumb
} from 'antd';
import classNames from 'classnames';

class VSTableNavigation extends React.Component {
  get navigationParts () {
    const {path} = this.props;
    if (!path) {
      return [];
    }
    const rootElement = {
      path: '',
      folderName: 'root'
    };
    const parts = path
      .split('/')
      .filter(Boolean)
      .map((folder, index, array) => ({
        path: array.slice(0, index + 1).join('/'),
        folderName: folder
      }));
    return [rootElement, ...parts];
  };

  onBreadcrumbClick = (route) => {
    const {onNavigate} = this.props;
    if (route && typeof route.path === 'string') {
      if (route.path && !route.path.endsWith('/')) {
        route.path = `${route.path}/`;
      }
      onNavigate && onNavigate(route.path);
    }
  };

  breadcrumbRenderer = (route, params, routes) => {
    const lastBreadcrumb = routes.indexOf(route) === routes.length - 1;
    return (
      <span
        className={classNames(
          'cp-versioned-storage-breadcrumb',
          {
            last: lastBreadcrumb
          }
        )}
        onClick={() => !lastBreadcrumb && this.onBreadcrumbClick(route)}
      >
        {route.folderName}
      </span>
    );
  };

  render () {
    return (
      <Breadcrumb
        itemRender={this.breadcrumbRenderer}
        routes={this.navigationParts}
      />
    );
  };
}

VSTableNavigation.PropTypes = {
  path: PropTypes.string,
  onNavigate: PropTypes.func
};

export default VSTableNavigation;
