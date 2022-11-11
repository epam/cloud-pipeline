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
import {observer} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';
import {message, Table} from 'antd';
import roleModel from '../../../utils/roleModel';
import styles from './sub-settings.css';

@roleModel.authenticationInfo
@observer
class SubSettings extends React.Component {
  state = {
    section: undefined,
    sub: undefined
  };

  componentDidMount () {
    this.updateFromProps();
    this.rememberSubSection();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.activeSectionKey !== this.props.activeSectionKey) {
      this.updateFromProps();
    } else {
      this.selectDefaultSection();
    }
    this.rememberSubSection();
  }

  shouldComponentUpdate (nextProps, nextState, nextContext) {
    const shallowCompareProp = (property) => nextProps[property] !== this.props[property];
    const shallowCompareState = (property) => nextState[property] !== this.state[property];
    const subSectionProp = nextProps.router && nextProps.router.params
      ? nextProps.router.params.sub
      : undefined;
    return shallowCompareProp('activeSectionKey') ||
      shallowCompareProp('className') ||
      shallowCompareProp('onSectionChange') ||
      shallowCompareProp('sections') ||
      shallowCompareProp('canNavigate') ||
      shallowCompareProp('children') ||
      shallowCompareProp('emptyDataPlaceholder') ||
      shallowCompareState('section') ||
      shallowCompareState('sub') ||
      subSectionProp !== this.state.sub;
  }

  @computed
  get user () {
    if (
      this.props.authenticatedUserInfo &&
      this.props.authenticatedUserInfo.loaded
    ) {
      return this.props.authenticatedUserInfo.value;
    }
    return undefined;
  }

  updateFromProps = () => {
    const {
      activeSectionKey
    } = this.props;
    this.setState({
      section: activeSectionKey
    }, this.selectDefaultSection);
  };

  rememberSubSection = () => {
    const {
      router
    } = this.props;
    this.setState({
      sub: router && router.params ? router.params.sub : undefined
    });
  };

  selectDefaultSection = () => {
    const {
      sections = [],
      router
    } = this.props;
    const {section} = this.state;
    const keys = sections.map(o => o.key);
    if (!keys.includes(section) && keys.length) {
      const sectionFromRoute = router && router.params && router.params.section
        ? sections.find(o =>
          (o.key || '').toString().toLowerCase() === router.params.section.toLowerCase()
        )
        : undefined;
      const defaultSection = sectionFromRoute || sections.find(o => o.default);
      this.setState({
        section: defaultSection ? defaultSection.key : keys[0]
      }, this.reportSectionSelection);
    }
  };

  reportSectionSelection = () => {
    const {section} = this.state;
    const {
      onSectionChange,
      router,
      root,
      sections = []
    } = this.props;
    if (onSectionChange) {
      onSectionChange(section);
    }
    if (router && root) {
      const currentSection = sections.find(o => (o.key || '').toString() === section);
      const {params = {}} = router;
      const {
        section: rootSection
      } = params;
      const navigation = currentSection && !currentSection.default ? currentSection.key : undefined;
      if (navigation !== rootSection) {
        const url = navigation
          ? `/settings/${root}/${navigation}`
          : `/settings/${root}`;
        router.replace(url);
      }
    }
  };

  onSelectSection = (section) => {
    const {
      section: current
    } = this.state;
    const {
      canNavigate,
      sections = []
    } = this.props;
    const currentSection = sections.find(o => o.key === section);
    if (
      section === current ||
      (currentSection && currentSection.disabled)
    ) {
      return;
    }
    const onNavigate = () => {
      this.setState({
        section
      }, this.reportSectionSelection);
    };
    const canNavigatePromise = () => new Promise((resolve) => {
      if (canNavigate === undefined || canNavigate === null) {
        resolve(true);
      } else if (typeof canNavigate === 'function') {
        const promise = canNavigate(section);
        if (promise && promise.then) {
          promise
            .catch(e => {
              message.error(e.message, 5);
              return Promise.resolve(false);
            })
            .then(result => resolve(!!result));
        } else {
          resolve(!!promise);
        }
      } else {
        resolve(true);
      }
    });
    canNavigatePromise()
      .then(navigate => navigate ? onNavigate() : undefined);
  };

  renderSectionsList () {
    const columns = [{
      key: 'title',
      dataIndex: 'title'
    }];
    const {sections = []} = this.props;
    const {section} = this.state;
    if (sections.length === 0) {
      return null;
    }
    return (
      <Table
        className={classNames(styles.list, 'cp-divider', 'right')}
        rowKey="key"
        showHeader={false}
        pagination={false}
        dataSource={sections}
        columns={columns}
        rowClassName={
          (item) => classNames(
            `section-${(item.key.toString() || '').replace(/\s/g, '-').toLowerCase()}`,
            'cp-settings-sidebar-element',
            {
              'cp-table-element-selected': item.key === section,
              'cp-table-element-disabled': item.disabled
            }
          )
        }
        onRowClick={(item) => this.onSelectSection(item.key)}
      />
    );
  }

  renderSectionContent () {
    const {section} = this.state;
    if (!section) {
      return undefined;
    }
    const {
      sections = [],
      children,
      router
    } = this.props;
    const currentSection = sections.find(o => o.key === section);
    if (!currentSection) {
      return null;
    }
    let content = children;
    const props = {
      router,
      section: currentSection,
      sub: router && router.params ? router.params.sub : undefined,
      user: this.user
    };
    if (typeof currentSection.render === 'function') {
      content = currentSection.render(props);
    } else if (typeof children === 'function') {
      content = children(props);
    }
    return (
      <div
        className={styles.content}
      >
        {content}
      </div>
    );
  }
  render () {
    const {
      className,
      sections = [],
      emptyDataPlaceholder
    } = this.props;
    if (sections.length === 0) {
      return (
        <div
          className={
            classNames(
              className,
              styles.container
            )
          }
        >
          {emptyDataPlaceholder}
        </div>
      );
    }
    return (
      <div
        className={
          classNames(
            className,
            styles.container
          )
        }
      >
        {this.renderSectionsList()}
        {this.renderSectionContent()}
      </div>
    );
  }
}

SubSettings.propTypes = {
  className: PropTypes.string,
  activeSectionKey: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  onSectionChange: PropTypes.func,
  sections: PropTypes.arrayOf(PropTypes.shape({
    key: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    title: PropTypes.string,
    route: PropTypes.string,
    default: PropTypes.bool,
    render: PropTypes.func,
    disabled: PropTypes.bool
  })),
  canNavigate: PropTypes.func,
  children: PropTypes.oneOfType([PropTypes.node, PropTypes.func]),
  emptyDataPlaceholder: PropTypes.node,
  router: PropTypes.object
};

export default observer(SubSettings);
