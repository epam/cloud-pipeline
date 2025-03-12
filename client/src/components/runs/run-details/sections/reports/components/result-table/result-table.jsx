import React, {Component} from 'react';
import {Input, Table} from 'antd';
import PathCell from '../path-cell';
import highlightText from '../../../../../../special/highlightText';

import styles from './result-table.css';

function searchSatisfied (text, search) {
  return (text || '').toLowerCase().includes(search.toLowerCase());
}

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
      searchSatisfied(entry.name, searchText) ||
      searchSatisfied(entry.fileMask, searchText) ||
      entry.items.some(path => searchSatisfied(path, searchText))
    ).map((item, index) => ({
      ...item,
      ruleId: item.ruleId || `${item.name}-${item.fileMask}-${index}`
    }));

    return (
      <div>
        <Input
          className={styles.pathSearchInput}
          placeholder="Filter reports"
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
            sortOrder: sortedInfo.columnKey === 'name' ? sortedInfo.order : undefined,
            render: (value) => highlightText(value, searchText)
          },
          {
            title: 'Mask',
            dataIndex: 'fileMask',
            key: 'fileMask',
            sorter: (a, b) => a.name.localeCompare(b.name),
            sortOrder: sortedInfo.columnKey === 'fileMask' ? sortedInfo.order : undefined,
            render: (value) => highlightText(value, searchText)
          },
          {
            title: 'Path',
            dataIndex: 'items',
            key: 'items',
            render: (paths, rule) => (
              <PathCell paths={paths} rule={rule} search={searchText} />
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
