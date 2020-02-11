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
import {observer, inject} from 'mobx-react';
import {computed, observable} from 'mobx';
import {Link} from 'react-router';
import connect from '../../utils/connect';
import dockerRegistries from '../../models/tools/DockerRegistriesTree';
import ToolsGroupPrivateCreate from '../../models/tools/ToolsGroupPrivateCreate';
import LoadingView from '../special/LoadingView';
import DockerRegistriesActionsButton from './DockerRegistriesActionsButton';
import DockerRegistriesNavigation from './DockerRegistriesNavigation';
import ToolsTable from './ToolsTable';
import Metadata from '../special/metadata/Metadata';
import {
  ContentIssuesMetadataPanel,
  CONTENT_PANEL_KEY,
  METADATA_PANEL_KEY,
  ISSUES_PANEL_KEY
} from '../special/splitPanel/SplitPanel';
import Issues from '../special/issues/Issues';
import {Alert, Row, Button, Card, Icon, Col} from 'antd';
import roleModel from '../../utils/roleModel';
import styles from './Tools.css';
import ToolsGroupListWithIssues from '../../models/tools/ToolsGroupListWithIssues';

const findGroupByNameSelector = (name) => (group) => {
  return group.name.toLowerCase() === name.toLowerCase();
};
const findGroupByName = (groups, name) => {
  return groups.filter(findGroupByNameSelector(name))[0];
};

@roleModel.authenticationInfo
@connect({dockerRegistries})
@inject(({dockerRegistries}, {params}) => {
  return {
    dockerRegistries,
    registryId: params.registryId,
    groupId: params.groupId
  };
})
@observer
export default class ToolsNew extends React.Component {

  state = {
    metadata: false,
    redirected: false,
    search: null,
    createPrivateGroupInProgress: false,
    createPrivateGroupError: null,
    issuesItem: null,
    showIssuesPanel: false
  };

  @observable
  _toolsWithIssues;

  @computed
  get isPrivate () {
    return this.props.groupId === 'personal' || (this.currentGroup && this.currentGroup.privateGroup);
  }

  @computed
  get registries () {
    if (this.props.dockerRegistries.loaded) {
      return (this.props.dockerRegistries.value.registries || []).map(r => r);
    }
    return [];
  }

  @computed
  get currentRegistry () {
    return this.registries.filter(r => `${r.id}` === `${this.props.registryId}`)[0];
  }

  @computed
  get groups () {
    if (this.currentRegistry) {
      const groups = (this.currentRegistry.groups || []).map(g => g);
      if (this.currentRegistry.privateGroupAllowed && groups.filter(g => g.privateGroup).length === 0) {
        return [
          {
            id: 'personal',
            name: 'personal',
            privateGroup: true,
            missing: true
          }, ...groups
        ];
      } else {
        return groups;
      }
    }
    return [];
  }

  @computed
  get hasPersonalGroup () {
    return this.groups.filter(g => g.privateGroup && !g.missing).length === 1;
  }

  @computed
  get currentGroup () {
    return this.groups.filter(
      g => `${g.id}` === `${this.props.groupId}` || (g.privateGroup && this.props.groupId === 'personal')
    )[0];
  }

  @computed
  get defaultGroup () {
    return this.getDefaultGroup(this.groups, false);
  }

  @computed
  get tools () {
    if (this.currentGroup) {
      const checkIssues = (tool) => {
        const currentTool = tool;
        if (this._toolsWithIssues && this._toolsWithIssues.loaded) {
          const toolWithIssues = this._toolsWithIssues.value.toolsWithIssues
            .filter(t => t.id === currentTool.id)[0];
          if (toolWithIssues && toolWithIssues.hasOwnProperty('issuesCount')) {
            currentTool.issuesCount = toolWithIssues.issuesCount;
          }
        }
        return currentTool;
      };
      return (this.currentGroup.tools || [])
        .map(t => t)
        .filter(
          t => !this.state.search ||
          !this.state.search.length ||
          t.image.toLowerCase().indexOf(this.state.search.toLowerCase()) >= 0 ||
          (
            t.labels &&
            t.labels.filter(l => l.toLowerCase().indexOf(this.state.search.toLowerCase()) >= 0).length > 0
          )
        )
        .map(checkIssues);
    }
    return [];
  }

