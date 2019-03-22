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
import {Alert, Row} from 'antd';
import LoadingView from '../../../special/LoadingView';
import CardsPanel from './components/CardsPanel';
import highlightText from '../../../special/highlightText';
import roleModel from '../../../../utils/roleModel';
import {getDisplayOnlyFavourites} from '../utils/favourites';
import localization from '../../../../utils/localization';
import styles from './Panel.css';

const MAX_TAGS = 5;

@roleModel.authenticationInfo
@inject('projects')
@localization.localizedComponent
@observer
export default class MyProjectsPanel extends localization.LocalizedReactComponent {

  static propTypes = {
    panelKey: PropTypes.string,
    onInitialize: PropTypes.func
  };

  searchProjectFn = (project, search) => {
    return !search || search.length === 0 ||
      project.name.toLowerCase().indexOf(search.toLowerCase()) >= 0 ||
      project.tags.filter(t => (t.value || '').toLowerCase().indexOf(search.toLowerCase()) >= 0).length > 0;
  };

  @computed
  get projects () {
    if (this.props.projects.loaded) {
      return (this.props.projects.value.childFolders || []).map(project => {
        return {
          ...project,
          tags: (() => {
            const result = [];
            for (let key in project.data) {
              if (project.data.hasOwnProperty(key)) {
                result.push({key, value: project.data[key].value});
              }
            }
            return result;
          })()
        };
      });
    }
    return [];
  }

  renderProject = (project, search) => {
    const renderTag = (tag, index) => {
      return (
        <div className={styles.projectTag} key={index}>
          <Row className={styles.projectTagKey}>{tag.key}</Row>
          <Row className={styles.projectTagValue}>{highlightText(tag.value, search)}</Row>
        </div>
      );
    };
    const tagsToRender = [];
    for (let i = 0; i < Math.min(MAX_TAGS, project.tags.length); i++) {
      tagsToRender.push(project.tags[i]);
    }
    const moreTags = project.tags.length - MAX_TAGS;
    return [
      <Row
        key="name"
        type="flex"
        align="middle"
        style={{fontWeight: 'bold', height: project.tags.length > 0 ? undefined : '100%'}}>
        <span type="main">{highlightText(project.name, search)}</span>
      </Row>,
      <Row
        key="tags"
        type="flex"
        justify="start">
        {tagsToRender.map(renderTag)}
        {
          moreTags > 0
            ? <span style={{fontStyle: 'italic'}}>+{moreTags} more tags</span>
            : undefined
        }
      </Row>
    ];
  };

  getProjectActions = (project) => {
    const history = ({id}) => {
      this.props.router && this.props.router.push(`/folder/${id}/history`);
    };
    const actions = [
      {
        title: 'History',
        icon: 'compass',
        action: history
      }
    ];
    if (project.storages && (project.storages || []).length > 0) {
      const storages = (project.storages || []).map(s => s);
      if (storages.length === 1) {
        actions.push({
          title: 'Data storage',
          icon: 'hdd',
          action: () => this.props.router && this.props.router.push(`/storage/${storages[0].id}`)
        });
      } else {
        actions.push({
          title: 'Data storages',
          icon: 'hdd',
          overlay: (
            <div style={{display: 'flex', flexDirection: 'column'}}>
              {
                storages.map((storage, index) => {
                  return (
                    <Row key={index}>
                      <a onClick={() => {
                        this.props.router && this.props.router.push(`/storage/${storage.id}`);
                      }}>{storage.name}</a>
                    </Row>
                  );
                })
              }
            </div>
          )
        });
      }
    }
    return actions;
  };

  renderContent = () => {
    const navigate = ({id}) => {
      this.props.router && this.props.router.push(`/folder/${id}`);
    };
    return (
      <div key="cards" style={{flex: 1, overflow: 'auto'}}>
        <CardsPanel
          panelKey={this.props.panelKey}
          onClick={navigate}
          favouriteEnabled
          displayOnlyFavourites={getDisplayOnlyFavourites()}
          search={{
            placeholder: 'Search projects',
            searchFn: this.searchProjectFn
          }}
          emptyMessage={search =>
            search && search.length
              ? `No projects found for '${search}'`
              : 'There are no projects you have access to'
          }
          actions={this.getProjectActions}
          cardClassName={styles.projectCard}
          cardStyle={{width: '100%'}}
          childRenderer={this.renderProject}>
          {this.projects}
        </CardsPanel>
      </div>
    );
  };

  render () {
    if (!this.props.projects.loaded && this.props.projects.pending) {
      return <LoadingView />;
    }
    if (this.props.projects.error) {
      return <Alert type="warning" message={this.props.projects.error} />;
    }
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return <LoadingView />;
    }
    if (this.props.authenticatedUserInfo.error) {
      return (<Alert type="warning" message={this.props.authenticatedUserInfo.error} />);
    }
    return (
      <div className={styles.container} style={{display: 'flex', flexDirection: 'column'}}>
        {this.renderContent()}
      </div>
    );
  }

  update () {
    this.forceUpdate();
  }

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
    this.props.projects.fetch();
  }
}
