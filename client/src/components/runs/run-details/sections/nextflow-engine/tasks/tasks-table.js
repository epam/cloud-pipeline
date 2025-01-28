import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Icon, Pagination} from 'antd';
import styles from './nextflow-engine-tasks.css';
import displayDate from '../../../../../../utils/displayDate';
import {NextflowTaskStatus} from './utilities';

export const TASKS_TABLE_PAGE_SIZE = 25;

function attributeRenderer (attribute, formatter = (o) => `${o}`) {
  return (task) => {
    const {attributes = {}} = task;
    const {[attribute]: attributeValue} = attributes;
    if (attributeValue !== undefined && attributeValue !== null) {
      return formatter(attributeValue);
    }
    return undefined;
  };
}

const columns = [
  {
    key: 'taskTag',
    title: 'Name',
    render: (o) => {
      const {
        taskTag,
        name,
        taskName
      } = o;
      return taskTag || name || taskName;
    },
    sorting: true
  },
  {
    key: 'taskGroup',
    dataIndex: 'taskGroup',
    title: 'Process',
    sorting: true
  },
  {
    key: 'status',
    dataIndex: 'status',
    title: 'Status',
    sorting: true,
    render: (o) => (<NextflowTaskStatus status={o.status} />)
  },
  {
    key: 'started',
    title: 'Started',
    render: (o) => {
      const {startDateTime} = o;
      if (startDateTime) {
        return displayDate(startDateTime);
      }
      return undefined;
    }
  },
  {
    key: 'finished',
    title: 'Finished',
    render: (o) => {
      const {endDateTime} = o;
      if (endDateTime) {
        return displayDate(endDateTime);
      }
      return undefined;
    }
  },
  {
    key: 'cpus',
    title: 'CPU',
    render: attributeRenderer('cpus')
  },
  {
    key: 'disk',
    title: 'Disk',
    render: attributeRenderer('disk')
  },
  {
    key: 'memory',
    title: 'Memory',
    render: attributeRenderer('memory')
  }
];
function TasksTableColumn (props) {
  const {
    className,
    style,
    column,
    sorting = [],
    onChangeColumnSorting
  } = props;
  const {
    key,
    title,
    sorting: sortingEnabled = false
  } = column || {};
  const columnSorting = sorting.find((s) => s.column === key);
  const onChangeSorting = (desc, event) => {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    if (onChangeColumnSorting) {
      onChangeColumnSorting(key, desc);
    }
  };
  const sortedAsc = columnSorting ? !columnSorting.descending : false;
  const sortedDesc = columnSorting ? columnSorting.descending : false;
  const toggleSorting = (event) => {
    event.stopPropagation();
    event.preventDefault();
    if (sortedAsc) {
      onChangeSorting(true);
    } else if (sortedDesc) {
      onChangeSorting(undefined);
    } else {
      onChangeSorting(false);
    }
  };
  return (
    <div
      className={classNames(
        className,
        styles.cell,
        'cp-card-background-color',
        'cp-divider',
        'light',
        'bottom',
        {
          [styles.sorting]: sortingEnabled
        }
      )}
      style={{
        ...(style || {}),
        gridRow: 'header',
        gridColumn: key,
        position: 'sticky',
        top: 0,
        zIndex: 1,
        fontWeight: 'bold'
      }}
      onClick={sortingEnabled ? toggleSorting : undefined}
    >
      <span>{title || key}</span>
      {
        sortingEnabled && (
          <div
            className={styles.cellSorting}
          >
            <Icon
              type="up"
              className={classNames(
                styles.cellSortingControl,
                {
                  [styles.active]: sortedAsc,
                  'cp-primary': sortedAsc,
                  'cp-text-not-important': !sortedAsc
                }
              )}
              onClick={(event) => onChangeSorting(sortedAsc ? undefined : false, event)}
            />
            <Icon
              type="down"
              className={classNames(
                styles.cellSortingControl,
                {
                  [styles.active]: sortedDesc,
                  'cp-primary': sortedDesc,
                  'cp-text-not-important': !sortedDesc
                }
              )}
              onClick={(event) => onChangeSorting(sortedDesc ? undefined : true, event)}
            />
          </div>
        )
      }
    </div>
  );
}

