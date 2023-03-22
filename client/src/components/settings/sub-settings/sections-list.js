/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Input, Table} from 'antd';
import styles from './sub-settings.css';

class SectionsList extends React.Component {
  state = {
    sectionSearch: undefined
  };

  onSelectSection = (section) => {
    const {
      onSectionChange
    } = this.props;
    if (typeof onSectionChange === 'function') {
      onSectionChange(section);
    }
  };

  render () {
    const columns = [{
      key: 'title',
      dataIndex: 'title'
    }];
    const {
      activeSectionKey,
      sections = [],
      showSearch,
      searchPlaceholder,
      className
    } = this.props;
    const {
      sectionSearch
    } = this.state;
    if (sections.length === 0) {
      return null;
    }
    const onSearch = (event) => this.setState({sectionSearch: event.target.value});
    const filteredSections = sections.filter(
      (section) => !sectionSearch ||
        !sectionSearch.length ||
        (
          typeof section.title === 'string' &&
          (section.title || '').toLowerCase().includes(sectionSearch.toLowerCase())
        ) ||
        (
          typeof section.name === 'string' &&
          (section.name || '').toLowerCase().includes(sectionSearch.toLowerCase())
        )
    );
    return (
      <div
        className={
          classNames(
            styles.listContainer,
            className
          )
        }
      >
        {
          showSearch && (
            <div
              className={styles.listSearch}
            >
              <Input.Search
                style={{width: '100%'}}
                value={sectionSearch}
                onChange={onSearch}
                placeholder={searchPlaceholder}
              />
            </div>
          )
        }
        <Table
          className={styles.list}
          rowKey="key"
          showHeader={false}
          pagination={false}
          dataSource={filteredSections}
          columns={columns}
          rowClassName={
            (item) => classNames(
              `section-${(item.key.toString() || '').replace(/\s/g, '-').toLowerCase()}`,
              'cp-settings-sidebar-element',
              {
                'cp-table-element-selected': item.key === activeSectionKey,
                'cp-table-element-disabled': item.disabled
              }
            )
          }
          onRowClick={(item) => this.onSelectSection(item.key)}
        />
      </div>
    );
  }
}

SectionsList.propTypes = {
  className: PropTypes.string,
  activeSectionKey: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  onSectionChange: PropTypes.func,
  showSearch: PropTypes.bool,
  searchPlaceholder: PropTypes.string,
  sections: PropTypes.arrayOf(PropTypes.shape({
    key: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
    title: PropTypes.oneOfType([PropTypes.string, PropTypes.node]),
    name: PropTypes.string
  }))
};

export default observer(SectionsList);
