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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';
import {Icon} from 'antd';
import moment from 'moment-timezone';
import PipelineRunFilter from '../../../models/pipelines/PipelineRunFilter';
import {SearchHint} from './controls';
import roleModel from '../../../utils/roleModel';
import LoadingView from '../../special/LoadingView';
import StatusIcon from '../../special/run-status-icon';
import {getSpotTypeName} from '../../special/spot-instance-names';
import AWSRegionTag from '../../special/AWSRegionTag';
import ToolImage from '../../../models/tools/ToolImage';
import displayDate from '../../../utils/displayDate';
import localization from '../../../utils/localization';
import {SearchGroupTypes} from '../searchGroupTypes';
import styles from './top-suggestions.css';

const TOP_SUGGESTION_SECTION_LENGTH = 5;

@inject('dataStorages', 'pipelines', 'dockerRegistries')
@roleModel.authenticationInfo
@localization.localizedComponent
@observer
class TopSuggestions extends localization.LocalizedReactComponent {
  state = {
    runs: [],
    runsLoaded: false,
    runsLoading: false
  };

  componentDidMount () {
    this.loadRuns();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    this.loadRuns();
  }

  @computed
  get tools () {
    const {dockerRegistries} = this.props;
    if (dockerRegistries.loaded) {
      const {registries = []} = dockerRegistries.value;
      const result = [];
      for (let r = 0; r < registries.length; r++) {
        const registry = registries[r];
        const {groups = []} = registry;
        for (let g = 0; g < groups.length; g++) {
          const group = groups[g];
          const {tools = []} = group;
          for (let t = 0; t < tools.length; t++) {
            const tool = tools[t];
            const regExp = new RegExp(`^${registry.path}/${tool.image}(:.*)?$`, 'i');
            result.push({
              ...tool,
              registryObj: registry,
              groupObj: group,
              imageRegExp: regExp,
              name: tool.image.split('/').pop(),
              key: `${registry.path}/${tool.image}`
            });
          }
        }
      }
      return result;
    }
    return [];
  }

  @computed
  get pipelines () {
    const {pipelines} = this.props;
    if (pipelines.loaded) {
      return (pipelines.value || []).map(p => p);
    }
    return [];
  }

  @computed
  get dataStorages () {
    const {dataStorages} = this.props;
    if (dataStorages.loaded) {
      return (dataStorages.value || []).map(p => p);
    }
    return [];
  }

  get topTools () {
    const {runs} = this.state;
    const extractDockerImage = r => {
      if (r.pipelineId) {
        return undefined;
      }
      const [registry, group, toolVersion] = (r.dockerImage || '').split('/');
      return `${registry}/${group}/${toolVersion.split(':')[0]}`;
    };
    return Array.from(new Set(runs.map(r => extractDockerImage(r))))
      .map(image => this.tools.find(tool => tool.imageRegExp.test(image)))
      .filter(Boolean)
      .map(tool => ({
        ...tool,
        hits: runs.filter(run => tool.imageRegExp.test(run.dockerImage)).length,
        lastRun: runs.filter(run => tool.imageRegExp.test(run.dockerImage))[0],
        url: `/tool/${tool.id}`
      }))
      .slice(0, TOP_SUGGESTION_SECTION_LENGTH)
      .sort((a, b) => b.hits - a.hits);
  }

  get topPipelines () {
    const {runs} = this.state;
    return Array.from(new Set(runs.map(r => r.pipelineId)))
      .filter(Boolean)
      .map(pipelineId => this.pipelines.find(pipeline => pipeline.id === pipelineId))
      .filter(Boolean)
      .map(pipeline => ({
        ...pipeline,
        hits: runs.filter(run => pipeline.id === run.pipelineId).length,
        tool: Array.from(new Set(runs.filter(run => pipeline.id === run.pipelineId)))
          .map(run => this.tools.find(tool => tool.imageRegExp.test(run.dockerImage)))
          .filter(Boolean)[0],
        lastRun: runs.filter(run => pipeline.id === run.pipelineId)[0],
        url: `/${pipeline.id}`
      }))
      .slice(0, TOP_SUGGESTION_SECTION_LENGTH)
      .sort((a, b) => b.hits - a.hits);
  }

