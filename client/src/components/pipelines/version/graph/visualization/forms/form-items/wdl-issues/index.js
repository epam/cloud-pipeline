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
import classNames from 'classnames';
import {Alert} from 'antd';
import styles from './wdl-issues.css';

function WdlIssues (
  {
    className,
    issueClassName,
    style,
    issueStyle,
    issues = [],
    alert = false,
    fullDescription = false
  }
) {
  if (!issues || !issues.length) {
    return null;
  }
  const getIssueText = (issue) => fullDescription ? issue.description : issue.message;
  const renderSingleIssue = (issue, idx) => {
    if (alert) {
      return (
        <Alert
          key={`issue-${idx}`}
          type={issue.level}
          className={styles.wdlAlertIssue}
          message={(
            <div
              className={
                classNames(
                  styles.wdlIssue,
                  issueClassName
                )
              }
              style={issueStyle}
            >
              {getIssueText(issue)}
            </div>
          )}
        />
      );
    }
    return (
      <div
        key={`issue-${idx}`}
        className={
          classNames(
            styles.wdlIssue,
            issueClassName,
            'cp-error'
          )
        }
        style={issueStyle}
      >
        {getIssueText(issue)}
      </div>
    );
  };
  return (
    <div
      className={classNames(className, styles.wdlIssuesContainer)}
      style={style}
    >
      {
        issues.map(renderSingleIssue)
      }
    </div>
  );
}

WdlIssues.propTypes = {
  className: PropTypes.string,
  issueClassName: PropTypes.string,
  style: PropTypes.object,
  issueStyle: PropTypes.object,
  issues: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  alert: PropTypes.bool,
  fullDescription: PropTypes.bool
};

export default WdlIssues;
