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
import {WdlEvent} from '../../../../../../../../utils/pipeline-builder';
import classNames from 'classnames';
import {Collapse} from 'antd';
import WdlIssues from '../wdl-issues';
import styles from './wdl-document-properties.css';

class WdlDocumentProperties extends React.Component {
  state = {
    issues: []
  };

  componentDidMount () {
    this.subscribeOnDocumentEvents(this.props.document);
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.document !== this.props.document) {
      this.subscribeOnDocumentEvents(this.props.document, prevProps.document);
    }
  }

  subscribeOnDocumentEvents = (newDocument, previousDocument) => {
    if (previousDocument) {
      this.unsubscribeFromDocumentEvents(previousDocument);
    }
    if (newDocument) {
      newDocument.on(WdlEvent.changed, this.onDocumentChanged, this);
    }
    this.updateIssues();
  };

  unsubscribeFromDocumentEvents = (doc) => {
    if (doc) {
      doc.off(WdlEvent.changed, this.onDocumentChanged, this);
    }
  };

  onDocumentChanged = () => {
    this.updateIssues();
  };

  updateIssues = () => {
    const {
      document: wdlDocument
    } = this.props;
    const {
      issues = []
    } = wdlDocument || {};
    this.setState({
      issues
    });
  };

  render () {
    const {
      className,
      style,
      document: wdlDocument
    } = this.props;
    if (!wdlDocument) {
      return null;
    }
    const {
      issues
    } = this.state;
    return (
      <div
        className={
          classNames((className, styles.container))
        }
        style={style}
      >
        <div
          className={
            classNames(
              styles.header,
              styles.mainHeader
            )
          }
        >
          <b>Document info</b>
        </div>
        <div className={styles.row}>
          Version: <b>{wdlDocument.version}</b>
        </div>
        <Collapse
          bordered={false}
          className="wdl-properties-collapse"
        >
          <Collapse.Panel
            key="issues"
            header={(
              <div>
                Document issues ({issues.length})
              </div>
            )}
          >
            <WdlIssues
              issues={issues}
              fullDescription
              alert
            />
          </Collapse.Panel>
        </Collapse>
      </div>
    );
  }
}

WdlDocumentProperties.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  onSelect: PropTypes.func,
  document: PropTypes.object
};

export default WdlDocumentProperties;
