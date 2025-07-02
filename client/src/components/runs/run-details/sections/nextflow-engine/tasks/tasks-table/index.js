import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Alert, Icon, Pagination} from 'antd';
import displayDate from '../../../../../../../utils/displayDate';
import {NextflowTaskStatus} from '../utilities';
import TasksTableTagFilter from './tasks-table-tag-filter';
import TasksTableStatusesFilter from './tasks-table-statuses-filter';
import styles from './tasks-table.css';
import {
  isRunCompleted,
  NO_DATA_AVAILABLE_COMPLETED_JOB_MESSAGE,
  NO_DATA_AVAILABLE_RUNNING_JOB_MESSAGE
} from '../../../../utilities/helpers';

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
    key: 'taskId',
    title: '##',
    size: '100px',
    render: (o) => {
      const {
        name,
        taskName
      } = o;
      return name || taskName;
    }
  },
  {
    key: 'taskGroup',
    size: '400px',
    dataIndex: 'taskGroup',
    title: 'Process',
    sorting: true
  },
  {
    key: 'taskTag',
    size: '200px',
    dataIndex: 'taskTag',
    title: 'Tag',
    sorting: true,
    filter: (props) => (
      <TasksTableTagFilter
        {...props}
      />
    )
  },
  {
    key: 'status',
    size: '100px',
    dataIndex: 'status',
    title: 'Status',
    sorting: true,
    render: (o) => (<NextflowTaskStatus status={o.status} />),
    filter: (props) => (
      <TasksTableStatusesFilter
        {...props}
      />
    )
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
  ...[
    'native_id',
    'cpus',
    'memory',
    'disk',
    'time',
    'duration',
    'realtime',
    '%cpu',
    '%mem',
    'vmem',
    'rss',
    'peak_vmem',
    'peak_rss',
    'read_bytes',
    'write_bytes'
  ].map((field) => ({
    key: field,
    title: field,
    render: attributeRenderer(field)
  }))
];

function getTableColumnName (field) {
  return field.replace(/[^A-Za-z0-9_-]/g, '_');
}

function getTableColumnSize (size) {
  return size ?? 'minmax(min-content,max-content)';
}

function TasksTableColumn (props) {
  const {
    className,
    style,
    column,
    sorting = [],
    onChangeColumnSorting,
    filter,
    onFilterChange
  } = props;
  const {
    key,
    title,
    sorting: sortingEnabled = false,
    filter: ColumnFilter
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
        gridColumn: getTableColumnName(key),
        position: 'sticky',
        top: 0,
        zIndex: 1,
        fontWeight: 'bold'
      }}
      onClick={sortingEnabled ? toggleSorting : undefined}
    >
      <span>{title || key}</span>
      {
        ColumnFilter &&
        onFilterChange
          ? (
            <ColumnFilter
              filter={filter}
              onFilterChange={onFilterChange}
              style={{marginLeft: 5}}
            />)
          : undefined
      }
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
  onChangeColumnSorting: PropTypes.func,
  filter: PropTypes.object,
  onFilterChange: PropTypes.func
};

class TasksTable extends React.PureComponent {
  state = {
    hovered: undefined
  };

  componentDidMount () {
    this.onUnHover();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.tasks !== this.props.tasks) {
      this.onUnHover();
    }
  }

  onHover = (element) => {
    const {hovered} = this.state;
    if (hovered !== element) {
      this.setState({hovered: element});
    }
  };

  onUnHover = () => this.onHover(undefined);

  render () {
    const {
      className,
      style,
      tasks = [],
      pending,
      error,
      run,
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
      multipleSorting = false,
      onTaskClick
    } = this.props;
    const {
      hovered
    } = this.state;
    const completed = isRunCompleted(run);
    const onClick = (task) => {
      if (onTaskClick) {
        onTaskClick(task);
      }
    };
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
      gridTemplateColumns: columns
        // eslint-disable-next-line max-len
        .map(column => `[${getTableColumnName(column.key)}] ${getTableColumnSize(column.size)}`).join(' '),
      gridTemplateRows: tasks.length > 0
        ? `[header] 30px repeat(${tasks.length}, 30px)`
        : '[header] 30px'
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
          {
            pending && (<Icon type="loading" style={{marginLeft: 5}} />)
          }
        </div>
        <div className={styles.tasksGrid} style={tableStyle} onMouseLeave={this.onUnHover}>
          {
            columns.map((column) => (
              <TasksTableColumn
                key={`header-${column.key}`}
                column={column}
                sorting={sorting}
                onChangeColumnSorting={onChangeColumnSorting}
                filter={filter}
                onFilterChange={onFilterChange}
              />
            ))
          }
          {
            tasks.map((task, row) => columns.map((column) => (
              <div
                key={`${task.taskKey}-${column.key}`}
                style={{gridRow: `${row + 2}`, gridColumn: getTableColumnName(column.key)}}
                className={classNames(
                  styles.cell,
                  styles.taskCell,
                  'cp-run-engine-task-cell',
                  'cp-divider',
                  'light',
                  'bottom',
                  {
                    [styles.hovered]: hovered ? hovered.taskKey === task.taskKey : false,
                    active: hovered ? hovered.taskKey === task.taskKey : false,
                    'cp-primary': hovered ? hovered.taskKey === task.taskKey : false
                  }
                )}
                onMouseOver={() => this.onHover(task)}
                onClick={() => onClick(task)}
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
        {
          tasks.length === 0 && (
            <div
              className={styles.tasksInfo}
            >
              {
                error && (
                  <Alert message={error} showIcon type="warning" />
                )
              }
              {
                pending && !error && (
                  <div className="cp-text-not-important">
                    <Icon type="loading" style={{marginRight: 5}} />
                    <span>Loading tasks...</span>
                  </div>
                )
              }
              {
                !pending && !error && (
                  <span className="cp-text-not-important">
                    {
                      completed || total > 0
                        ? NO_DATA_AVAILABLE_COMPLETED_JOB_MESSAGE
                        : NO_DATA_AVAILABLE_RUNNING_JOB_MESSAGE
                    }
                  </span>
                )
              }
            </div>
          )
        }
      </div>
    );
  }
}

TasksTable.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  tasks: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  total: PropTypes.number,
  totalFiltered: PropTypes.number,
  run: PropTypes.object,
  page: PropTypes.number,
  onPageChange: PropTypes.func,
  pageSize: PropTypes.number,
  taskGroupFilter: PropTypes.string,
  filter: PropTypes.object,
  onFilterChange: PropTypes.func,
  sorting: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  onSortingChange: PropTypes.func,
  multipleSorting: PropTypes.bool,
  pending: PropTypes.bool,
  error: PropTypes.string,
  onTaskClick: PropTypes.func
};

export default TasksTable;
