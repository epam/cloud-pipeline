import "core-js/stable";
import "regenerator-runtime/runtime";
import "isomorphic-fetch";
import React from 'react';
import ReactDOM from 'react-dom';
import {Router} from '@reach/router';
import ModeSelector from './mode-selector';
import {ContextMenuOverlay} from './components/shared/context-menu';

if (BUILD_DESCRIPTION) {
  console.log('APPLICATION VERSION', BUILD_VERSION, `(${BUILD_DESCRIPTION})`);
} else {
  console.log('APPLICATION VERSION', BUILD_VERSION);
}

ReactDOM.render(
  (
    <ContextMenuOverlay>
      <Router>
        <ModeSelector path="/" exact />
        <ModeSelector path="/*" launch />
      </Router>
    </ContextMenuOverlay>
  ),
  document.getElementById('root')
);
