import React from 'react';
import {Provider} from 'mobx-react';
import {createHashHistory} from 'history';
import {
  Route,
  Router,
  Switch,
} from 'react-router-dom';

import styles from './fs-browser.css';

const history = createHashHistory({});

const stores = {
  history,
};

export default function () {
  return (
    <Provider {...stores}>
      <Router history={history}>
        <div
          id="qsp-container"
          className={styles.container}
        >
          FS Browser
        </div>
      </Router>
    </Provider>
  );
}