  get topRuns () {
    const {runs} = this.state;
    return runs
      .slice(0, TOP_SUGGESTION_SECTION_LENGTH)
      .map(run => ({
        ...run,
        tool: this.tools.find(tool => tool.imageRegExp.test(run.dockerImage)),
        url: `/run/${run.id}`
      }));
  }

  get topStorages () {
    const {runs} = this.state;
    const ids = new Set(
      runs
        .map(r => r.pipelineRunParameters)
        .filter(Boolean)
        .reduce((r, c) => ([...r, ...c]), [])
        .filter(p => p.dataStorageLinks)
        .map(p => p.dataStorageLinks)
        .reduce((r, c) => ([...r, ...c]), [])
        .map(s => s.dataStorageId)
    );
    return this.dataStorages
      .filter(s => ids.has(s.id))
      .map(storage => ({
        ...storage,
        url: `/storage/${storage.id}`
      }));
  }

  loadRuns = () => {
    const {runsLoaded, runsLoading} = this.state;
    const {authenticatedUserInfo} = this.props;
    if (!runsLoaded && !runsLoading && authenticatedUserInfo.loaded) {
      this.setState({
        runsLoading: true
      }, () => {
        const loadRuns = (statuses, pageSize = 100) => new Promise((resolve) => {
          const request = new PipelineRunFilter({}, true);
          request.filter({
            page: 1,
            pageSize,
            userModified: false,
            owners: [this.props.authenticatedUserInfo.value.userName],
            statuses
          })
            .catch(() => {})
            .then(() => {
              if (request.loaded) {
                resolve((request.value || []).sort((a, b) => {
                  const aD = moment.utc(a.endDate || undefined);
                  const bD = moment.utc(b.endDate || undefined);
                  return bD - aD;
                }));
              } else {
                resolve([]);
              }
            });
        });
        Promise.all([
          loadRuns([
            'PAUSED',
            'PAUSING',
            'RESUMING',
            'RUNNING'
          ]),
          loadRuns([
            'FAILURE',
            'STOPPED',
            'SUCCESS'
          ])
        ])
          .then(result => {
            this.setState({
              runs: result.reduce((r, c) => ([...r, ...c]), []),
              runsLoaded: true,
              runsLoading: false
            });
          });
      });
    }
  };

  onNavigate = (object) => (event) => {
    event && event.preventDefault();
    event && event.stopPropagation();
    const {onNavigate} = this.props;
    if (onNavigate) {
      onNavigate(object);
    }
  }

  renderLastRun = (obj) => {
    const {lastRun} = obj || {};
    if (lastRun) {
      const date = lastRun.endDate || lastRun.startDate;
      return `Last ran at ${displayDate(date, 'd MMMM YYYY, HH:mm')}`;
    }
    return undefined;
  };

  renderToolCard = (tool) => (
    <a
      key={tool.key}
      onClick={this.onNavigate(tool)}
      className={classNames(styles.suggestion, styles.tool)}
      href={`/#${tool.url}`}
    >
      {
        tool.iconId && (
          <img
            className={styles.background}
            src={ToolImage.url(tool.id, tool.iconId)}
          />
        )
      }
      <div className={styles.header}>
        {
          tool.iconId
            ? (
              <img
                className={styles.toolIcon}
                src={ToolImage.url(tool.id, tool.iconId)}
              />
            )
            : (
              <Icon type="tool" className={styles.icon} />
            )
        }
        <div
          className={classNames(styles.toolName, styles.notTransparentBackground)}
        >
          <div className={styles.name}>
            {tool.name}
          </div>
          <div className={styles.registry}>
            {tool.registryObj.description || tool.registryObj.path}
            <Icon
              type="caret-right"
            />
            {tool.groupObj.name}
          </div>
        </div>
      </div>
      {
        tool.shortDescription && (
          <div className={classNames(styles.description, styles.notTransparentBackground)}>
            {tool.shortDescription}
          </div>
        )
      }
      <div className={classNames(styles.lastRun)}>
        <span>{this.renderLastRun(tool)}</span>
      </div>
    </a>
  );