  @computed
  get globalSearchTools () {
    const tools = [];
    if (this.currentGroup) {
      (this.registries || []).forEach(
        r => r.groups && r.groups.forEach(
          g => g.tools && g.id && g.id !== this.currentGroup.id && g.tools.forEach(
            t => {
              if (this.state.search &&
                this.state.search.length &&
                (t.image.toLowerCase().indexOf(this.state.search.toLowerCase()) >= 0 ||
                  (t.labels && t.labels
                    .filter(
                      l => l.toLowerCase().indexOf(this.state.search.toLowerCase()) >= 0
                    ).length > 0))) {
                tools.push(t);
              }
            })));
    }
    return tools;
  }

  refresh = async () => {
    await this.props.dockerRegistries.fetch();
  };

  navigate = (registryId, groupId) => {
    if (!registryId) {
      this.props.router.push('/tools');
    } else {
      if (groupId) {
        this.props.router.push(`/tools/${registryId}/${groupId}`);
      } else {
        this.props.router.push(`/tools/${registryId}`);
      }
    }
  };

  navigateToTool = (id) => {
    this.props.router.push(`/tool/${id}`);
  };

  search = (searchCriteria) => {
    this.setState({search: searchCriteria});
  };

  openIssuesPanel = (item) => {
    this.setState({
      issuesItem: item,
      showIssuesPanel: true
    });
  };

  closeIssuesPanel = () => {
    this.setState({
      issuesItem: null,
      showIssuesPanel: false
    });
  };

  reloadIssues = (propsReplace = null) => {
    const props = propsReplace || this.props;
    if (props.groupId && +props.groupId > 0) {
      this._toolsWithIssues = new ToolsGroupListWithIssues(props.groupId);
    }
  };

  renderContent = (content, isError) => {
    if (this.currentGroup) {
      const onPanelClose = (key) => {
        switch (key) {
          case METADATA_PANEL_KEY:
            this.setState({metadata: false});
            break;
          case ISSUES_PANEL_KEY:
            this.closeIssuesPanel();
            break;
        }
      };
      return (
        <ContentIssuesMetadataPanel
          style={{flex: 1, overflow: 'auto'}}
          onPanelClose={onPanelClose}>
          <div
            key={CONTENT_PANEL_KEY}
            style={{
              overflowY: 'auto',
              height: '100%',
              flex: 1,
              display: 'flex',
              flexDirection: 'column'
            }}>
            {content}
          </div>
          {
            this.state.showIssuesPanel && this.state.issuesItem &&
            <Issues
              key={ISSUES_PANEL_KEY}
              onCloseIssuePanel={this.closeIssuesPanel}
              onReloadIssues={this.reloadIssues}
              entityId={this.state.issuesItem.id}
              entityClass="TOOL"
              entityDisplayName={this.state.issuesItem.image}
              entity={this.state.issuesItem} />
          }
          {
            this.state.metadata && this.currentGroup && !this.currentGroup.missing &&
            <Metadata
              key={METADATA_PANEL_KEY}
              readOnly={!this.currentGroup || !roleModel.isOwner(this.currentGroup)}
              entityId={
                this.currentGroup
                  ? this.currentGroup.id
                  : undefined
              } entityClass="TOOL_GROUP" />
          }
        </ContentIssuesMetadataPanel>
      );
    } else {
      const divStyle = {
        padding: 7,
        paddingRight: 12,
        overflowY: 'auto',
        position: 'relative',
        width: '100%'
      };
      if (!isError) {
        divStyle.display = 'flex';
        divStyle.flex = 1;
      }
      return (
        <div
          style={divStyle}>
          {content}
        </div>
      );
    }
  };

