/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Icon, Row} from 'antd';
import renderHighlights from './renderHighlights';
import renderSeparator from './renderSeparator';
import {PreviewIcons} from './previewIcons';
import {metadataLoad, renderAttributes} from './renderAttributes';
import StatusIcon from '../../special/run-status-icon';
import UserName from '../../special/UserName';
import displayDate from '../../../utils/displayDate';
import PipelineRunFilter from '../../../models/pipelines/PipelineRunSingleFilter';
import styles from './preview.css';

const PAGE_SIZE = 20;

@inject('metadataCache', 'dataStorageCache')
@inject((stores, params) => {
  const {pipelines} = stores;
  return {
    versions: params.item && params.item.id
      ? pipelines.versionsForPipeline(params.item.id)
      : null,
    pipeline: params.item && params.item.id
      ? pipelines.getPipeline(params.item.id)
      : null,
    history: params.item && params.item.id
      ? new PipelineRunFilter({
        page: 1,
        pageSize: PAGE_SIZE,
        pipelineIds: [params.item.id],
        userModified: false
      }, false)
      : null,
    metadata: metadataLoad(params, 'PIPELINE', stores)
  };
})
@observer
export default class PipelinePreview extends React.Component {
  static propTypes = {
    item: PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      parentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      name: PropTypes.string,
      description: PropTypes.string
    })
  };

  @computed
  get name () {
    if (!this.props.item) {
      return null;
    }
    if (this.props.pipeline && this.props.pipeline.loaded) {
      return this.props.pipeline.value.name;
    }
    return this.props.item.name;
  }

  @computed
  get description () {
    if (!this.props.item) {
      return null;
    }
    if (this.props.pipeline && this.props.pipeline.loaded) {
      return this.props.pipeline.value.description;
    }
    return this.props.item.description;
  }

  renderVersions = () => {
    if (this.props.versions) {
      if (this.props.versions.pending) {
        return <Row className={styles.contentPreview} type="flex" justify="center"><Icon type="loading" /></Row>;
      }
      if (this.props.versions.error) {
        return (
          <div className={styles.contentPreview}>
            <span style={{color: '#ff556b'}}>{this.props.versions.error}</span>
          </div>
        );
      }
      const versions = (this.props.versions.value || []).map(v => v);
      const cellStyle = {
        paddingRight: 10
      };
      return (
        <div className={styles.info}>
          <table>
            <tbody>
              {
                versions.map((version, index) => {
                  return (
                    <tr key={index}>
                      <td style={cellStyle}><Icon type="tag" /> {version.name}</td>
                      <td style={cellStyle}>{version.message}</td>
                      <td style={cellStyle}>{displayDate(version.createdDate, 'LL')}</td>
                    </tr>
                  );
                })
              }
            </tbody>
          </table>
        </div>
      );
    }
    return null;
  };

  renderRunHistory = () => {
    if (this.props.history) {
      if (this.props.history.pending) {
        return <Row className={styles.contentPreview} type="flex" justify="center"><Icon type="loading" /></Row>;
      }
      if (this.props.history.error) {
        return (
          <div className={styles.contentPreview}>
            <span style={{color: '#ff556b'}}>{this.props.history.error}</span>
          </div>
        );
      }
      const runs = (this.props.history.value || []).map(r => r);
      if (runs.length === 0) {
        return null;
      }
      const runName = (run) => {
        const {
          podId
        } = run;
        let clusterIcon;
        if (run.nodeCount > 0) {
          clusterIcon = <Icon type="database" />;
        }
        return (
          <span><StatusIcon run={run} small /> {clusterIcon} {podId}</span>
        );
      };
      return (
        <div className={styles.info}>
          {
            this.props.history.total > PAGE_SIZE &&
            <span>Last {Math.min(PAGE_SIZE, this.props.history.total)} runs:</span>
          }
          <table className={styles.runTable}>
            <tbody>
              <tr className={styles.run}>
                <th className={styles.run}>RUN</th>
                <th className={styles.run}>VERSION</th>
                <th className={styles.run}>STARTED</th>
                <th className={styles.run}>COMPLETED</th>
                <th className={styles.run}>OWNER</th>
              </tr>
              {
                runs.map((run, index) => {
                  return (
                    <tr key={index}>
                      <td className={styles.run}>{runName(run)}</td>
                      <td className={styles.run}>{run.version}</td>
                      <td className={styles.run}>{displayDate(run.startDate)}</td>
                      <td className={styles.run}>{displayDate(run.endDate)}</td>
                      <td className={styles.run}><UserName userName={run.owner} /></td>
                    </tr>
                  );
                })
              }
            </tbody>
          </table>
        </div>
      );
    }
    return null;
  };

  render () {
    if (!this.props.item) {
      return null;
    }
    const highlights = renderHighlights(this.props.item);
    const versions = this.renderVersions();
    const attributes = renderAttributes(this.props.metadata);
    const history = this.renderRunHistory();
    return (
      <div className={styles.container}>
        <div className={styles.header}>
          <Row className={styles.title} type="flex" align="middle">
            <Icon type={PreviewIcons[this.props.item.type]} />
            <span>{this.name}</span>
          </Row>
          {
            this.description &&
            <Row className={styles.description}>
              {this.description}
            </Row>
          }
        </div>
        <div className={styles.content}>
          {highlights && renderSeparator()}
          {highlights}
          {versions && renderSeparator()}
          {versions}
          {attributes && renderSeparator()}
          {attributes}
          {history && renderSeparator()}
          {history}
        </div>
      </div>
    );
  }
}
