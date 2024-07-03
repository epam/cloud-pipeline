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
import {
  Alert,
  Checkbox,
  Icon
} from 'antd';
import styles from './ome-tiff-annotations.css';

@inject('annotations')
@observer
class OMETiffAnnotations extends React.Component {
  render () {
    const {
      annotations,
      className,
      style
    } = this.props;
    if (!annotations) {
      return null;
    }
    const nonEmptyAnnotations = (annotations.usersAnnotations || [])
      .filter((userAnnotations) => (userAnnotations.annotations || []).length > 0);
    const annotationsPending = annotations.usersAnnotations
      .some((userAnnotations) => userAnnotations.pending);
    const changeVisibilityCallback = (annotation) => (event) => {
      annotation.visible = event.target.checked;
    };
    const allVisible = nonEmptyAnnotations.length > 0 &&
      !nonEmptyAnnotations.some((annotation) => !annotation.visible);
    const allHidden = nonEmptyAnnotations.length === 0 ||
      !nonEmptyAnnotations.some((annotation) => annotation.visible);
    const onChangeAll = (event) => {
      const visible = event.target.checked;
      nonEmptyAnnotations.forEach((annotation) => {
        annotation.visible = visible;
      });
    };
    return (
      <div
        className={
          classNames(
            className,
            styles.container
          )
        }
        style={style}
      >
        <div
          className={styles.header}
        >
          <Checkbox
            indeterminate={!allVisible && !allHidden}
            checked={allVisible}
            onChange={onChangeAll}
            disabled={annotations.pending}
          >
            <b>Annotations:</b>
          </Checkbox>
          {
            (annotations.pending || annotationsPending) && (
              <Icon type="loading" style={{marginLeft: 5}} />
            )
          }
        </div>
        <div>
          {
            nonEmptyAnnotations.length === 0 &&
            !annotations.pending &&
            !annotationsPending && (
              <span className="cp-text-not-important">
                There are no annotations
              </span>
            )
          }
          {
            !annotations.pending && annotations.error && (
              <Alert type="error" message={annotations.error} />
            )
          }
          {
            nonEmptyAnnotations.map((userAnnotations) => (
              <div
                key={userAnnotations.path}
                className={styles.userAnnotations}
              >
                <Checkbox
                  checked={userAnnotations.visible}
                  onChange={changeVisibilityCallback(userAnnotations)}
                >
                  {userAnnotations.userName}
                </Checkbox>
              </div>
            ))
          }
        </div>
      </div>
    );
  }
}

OMETiffAnnotations.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object
};

export default OMETiffAnnotations;
