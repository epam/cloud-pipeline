/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import React, {Component} from 'react';
import {Layout, LocaleProvider} from 'antd';
import enUS from 'antd/lib/locale-provider/en_US';
import {observer, Provider, inject} from 'mobx-react';
import {observable} from 'mobx';
import styles from './App.css';
import Navigation from './navigation/Navigation';
import NotificationCenter from './notification/NotificationCenter';
import searchStyles from '../search/search.css';
import {SearchDialog} from '../search';
import roleModel from '../../utils/roleModel';
import {Pages} from '../../utils/ui-navigation';

@inject('preferences', 'uiNavigation')
@roleModel.authenticationInfo
@observer
export default class App extends Component {
  state = {
    navigationCollapsed: true,
    documentTitleSet: false,
    searchFormVisible: false
  };

  @observable info = {
    searchFormVisible: false
  };

  searchDialog;

  onSearchDialogInitialized = (control) => {
    this.searchDialog = control;
  };

  openSearchDialog = () => {
    this.searchDialog && this.searchDialog.openDialog && this.searchDialog.openDialog();
  };

  onLibraryCollapsedChange = () => {
    const {uiNavigation} = this.props;
    uiNavigation.libraryExpanded = !uiNavigation.libraryExpanded;
  };

  onSearchControlVisibilityChanged = (visible) => {
    this.info.searchFormVisible = visible;
    this.setState({
      searchFormVisible: visible
    });
  };

  render () {
    const {
      preferences,
      authenticatedUserInfo,
      uiNavigation
    } = this.props;
    uiNavigation.getActivePage(this.props.router);
    const isBillingPrivilegedUser = authenticatedUserInfo.loaded &&
      roleModel.isManager.billing(this);
    const activeTabPath = uiNavigation.getActivePage(this.props.router);
    const isExternalApp = [
      Pages.miew
    ].indexOf(activeTabPath) >= 0;
    const isSearch = /[\\/]+search\/advanced/i.test(this.props.router.location.pathname);
    let content;
    if (isExternalApp) {
      content = this.props.children;
    } else {
      const searchStyle = [searchStyles.searchBlur];
      if (this.state.searchFormVisible) {
        searchStyle.push(searchStyles.enabled);
      }
      content = (
        <Layout id="root-layout">
          <Layout.Sider
            collapsible
            collapsed={this.state.navigationCollapsed}
            trigger={null} >
            <Navigation
              deploymentName={this.props.preferences.deploymentName}
              activeTabPath={activeTabPath}
              collapsed={!uiNavigation.libraryExpanded}
              onLibraryCollapsedChange={this.onLibraryCollapsedChange}
              openSearchDialog={this.openSearchDialog}
              searchControlVisible={this.state.searchFormVisible}
              searchEnabled={preferences.loaded && preferences.searchEnabled}
              billingEnabled={
                preferences.loaded &&
                authenticatedUserInfo.loaded &&
                (
                  (!isBillingPrivilegedUser && preferences.billingEnabled) ||
                  (isBillingPrivilegedUser && preferences.billingAdminsEnabled)
                )
              }
              router={this.props.router} />
          </Layout.Sider>
          <Layout.Content
            id="root-content"
            className={`${styles.contentWrapper} ${searchStyle.join(' ')}`}>
            <Provider displayInfo={this.info}>
              {this.props.children}
            </Provider>
          </Layout.Content>
        </Layout>
      );
    }
    return (
      <LocaleProvider locale={enUS}>
        <div id="root-container" className={styles.appContainer}>
          {
            this.props.uiNavigation.searchEnabled() && !isExternalApp && (
              <SearchDialog
                onInitialized={this.onSearchDialogInitialized}
                router={this.props.router}
                blockInput={activeTabPath === Pages.run || isSearch}
                onVisibilityChanged={this.onSearchControlVisibilityChanged}
              />
            )
          }
          {content}
          <NotificationCenter
            delaySeconds={2}
          />
        </div>
      </LocaleProvider>
    );
  }

  componentDidUpdate () {
    document.title = this.props.preferences.deploymentName || 'Loading...';
  }

  componentDidMount () {
    document.title = this.props.preferences.deploymentName || 'Loading...';
  }
}