TasksTableColumn.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  column: PropTypes.object,
  sorting: PropTypes.oneOfType([PropTypes.string, PropTypes.array]),
  onChangeColumnSorting: PropTypes.func
};

function TasksTable (props) {
  const {
    className,
    style,
    tasks = [],
    total = 0,
    totalFiltered = 0,
    page = 0,
    onPageChange,
    pageSize = TASKS_TABLE_PAGE_SIZE,
    taskGroupFilter,
    filter,
    onFilterChange,
    sorting,
    onSortingChange,
    multipleSorting = false
  } = props;
  const onPageChangeCallback = (newPage) => {
    if (onPageChange) {
      onPageChange(Math.max(0, newPage - 1));
    }
  };
  const onChangeColumnSorting = (columnKey, descending) => {
    if (!onSortingChange) {
      return;
    }
    if (multipleSorting) {
      const newSorting = sorting.slice();
      let idx = newSorting.findIndex((s) => s.key === columnKey);
      if (idx === -1) {
        newSorting.push({column: columnKey, descending: false});
        idx = newSorting.length - 1;
      }
      if (descending !== undefined) {
        newSorting[idx].descending = descending;
      } else {
        newSorting.splice(idx, 1);
      }
      onSortingChange(newSorting);
    } else {
      if (descending !== undefined) {
        onSortingChange([{column: columnKey, descending: descending}]);
      } else {
        onSortingChange([]);
      }
    }
  };
  const tableStyle = {
    gridTemplateColumns: columns.map(column => `[${column.key}] auto`).join(' '),
    gridTemplateRows: `[header] 30px repeat(${tasks.length}, 30px)`
  };
  return (
    <div
      className={classNames(className, styles.tasksTableContainer)}
      style={style}
    >
      <div className={classNames(styles.tasksTableHeader, 'cp-card-background-color')}>
        {
          taskGroupFilter ? (
            <span>
              <span style={{fontWeight: 'bold'}}>
                {taskGroupFilter}
              </span>
              <span style={{marginLeft: 5}}>
                process tasks
              </span>
            </span>
          ) : (
            <span style={{fontWeight: 'bold'}}>
              Tasks
            </span>
          )
        }
        {
          total > 0 && (
            <span style={{marginLeft: 5}}>
              {
                total > totalFiltered
                  ? `(${totalFiltered} / ${total})`
                  : `(${totalFiltered})`
              }
            </span>
          )
        }
      </div>
      <div className={styles.tasksGrid} style={tableStyle}>
        {
          columns.map((column) => (
            <TasksTableColumn
              key={`header-${column.key}`}
              column={column}
              sorting={sorting}
              onChangeColumnSorting={onChangeColumnSorting}
            />
          ))
        }
        {
          tasks.map((task, row) => columns.map((column) => (
            <div
              key={`${task.taskKey}-${column.key}`}
              style={{gridRow: `${row + 2}`, gridColumn: column.key}}
              className={classNames(
                styles.cell,
                'cp-divider',
                'light',
                'bottom'
              )}
            >
              {
                column.render
                  ? column.render(task)
                  : (column.dataIndex ? task[column.dataIndex] : undefined)
              }
            </div>
          )))
        }
      </div>
      {
        totalFiltered > pageSize && (
          <div className={styles.tasksTablePagination}>
            <Pagination
              current={page + 1}
              pageSize={pageSize}
              total={totalFiltered}
              onChange={onPageChangeCallback}
              size="small"
            />
          </div>
        )
      }
    </div>
  );
}

TasksTable.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  tasks: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  total: PropTypes.number,
  totalFiltered: PropTypes.number,
  page: PropTypes.number,
  onPageChange: PropTypes.func,
  pageSize: PropTypes.number,
  taskGroupFilter: PropTypes.string,
  filter: PropTypes.object,
  onFilterChange: PropTypes.func,
  sorting: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  onSortingChange: PropTypes.func,
  multipleSorting: PropTypes.bool
};

export default TasksTable;
