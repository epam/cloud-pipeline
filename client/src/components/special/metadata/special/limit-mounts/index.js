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
import {Checkbox} from 'antd';
import classNames from 'classnames';
import styles from './limit-mounts.css';
import {LimitMountsInput} from '../../../../pipelines/launch/form/LimitMountsInput';
import {CP_CAP_LIMIT_MOUNTS} from '../../../../pipelines/launch/form/utilities/parameters';

class LimitMountsUserPreference extends React.Component {
  onChangeLimitMounts = (value) => {
    const {
      metadata = {}
    } = this.props;
    const {value: currentValue = undefined} = metadata;
    if (value === null) {
      value = undefined;
    }
    if (value !== currentValue) {
      const {onChange, onRemove} = this.props;
      if (value !== undefined && onChange) {
        onChange(value);
      } else if (value === undefined && onRemove) {
        onRemove();
      } else if (onChange) {
        onChange(value);
      }
    }
  };

  onChangeDoNotMountStorages = (e) => {
    if (e.target.checked) {
      this.onChangeLimitMounts('None');
    } else {
      this.onChangeLimitMounts(undefined);
    }
  };

  render () {
    const {
      readOnly,
      metadata = {}
    } = this.props;
    const {value = undefined} = metadata;
    const doNotMountStorages = /^none$/i.test(value);
    return (
      <div>
        <div
          className={
            classNames(
              styles.limitMountsRow,
              styles.header,
              'cp-text'
            )
          }
        >
          Limit mounts:
        </div>
        <div className={styles.limitMountsRow}>
          <Checkbox
            disabled={readOnly}
            checked={doNotMountStorages}
            onChange={this.onChangeDoNotMountStorages}
          >
            Do not mount storages
          </Checkbox>
        </div>
        {
          !doNotMountStorages && (
            <div
              className={
                classNames(
                  styles.limitMountsRow
                )
              }
            >
              <LimitMountsInput
                value={value}
                allowSensitive={false}
                disabled={readOnly}
                onChange={this.onChangeLimitMounts}
              />
            </div>
          )
        }
      </div>
    );
  }
}

LimitMountsUserPreference.metatadaKey = CP_CAP_LIMIT_MOUNTS;

LimitMountsUserPreference.propTypes = {
  metadata: PropTypes.object,
  readOnly: PropTypes.bool,
  onChange: PropTypes.func,
  onRemove: PropTypes.func,
  info: PropTypes.object
};

export {CP_CAP_LIMIT_MOUNTS as METADATA_KEY};

export default LimitMountsUserPreference;
