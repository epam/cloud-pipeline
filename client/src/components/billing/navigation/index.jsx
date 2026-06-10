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
import {observable, makeObservable} from 'mobx';
import {inject, observer, Provider} from 'mobx-react';
import classNames from 'classnames';
import {Menu} from 'antd';
import FilterStore from './filter-store';
import RunnerTypes from './runner-types';
import ReportsRouting from './reports-routing';
import styles from './billing-navigation.module.css';
import roleModel from '../../../utils/roleModel';

@inject('routing')
@roleModel.authenticationInfo
@observer
class BillingNavigation extends React.Component {
  static attach = (...opts) => inject('filters')(...opts);
  static generateNavigationFn = (navigation, ...configurationRest) =>
    navigation ? (...opts) => navigation(...configurationRest, ...opts) : undefined;

  static resourcesNavigation = (filters, {group: resourceGroup, key}) => {
    if (filters && resourceGroup) {
      if (/^storage$/i.test(resourceGroup)) {
        if (key && /^file$/i.test(key)) {
          filters.reportNavigation(ReportsRouting.storages.file.name);
        } else if (key && /^object$/i.test(key)) {
          filters.reportNavigation(ReportsRouting.storages.object.name);
        } else {
          filters.reportNavigation(ReportsRouting.storages.name);
        }
      } else if (/^compute instances$/i.test(resourceGroup)) {
        if (key && /^cpu$/i.test(key)) {
          filters.reportNavigation(ReportsRouting.instances.cpu.name);
        } else if (key && /^gpu$/i.test(key)) {
          filters.reportNavigation(ReportsRouting.instances.gpu.name);
        } else {
          filters.reportNavigation(ReportsRouting.instances.name);
        }
      }
    }
  };

  static usersNavigation = (filters, {key}) => {
    if (filters) {
      filters.reportNavigation(ReportsRouting.general.name, {id: key, type: RunnerTypes.user});
    }
  };

  static billingCentersNavigation = (filters, {key}) => {
    if (filters) {
      filters.reportNavigation(ReportsRouting.general.name, {
        id: key,
        type: RunnerTypes.billingGroup,
      });
    }
  };

  filterStore = new FilterStore();

  constructor(props) {
    super(props);
    makeObservable(this, {
      filterStore: observable,
    });
  }

  syncFilterStore = (props = this.props) => {
    const {location, router, routing} = props;
    this.filterStore.rebuild({
      location: location ?? routing?.location ?? {},
      router: router ?? routing,
    });
  };

  navigateToReport = (key) => {
    this.syncFilterStore();
    this.filterStore.reportNavigation(key);
  };

  renderSubMenuLabel = (key, label) => (
    <span
      className={styles.subMenuTitle}
      role="button"
      tabIndex={0}
      onClick={(e) => {
        e.stopPropagation();
        this.navigateToReport(key);
      }}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          e.stopPropagation();
          this.navigateToReport(key);
        }
      }}
    >
      {label}
    </span>
  );

  UNSAFE_componentWillReceiveProps(nextProps) {
    this.syncFilterStore(nextProps);
  }

  componentDidMount() {
    this.syncFilterStore();
  }

  componentDidUpdate(prevProps) {
    const location = this.props.location ?? this.props.routing?.location;
    const prevLocation = prevProps.location ?? prevProps.routing?.location;
    if (location && prevLocation) {
      const {pathname, search} = location;
      const {pathname: prevPathname, search: prevSearch} = prevLocation;
      if (prevSearch !== search || prevPathname !== pathname) {
        this.syncFilterStore();
      }
    }
  }

  renderMenu = () => {
    const location = this.props.location ?? this.props.routing?.location;
    const report = location ? ReportsRouting.parse(location) : undefined;
    const onSelect = ({key}) => {
      this.navigateToReport(key);
    };
    const isSubMenuSelected = (key) =>
      report === key || (typeof report === 'string' && report.startsWith(`${key}.`));
    const storagesMenu = {
      key: 'storages',
      label: this.renderSubMenuLabel('storages', 'Storages'),
      className: classNames('cp-billing-sub-menu', {
        'cp-billing-sub-menu-selected': isSubMenuSelected('storages'),
      }),
      children: [
        {key: 'storages.file', label: 'File storages'},
        {key: 'storages.object', label: 'Object storages'},
      ],
    };
    const instancesMenu = {
      key: 'instances',
      label: this.renderSubMenuLabel('instances', 'Compute instances'),
      className: classNames('cp-billing-sub-menu', {
        'cp-billing-sub-menu-selected': isSubMenuSelected('instances'),
      }),
      children: [
        {key: 'instances.cpu', label: 'CPU'},
        {key: 'instances.gpu', label: 'GPU'},
      ],
    };
    const quotasMenu = {
      key: 'quotas',
      label: this.renderSubMenuLabel('quotas', 'Quotas'),
      className: classNames('cp-billing-sub-menu', {
        'cp-billing-sub-menu-selected': isSubMenuSelected('quotas'),
      }),
      children: [
        {key: 'quotas.storage', label: 'Storages'},
        {key: 'quotas.compute', label: 'Compute instances'},
      ],
    };
    const isBillingManager = roleModel.isManager.billing(this);
    const menuItems = [
      {key: 'general', label: 'General'},
      storagesMenu,
      instancesMenu,
      ...(isBillingManager ? [{type: 'divider'}, quotasMenu] : []),
    ];
    return (
      <Menu
        className="cp-billing-menu"
        mode="inline"
        inlineIndent={12}
        onClick={onSelect}
        defaultOpenKeys={['storages', 'instances', 'quotas']}
        selectedKeys={report ? [report] : []}
        items={menuItems}
      />
    );
  };

  render() {
    if (!this.filterStore) {
      return null;
    }
    const {children, className} = this.props;
    return (
      <Provider filters={this.filterStore}>
        <div className={classNames(styles.container, className)}>
          <div className={styles.menu}>{this.renderMenu()}</div>
          <div className={styles.content}>{children}</div>
        </div>
      </Provider>
    );
  }
}

BillingNavigation.propTypes = {
  className: PropTypes.string,
  location: PropTypes.object,
  router: PropTypes.object,
  children: PropTypes.node,
};

const RUNNER_SEPARATOR = FilterStore.RUNNER_SEPARATOR;
const REGION_SEPARATOR = FilterStore.REGION_SEPARATOR;

export {RUNNER_SEPARATOR, REGION_SEPARATOR, RunnerTypes};

export default BillingNavigation;
