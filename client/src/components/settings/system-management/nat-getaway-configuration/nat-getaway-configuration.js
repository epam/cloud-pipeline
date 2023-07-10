/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  Button,
  Spin,
  Table,
  message,
  Tooltip,
  Input
} from 'antd';
import classNames from 'classnames';

import {NATRules, DeleteRules, SetRules} from '../../../../models/nat';
import AddRouteForm from './add-route-modal/add-route-modal';
import NATRouteStatuses from './route-statuses';
import {columns} from './helpers';
import * as portUtilities from './ports-utilities';
import highlightText from '../../../special/highlightText';
import styles from './nat-getaway-configuration.css';

const {Column, ColumnGroup} = Table;

function getRouteIdentifier (route) {
  if (!route) {
    return undefined;
  }
  const {
    externalIp,
    internalIp,
    grouped = false,
    externalPort,
    isNew,
    protocol
  } = route;
  return [
    externalIp,
    protocol,
    isNew ? 'new' : (internalIp || ''),
    grouped ? undefined : externalPort
  ]
    .filter(Boolean)
    .join('|');
}

function routesSorter (a, b) {
  const {externalName: nameA} = a;
  const {externalName: nameB} = b;
  return nameA.toString().localeCompare(nameB.toString());
}

function collapseColumn (content = '') {
  return {
    children: content,
    props: {
      colSpan: 0
    }
  };
};

export default class NATGetaway extends React.Component {
  state = {
    addedRoutes: [],
    removedRoutes: [],
    routes: [],
    addRouteModalIsOpen: false,
    pending: false,
    expandedRowKeys: [],
    search: undefined
  }

  tableColumnsAmount;

  get sortedContent () {
    const {
      routes = [],
      addedRoutes = []
    } = this.state;
    const routeIsTouched = (route = {}) => {
      return Boolean(route.isNew) ||
        this.routeIsRemoved(route) ||
        (route.routes || []).some(r => this.routeIsRemoved(r));
    };
    const sorted = [
      ...portUtilities.groupRoutes(routes),
      ...portUtilities.groupRoutes(addedRoutes)
    ].map(route => ({
      ...route,
      isTouched: routeIsTouched(route)
    })).sort(routesSorter);
    const important = sorted.filter(r => r.isTouched || r.status !== NATRouteStatuses.ACTIVE);
    const rest = sorted.filter(r => !r.isTouched && r.status === NATRouteStatuses.ACTIVE);
    return [
      ...important,
      important.length > 0 && rest.length > 0
        ? {divider: true, description: '', key: 'divider2'}
        : undefined,
      ...rest
    ].filter(Boolean);
  }

  get filteredAndSortedContent () {
    const {search} = this.state;
    if (!search) {
      return this.sortedContent;
    }
    const searchFilter = (route = {}, search = '') => {
      const {
        divider,
        routes,
        isTouched
      } = route;
      if (divider || isTouched) {
        return true;
      }
      const checkStringifiedProperty = (property, search) => {
        if (property) {
          return `${property}`.toLowerCase().includes(search.toLowerCase());
        }
        return false;
      };
      const checkPorts = (ports = [], search) => {
        if (typeof ports === 'string' || typeof ports === 'number') {
          return checkStringifiedProperty(ports, search);
        }
        if (Array.isArray(ports)) {
          return ports.some(port => checkStringifiedProperty(port, search));
        }
        return false;
      };
      if (routes && routes.length > 0) {
        return routes.some(route => {
          const {
            externalIp,
            externalName,
            internalIp,
            internalName,
            protocol,
            externalPort,
            internalPort,
            description
          } = route;
          return checkStringifiedProperty(externalIp, search) ||
          checkStringifiedProperty(externalName, search) ||
          checkPorts(externalPort, search) ||
          checkStringifiedProperty(internalIp, search) ||
          checkStringifiedProperty(internalName, search) ||
          checkPorts(internalPort, search) ||
          checkStringifiedProperty(protocol, search) ||
          checkStringifiedProperty(description, search);
        });
      }
      return false;
    };
    return this.sortedContent.filter(route => searchFilter(route, search));
  }

  get hasChildRoutes () {
    return this.filteredAndSortedContent.some(o => o.children && o.children.length > 0);
  }

  get tableContentChanged () {
    const {addedRoutes = [], removedRoutes = []} = this.state;
    return addedRoutes.length > 0 || removedRoutes.length > 0;
  }

  componentDidMount () {
    this.loadRoutes();
  }

  componentDidUpdate () {
    this.props.handleModified(this.tableContentChanged);
  }

  componentWillUnmount () {
    this.props.handleModified(false);
  }