  renderPipelineCard = (pipeline) => (
    <a
      key={`pipeline-${pipeline.id}`}
      onClick={this.onNavigate(pipeline)}
      className={
        classNames(
          styles.suggestion,
          styles.pipeline
        )
      }
      href={`/#${pipeline.url}`}
    >
      {
        pipeline.tool && pipeline.tool.iconId && (
          <img
            className={styles.background}
            src={ToolImage.url(pipeline.tool.id, pipeline.tool.iconId)}
          />
        )
      }
      <div
        className={classNames(styles.header, styles.notTransparentBackground)}
      >
        <Icon type="fork" className={styles.icon} />
        <span>{pipeline.name}</span>
        <span className={styles.postfix}>{this.localizedString('pipeline')}</span>
      </div>
      {
        pipeline.description && (
          <div
            className={classNames(styles.description, styles.notTransparentBackground)}
          >
            {pipeline.description}
          </div>
        )
      }
      <div className={classNames(styles.lastRun)}>
        <span>{this.renderLastRun(pipeline)}</span>
      </div>
    </a>
  );

  renderRunCard = (run) => (
    <a
      key={`pipeline-${run.id}`}
      onClick={this.onNavigate(run)}
      className={
        classNames(
          styles.suggestion,
          styles.run
        )
      }
      href={`/#${run.url}`}
    >
      {
        run.tool && run.tool.iconId && (
          <img
            className={styles.background}
            src={ToolImage.url(run.tool.id, run.tool.iconId)}
          />
        )
      }
      <div
        className={classNames(styles.header, styles.notTransparentBackground)}
      >
        <StatusIcon
          className={styles.statusIcon}
          run={run}
        />
        {
          run.nodeCount > 0 && (
            <Icon type="database" />
          )
        }
        {
          run.serviceUrl && (
            <Icon type="export" />
          )
        }
        {run.podId}
      </div>
      <div
        className={classNames(styles.description, styles.notTransparentBackground)}
      >
        <div>
          Started: {displayDate(run.startDate, 'd MMMM YYYY, HH:mm')}
        </div>
        {
          run.endDate && (
            <div>
              Finished: {displayDate(run.endDate, 'd MMMM YYYY, HH:mm')}
            </div>
          )
        }
      </div>
      <div
        className={styles.attributes}
      >
        {
          run.tool && run.tool.iconId && (
            <img
              className={styles.dockerImageIcon}
              src={ToolImage.url(run.tool.id, run.tool.iconId)}
            />
          )
        }
        <span>{(run.dockerImage || '').split('/').slice(-1)[0]}</span>
        {
          run.instance && (
            <AWSRegionTag
              provider={run.instance.cloudProvider}
              style={{fontSize: 'larger'}}
            />
          )
        }
        {
          run.instance && (
            <span>
              {getSpotTypeName(run.instance.spot, run.instance.cloudProvider)}
            </span>
          )
        }
        {
          run.instance && (
            <span>
              {run.instance.nodeType}
            </span>
          )
        }
      </div>
    </a>
  );

  renderStorageCard = (storage) => (
    <a
      key={`storage-${storage.id}`}
      onClick={this.onNavigate(storage)}
      href={`/#${storage.url}`}
      className={
        classNames(
          styles.suggestion,
          styles.storage
        )
      }
    >
      <div
        className={classNames(styles.header, styles.notTransparentBackground)}
      >
        {
          /^nfs$/i.test(storage.type) && (
            <span
              className={classNames(styles.storageType, styles.nfs)}
            >
              NFS
            </span>
          )
        }
        <AWSRegionTag regionId={storage.regionId} />
        <span
          className={classNames({[styles.sensitive]: storage.sensitive})}
        >
          {storage.name}
        </span>
      </div>
      <div
        className={classNames(styles.description, styles.notTransparentBackground)}
      >
        {storage.pathMask}
      </div>
    </a>
  );

