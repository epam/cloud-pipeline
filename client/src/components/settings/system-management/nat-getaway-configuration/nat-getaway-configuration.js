import React from 'react';
import {computed} from 'mobx';
import {inject, observer} from 'mobx-react';
import {Button, Icon, Spin, Table, message, Row, Tooltip} from 'antd';
import classNames from 'classnames';

import {DeleteRules, SetRules} from '../../../../models/nat';
import AddRouteForm from './add-route-modal/add-route-modal';
import {contentIsEqual, compareTableRecords, columns} from './helpers';
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

@inject('natRules')
@observer
export default class NATGetaway extends React.Component {
    state = {
      tableContent: this.NatContent || [],
      addRouteModalIsOpen: false,
      pending: false,
      savingError: null
    }

    @computed
    get NatContent () {
      if (this.props.natRules && this.props.natRules.loaded) {
        return this.props.natRules.value.map(v => ({...v})) || [];
      }
      return [];
    }

    @computed
    get sortedContent () {
      return this.state.tableContent
        .sort((
          {externalName: nameA, externalPort: portA},
          {externalName: nameB, externalPort: portB}) => nameA.toString().localeCompare(nameB.toString()) || (portA - portB)
        );
    }

    get tableExternalColumns () {
      return columns.external;
    }

    get tableInternalColumns () {
      return columns.internal;
    }

    get tableContentChanged () {
      return !contentIsEqual(
        this.NatContent,
        this.state.tableContent.filter(item => !item.isRemoved)
      );
    }

    async componentDidMount () {
      await this.loadRules();
    }

    loadRules = async () => {
      const {natRules} = this.props;
      if (!natRules.loaded) {
        await natRules.fetch();
        if (natRules.error) {
          message.error(natRules.error);
        } else if (natRules.loaded && natRules.value) {
          this.setState({
            tableContent: this.NatContent
          });
        } else {
          return [];
        };
      }
    }

    getRowClassName (record) {
      return record.isRemoved ? styles.removed : '';
    }

