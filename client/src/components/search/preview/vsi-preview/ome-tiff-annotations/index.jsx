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
import {inject, observer} from 'mobx-react';
import classNames from 'classnames';
import {Alert, Checkbox} from 'antd';
import {LoadingOutlined} from '@ant-design/icons';
import UserName from '../../../../shared/user-name';
import styles from './ome-tiff-annotations.module.css';

@inject('annotations')
@observer
class OMETiffAnnotations extends React.Component {
  render() {
    const {annotations, className, style} = this.props;
    if (!annotations) {
      return null;
    }
    const nonEmptyAnnotations = (annotations.usersAnnotations || []).filter(
      (userAnnotations) => (userAnnotations.annotations || []).length > 0,
    );
    const annotationsPending = annotations.usersAnnotations.some(
      (userAnnotations) => userAnnotations.pending,
    );
    const changeVisibilityCallback = (annotation) => (event) => {
      annotation.visible = event.target.checked;
    };
    const allVisible =
      nonEmptyAnnotations.length > 0 &&
      !nonEmptyAnnotations.some((annotation) => !annotation.visible);
    const allHidden =
      nonEmptyAnnotations.length === 0 ||
      !nonEmptyAnnotations.some((annotation) => annotation.visible);
    const onChangeAll = (event) => {
      const visible = event.target.checked;
      nonEmptyAnnotations.forEach((annotation) => {
        annotation.visible = visible;
      });
    };
    return (
      <div className={classNames(className, styles.container)} style={style}>
        <div className={styles.header}>
          <Checkbox
            indeterminate={!allVisible && !allHidden}
            checked={allVisible}
            onChange={onChangeAll}
            disabled={annotations.pending}
          >
            <b>Annotations:</b>
          </Checkbox>
          {(annotations.pending || annotationsPending) && (
            <LoadingOutlined style={{marginLeft: 5}} />
          )}
        </div>
        <div>
          {nonEmptyAnnotations.length === 0 && !annotations.pending && !annotationsPending && (
            <span className="cp-text-not-important">There are no annotations</span>
          )}
          {!annotations.pending && annotations.error && (
            <Alert type="error" title={annotations.error} />
          )}
          {nonEmptyAnnotations.map((userAnnotations) => (
            <div key={userAnnotations.path} className={styles.userAnnotations}>
              <Checkbox
                checked={userAnnotations.visible}
                onChange={changeVisibilityCallback(userAnnotations)}
              >
                <UserName userName={userAnnotations.userName} showIcon />
              </Checkbox>
            </div>
          ))}
        </div>
      </div>
    );
  }
}

OMETiffAnnotations.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
};

export default OMETiffAnnotations;