  renderSuggestionsDescription = (count, object, searchObject, group, title) => {
    const {onChangeDocumentType} = this.props;
    if (
      count > 0 &&
      group &&
      group.types &&
      group.types.length &&
      onChangeDocumentType
    ) {
      const changeFilter = (e) => {
        e.preventDefault();
        e.stopPropagation();
        onChangeDocumentType(group.types);
      };
      return (
        <div
          className={
            classNames(
              styles.suggestion,
              styles.navigation
            )
          }
        >
          <div>
            {
              title || (
                <span>
                  Suggested <b>{object.toLowerCase()}{count > 1 ? 's' : ''}</b> for you.
                </span>
              )
            }
          </div>
          <div>
            You can search {searchObject || `${object.toLowerCase()}s`} by
            <a
              onClick={changeFilter}
              className={styles.link}
            >
              specifying
              <span className={styles.button}>
                <Icon type={group.icon} /> {object}s
              </span>
              filter
            </a>
            .
          </div>
        </div>
      );
    }
    return undefined;
  };

  renderSuggestions = () => {
    return (
      <div>
        {
          this.topTools.length > 0 && (
            <div
              className={styles.suggestions}
            >
              {this.topTools.map(this.renderToolCard)}
              {
                this.renderSuggestionsDescription(
                  this.topTools.length,
                  'Tool',
                  'Tool',
                  SearchGroupTypes.tool
                )
              }
            </div>
          )
        }
        {
          this.topPipelines.length > 0 && (
            <div
              className={styles.suggestions}
            >
              {this.topPipelines.map(this.renderPipelineCard)}
              {
                this.renderSuggestionsDescription(
                  this.topPipelines.length,
                  this.localizedString('Pipeline'),
                  this.localizedString('Pipeline'),
                  SearchGroupTypes.pipeline
                )
              }
            </div>
          )
        }
        {
          this.topRuns.length > 0 && (
            <div
              className={styles.suggestions}
            >
              {this.topRuns.map(this.renderRunCard)}
              {
                this.renderSuggestionsDescription(
                  this.topRuns.length,
                  this.localizedString('Run'),
                  this.localizedString('Run'),
                  SearchGroupTypes.run,
                  (
                    <span>
                      {/* eslint-disable-next-line */}
                      Your last <b>{this.localizedString('run')}{this.topRuns.length > 1 ? 's' : ''}</b>.
                    </span>
                  )
                )
              }
            </div>
          )
        }
        {
          this.topStorages.length > 0 && (
            <div
              className={styles.suggestions}
            >
              {this.topStorages.map(this.renderStorageCard)}
              {
                this.renderSuggestionsDescription(
                  this.topStorages.length,
                  this.localizedString('Storage'),
                  `${this.localizedString('storage')}s and data`,
                  SearchGroupTypes.storage
                )
              }
            </div>
          )
        }
      </div>
    );
  };

  render () {
    const {authenticatedUserInfo} = this.props;
    const {runsLoaded} = this.state;
    if ((authenticatedUserInfo.pending && !authenticatedUserInfo.loaded) || !runsLoaded) {
      return (
        <LoadingView />
      );
    }
    const noSuggestions = this.topTools.length === 0 &&
      this.topPipelines.length === 0 &&
      this.topRuns.length === 0;
    return (
      <div>
        {
          !noSuggestions && this.renderSuggestions()
        }
        {
          noSuggestions && (
            <SearchHint />
          )
        }
      </div>
    );
  }
}

TopSuggestions.propTypes = {
  onChangeDocumentType: PropTypes.func,
  onNavigate: PropTypes.func
};

export default TopSuggestions;