  renderHeader = () => {
    return (
      <Row id="tools-header" type="flex" justify="space-between" align="middle" style={{marginBottom: 5}}>
        <Col type="flex" style={{flex: 1}} className={styles.itemHeader}>
          <DockerRegistriesNavigation
            onNavigate={this.navigate}
            onSearch={this.search}
            searchString={this.state.search}
            currentGroup={this.currentGroup}
            currentRegistry={this.currentRegistry}
            registries={this.registries}
            groups={this.groups} />
        </Col>
        <Col type="flex" className={styles.currentFolderActions}>
          {
            this.currentGroup && !this.currentGroup.missing &&
            <Button
              size="small"
              id={this.state.metadata ? 'hide-metadata-button' : 'show-metadata-button'}
              onClick={() => this.setState({metadata: !this.state.metadata})}>
              <span style={{lineHeight: 'inherit', verticalAlign: 'middle'}}>
                {this.state.metadata ? 'Hide attributes' : 'Show attributes'}
              </span>
            </Button>
          }
          <DockerRegistriesActionsButton
            docker={this.props.dockerRegistries.value}
            registry={this.currentRegistry}
            group={this.currentGroup}
            hasPersonalGroup={this.hasPersonalGroup}
            onRefresh={this.refresh}
            onNavigate={this.navigate}
            router={this.props.router}
          />
        </Col>
      </Row>
    );
  };

  _createPrivateGroup = () => {
    this.setState({
      createPrivateGroupInProgress: true,
      createPrivateGroupError: null
    }, async () => {
      const request = new ToolsGroupPrivateCreate(this.props.registryId);
      await request.send({});
      if (!request.error) {
        await this.refresh();
      }
      this.setState({
        createPrivateGroupInProgress: false,
        createPrivateGroupError: request.error
      });
    });
  };

  defaultGroupLinkMessage = () => {
    if (this.defaultGroup) {
      const link = (
        <Link to={`/tools/${this.currentRegistry.id}/${this.defaultGroup.id}`}>
          {this.defaultGroup.name}
        </Link>
      );
      return (
        <span>
            You can explore {link} repository
          </span>
      );
    }
    return null;
  };

  renderCreatePrivateToolBody = () => {
    const navigateToDefaultGroupMessage = () => {
      const message = this.defaultGroupLinkMessage();
      if (message) {
        return (
          <Row style={{fontSize: 'larger', marginBottom: 10}}>
            {message}
          </Row>
        );
      }
      return null;
    };
    return (
      <Row type="flex" className={styles.container}>
        <Row
          type="flex"
          align="middle"
          justify="center"
          className={styles.privateToolNotFoundContainer}>
          <Row style={{fontSize: 'large', margin: 10}}>
            <Icon
              type="info-circle-o"
              style={{fontSize: 'x-large', verticalAlign: 'middle'}} />
            <span
              style={{marginLeft: 10, marginRight: 10}}>
              Personal tool group was not found in registry.
            </span>
          </Row>
          {navigateToDefaultGroupMessage()}
          {
            this.state.createPrivateGroupError
              ? <Row
                style={{fontSize: 'larger', marginBottom: 10, color: 'red'}}>
                {this.state.createPrivateGroupError}
              </Row>
              : undefined
          }
          {
            this.currentRegistry.privateGroupAllowed &&
            <Button type="primary" onClick={this._createPrivateGroup}>
              CREATE PERSONAL TOOL GROUP
            </Button>
          }
        </Row>
      </Row>
    );
  };

