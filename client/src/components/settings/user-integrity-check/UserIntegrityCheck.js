import React from 'react';
import {Modal} from 'antd';
import PropTypes from 'prop-types';

import './UserIntegrityCheck.css';

export class UserIntegrityCheck extends React.Component {
    state = {visible: this.props.visible};
    handleCancel = () => {
      this.setState({
        visible: false
      });
    }
    renderTableHead = (data) => {
      if (data && data.length > 0) {
        return (
          <tr>
            <th>USERNAME</th>
            {
              data[0].attributes.map((attr, index) => (
                <th key={index}>
                  {attr.id.toUpperCase()}
                </th>)
              )}
          </tr>

        );
      }
      return null;
    }

    renderTableContent = (data) => {
      if (data && data.length > 0) {
        return data.map((rowData, index) => {
          if (rowData && rowData.attributes && rowData.userName) {
            return (
              <tr key={index}>
                <td>{rowData.userName}</td>
                {rowData.attributes.map(attr => (<td key={attr.id}>{attr.value}</td>))}
              </tr>);
          }
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
            bodyStyle={{padding: '10px'}}
            width={800}
          >
            <table>
              <thead>
                {this.renderTableHead(this.props.data)}
              </thead>
              <tbody>
                {this.renderTableContent(this.props.data)}
              </tbody>
            </table>
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
