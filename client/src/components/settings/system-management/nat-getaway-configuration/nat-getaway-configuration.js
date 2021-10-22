import React from 'react';
import {Button, Icon, Spin, Table, message} from 'antd';

import AddRouteForm from './add-route-modal/add-route-modal';
import {contentIsEqual, mockedRequest, mockedData, mockedColumns} from './helpers';
import styles from './nat-getaway-configuration.css';

const {Column, ColumnGroup} = Table;

export default class NATGetaway extends React.Component {
    state = {
      initialContent: this.tableContent,
      tableContent: this.tableContent || [],
      backup: [],
      revertAction: false,
      addRouteModalIsOpen: false,
      pending: false,
      savingError: null
    }
    get tableContent () {
      return mockedData;
    }
    get tableExternalColumns () {
      return mockedColumns.external;
    }

    get tableInternalColumns () {
      return mockedColumns.internal;
    }

    get tableContentChanged () {
      return !contentIsEqual(this.state.initialContent, this.state.tableContent);
    }

  removeRow = (record) => {
    const newContent = this.state.tableContent
      .filter(item => item.key !== record.key);
    this.setState({
      tableContent: newContent,
      revertAction: true,
      backup: [...this.state.backup, this.state.tableContent]
    });
  }

  undoLastAction = () => {
    this.setState({
      tableContent: this.state.backup.pop(),
      revertAction: !!this.state.backup.length
    });
  }
  addNewDataToTable = async (formData) => {
    const {serverName, ip, ports} = formData;
    const formattedData = Object.entries(ports).map(([name, port]) => ({
      serverName,
      ip,
      port
    }));
    // mocking request result
    this.setState({
      pending: true
    });
    try {
      const newRows = await mockedRequest(formattedData);
      this.setState({
        tableContent: [...this.state.tableContent, ...newRows],
        backup: [...this.state.backup, this.state.tableContent]
      });
      this.closeAddRouteModal();
    } catch (e) {
      console.error(e);
    } finally {
      this.setState({
        pending: false
      });
    }
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

  clearBackupState = () => {
    this.setState({
      initialContent: this.tableContent,
      tableContent: this.tableContent || [],
      backup: [],
      revertAction: false
    });
  }

  onSave = () => {
    this.setState({pending: true});
    try {
      setTimeout(() => {
        this.setState({
          pending: false,
          savingError: null
        }, () => {
          message.success('Saved');
          this.clearBackupState();
        });
      }, 1000);
    } catch (e) {
      console.error(e);
      this.setState({
        pending: false,
        savingError: e.message
      }, () => message.error(e.toString(), 5));
    }
  }

  render () {
    return (
      <div className={styles.natTableContainer}>
        <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
          <div className={styles.tableActions}>
            <Button icon="plus" onClick={() => this.openAddRouteModal()}>Add route</Button>
            {
              this.state.revertAction && (
                <Button
                  type="primary"
                  icon="rollback"
                  onClick={() => this.undoLastAction()}
                >
                  Undo
                </Button>
              )}
          </div>
          <Button
            type="primary"
            disabled={!this.tableContentChanged}
            onClick={this.onSave}
          >
            SAVE
          </Button>
        </div>
        <Spin spinning={this.state.pending}>
          <Table
            className={styles.table}
            dataSource={this.state.tableContent}
            pagination={false}
            rowKey="key"
          >
            <ColumnGroup title="External resources">
              {this.tableExternalColumns.map((col) => (
                <Column
                  title={col.prettyName || col.name}
                  dataIndex={col.name}
                  key={col.name}
                  className={styles.externalColumn}
                />))
              }
            </ColumnGroup>
            <ColumnGroup title="Internal config">
              {this.tableInternalColumns.map((col) => (
                <Column
                  title={col.prettyName || col.name}
                  dataIndex={col.name}
                  key={col.name}
                />))
              }
              <Column
                key="remover"
                render={(opts) => (
                  <Button onClick={() => this.removeRow(opts)}>
                    <Icon type="delete" />
                  </Button>)}
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
