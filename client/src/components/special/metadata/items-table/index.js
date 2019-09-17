/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import PropTypes from 'prop-types';
import {
  Button,
  Row,
} from 'antd';
import {isJson, parse, plural} from './utilities';
import styles from './items-table.css';

export {isJson};

class ItemsTable extends React.Component {
  static propTypes = {
    disabled: PropTypes.bool,
    editMode: PropTypes.bool,
    value: PropTypes.string,
    onEditModeChange: PropTypes.func.isRequired
  };

  state = {
    expanded: false
  };

  get items () {
    const {value} = this.props;
    return parse(value);
  }

  onExpandCollapse = (expand) => (e) => {
    e.stopPropagation();
    e.preventDefault();
    this.setState({expanded: expand});
  };

  render () {
    const {expanded} = this.state;
    const {
      editMode,
      onEditModeChange
    } = this.props;
    if (!expanded) {
      return (
        <div
          className={styles.container}
        >
          <a
            className={styles.link}
            onClick={this.onExpandCollapse(true)}
          >
            {plural(this.items.length, 'item')}
          </a>
        </div>
      );
    }
    return (
      <div
        className={styles.container}
      >
        <Row
          className={styles.actions}
          type="flex"
          justify="end"
          align="center"
        >
          {
            !editMode &&
            (
              <Button
                size="small"
                onClick={() => onEditModeChange(true)}
              >
                Edit
              </Button>
            )
          }
          {
            editMode &&
            (
              <Button
                size="small"
                type="primary"
                onClick={() => onEditModeChange(false)}
              >
                Save
              </Button>
            )
          }
          <Button
            size="small"
            onClick={this.onExpandCollapse(false)}
          >
            Close
          </Button>
        </Row>
      </div>
    );
  }
}

export default ItemsTable;
