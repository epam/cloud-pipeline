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
import {Link} from 'react-router';
import {computed} from 'mobx';
import IssueCommentPreview from '../../../special/issues/controls/IssueCommentPreview';
import LoadingView from '../../../special/LoadingView';
import localization from '../../../../utils/localization';
import {Alert, Card, Icon, Row} from 'antd';
import roleModel from '../../../../utils/roleModel';
import styles from './Panel.css';
import moment from 'moment';

const NEW_ISSUE_EVENT = 'new issue';
const UPDATE_ISSUE_EVENT = 'update issue';
const NEW_COMMENT_EVENT = 'new comment';
const UPDATE_COMMENT_EVENT = 'update comment';

@roleModel.authenticationInfo
@localization.localizedComponent
@inject('pipelinesLibrary', 'dockerRegistries', 'myIssues')
@inject((stores, parameters) => ({
  issues: stores.myIssues
}))
@observer
export default class ActivitiesPanel extends localization.LocalizedReactComponent {

  static propTypes = {
    onInitialize: PropTypes.func
  };

  findElement = (id, type) => {
    if (type === 'TOOL') {
      const registries = (this.props.dockerRegistries.value.registries || []).map(r => r);
      for (let i = 0; i < registries.length; i++) {
        const registry = registries[i];
        const groups = (registry.groups || []).map(g => g);
        for (let j = 0; j < groups.length; j++) {
          const group = groups[j];
          const tools = (group.tools || []).map(t => t);
          for (let t = 0; t < tools.length; t++) {
            const tool = tools[t];
            if (`${tool.id}` === `${id}`) {
              const parts = tool.image.split('/');
              return {
                name: parts[parts.length - 1],
                url: `/tool/${id}`,
                type
              };
            }
          }
        }
      }
    } else {
      const findEntity = ({storages, childFolders, pipelines, configurations}) => {
        switch (type) {
          case 'PIPELINE':
            for (let i = 0; i < (pipelines || []).length; i++) {
              if (`${pipelines[i].id}` === `${id}`) {
                return {
                  name: pipelines[i].name,
                  url: `/${id}`,
                  type
                };
              }
            }
            break;
          case 'CONFIGURATION':
            for (let i = 0; i < (configurations || []).length; i++) {
              if (`${configurations[i].id}` === `${id}`) {
                return {
                  name: configurations[i].name,
                  url: `/${id}`,
                  type
                };
              }
            }
            break;
          case 'FOLDER':
            for (let i = 0; i < (childFolders || []).length; i++) {
              if (`${childFolders[i].id}` === `${id}`) {
                return {
                  name: childFolders[i].name,
                  url: `/folder/${id}`,
                  type
                };
              }
            }
            break;
          case 'DATA_STORAGE':
            for (let i = 0; i < (storages || []).length; i++) {
              if (`${storages[i].id}` === `${id}`) {
                return {
                  name: storages[i].name,
                  url: `/storage/${id}`,
                  type
                };
              }
            }
            break;
        }
        for (let i = 0; i < (childFolders || []).length; i++) {
          let result = findEntity(childFolders[i]);
          if (result) {
            return result;
          }
        }
        return null;
      };
      return findEntity(this.props.pipelinesLibrary.value);
    }
  };

  @computed
  get issuesEvents () {
    if (this.props.issues.loaded) {
      const events = [];
      const issues = (this.props.issues.value.elements || []).map(i => i);
      for (let i = 0; i < issues.length; i++) {
        const entity = this.findElement(issues[i].entity.entityId, issues[i].entity.entityClass);
        const initialEvent = {
          type: NEW_ISSUE_EVENT,
          date: issues[i].createdDate,
          object: issues[i],
          entity
        };
        if (issues[i].createdDate !== issues[i].updatedDate) {
          events.push({
            type: UPDATE_ISSUE_EVENT,
            date: issues[i].updatedDate,
            object: issues[i],
            entity
          });
          initialEvent.modified = true;
        }
        events.push(initialEvent);
        const comments = (issues[i].comments || []).map(c => c);
        for (let j = 0; j < comments.length; j++) {
          const initialCommentEvent = {
            type: NEW_COMMENT_EVENT,
            date: comments[j].createdDate,
            object: comments[j],
            parent: issues[i],
            entity
          };
          if (comments[j].createdDate !== comments[j].updatedDate) {
            events.push({
              type: UPDATE_COMMENT_EVENT,
              date: comments[j].updatedDate,
              object: comments[j],
              parent: issues[i],
              entity
            });
            initialCommentEvent.modified = true;
          }
          events.push(initialCommentEvent);
        }
      }
      events.sort((eA, eB) => {
        const dateA = moment(eA.date);
        const dateB = moment(eB.date);
        if (dateA < dateB) {
          return 1;
        } else if (dateA > dateB) {
          return -1;
        }
        return 0;
      });
      return events;
    }
    return [];
  }

