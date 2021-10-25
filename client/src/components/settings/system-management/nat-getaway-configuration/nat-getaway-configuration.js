import React from 'react';
import {Button, Icon, Spin, Table, message} from 'antd';

import AddRouteForm from './add-route-modal/add-route-modal';
import {contentIsEqual, mockedRequest, mockedData, mockedColumns} from './helpers';
import styles from './nat-getaway-configuration.css';

const {Column, ColumnGroup} = Table;

export default class NATGetaway extends React.Component {
    state = {
      initialContent: this.tableContent || [],
      tableContent: this.tableContent || [],
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
      return !contentIsEqual(
        this.state.initialContent,
        this.state.tableContent.filter(item => !item.isRemoved)
      );
    }

    getRowClassName (record) {
      return record.isRemoved ? styles.removed : '';
    }

    removeRow = (record) => {
      const index = this.state.tableContent.findIndex(item => record.key === item.key);
      const newContent = [...this.state.tableContent];
      if (index > -1) {
        newContent[index].isRemoved = true;
      }
      this.setState({
        tableContent: newContent
      }, () => console.log(this.tableContent));
    }

    revertRow = (record) => {
      const index = this.state.tableContent.findIndex(item => record.key === item.key);
      const newContent = [...this.state.tableContent];
      if (index > -1) {
        newContent[index].isRemoved = false;
      }
      this.setState({
        tableContent: newContent
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
          tableContent: [...this.state.tableContent, ...newRows]
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

    onSave = () => {
      this.setState({pending: true});
      try {
        setTimeout(() => {
          this.setState({
            pending: false,
            savingError: null
          }, () => {
            message.success('Saved');
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
          <div className={styles.tableContentActions}>
            <div className={styles.addRouteAction}>
              <Button icon="plus" onClick={() => this.openAddRouteModal()}>Add route</Button>
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
              rowClassName={this.getRowClassName}
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
                  render={(record) => {
                    return !record.isRemoved ? (
                      <Button type="danger" onClick={() => this.removeRow(record)}>
                        <Icon type="delete" />
                      </Button>
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