  loadRoutes = () => {
    this.setState({pending: true}, async () => {
      const state = {
        pending: false
      };
      try {
        const natRules = new NATRules();
        await natRules.fetch();
        if (natRules.error) {
          throw new Error(natRules.error);
        } else if (natRules.loaded && natRules.value) {
          state.routes = natRules.value.map(v => ({...v})) || [];
        } else {
          state.routes = [];
        }
      } catch (e) {
        message.error(e.message, 5);
      } finally {
        this.setState(state);
      }
    });
  };

  removeRoute = (route) => {
    const {addedRoutes = [], removedRoutes = []} = this.state;
    const {
      externalIp,
      externalPort,
      isNew,
      grouped,
      routes = []
    } = route;
    if (isNew) {
      const ports = grouped ? routes.map(o => o.externalPort) : [externalPort];
      const state = {
        addedRoutes: addedRoutes
          .filter(r => !(r.externalIp === externalIp && ports.includes(r.externalPort)))
      };
      if (grouped) {
        const {expandedRowKeys = []} = this.state;
        state.expandedRowKeys = expandedRowKeys.filter(key => key !== getRouteIdentifier(route));
      }
      this.setState(state);
    } else {
      const routesToRemove = grouped ? routes : [route];
      this.setState({
        removedRoutes: [
          ...removedRoutes,
          ...routesToRemove
        ]
      });
    }
  };

  revertRoute = (route) => {
    const {
      externalIp,
      externalPort,
      grouped,
      routes = []
    } = route;
    const {removedRoutes = []} = this.state;
    if (grouped) {
      const ports = routes.map(subRoute => subRoute.externalPort);
      this.setState({
        removedRoutes: removedRoutes
          .filter(r => !(r.externalIp === externalIp && ports.includes(r.externalPort)))
      });
    } else {
      this.setState({
        removedRoutes: removedRoutes
          .filter(r => !(r.externalIp === externalIp && r.externalPort === externalPort))
      });
    }
  };

  routeIsRemoved = (route) => {
    const {
      externalIp,
      externalPort,
      grouped,
      routes = []
    } = route;
    if (grouped) {
      const removed = routes
        .filter(subRoute => this.routeIsRemoved(subRoute))
        .length;
      return removed === routes.length;
    }
    const {removedRoutes = []} = this.state;
    return !!removedRoutes
      .find(r => r.externalIp === externalIp && r.externalPort === externalPort);
  };

  addNewDataToTable = async (formData) => {
    const {serverName, ip, ports = [], description} = formData;
    const formattedData = ports.map(({port, protocol}) => ({
      externalName: serverName,
      externalIp: ip,
      externalPort: port,
      protocol,
      isNew: true,
      description
    }));
    const {addedRoutes = []} = this.state;
    this.setState({
      addedRoutes: [
        ...addedRoutes,
        ...formattedData
      ]
    });
  };

  openAddRouteModal = () => {
    this.setState({
      addRouteModalIsOpen: true
    });
  };

  closeAddRouteModal = () => {
    this.setState({
      addRouteModalIsOpen: false
    });
  };

  onSave = () => {
    this.setState({
      pending: true
    }, async () => {
      try {
        const {
          addedRoutes = [],
          removedRoutes = []
        } = this.state;
        const routesToRemove = removedRoutes
          .map(route => ({
            externalName: route.externalName,
            externalIp: route.externalIp,
            port: route.externalPort,
            protocol: route.protocol
          }));
        const routesToAdd = addedRoutes
          .map(route => ({
            externalName: route.externalName,
            externalIp: route.externalIp,
            port: route.externalPort,
            protocol: route.protocol,
            description: route.description
          }));
        if (routesToRemove.length) {
          const deleteRequest = new DeleteRules();
          await deleteRequest.send({rules: routesToRemove});
          if (deleteRequest.error) {
            message.error(deleteRequest.error, 5);
          }
        }
        if (routesToAdd.length) {
          const saveRequest = new SetRules();
          await saveRequest.send({rules: routesToAdd});
          if (saveRequest.error) {
            message.error(saveRequest.error, 5);
          }
        }
      } catch (e) {
        console.error(e.message, 5);
      } finally {
        this.setState({
          pending: false,
          addedRoutes: [],
          removedRoutes: []
        }, () => this.loadRoutes());
      }
    });
  };

  onRevert = () => {
    this.setState({
      addedRoutes: [],
      removedRoutes: []
    });
  };

  onRefreshTable = () => {
    this.loadRoutes();
  };

  onChangeExpandedRows = (expandedRows) => {
    this.setState({
      expandedRowKeys: expandedRows
    });
  };

  onSearchChange = (e) => {
    this.setState({
      search: e.target.value
    });
  };

  clearSearch = () => {
    this.setState({search: undefined});
  };

