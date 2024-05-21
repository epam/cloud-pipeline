import React from 'react';
import Button from 'antd/es/button';
import Collapse from 'antd/es/collapse';
import Form from 'antd/es/form';
import InputNumber from 'antd/es/input-number';
import Select from 'antd/es/select';
import message from 'antd/es/message';
import BucketInput from './components/bucket-input';
import InstanceInput from './components/instance-input';
import 'antd/es/button/style/css';
import 'antd/es/collapse/style/css';
import 'antd/es/form/style/css';
import 'antd/es/input/style/css';
import 'antd/es/input-number/style/css';
import 'antd/es/message/style/css';
import 'antd/es/select/style/css';
import './App.css';
import config from './api/config';
import * as pipeline from './api/launch';
import * as storage from './api/storage';

const formItemLayout = {
  labelCol: {
    xs: { span: 24 },
    sm: { span: 4 },
  },
  wrapperCol: {
    xs: { span: 24 },
    sm: { span: 20 },
  },
};

class App extends React.Component {
  state = {
    pending: false,
    link: null
  };

  pipelineRunningMessage;

  onSubmit = () => {
    this.props.form.validateFields(async (err, values) => {
      if (!err) {
        const options = {
          ...values
        };
        const storageInfo = await storage.info(config.launch.params.fastqs.storage);
        if (options.fastqs && !storageInfo.error && storageInfo.pathMask) {
          options.fastqs = `${storageInfo.pathMask}/${options.fastqs}`;
        }
        const workdirInfo = await storage.info(config.launch.params.workdir.storage);
        if (options.workdir && !workdirInfo.error && workdirInfo.pathMask) {
          options.workdir = `${workdirInfo.pathMask}/${options.workdir}`;
        }
        this.setState({
          pending: true
        });
        const hide = message.loading('Launching...', 0);
        const run = await pipeline.launch(options);
        hide();
        if (run && run.error) {
          message.error(run.error, 5);
          this.setState({pending: false});
        } else {
          this.waitForJobCompletion(run.id);
        }
      }
    });
  };

  waitForJobCompletion = (id) => {
    if (!this.pipelineRunningMessage) {
      this.pipelineRunningMessage = message.loading('Pipeline execution is in progress...', 0);
    }
    pipeline.getInfo(id)
      .then(info => {
        if (info.error) {
          message.error(info.error, 2);
        } else {
          const {status} = info;
          if (/^success$/i.test(status)) {
            // pipeline successfully finished
            return this.onPipelineFinished(info, true);
          } else if (/^failure$/i.test(status)) {
            return this.onPipelineFinished(info, false);
          } else {
            setTimeout(() => this.waitForJobCompletion(id), 5000);
          }
        }
      });
  };

  onPipelineFinished = async (run, success) => {
    if (this.pipelineRunningMessage) {
      this.pipelineRunningMessage();
      this.pipelineRunningMessage = null;
    }
    this.setState({pending: false});
    if (!success) {
      message.error('Pipeline failed', 10);
    } else {
      const file = config.launch.resultFile(run);
      if (file) {
        const workdirInfo = await storage.info(config.launch.params.workdir.storage);
        if (workdirInfo && workdirInfo.pathMask) {
          const regExp = new RegExp(`^${workdirInfo.pathMask}/(.+)$`, 'i');
          const e = regExp.exec(file);
          if (e && e.length > 1) {
            const relativePath = e[1];
            const payload = await storage.generatePreSignedUrl(config.launch.params.workdir.storage, relativePath);
            if (payload && payload.url) {
              this.openLink(payload.url);
              return;
            }
          }
        }
        message.error('Results file not found', 5);
      }
    }
  };

  openLink = (link) => {
    const hide = message.loading('Loading results...', 0);
    this.setState({
      link,
      loadingMessage: hide
    });
  };

  resultsLoaded = () => {
    const {loadingMessage} = this.state;
    if (loadingMessage) {
      loadingMessage();
    }
    this.setState({loadingMessage: null});
  };

  render () {
    const {getFieldDecorator} = this.props.form;
    const {pending, link} = this.state;
    if (link) {
      return (
        <iframe src={link} onLoad={this.resultsLoaded}>
          Your browser does not support iframe <br />
          Open <a href={link}>results</a>
        </iframe>
      );
    }
    return (
      <div className="App">
        <header className="App-header">
          <Form {...formItemLayout} className="App-form">
            <Collapse defaultActiveKey="parameters" bordered={false} style={{marginBottom: 10}}>
              <Collapse.Panel
                key="parameters"
                header={(
                  <div style={{textAlign: 'left'}}>
                    Parameters
                  </div>
                )}
              >
                <Form.Item label="Fastq folder">
                  {getFieldDecorator('fastqs', {
                    rules: [{ required: true, message: 'This field is required' }],
                  })(
                    <BucketInput
                      disabled={pending}
                      storageId={config.launch.params.fastqs.storage}
                    />
                  )}
                </Form.Item>
                <Form.Item label="Transcriptome">
                  {getFieldDecorator('transcriptome', {
                    rules: [{ required: true, message: 'This field is required' }],
                    initialValue: config.launch.params.transcriptome.default
                  })(
                    <Select
                      disabled={pending}
                      style={{width: '100%'}}
                      placeholder="Transcriptome"
                    >
                      {
                        Object.keys(config.launch.params.transcriptome.values).map(key => (
                          <Select.Option key={key} value={config.launch.params.transcriptome.values[key]}>
                            {key}
                          </Select.Option>
                        ))
                      }
                    </Select>
                  )}
                </Form.Item>
                <Form.Item label="Workdir">
                  {getFieldDecorator('workdir', {
                    rules: [{ required: true, message: 'This field is required' }],
                    initialValue: 'results'
                  })(
                    <BucketInput
                      disabled={pending}
                      storageId={config.launch.params.workdir.storage}
                    />
                  )}
                </Form.Item>
              </Collapse.Panel>
              <Collapse.Panel
                key="instance"
                header={(
                  <div style={{textAlign: 'left'}}>
                    Optional
                  </div>
                )}
              >
                <Form.Item label="Instance">
                  {getFieldDecorator('instance', {
                    initialValue: config.launch.params.instance
                  })(
                    <InstanceInput
                      disabled={pending}
                    />
                  )}
                </Form.Item>
                <Form.Item label="Disk">
                  {getFieldDecorator('disk', {
                    initialValue: config.launch.params.disk
                  })(
                    <InputNumber
                      disabled={pending}
                      style={{width: '100%'}}
                    />
                  )}
                </Form.Item>
              </Collapse.Panel>
            </Collapse>
            <Button
              onClick={this.onSubmit}
              disabled={pending}
            >
              LAUNCH
            </Button>
          </Form>
        </header>
      </div>
    );
  }
}

const AppForm = Form.create({})(App);

export default AppForm;
