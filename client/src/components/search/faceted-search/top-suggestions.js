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
import PipelineRunFilter from '../../../models/pipelines/PipelineRunFilter';
import {SearchHint} from './controls';
import roleModel from '../../../utils/roleModel';
import LoadingView from '../../special/LoadingView';
import ToolImage from '../../../models/tools/ToolImage';
import displayDate from '../../../utils/displayDate';
import localization from '../../../utils/localization';
import {SearchGroupTypes} from '../searchGroupTypes';
import styles from './top-suggestions.css';

const TOP_SUGGESTION_SECTION_LENGTH = 5;

@inject('pipelines', 'dockerRegistries')
@roleModel.authenticationInfo
@localization.localizedComponent
@observer
class TopSuggestions extends localization.LocalizedReactComponent {
  state = {
    runs: [],
    runsLoaded: false
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
        lastRun: runs.filter(run => tool.imageRegExp.test(run.dockerImage))[0]
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
        lastRun: runs.filter(run => pipeline.id === run.pipelineId)[0]
      }))
      .slice(0, TOP_SUGGESTION_SECTION_LENGTH)
      .sort((a, b) => b.hits - a.hits);
  }

  get topRuns () {
    const {runs} = this.state;
    return runs.slice(0, TOP_SUGGESTION_SECTION_LENGTH);
  }

  loadRuns = () => {
    const {runsLoaded} = this.state;
    const {authenticatedUserInfo} = this.props;
    if (!runsLoaded && authenticatedUserInfo.loaded) {
      const request = new PipelineRunFilter({}, true);
      request.filter({
        page: 1,
        pageSize: 100,
        userModified: false,
        owners: [this.props.authenticatedUserInfo.value.userName]
      })
        .then(() => {
          if (request.loaded) {
            this.setState({
              runs: (request.value || []),
              runsLoaded: true
            });
          }
        })
        .catch(() => {
          this.setState({
            runsLoaded: true
          });
        });
    }
  };

  onNavigate = (event) => {
    event && event.preventDefault();
    event && event.stopPropagation();
  }

  renderLastRun = (obj) => {
    const {lastRun} = obj || {};
    if (lastRun) {
      const date = lastRun.endDate || lastRun.createdDate;
      return `Last ran at ${displayDate(date, 'd MMMM YYYY, HH:mm')}`;
    }
    return undefined;
  };

  renderToolCard = (tool) => (
    <a
      onClick={this.onNavigate}
      className={styles.cardLinkWrapper}
    >
      <div
        key={tool.key}
        className={classNames(styles.suggestion, styles.tool)}
      >
        {
          tool.iconId && (
            <div className={styles.backgroundWrapper}>
              <img
                className={styles.background}
                src={ToolImage.url(tool.id, tool.iconId)} />
            </div>
          )
        }
        <div className={styles.header}>
          {
            tool.iconId && (
              <img
                className={styles.toolIcon}
                src={ToolImage.url(tool.id, tool.iconId)} />
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
              <Icon type="caret-right" />
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
      </div>
    </a>
  );

  renderPipelineCard = (pipeline) => (
    <a
      onClick={this.onNavigate}
      className={styles.cardLinkWrapper}
    >
      <div
        key={`pipeline-${pipeline.id}`}
        className={
          classNames(
            styles.suggestion,
            styles.pipeline
          )
        }
      >
        {
          pipeline.tool && pipeline.tool.iconId && (
            <div className={styles.backgroundWrapper}>
              <img
                className={styles.background}
                src={ToolImage.url(pipeline.tool.id, pipeline.tool.iconId)} />
            </div>
          )
        }
        <div
          className={classNames(styles.header, styles.notTransparentBackground)}
        >
          {pipeline.name} <span className={styles.postfix}>{this.localizedString('pipeline')}</span>
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
      </div>
    </a>
  );

  renderSuggestionsDescription = (count, object, group) => {
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
            Suggested <b>{object.toLowerCase()}{count > 1 ? 's' : ''}</b> for you.
          </div>
          <div>
            You can search {object.toLowerCase()}s by
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
                  SearchGroupTypes.pipeline
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
    console.log(this.topTools, this.topPipelines, this.topRuns);
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
  onChangeDocumentType: PropTypes.func
};

export default TopSuggestions;
