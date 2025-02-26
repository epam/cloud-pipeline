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
    let {sortedInfo, searchText} = this.state;
    const filteredData = this.props.resultItems.filter(entry => entry.items.some(path => {
      return path.toLowerCase().includes(searchText.toLowerCase());
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
            title: (
              <span>
                Name
              </span>
            ),
            dataIndex: 'fileMask',
            key: 'fileMask',
            sorter: (a, b) => a.name.localeCompare(b.name),
            sortOrder: sortedInfo.columnKey === 'fileMask' && sortedInfo.order,
            render: (text) => (
              <div>
                {text}
              </div>
            )
          },
          {
            title: 'Path',
            dataIndex: 'items',
            key: 'items',
            render: (paths) => {
              return <PathCell paths={paths} />;
            }
          }]}
          showSorterTooltip={{target: 'sorter-icon'}}
          pagination={false}
          size="small" />
      </div>
    );
  }
};
