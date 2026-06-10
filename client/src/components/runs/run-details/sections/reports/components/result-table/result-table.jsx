import React, {Component} from 'react';
import {Input, Table} from 'antd';
import PathCell from '../path-cell';

import styles from './result-table.module.css';
import PropTypes from 'prop-types';
import classNames from 'classnames';

class ResultTable extends Component {
  state = {
    sortedInfo: {},
    searchText: '',
  };

  handleChange = (pagination, filters, sorter) => {
    this.setState({
      sortedInfo: sorter,
    });
  };

  onSearch = (e) => {
    const {value} = e.target;

    this.setState({searchText: value});
  };

  render() {
    const {className, style, resultItems = []} = this.props;
    const {sortedInfo, searchText} = this.state;
    const filteredData = resultItems
      .filter(
        (entry) =>
          (entry.name && entry.name.toLowerCase().includes(searchText.toLowerCase())) ||
          entry.items.some((path) => path.toLowerCase().includes(searchText.toLowerCase())),
      )
      .map((item, index) => ({
        ...item,
        ruleId: item.ruleId || `${item.name}-${item.fileMask}-${index}`,
      }));

    return (
      <div className={classNames(className, styles.reportsResultTableContainer)} style={style}>
        <Input
          className={styles.pathSearchInput}
          placeholder="Filter by path"
          value={searchText}
          onChange={this.onSearch}
        />
        <Table
          className={styles.reportsResultTable}
          onChange={this.handleChange}
          dataSource={filteredData}
          columns={[
            {
              title: 'Name',
              dataIndex: 'name',
              key: 'name',
              sorter: (a, b) => a.name.localeCompare(b.name),
              sortOrder: sortedInfo.columnKey === 'name' ? sortedInfo.order : undefined,
            },
            {
              title: 'Mask',
              dataIndex: 'fileMask',
              key: 'fileMask',
              sorter: (a, b) => a.name.localeCompare(b.name),
              sortOrder: sortedInfo.columnKey === 'fileMask' ? sortedInfo.order : undefined,
            },
            {
              title: 'Path',
              dataIndex: 'items',
              key: 'items',
              render: (paths, rule) => <PathCell paths={paths} rule={rule} />,
            },
          ]}
          rowKey="ruleId"
          showSorterTooltip={{target: 'sorter-icon'}}
          pagination={false}
          size="small"
        />
      </div>
    );
  }
}

ResultTable.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  resultItems: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
};

export {ResultTable};
