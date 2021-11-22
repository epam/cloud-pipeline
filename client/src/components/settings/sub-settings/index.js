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
import classNames from 'classnames';
import {message, Table} from 'antd';
import styles from './sub-settings.css';

class SubSettings extends React.PureComponent {
  state = {
    section: undefined
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.activeSectionKey !== this.props.activeSectionKey) {
      this.updateFromProps();
    } else {
      this.selectDefaultSection();
    }
  }

  updateFromProps = () => {
    const {
      activeSectionKey
    } = this.props;
    this.setState({
      section: activeSectionKey
    }, this.selectDefaultSection);
  };

  selectDefaultSection = () => {
    const {
      sections = []
    } = this.props;
    const {section} = this.state;
    const keys = sections.map(o => o.key);
    if (!keys.includes(section) && keys.length) {
      this.setState({
        section: keys[0]
      }, this.reportSectionSelection);
    }
  };

  reportSectionSelection = () => {
    const {section} = this.state;
    const {onSectionChange} = this.props;
    if (onSectionChange) {
      onSectionChange(section);
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
      children
    } = this.props;
    const currentSection = sections.find(o => o.key === section);
    if (!currentSection) {
      return null;
    }
    let content = children;
    if (typeof currentSection.render === 'function') {
      content = currentSection.render();
    } else if (typeof children === 'function') {
      content = children(currentSection);
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
    render: PropTypes.func,
    disabled: PropTypes.bool
  })),
  canNavigate: PropTypes.func,
  children: PropTypes.oneOfType([PropTypes.node, PropTypes.func]),
  emptyDataPlaceholder: PropTypes.node
};

export default observer(SubSettings);
