import React from 'react';
import {Modal, Input, AutoComplete} from 'antd';
import {inject, observer} from 'mobx-react';
import PropTypes from 'prop-types';

import styles from './UserIntegrityCheck.css';
const mockedDataForTable = [
  {id: 1, userName: 'PIPE_ADMIN', attributes: [{id: 'Tissue', value: 'TissueValue'}, {id: 'billing-centre', value: 'billing-centre-value'}, {id: 'Staining', value: 'staining value1'},{id: 'billing-centre4', value: 'billing-centre-value4'}, {id: 'Staining5', value: 'staining value5'}, {id: 'billing-centre7', value: 'billing-centre-value7'}, {id: 'Staining6', value: 'staining value6'}]},
  {id: 1, userName: 'PEPE_ADMIN', attributes: [{id: 'billing-centre', value: 'billing-centre-value'}, {id: 'Staining', value: 'staining value1'}]},
  {id: 1, userName: 'P0PE_ADMIN', attributes: [{id: 'attr0', value: 'value0'}, {id: 'attr2', value: 'value2'}]},
  {id: 1, userName: 'PAPE_ADMIN', attributes: [{id: 'Library type', value: 'Library type value'}]},
  {id: 1, userName: 'PUPE_ADMIN', attributes: [{id: 'attr1', value: 'value1'}, {id: 'billing-centre', value: 'billing-centre-value'}]},
  {id: 1, userName: 'PYPE_ADMIN', attributes: [{id: 'Staining', value: 'staining value1'}]},
  {id: 1, userName: 'POOPE_ADMIN', attributes: [{id: 'attr1', value: 'value1'}]},
  {id: 1, userName: 'PAAPE_ADMIN', attributes: [{id: 'attr2', value: 'value2'}, {id: 'Staining', value: 'staining value1'}]}
];

@inject(({systemDictionaries}) => ({systemDictionaries}))
@observer
export class UserIntegrityCheck extends React.Component {
  state={
    tableContent: []
  }
  componentDidUpdate (prevProps) {
    if (prevProps.systemDictionaries !== this.props.systemDictionaries) {

    }
  }
  getTableAttributes = (data) => {
    return new Set(data.map(o => o.attributes.map((attr) => attr.id)).flat());
  }
  getDictionaries = () => this.props.systemDictionaries.value.filter(d => !!d);

  getTableContent = (data) => {
    const tableColumns = Array.from(this.getTableAttributes(data));
    const dictionaries = this.getDictionaries();
    return data.reduce((content, {userName, attributes}) => {
      const row = new Array(tableColumns.length + 1).fill('');
      row[0] = userName;
      attributes.forEach((attr) => {
        const attrName = attr.id;
        const dictIndex = dictionaries.findIndex(d => d.key === attrName);
        const attrColumnIndex = tableColumns.findIndex((attr) => attr === attrName);
        if (dictIndex > -1) {
          row[attrColumnIndex+1] = {
            user: userName,
            colName: tableColumns[attrColumnIndex],
            value: dictionaries[dictIndex].values.filter(v => !!v)
          };
        }
      });
      content.push(row);
      return content;
    }, []);
  }
    state = {visible: this.props.visible};
    handleCancel = () => {
      this.setState({
        visible: false
      });
    }
    handleChange = (opts) => {
      console.log(opts);
    }
    renderTableHead = (data) => {
      if (data && data.length > 0) {
        return (
          <tr>
            <th>USERNAME</th>
            {
              Array.from(this.getTableAttributes(mockedDataForTable)).map((attr, index) => (
                <th key={index} style={{textOverflow: 'ellipsis', whiteSpace: 'normal'}}>
                  {attr}
                </th>)
              )}
          </tr>

        );
      }
      return null;
    }

    renderTableContent = (data) => {
      if (data && data.length > 0) {
        return data.map((row, rowIndex) => {
          return (
            <tr key={rowIndex}>
              {row.map((data, index) => {
                if (index === 0) {
                  return (<td key={index}>{data}</td>);
                } else if (data) {
                  const {value, user, colName} = data;
                  return (
                    <td key={index}>
                      <AutoComplete
                        mode="combobox"
                        size="large"
                        style={{width: '100%'}}
                        allowClear
                        autoFocus
                        backfill
                        onChange={(value) => this.handleChange({value, colName, row: rowIndex, user})}
                        filterOption={
                          (input, option) =>
                            option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                        }
                        defaultValue={value[0].value}
                      >
                        {value.map((v) => {
                          return (
                            <AutoComplete.Option
                              key={v.id}
                              value={v.value}
                            >
                              {v.value}
                            </AutoComplete.Option>);
                        })}
                      </AutoComplete>
                    </td>
                  );
                } else {
                  return (
                    <td key={index}>
                      <Input size="large" style={{width: '100%'}} />
                    </td>
                  );
                }
              })
              }
            </tr>
          );
        });
      }
      return null;
    };

    render () {
      const {visible} = this.state;
      if (this.props.data && this.props.data.length > 0) {
        return (
          <Modal
            title="Title"
            visible={visible}
            footer={null}
            onCancel={this.handleCancel}
            bodyStyle={{padding: '10px', overflowX: 'scroll'}}
            width={'90vw'}
          >
            <div className={styles.tableContainer}>
              <table>
                <thead>
                  {this.renderTableHead(mockedDataForTable)}
                </thead>
                <tbody>
                  {this.renderTableContent(this.getTableContent(mockedDataForTable))}
                </tbody>
              </table>
            </div>
          </Modal>
        );
      }
      return null;
    }
}
UserIntegrityCheck.propTypes = {
  visible: PropTypes.bool,
  data: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
      userName: PropTypes.string,
      attributes: PropTypes.arrayOf(
        PropTypes.shape({
          id: PropTypes.string,
          value: PropTypes.string
        }))
    }))
};
UserIntegrityCheck.defaultProps = {
  visible: false,
  data: []
};
