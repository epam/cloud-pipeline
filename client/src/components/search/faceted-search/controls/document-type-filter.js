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
import {Icon} from 'antd';
import classNames from 'classnames';
import {SearchGroupTypes} from '../../searchGroupTypes';
import localization from '../../../../utils/localization';
import displayCount from '../../../../utils/displayCount';
import styles from './controls.css';

@localization.localizedComponent
@observer
class DocumentTypeFilter extends localization.LocalizedReactComponent {
  get filters () {
    const {values = [], selection} = this.props;
    return Object.values(SearchGroupTypes).map(group => ({
      ...group,
      enabled: group.test(selection),
      count: values
        .filter(v => group.test(v.name))
        .map(v => v.count)
        .reduce((r, c) => r + c, 0)
    }));
  }

  handleFilterClick = (filter) => () => {
    if (filter.count > 0) {
      const {selection, onChange} = this.props;
      const newSelection = (selection || []).filter(s => !filter.test(s));
      if (!filter.enabled) {
        newSelection.push(...filter.types);
      }
      if (onChange) {
        onChange(newSelection);
      }
    }
  };

  render () {
    return (
      <div
        className={styles.documentTypeFilter}
      >
        {
          this.filters.map(f => (
            <div
              className={
                classNames(
                  styles.filter,
                  {
                    [styles.selected]: f.enabled,
                    [styles.disabled]: f.count === 0
                  }
                )
              }
              key={f.types.join('-')}
              onClick={this.handleFilterClick(f)}
            >
              <Icon
                className={styles.icon}
                type={f.icon}
              />
              {f.title(this.localizedString)()}
              {
                f.count > 0
                  ? (
                    <span
                      className={styles.count}
                    >
                      ({displayCount(f.count, true)})
                    </span>
                  )
                  : undefined
              }
            </div>
          ))
        }
      </div>
    );
  }
}

DocumentTypeFilter.propTypes = {
  onChange: PropTypes.func,
  selection: PropTypes.array,
  values: PropTypes.array
};

const DocumentTypeFilterName = 'doc_type';

export {DocumentTypeFilterName};
export default DocumentTypeFilter;
