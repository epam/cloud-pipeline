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
import {Button, Icon, Spin, Table, message, Tooltip} from 'antd';
import classNames from 'classnames';

import {NATRules, DeleteRules, SetRules} from '../../../../models/nat';
import AddRouteForm from './add-route-modal/add-route-modal';
import {columns} from './helpers';
import styles from './nat-getaway-configuration.css';

const {Column, ColumnGroup} = Table;
const NATRouteStatuses = {
  CREATION_SCHEDULED: 'CREATION_SCHEDULED',
  SERVICE_CONFIGURED: 'SERVICE_CONFIGURED',
  DNS_CONFIGURED: 'DNS_CONFIGURED',
  PORT_FORWARDING_CONFIGURED: 'PORT_FORWARDING_CONFIGURED',
  ACTIVE: 'ACTIVE',
  TERMINATION_SCHEDULED: 'TERMINATION_SCHEDULED',
  TERMINATING: 'TERMINATING',
  RESOURCE_RELEASED: 'RESOURCE_RELEASED',
  TERMINATED: 'TERMINATED',
  FAILED: 'FAILED',
  UNKNOWN: 'UNKNOWN'
};

function getRouteIdentifier (route) {
  if (!route) {
    return undefined;
  }
  const {
    externalIp,
    externalPort
  } = route;
  return `${externalIp}-${externalPort}`;
}

function routesSorter (a, b) {
  const {externalName: nameA, externalPort: portA} = a;
  const {externalName: nameB, externalPort: portB} = b;
  return nameA.toString().localeCompare(nameB.toString()) || (portA - portB);
}

export default class NATGetaway extends React.Component {
  state = {
    addedRoutes: [],
    removedRoutes: [],
    routes: [],
    addRouteModalIsOpen: false,
    pending: false
  }

  get sortedContent () {
    const {
      routes = [],
      addedRoutes = []
    } = this.state;
    return routes
      .sort(routesSorter)
      .concat(addedRoutes.sort(routesSorter));
  }

  get tableContentChanged () {
    const {addedRoutes = [], removedRoutes = []} = this.state;
    return addedRoutes.length > 0 || removedRoutes.length > 0;
  }

  componentDidMount () {
    this.loadRoutes();
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
          state.rules = natRules.value.map(v => ({...v})) || [];
        }
      } catch (e) {
        message.error(e.message, 5);
      } finally {
        this.setState(state);
      }
    });
  }

  renderStatusIcon = (status) => {
    switch (status) {
      case NATRouteStatuses.ACTIVE:
        return (
          <Tooltip title={status}>
            <Icon
              className={classNames(styles.routeStatus, styles.activeStatus)}
              type="play-circle-o"
            />
          </Tooltip>
        );
      case NATRouteStatuses.CREATION_SCHEDULED:
        return (
          <Tooltip title={status}>
            <Icon
              type="hourglass"
              className={classNames(styles.routeStatus, styles.activeStatus)}
            />
          </Tooltip>
        );
      case NATRouteStatuses.SERVICE_CONFIGURED:
      case NATRouteStatuses.DNS_CONFIGURED:
      case NATRouteStatuses.PORT_FORWARDING_CONFIGURED:
        return (
          <Tooltip title={status}>
            <Icon
              type="loading"
              className={classNames(styles.routeStatus, styles.activeStatus)}
            />
          </Tooltip>
        );
      case NATRouteStatuses.TERMINATION_SCHEDULED:
      case NATRouteStatuses.TERMINATING:
      case NATRouteStatuses.RESOURCE_RELEASED:
      case NATRouteStatuses.TERMINATED:
        return (
          <Tooltip title={status}>
            <Icon
              className={classNames(styles.routeStatus, styles.scheduledStatus, styles.blink)}
              type="clock-circle-o"
            />
          </Tooltip>
        );
      case NATRouteStatuses.FAILED:
        return (
          <Tooltip title={status}>
            <Icon
              className={classNames(styles.routeStatus, styles.failedStatus)}
              type="exclamation-circle-o" />
          </Tooltip>
        );
      case NATRouteStatuses.UNKNOWN:
        return (
          <Tooltip title={status}>
            <Icon
              className={classNames(styles.routeStatus, styles.unknownStatus)}
              type="question-circle-o" />
          </Tooltip>
        );
      default:
        return (
          <Icon
            className={classNames(styles.routeStatus, styles.unknownStatus)}
            style={{visibility: 'hidden'}}
            type="question-circle-o"
          />
        );
    }
  };

  removeRoute = (route) => {
    const key = getRouteIdentifier(route);
    const {addedRoutes = [], removedRoutes = []} = this.state;
    if (route.isNew) {
      this.setState({
        addedRoutes: addedRoutes.filter(r => getRouteIdentifier(r) !== key)
      });
    } else {
      this.setState({
        removedRoutes: [
          ...removedRoutes,
          key
        ]
      });
    }
  }

  revertRoute = (route) => {
    const key = getRouteIdentifier(route);
    const {removedRoutes = []} = this.state;
    this.setState({
      removedRoutes: removedRoutes.filter(o => o !== key)
    });
  }

  routeIsRemoved = (route) => {
    const {removedRoutes = []} = this.state;
    return removedRoutes.includes(getRouteIdentifier(route));
  }

  addNewDataToTable = async (formData) => {
    const {serverName, ip, ports = [], description} = formData;
    const formattedData = ports.map(port => ({
      externalName: serverName,
      externalIp: ip,
      externalPort: port,
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
          routes = [],
          addedRoutes = [],
          removedRoutes = []
        } = this.state;
        const routesToRemove = routes
          .filter(route => removedRoutes.includes(getRouteIdentifier(route)))
          .map(route => ({
            externalName: route.externalName,
            externalIp: route.externalIp,
            port: route.externalPort
          }));
        const routesToAdd = addedRoutes
          .map(route => ({
            externalName: route.externalName,
            externalIp: route.externalIp,
            port: route.externalPort,
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

  render () {
    const {pending} = this.state;
    return (
      <div style={{position: 'relative'}}>
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
            className={styles.table}
            dataSource={this.sortedContent}
            pagination={false}
            rowKey={getRouteIdentifier}
            rowClassName={record => classNames({
              [styles.removed]: this.routeIsRemoved(record),
              [styles.new]: record.isNew
            })}
          >
            <ColumnGroup title="External resources">
              {columns.external.map((col) => {
                return (
                  <Column
                    title={col.prettyName || col.name}
                    dataIndex={col.name}
                    key={col.name}
                    className={classNames(styles.externalColumn, styles.column)}
                    render={(text, record) => (
                      <div>
                        {col.name === 'externalName' && this.renderStatusIcon(record.status)}
                        {text}
                      </div>)
                    }
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
                  className={classNames(styles.internalColumn, styles.column)}
                />))
              }
            </ColumnGroup>
            <ColumnGroup>
              <Column
                key="comment"
                title="Comment"
                className={styles.commentColumn}
                dataIndex="description"
                render={description => (
                  <Tooltip title={description}>
                    {description}
                  </Tooltip>
                )}
              />
              <Column
                key="remover"
                className={styles.actionsColumn}
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
