import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {TitleSection} from '../index';
import styles from './parameter-group.css';

@observer
export default class ParameterGroup extends React.Component {
  renderParameters = () => {
    const {formStore} = this.props;
    const {parameters} = formStore;
    if (!parameters || Object.keys(parameters).length === 0) {
      return <div>No parameters found.</div>;
    }
    return (
      <table>
        <tbody>
          {Object.entries(parameters).map(([parameterName, parameter]) => {
            return (
              <tr key={parameterName}>
                <td style={{textAlign: 'end', paddingRight: 5}}>
                  <b>{parameterName}:</b>
                </td>
                <td>
                  <span>{`${parameter.value}`}</span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    );
  };

  render () {
    return (
      <div>
        <TitleSection title="Parameters" />
        <div className={styles.indent}>
          {this.renderParameters()}
        </div>
      </div>
    );
  }
}

ParameterGroup.propTypes = {
  formStore: PropTypes.object
};
