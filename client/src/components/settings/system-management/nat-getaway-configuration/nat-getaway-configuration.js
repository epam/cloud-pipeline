/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Spin, Table, message, Tooltip} from 'antd';
import classNames from 'classnames';

import {NATRules, DeleteRules, SetRules} from '../../../../models/nat';
import AddRouteForm from './add-route-modal/add-route-modal';
import {columns} from './helpers';
import * as portUtilities from './ports-utilities';
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

export default class NATGetaway extends React.Component {
  state = {
    addedRoutes: [],
    removedRoutes: [],
    routes: [],
    addRouteModalIsOpen: false,
    pending: false,
    expandedRowKeys: []
  }

  get sortedContent () {
    const {
      routes = [],
      addedRoutes = []
    } = this.state;
    const added = portUtilities.groupRoutes(addedRoutes);
    return portUtilities.groupRoutes(routes)
      .sort(routesSorter)
      .concat(added.sort(routesSorter));
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
  }

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
  }

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
  }

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
  }

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
  }

  openAddRouteModal = () => {
    this.setState({
      addRouteModalIsOpen: true
    });
  }

  closeAddRouteModal = () => {
    this.setState({
      addRouteModalIsOpen: false
    });
  }

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
  }

  onRevert = () => {
    this.setState({
      addedRoutes: [],
      removedRoutes: []
    });
  }

  onRefreshTable = () => {
    this.loadRoutes();
  }

  onChangeExpandedRows = (expandedRows) => {
    this.setState({
      expandedRowKeys: expandedRows
    });
  }

  render () {
    const {
      pending,
      expandedRowKeys = []
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
              indentSize={0}
              className={classNames(styles.table, 'cp-settings-nat-table')}
              dataSource={this.sortedContent}
              pagination={false}
              rowKey={getRouteIdentifier}
              rowClassName={record => classNames({
                [styles.removed]: this.routeIsRemoved(record),
                'cp-nat-route-removed': this.routeIsRemoved(record),
                'cp-disabled': this.routeIsRemoved(record),
                'cp-primary': !this.routeIsRemoved(record) && record.isNew
              })}
              expandedRowKeys={expandedRowKeys}
              onExpandedRowsChange={this.onChangeExpandedRows}
            >
              <ColumnGroup title="External resources">
                {columns.external.map((col) => {
                  return (
                    <Column
                      title={col.prettyName}
                      dataIndex={col.name}
                      key={col.name}
                      className={classNames('external-column', styles.column, col.className)}
                      render={(col.renderer || (o => o))}
                    />);
                })
                }
              </ColumnGroup>
              <ColumnGroup title="Internal config">
                {columns.internal.map((col) => (
                  <Column
                    title={col.prettyName || col.name}
                    dataIndex={col.name}
                    key={col.name}
                    className={classNames('internal-column', styles.column, col.className)}
                    render={(col.renderer || (o => o))}
                  />))
                }
              </ColumnGroup>
              <ColumnGroup>
                <Column
                  key="comment"
                  title="Comment"
                  className={classNames('nat-column', styles.commentColumn)}
                  dataIndex="description"
                  render={description => (
                    <Tooltip title={description}>
                      {description}
                    </Tooltip>
                  )}
                />
                <Column
                  key="remover"
                  className={classNames('nat-column', styles.actionsColumn)}
                  render={(record) => !this.routeIsRemoved(record) ? (
                    <Button
                      type="danger"
                      icon="delete"
                      onClick={() => this.removeRoute(record)}
                      size="small"
                    />
                  ) : (
                    <Button
                      icon="rollback"
                      size="small"
                      onClick={() => this.revertRoute(record)}
                    />
                  )}
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