  renderEvent = (event) => {
    let title;
    let content;
    let parent;
    let owner = event.object.author;
    if (this.props.authenticatedUserInfo.loaded &&
      owner.toLowerCase() === this.props.authenticatedUserInfo.value.userName.toLowerCase()) {
      owner = 'You';
    }
    if (event.entity) {
      let name, type;
      switch (event.entity.type) {
        case 'PIPELINE':
          name = <span><Icon type="fork" /> {event.entity.name}</span>;
          type = this.localizedString('pipeline');
          break;
        case 'TOOL':
          name = <span><Icon type="tool" /> {event.entity.name}</span>;
          type = this.localizedString('tool');
          break;
        case 'FOLDER':
          name = <span><Icon type="folder" /> {event.entity.name}</span>;
          type = this.localizedString('folder');
          break;
        case 'DATA_STORAGE':
          name = <span><Icon type="hdd" /> {event.entity.name}</span>;
          type = this.localizedString('storage');
          break;
        case 'CONFIGURATION':
          name = <span><Icon type="setting" /> {event.entity.name}</span>;
          type = this.localizedString('configuration');
          break;
      }
      if (name) {
        parent = <span> for <b>{type} <Link to={event.entity.url}>{name}</Link></b></span>;
      }
    }
    let modified;
    if (event.modified) {
      modified = <i> (modified)</i>;
    }
    switch (event.type) {
      case NEW_ISSUE_EVENT:
        title = (
          <Row>
            <b>{owner}</b> created new {this.localizedString('issue')} <b>{event.object.name}</b>{parent} {moment.utc(event.date).fromNow(false)}{modified}:
          </Row>
        );
        content = (
          <IssueCommentPreview
            style={{padding: 0}}
            text={event.object.text} />
        );
        break;
      case NEW_COMMENT_EVENT:
        title = (
          <Row>
            <b>{owner}</b> commented {this.localizedString('issue')} <b>{event.parent.name}</b>{parent} {moment.utc(event.date).fromNow(false)}{modified}:
          </Row>);
        content = (
          <IssueCommentPreview
            style={{padding: 0, fontSize: 'small'}}
            text={event.object.text} />
        );
        break;
      case UPDATE_ISSUE_EVENT:
        title = (
          <Row>
            <b>{owner}</b> updated an {this.localizedString('issue')} <b>{event.object.name}</b>{parent} {moment.utc(event.date).fromNow(false)}{modified}:
          </Row>
        );
        content = (
          <IssueCommentPreview
            style={{padding: 0}}
            text={event.object.text} />
        );
        break;
      case UPDATE_COMMENT_EVENT:
        title = (
          <Row>
            <b>{owner}</b> updated comment for {this.localizedString('issue')} <b>{event.parent.name}</b>{parent} {moment.utc(event.date).fromNow(false)}{modified}:
          </Row>
        );
        content = (
          <IssueCommentPreview
            style={{padding: 0, fontSize: 'small'}}
            text={event.object.text} />
        );
        break;
    }
    return [
      <Row type="flex" key="title" style={{backgroundColor: '#fafafa', padding: 5}}>{title}</Row>,
      <Row type="flex" key="content" style={{padding: 5}}>{content}</Row>
    ];
  };

  renderContent = () => {
    if (this.issuesEvents.length === 0) {
      return <Row type="flex">There are no recent activities.</Row>;
    }
    let lastVisited = localStorage.getItem('LAST_VISITED');
    if (lastVisited) {
      lastVisited = moment.utc(lastVisited);
    }
    return (
      <div key="cards" style={{flex: 1, overflow: 'auto'}}>
        {
          this.issuesEvents.map((event, index) => {
            const isNew = !lastVisited || lastVisited < moment.utc(event.date);
            return (
              <Card
                className={isNew ? styles.newNotification : undefined}
                key={index}
                bodyStyle={{padding: 0}} style={{margin: 2, marginBottom: 5}}>
                {this.renderEvent(event)}
              </Card>
            );
          })
        }
      </div>
    );
  };

  render () {
    if (!this.props.issues.loaded && this.props.issues.pending) {
      return <LoadingView />;
    }
    if (this.props.issues.error) {
      return <Alert type="warning" message={this.props.issues.error} />;
    }
    if (!this.props.pipelinesLibrary.loaded && this.props.pipelinesLibrary.pending) {
      return <LoadingView />;
    }
    if (this.props.pipelinesLibrary.error) {
      return <Alert type="warning" message={this.props.pipelinesLibrary.error} />;
    }
    if (!this.props.dockerRegistries.loaded && this.props.dockerRegistries.pending) {
      return <LoadingView />;
    }
    if (this.props.dockerRegistries.error) {
      return <Alert type="warning" message={this.props.dockerRegistries.error} />;
    }
    if (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending) {
      return <LoadingView />;
    }
    if (this.props.authenticatedUserInfo.error) {
      return (<Alert type="warning" message={this.props.authenticatedUserInfo.error} />);
    }
    return (
      <div className={styles.container}>
        {this.renderContent()}
      </div>
    );
  }

  update () {
    this.forceUpdate();
  }

  componentDidMount () {
    this.props.onInitialize && this.props.onInitialize(this);
  }
}
