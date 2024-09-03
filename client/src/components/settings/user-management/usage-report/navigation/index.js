/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observable} from 'mobx';
import {inject, observer, Provider} from 'mobx-react';
import classNames from 'classnames';
import FilterStore, {runnersEqual} from './filter-store';
import RunnerTypes from './runner-types';
import styles from './navigation.css';
import roleModel from '../../../../../utils/roleModel';

@roleModel.authenticationInfo
@observer
class UsageNavigation extends React.Component {
  static attach = (...opts) => inject('filters')(...opts);
  static generateNavigationFn = (navigation, ...configurationRest) => (
    (navigation)
      ? (...opts) => navigation(...configurationRest, ...opts)
      : undefined
  );

  @observable filterStore = new FilterStore();
  componentWillReceiveProps (nextProps, nextContext) {
    this.filterStore.rebuild(this.props);
  }

  componentDidMount () {
    this.filterStore.rebuild(this.props);
  }

  componentDidUpdate (prevProps) {
    const {location, router} = this.props;
    if (location && router) {
      const {pathname, search} = location;
      const {pathname: prevPathname, search: prevSearch} = prevProps.location;
      if (prevSearch !== search || prevPathname !== pathname) {
        this.filterStore.rebuild(this.props);
      }
    }
  }

  render () {
    if (!this.filterStore) {
      return null;
    }
    const {
      children,
      className
    } = this.props;
    return (
      <Provider filters={this.filterStore}>
        <div
          className={
            classNames(
              styles.container,
              className
            )
          }
        >
          <div className={styles.content}>
            {children}
          </div>
        </div>
      </Provider>
    );
  }
}

UsageNavigation.propTypes = {
  className: PropTypes.string,
  location: PropTypes.object,
  router: PropTypes.object,
  children: PropTypes.node
};

const RUNNER_SEPARATOR = FilterStore.RUNNER_SEPARATOR;
const REGION_SEPARATOR = FilterStore.REGION_SEPARATOR;

export {RUNNER_SEPARATOR, REGION_SEPARATOR, RunnerTypes, runnersEqual};

export default UsageNavigation;
