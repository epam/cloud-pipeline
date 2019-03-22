/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import GenerateDownloadUrlRequest from '../../../models/dataStorage/GenerateDownloadUrl';
import LoadingView from '../../special/LoadingView';
import {Alert} from 'antd';
import Miew from 'miew';
import styles from './EmbeddedMiew.css';

@observer
export default class EmbeddedMiew extends React.Component {

  static propTypes = {
    pdb: PropTypes.string,
    s3item: PropTypes.object,
    previewMode: PropTypes.bool,
    onMessage: PropTypes.func,
    onError: PropTypes.func,
    onInit: PropTypes.func
  };

  state = {
    pdb: null,
    error: null
  };

  viewer;
  container;

  generateUtlRequest;

  initializeMiew = (container) => {
    if (container) {
      this.container = container;
      this.updatePdb();
    }
  };

  render () {
    if (this.state.error) {
      return <Alert type="error" message={this.state.error} />;
    }
    if (!this.state.pdb) {
      return <LoadingView />;
    }
    return <div className={styles.miewContainer} ref={this.initializeMiew} />;
  }

  updatePdb = () => {
    if (this.container && this.state.pdb) {
      if (this.viewer) {
        this.viewer.logger.removeEventListener('message');
      }
      this.viewer = new Miew({
        container: this.container,
        load: this.state.pdb,
        rep: {
          mode: 'CA',
          colorer: 'EL'
        }
      });
      if (this.viewer.init()) {
        this.viewer.enableHotKeys(false);
        this.viewer.logger.addEventListener('message', (e) => {
          if (e.level === 'error' && this.props.onError) {
            this.props.onError(e.message);
          }
          if (e.level === 'error' && this.props.previewMode) {
            this.setState({error: e.message});
          }
          if (this.props.onMessage) {
            this.props.onMessage(e);
          }
        });
        this.viewer.run();
        if (this.props.previewMode) {
          this.viewer.set({
            axes: false,
            fps: false
          });
        } else {
          this.viewer.set({
            fps: false
          });
        }
      }
    }
  };

  generateUrl = async (s3item, callback) => {
    this.generateUtlRequest = new GenerateDownloadUrlRequest(s3item.storageId, s3item.path, s3item.version);
    await this.generateUtlRequest.fetch();
    if (this.generateUtlRequest.error) {
      callback(null, this.generateUtlRequest.error);
    } else {
      callback(this.generateUtlRequest.value.url, null);
    }
  };

  setPdb = (pdb, error) => {
    this.setState({
      pdb: pdb,
      error: error
    }, () => this.updatePdb());
  };

  pdbChanged = () => {
    if (this.props.pdb) {
      this.setPdb(this.props.pdb, null);
    } else if (this.props.s3item) {
      this.generateUrl(this.props.s3item, this.setPdb);
    } else {
      this.setPdb(null, null);
    }
  };

  componentDidMount () {
    this.pdbChanged();
  }

  componentDidUpdate (prevProps) {
    const s3ItemChanged = () => {
      if (!prevProps.s3item && !this.props.s3item) {
        return false;
      }
      if (!!prevProps.s3item && !this.props.s3item) {
        return false;
      }
      if (!prevProps.s3item && !!this.props.s3item) {
        return false;
      }
      return prevProps.s3item.storageId !== this.props.s3item.storageId ||
          prevProps.s3item.path !== this.props.s3item.path ||
          prevProps.s3item.version !== this.props.s3item.version;
    };
    if (prevProps.pdb !== this.props.pdb || s3ItemChanged()) {
      this.pdbChanged();
    }
  }

  setRep = (mode, color) => {
    if (this.viewer) {
      this.viewer.rep({
        mode,
        colorer: color
      });
    }
  };

  execute = (command, success, error) => {
    if (this.viewer) {
      this.viewer.script(command, success, error);
    }
  };

}
