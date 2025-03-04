import React, {Component} from 'react';
import {Input, Table} from 'antd';
import PathCell from '../path-cell';

import styles from './result-table.css';

export class ResultTable extends Component {
  state = {
    sortedInfo: {},
    searchText: ''
  };

  handleChange = (pagination, filters, sorter) => {
    this.setState({
      sortedInfo: sorter
    });
  };

  onSearch = (e) => {
    const {value} = e.target;

    this.setState({searchText: value});
  };

  computeRowClassName = (record, index) => {
    return index % 2 === 0 ? styles.tableRowDark : styles.tableRowLight;
  }

  render () {
    const {sortedInfo, searchText} = this.state;
    const filteredData = this.props.resultItems.filter(entry =>
      (entry.name && entry.name.toLowerCase().includes(searchText.toLowerCase())) ||
      entry.items.some(path => path.toLowerCase().includes(searchText.toLowerCase()))
    ).map((item, index) => ({
      ...item,
      ruleId: item.ruleId || `${item.name}-${item.fileMask}-${index}`
    }));

    return (
      <div>
        <Input
          className={styles.pathSearchInput}
          placeholder="Filter by path"
          value={searchText}
          onChange={this.onSearch}
        />
        <Table
          onChange={this.handleChange}
          dataSource={filteredData}
          rowClassName={this.computeRowClassName}
          columns={[{
            title: 'Name',
            dataIndex: 'name',
            key: 'name',
            sorter: (a, b) => a.name.localeCompare(b.name),
            sortOrder: sortedInfo.columnKey === 'name' ? sortedInfo.order : undefined
          },
          {
            title: 'Mask',
            dataIndex: 'fileMask',
            key: 'fileMask',
            sorter: (a, b) => a.name.localeCompare(b.name),
            sortOrder: sortedInfo.columnKey === 'fileMask' ? sortedInfo.order : undefined
          },
          {
            title: 'Path',
            dataIndex: 'items',
            key: 'items',
            render: (paths, rule) => (
              <PathCell paths={paths} rule={rule} />
            )
          }]}
          rowKey="ruleId"
          showSorterTooltip={{target: 'sorter-icon'}}
          pagination={false}
          size="small" />
      </div>
    );
  }
}
