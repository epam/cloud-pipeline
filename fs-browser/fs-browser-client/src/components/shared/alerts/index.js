import React from 'react';
import {observer} from 'mobx-react';
import messages from './messages';
import Message from './message';
import styles from './alerts.css';

@observer
class AlertsContainer extends React.Component {
  render() {
    return (
      <div
        className={styles.container}
      >
        {
          messages.items.map(item => (
            <Message
              key={item.id}
              title={item.title}
              type={item.type}
            />
          ))
        }
      </div>
    );
  }
}

export default AlertsContainer;
export {messages};