  render () {
    if (this.props.dockerRegistries.pending && !this.props.dockerRegistries.loaded) {
      return <LoadingView />;
    }
    let isError = false;
    let content;
    if (this.props.dockerRegistries.error) {
      content = <Alert
        style={{width: '100%'}}
        type="error"
        message={this.props.dockerRegistries.error} />;
      isError = true;
    } else if (this.registries.length === 0) {
      content = <Alert
        style={{width: '100%'}}
        type="warning"
        message="No registries configured" />;
      isError = true;
    } else if (this.currentRegistry && this.groups.length === 0) {
      content = <Alert
        style={{width: '100%'}}
        type="warning"
        message="No groups configured" />;
      isError = true;
    } else if (this.state.createPrivateGroupInProgress) {
      content = <LoadingView />;
    } else if (this.isPrivate && this.currentGroup && this.currentGroup.missing) {
      content = this.renderCreatePrivateToolBody();
    } else if (this.currentGroup && (this.currentGroup.tools || []).length === 0) {
      const noToolsMessage = () => {
        const defaultGroupLink = this.defaultGroupLinkMessage();
        if (this.isPrivate && defaultGroupLink) {
          return (
            <span>No tools found. {defaultGroupLink}</span>
          );
        }
        return 'No tools found';
      };
      content = <Alert
        style={{width: '100%'}}
        type="info"
        message={noToolsMessage()} />;
      isError = true;
    } else {
      content = <ToolsTable
        style={{flex: 1}}
        tools={this.tools}
        globalSearchTools={this.globalSearchTools}
        onSelectTool={this.navigateToTool}
        onOpenIssuesPanel={(tool) => {
          if (this.state.showIssuesPanel && tool.id === this.state.issuesItem.id) {
            this.closeIssuesPanel();
          } else {
            this.openIssuesPanel(tool);
          }
        }}
      />;
    }
    return (
      <Card
        className={styles.toolsCard}
        bodyStyle={{padding: 5, height: '100%', display: 'flex', flexDirection: 'column'}}>
        {this.renderHeader()}
        {this.renderContent(content, isError)}
      </Card>
    );
  }

  getDefaultGroup = (groups, usePersonal = false) => {
    const {authenticatedUserInfo} = this.props;
    if (authenticatedUserInfo && authenticatedUserInfo.loaded) {
      const adGroups = (authenticatedUserInfo.value.groups || []).map(g => g);
      const performGroupName = (groupName) => {
        if (groupName && groupName.toLowerCase().indexOf('role_') === 0) {
          return groupName.substring('role_'.length);
        }
        return groupName;
      };
      const rolesGroups = (authenticatedUserInfo.value.roles || []).map(r => performGroupName(r.name));
      const candidates = [...adGroups, ...rolesGroups];
      for (let i = 0; i < candidates.length; i++) {
        const group = findGroupByName(groups, candidates[i]);
        if (group) {
          return group;
        }
      }
    }
    const personalGroup = usePersonal
      ? ((groups || []).filter(g => g.privateGroup)[0] || {id: 'personal'})
      : null;
    return findGroupByName(groups, 'library') ||
      findGroupByName(groups, 'default') ||
      personalGroup ||
      groups.filter(g => !g.missing)[0];
  };

  redirectIfNeeded = () => {
    if (!this.state.redirected &&
      this.props.dockerRegistries.loaded &&
      (!this.currentRegistry || !this.currentGroup)) {
      if (!this.currentRegistry) {
        let registryToRedirect = this.props.registryId || (this.registries[0] ? this.registries[0].id : undefined);
        const [registry] = this.registries.filter(r => `${r.id}` === `${registryToRedirect}`);
        if (registry) {
          const groupToRedirect = this.getDefaultGroup(
            (registry.groups || []).map(g => g),
            registry.privateGroupAllowed
          );
          if (groupToRedirect) {
            this.props.router.push(`/tools/${registry.id}/${groupToRedirect.id}`);
          } else {
            this.props.router.push(`/tools/${registry.id}`);
          }
        } else {
          this.setState({
            redirected: true
          });
        }
      } else {
        const groupToRedirect = this.getDefaultGroup(
          (this.groups || []).map(g => g),
          this.currentRegistry.privateGroupAllowed
        );
        if (groupToRedirect) {
          this.props.router.push(`/tools/${this.currentRegistry.id}/${groupToRedirect.id}`);
        } else {
          this.setState({
            redirected: true
          });
        }
      }
    }
  };

  componentWillMount () {
    this.props.dockerRegistries.fetch();
    this.reloadIssues();
  }

  componentDidMount () {
    this.redirectIfNeeded();
  }

  componentDidUpdate (prevProps) {
    this.redirectIfNeeded();
  }

  componentWillReceiveProps (nextProps) {
    if (nextProps.registryId !== this.props.registryId ||
      nextProps.groupId !== this.props.groupId) {
      this.setState({
        redirected: false
      });
    }
    if (this.props.groupId !== nextProps.groupId) {
      this.reloadIssues(nextProps);
    }
  }

  componentWillUnmount () {
    this.props.dockerRegistries.invalidateCache();
  }
}
