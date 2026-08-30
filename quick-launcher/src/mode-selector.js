import React from 'react';
import App from './app';
import {useSettings} from './components/use-settings';
import FolderApp from './folder-app';
import LoadingIndicator from './components/shared/loading-indicator';
import './mode-selector.css';

function ModeSelector (props) {
  const settings = useSettings();
  const {launch} = props || {};
  if (!settings) {
    return (
      <div className="centered">
        <div className="loading">
          <LoadingIndicator/>
          <span>Loading...</span>
        </div>
      </div>
    );
  }
  const {applicationsMode} = settings;
  if (!launch && /^folder$/i.test(applicationsMode)) {
    return (
      <FolderApp {...props} />
    );
  }
  return (<App {...props} />);
}

export default ModeSelector;
