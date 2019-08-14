import React from 'react';
import {Provider} from 'mobx-react';
import {createHashHistory} from 'history';
import {
  Route,
  Router,
} from 'react-router-dom';
import Browser from './browser';
import Header from './header';
import Download from './download';
import Upload from './upload';

const history = createHashHistory({});

const stores = {
  history,
};

export default function () {
  return (
    <Provider {...stores}>
      <Router history={history}>
        <Route path="/" component={Header} />
        <Route path="/" component={Browser} />
        <Route path="/upload" component={Upload} />
        <Route path="/download" component={Download} />
      </Router>
    </Provider>
  );
}