    renderStatusIcon = (status) => {
      switch (status) {
        case NATRouteStatuses.ACTIVE:
          return (
            <Tooltip title={status}>
              <Icon
                className={classNames(styles.routeStatus, styles.activeStatus)}
                type="play-circle-o" />
            </Tooltip>
          );
        case NATRouteStatuses.CREATION_SCHEDULED:
        case NATRouteStatuses.SERVICE_CONFIGURED:
        case NATRouteStatuses.DNS_CONFIGURED:
        case NATRouteStatuses.PORT_FORWARDING_CONFIGURED:
          return (
            <Tooltip title={status}>
              <Icon
                className={classNames(styles.routeStatus, styles.scheduledStatus, styles.blink)}
                type="clock-circle-o" />

            </Tooltip>
          );
        case NATRouteStatuses.TERMINATION_SCHEDULED:
        case NATRouteStatuses.TERMINATING:
        case NATRouteStatuses.RESOURCE_RELEASED:
        case NATRouteStatuses.TERMINATED:
          return (
            <Tooltip title={status}>
              <Spin className={styles.routeStatus} />
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
        default: return null;
      }
    };

    removeRow = (record) => {
      const index = this.state.tableContent.findIndex(item => compareTableRecords(record, item));
      const newContent = this.state.tableContent.map(v => ({...v}));
      if (index > -1) {
        newContent[index].isRemoved = true;
      }
      this.setState({
        tableContent: newContent
      });
    }

    revertRow = (record) => {
      const index = this.state.tableContent.findIndex(item => compareTableRecords(record, item));
      const newContent = this.state.tableContent.map(v => ({...v}));
      if (index > -1) {
        delete newContent[index].isRemoved;
      }
      this.setState({
        tableContent: newContent
      });
    }

    addNewDataToTable = async (formData) => {
      const {serverName, ip, ports} = formData;
      const formattedData = Object.entries(ports).map(([name, port]) => ({
        externalName: serverName,
        externalIp: ip,
        externalPort: port,
        isNew: true
      }));
      this.setState({
        tableContent: [
          ...this.state.tableContent.map(v => ({...v})),
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

    onSave = async () => {
      try {
        const content = this.state.tableContent.reduce((
          res,
          {externalName, externalIp, externalPort, isRemoved, isNew}
        ) => {
          const row = {
            externalName,
            externalIp,
            port: externalPort
          };
          if (!isNew && isRemoved) {
            res.toRemove.push(row);
          } else if (isNew && !isRemoved) {
            res.toSave.push(row);
          }
          return res;
        }, {toRemove: [], toSave: []});

        if (content.toRemove.length) {
          this.setState({pending: true});
          const deleteRequest = new DeleteRules();
          await deleteRequest.send({rules: content.toRemove});
          this.setState({pending: false});
          if (deleteRequest.error) {
            message.error(deleteRequest.error);
          } else if (deleteRequest.loaded) {
            await this.onRefreshTable();
          }
        }
        if (content.toSave.length) {
          this.setState({pending: true});
          const saveRequest = new SetRules();
          await saveRequest.send({rules: content.toSave});
          this.setState({pending: false});
          if (saveRequest.error) {
            message.error(saveRequest.error);
          } else if (saveRequest.loaded && saveRequest.value) {
            message.success('Saved!');
            await this.onRefreshTable();
          }
        }
      } catch (e) {
        console.error(e);
        this.setState({
          pending: false,
          savingError: e.message
        }, () => message.error(e.toString(), 5));
      }
    }

    onRevert = () => {
      this.setState({
        tableContent: this.NatContent.map(v => ({...v}))
      });
    }

    onRefreshTable = async () => {
      if (!this.props.natRules.pending) {
        this.setState({pending: true});
        await this.props.natRules.fetch();
        if (this.props.natRules.loaded) {
          this.setState({
            pending: false,
            tableContent: this.NatContent
          });
        }
      }
    }

    render () {
      return (
        <div className={styles.natTableContainer}>
          <div className={styles.tableContentActions}>
            <div className={styles.tableActions}>
              <Button icon="plus" onClick={() => this.openAddRouteModal()}>Add route</Button>
            </div >
            <div className={styles.tableActions}>
              <Button
                type="primary"
                disabled={!this.tableContentChanged}
                icon="rollback"
                onClick={this.onRevert}
              >
                REVERT
              </Button>
              <Button
                type="primary"
                disabled={!this.tableContentChanged}
                onClick={this.onSave}
              >
                SAVE
              </Button>
              <Button
                onClick={this.onRefreshTable}
                icon="retweet"
                disabled={this.tableContentChanged}
              >
                REFRESH
              </Button>
            </div>
          </div>
          <Spin spinning={this.state.pending}>
            <Table
              className={styles.table}
              dataSource={this.sortedContent}
              pagination={false}
              rowKey={({externalIp, externalName, externalPort}) => `${externalIp}-${externalName}-${externalPort}`}
              rowClassName={this.getRowClassName}
            >
              <ColumnGroup title="External resources" className={styles.columnGroup}>
                {this.tableExternalColumns.map((col) => {
                  return (
                    <Column
                      title={col.prettyName || col.name}
                      dataIndex={col.name}
                      key={col.name}
                      className={classNames(styles.externalColumn, styles.column)}
                      render={(text, record) => (
                        <Row className={styles.externalNameCell}>
                          {col.name === 'externalName' && this.renderStatusIcon(record.status)}
                          {text}
                        </Row>)
                      }
                    />);
                })
                }
              </ColumnGroup>
              <ColumnGroup title="Internal config">
                {this.tableInternalColumns.map((col) => (
                  <Column
                    title={col.prettyName || col.name}
                    dataIndex={col.name}
                    key={col.name}
                    className={classNames(styles.internalColumn, styles.column)}
                  />))
                }
                <Column
                  key="remover"
                  className={styles.actionsColumn}
                  render={(record) => {
                    return !record.isRemoved ? (
                      <div>
                        <Button type="danger" onClick={() => this.removeRow(record)}>
                          <Icon type="delete" />
                        </Button>
                      </div>
                    ) : (
                      <div className={styles.revertActionBlock}>
                        <Icon
                          title="revert"
                          type="rollback"
                          className={styles.revertIcon}
                          onClick={() => this.revertRow(record)}
                        />
                        <p>deleted</p>
                      </div>
                    );
                  }}
                />
              </ColumnGroup>
            </Table>
          </Spin>
          <AddRouteForm
            visible={this.state.addRouteModalIsOpen}
            onAdd={this.addNewDataToTable}
            onCancel={this.closeAddRouteModal} />
        </div>
      );
    }
}
