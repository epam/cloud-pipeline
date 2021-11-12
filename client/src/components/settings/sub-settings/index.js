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
import classNames from 'classnames';
import {Table} from 'antd';
import styles from './sub-settings.css';

class SubSettings extends React.PureComponent {
  state = {
    section: undefined
  };

  componentDidMount () {
    this.selectDefaultSection();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    this.selectDefaultSection();
  }

  selectDefaultSection = () => {
    const {sections = []} = this.props;
    const {section} = this.state;
    const keys = sections.map(o => o.key);
    if (!keys.includes(section) && keys.length) {
      this.setState({
        section: keys[0]
      });
    }
  };

  onSelectSection = (section) => {
    this.setState({
      section
    });
  };

  renderSectionsList () {
    const columns = [{key: 'title', dataIndex: 'title'}];
    const {sections = []} = this.props;
    const {section} = this.state;
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
            `section-${item.key}`,
            'cp-settings-sidebar-element',
            {'cp-table-element-selected': item.key === section}
          )
        }
        onRowClick={(item) => this.onSelectSection(item.key)}
      />
    );
  }

  renderSectionContent () {
    const {section} = this.state;
    if (!section) {
      return null;
    }
    const {sections = []} = this.props;
    const currentSection = sections.find(o => o.key === section);
    if (!currentSection || typeof currentSection.render !== 'function') {
      return null;
    }
    return (
      <div
        className={styles.content}
      >
        {currentSection.render()}
      </div>
    );
  }

  render () {
    const {className} = this.props;
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
  sections: PropTypes.arrayOf(PropTypes.shape({
    key: PropTypes.string,
    title: PropTypes.string,
    render: PropTypes.func
  }))
};

export default SubSettings;
