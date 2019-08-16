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

const history = createHashHistory({});

const stores = {
  history,
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
            width: '100%',
            height: '100vh',
          }}
        >
          <Route path="/" component={Header} />
          <Route path="/" component={Browser} />
        </div>
      </Router>
    </Provider>
  );
}
