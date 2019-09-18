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
import {observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Modal,
  Row,
  Button
} from 'antd';
import {isJson, makePretty, parse, plural} from './utilities';
import ValueRenderer from './property-value-renderers';
import CodeEditor from '../../CodeEditor';
import styles from './items-table.css';

export {isJson};

@observer
class ItemsTable extends React.Component {
  static propTypes = {
    title: PropTypes.string,
    disabled: PropTypes.bool,
    value: PropTypes.string,
    onChange: PropTypes.func
  };

  state = {
    editMode: false,
    expanded: false,
    operationInProgress: false,
    valid: true,
    value: null
  };

  @computed
  get items () {
    const {value} = this.props;
    return parse(value);
  }

  operationWrapper = fn => (...opts) => {
    this.setState({
      operationInProgress: true
    }, async () => {
      await fn(...opts);
      this.setState({
        operationInProgress: false
      });
    });
  };

  toggleExpandMode = (expanded) => (e) => {
    if (e) {
      e.stopPropagation();
      e.preventDefault();
    }
    this.setState({expanded});
  };

  toggleEditMode = (editMode) => (e) => {
    if (e) {
      e.stopPropagation();
      e.preventDefault();
    }
    this.setState({
      editMode,
      valid: isJson(this.props.value),
      value: editMode ? makePretty(this.props.value) : null
    });
  };

  saveChanges = async () => {
    const {onChange} = this.props;
    const {value} = this.state;
    let result = true;
    if (onChange) {
      result = await onChange(value);
    }
    if (result) {
      this.toggleEditMode(false)();
    }
  };

  renderTable = () => {
    if (this.items.length === 0) {
      return null;
    }
    return (
      <div
        className={styles.tableContainer}
      >
        <table
          className={styles.table}
        >
          <tbody>
            <tr>
              {
                this.items.keys.map(key => (
                  <th key={key}>
                    {key}
                  </th>
                ))
              }
            </tr>
            {
              this.items.items.map((item, id) => (
                <tr
                  key={id}
                  className={styles.item}
                >
                  {
                    this.items.keys.map(key => (
                      <td
                        key={key}
                      >
                        <ValueRenderer value={item[key]} />
                      </td>
                    ))
                  }
                </tr>
              ))
            }
          </tbody>
        </table>
      </div>
    );
  };

  renderEditor = () => {
    const {valid, value} = this.state;
    const onChange = (code) => {
      this.setState({value: code, valid: isJson(code)});
    };
    const classNames = [styles.codeEditor];
    if (!valid) {
      classNames.push(styles.invalid);
    }
    return (
      <CodeEditor
        className={classNames.join(' ')}
        language="json"
        defaultCode={value}
        onChange={onChange}
      />
    );
  };

  renderFooter = () => {
    const {disabled} = this.props;
    const {
      editMode,
      operationInProgress,
      valid
    } = this.state;
    if (editMode) {
      return (
        <Row
          type="flex"
          justify="space-between"
          align="center"
        >
          <Button
            id="items-table-modal-edit-cancel"
            onClick={this.toggleEditMode(false)}
            disabled={operationInProgress}
          >
            CANCEL
          </Button>
          <Button
            disabled={!valid || operationInProgress}
            id="items-table-modal-edit-save"
            type="primary"
            onClick={this.operationWrapper(this.saveChanges)}
          >
            SAVE
          </Button>
        </Row>
      );
    }
    return (
      <Row
        type="flex"
        justify="end"
        align="center"
      >
        {
          !disabled && (
            <Button
              id="items-table-modal-edit"
              type="primary"
              onClick={this.toggleEditMode(true)}
              style={{marginRight: 5}}
              disabled={disabled || operationInProgress}
            >
              EDIT
            </Button>
          )
        }
        <Button
          id="items-table-modal-close"
          onClick={this.toggleExpandMode(false)}
        >
          CLOSE
        </Button>
      </Row>
    );
  };

  render () {
    const {title} = this.props;
    const {
      editMode,
      expanded
    } = this.state;
    return (
      <div
        className={styles.container}
      >
        <a
          id="items-table-expand"
          className={styles.link}
          onClick={this.toggleExpandMode(true)}
        >
          {plural(this.items.length, 'item')}
        </a>
        <Modal
          visible={expanded}
          closable={false}
          title={title}
          footer={this.renderFooter()}
          width="50%"
        >
          {!editMode && this.renderTable()}
          {editMode && this.renderEditor()}
        </Modal>
      </div>
    );
  }
}

export default ItemsTable;
