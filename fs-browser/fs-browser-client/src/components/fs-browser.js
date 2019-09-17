import React from 'react';
import {Provider} from 'mobx-react';
import {createHashHistory} from 'history';
import {
  Route,
  Router,
} from 'react-router-dom';
import {taskManager} from '../models';
import Browser from './browser';
import Header from './header';
import AlertsContainer, {messages} from './shared/alerts';

const history = createHashHistory({});

const stores = {
  history,
  messages,
  taskManager,
};

export default function () {
  return (
    <Provider {...stores}>
      <Router history={history}>
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            width: '100vw',
            height: '100vh',
            position: 'relative',
          }}
        >
          <AlertsContainer />
          <Route path="/" component={Header} />
          <Route path="/" component={Browser} />
        </div>
      </Router>
    </Provider>
  );
}