  renderColumnContent = column => (text, record, index) => {
    const {search} = this.state;
    const {renderer = (o) => o} = column;
    if (record.divider) {
      return collapseColumn(text);
    }
    if (!text) {
      return '';
    }
    return renderer(text, record, index, search);
  };

  render () {
    const {
      pending,
      expandedRowKeys = [],
      search
    } = this.state;
    return (
      <div
        className={styles.container}
      >
        <div
          style={{
            position: 'relative',
            flex: '0 1 auto',
            overflow: 'auto'
          }}
        >
          <Input.Search
            placeholder="Search routes"
            className={styles.searchInput}
            onChange={this.onSearchChange}
            value={search}
          />
          <div
            className={
              classNames(
                styles.refreshButtonContainer
              )
            }
          >
            <Button
              onClick={this.onRefreshTable}
              disabled={pending}
              size="small"
            >
              REFRESH
            </Button>
          </div>
          <Spin spinning={pending}>
            <Table
              ref={(table) => {
                if (table) {
                  this.tableColumnsAmount = table.columns
                    .reduce((acc, column) => column.children
                      ? [...acc, ...column.children]
                      : [...acc, column], [])
                    .length;
                }
              }}
              indentSize={0}
              className={
                classNames(
                  styles.table,
                  'cp-settings-nat-table',
                  {
                    [styles.hasChildRoutes]: this.hasChildRoutes
                  }
                )
              }
              dataSource={this.filteredAndSortedContent}
              pagination={false}
              rowKey={(record) => record.divider
                ? record.key || record.description
                : getRouteIdentifier(record)
              }
              rowClassName={record => classNames({
                [styles.removed]: this.routeIsRemoved(record),
                [styles.dividerRow]: record.divider,
                'cp-nat-route-removed': this.routeIsRemoved(record),
                'cp-disabled': this.routeIsRemoved(record),
                'cp-primary': !this.routeIsRemoved(record) && record.isNew
              })}
              expandedRowKeys={expandedRowKeys}
              onExpandedRowsChange={this.onChangeExpandedRows}
            >
              <ColumnGroup title="External resources">
                {columns.external.map((col) => (
                  <Column
                    title={col.prettyName}
                    dataIndex={col.name}
                    key={col.name}
                    className={classNames('external-column', styles.column, col.className)}
                    render={this.renderColumnContent(col)}
                  />
                ))}
              </ColumnGroup>
              <ColumnGroup title="Internal config">
                {columns.internal.map((col) => (
                  <Column
                    title={col.prettyName || col.name}
                    dataIndex={col.name}
                    key={col.name}
                    className={classNames('internal-column', styles.column, col.className)}
                    render={this.renderColumnContent(col)}
                  />))
                }
              </ColumnGroup>
              <ColumnGroup>
                <Column
                  key="comment"
                  title="Comment"
                  className={classNames('nat-column', styles.commentColumn)}
                  dataIndex="description"
                  render={(description, record, index) => {
                    const content = (
                      <Tooltip title={description}>
                        {highlightText(description, search)}
                      </Tooltip>
                    );
                    if (record.divider) {
                      return {
                        children: content,
                        props: {
                          colSpan: this.tableColumnsAmount
                        }
                      };
                    }
                    return content;
                  }}
                />
                <Column
                  key="remover"
                  className={classNames('nat-column', styles.actionsColumn)}
                  render={(text, record) => {
                    if (record.divider) {
                      return collapseColumn(text);
                    }
                    return !this.routeIsRemoved(text) ? (
                      <Button
                        type="danger"
                        icon="delete"
                        onClick={() => this.removeRoute(text)}
                        size="small"
                      />
                    ) : (
                      <Button
                        icon="rollback"
                        size="small"
                        onClick={() => this.revertRoute(text)}
                      />
                    );
                  }}
                />
              </ColumnGroup>
            </Table>
          </Spin>
        </div>
        <div className={styles.tableContentActions}>
          <div className={styles.tableActions}>
            <Button
              onClick={() => this.openAddRouteModal()}
              size="small"
            >
              ADD ROUTE
            </Button>
          </div>
          <div className={styles.tableActions}>
            <Button
              type="primary"
              disabled={!this.tableContentChanged}
              onClick={this.onRevert}
              size="small"
            >
              REVERT
            </Button>
            <Button
              type="primary"
              disabled={!this.tableContentChanged}
              onClick={this.onSave}
              size="small"
            >
              SAVE
            </Button>
          </div>
        </div>
        <AddRouteForm
          visible={this.state.addRouteModalIsOpen}
          onAdd={this.addNewDataToTable}
          onCancel={this.closeAddRouteModal}
          routes={this.sortedContent}
        />
      </div>
    );
  }
}
