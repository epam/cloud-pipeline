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
import Download from './download';
import Upload from './upload';

const history = createHashHistory({});

const stores = {
  history,
  taskManager,
};

export default function () {
  return (
    <Provider {...stores}>
      <Router history={history}>
        <Route path="/" component={Header} />
        <Route path="/" component={Browser} />
        <Route path="/upload/:taskId" component={Upload} />
        <Route path="/download/:taskId" component={Download} />
      </Router>
    </Provider>
  );
}
