import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import classNames from 'classnames';
import {Checkbox} from 'antd';
import DockerImageSelector from './docker-image-selector';
import PipelineSelector from './pipeline-selector';

const sectionStyles = {marginTop: 10, paddingBottom: 10};

@inject('pipelines', 'dockerRegistries')
@observer
class ConfigureRunAsPermissions extends React.Component {
  state = {
    name: undefined,
    isPrincipal: undefined,
    rest: {},
    pipelines: [],
    pipelinesAllowed: false,
    tools: [],
    toolsAllowed: false
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.sid !== this.props.sid) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {sid} = this.props;
    const {
      name,
      isPrincipal,
      pipelinesAllowed = true,
      toolsAllowed = true,
      pipelines = [],
      tools = [],
      ...rest
    } = sid || {};
    this.setState({
      name,
      isPrincipal,
      rest,
      pipelines,
      pipelinesAllowed,
      tools,
      toolsAllowed
    });
  };

  @computed
  get availablePipelines () {
    const {pipelines} = this.props;
    if (
      pipelines.loaded &&
      pipelines.loaded
    ) {
      return (pipelines.value || []).map(s => s);
    }
    return [];
  }

  @computed
  get availableTools () {
    const {dockerRegistries} = this.props;
    const result = [];
    if (dockerRegistries.loaded) {
      const {registries = []} = dockerRegistries.value || {};
      for (let r = 0; r < registries.length; r++) {
        const registry = registries[r];
        const {groups = []} = registry;
        for (let g = 0; g < groups.length; g++) {
          const group = groups[g];
          const {tools: groupTools = []} = group;
          if (groupTools.length > 0) {
            for (let t = 0; t < groupTools.length; t++) {
              const tool = groupTools[t];
              result.push({
                dockerImage: `${registry.path}/${tool.image}`,
                id: tool.id
              });
            }
          }
        }
      }
    }
    return result;
  }

  getPayload = () => {
    const {name, isPrincipal, rest, toolsAllowed, tools, pipelinesAllowed, pipelines} = this.state;
    if (name) {
      return {
        name,
        isPrincipal,
        toolsAllowed,
        tools,
        pipelinesAllowed,
        pipelines,
        ...rest
      };
    }
    return undefined;
  }

  onChangePipelinesAllowed = (pipelinesAllowedEvent) => {
    const allPipelinesAllowed = pipelinesAllowedEvent.target.checked;
    this.setState({
      pipelinesAllowed: allPipelinesAllowed
    }, this.onChange);
  };

  onChangePipelines = (pipelines) => {
    this.setState({
      pipelines
    }, this.onChange);
  };

  onChangeToolsAllowed = (toolsAllowedEvent) => {
    const allToolsAllowed = toolsAllowedEvent.target.checked;
    this.setState({
      toolsAllowed: allToolsAllowed
    }, this.onChange);
  };

  onChangeTools = (tools) => {
    this.setState({
      tools
    }, this.onChange);
  };

  onChange = () => {
    const payload = this.getPayload();
    const {
      onChange
    } = this.props;
    if (payload && typeof onChange === 'function') {
      onChange(payload);
    }
  };

  renderPipelineSelector = (pipelineId, idx) => {
    const {disabled} = this.props;
    const {pipelines} = this.state;
    const excludedPipelineIds = new Set(pipelines.filter((t) => t !== pipelineId));
    const excludedPipelines = this.availablePipelines
      .filter((t) => excludedPipelineIds.has(t.id))
      .map((t) => t.id);
    const onChange = (newToolId) => {
      const newPipelines = pipelines.slice();
      newPipelines[idx] = newToolId;
      this.onChangePipelines(newPipelines);
    };
    const onRemove = () => {
      const newPipelines = pipelines.slice();
      newPipelines.splice(idx, 1);
      this.onChangePipelines(newPipelines);
    };
    return (
      <PipelineSelector
        key={`tool-${idx}`}
        style={{marginTop: 5}}
        disabled={disabled}
        pipelineId={pipelineId}
        pipelinesToExclude={excludedPipelines}
        showDelete
        onChange={onChange}
        onRemove={onRemove}
      />
    );
  };

  renderPipelinesSection = () => {
    const {disabled} = this.props;
    const {pipelinesAllowed, pipelines} = this.state;
    const onAddPipeline = () => {
      this.onChangePipelines(pipelines.slice().concat([undefined]));
    };
    return (
      <div style={sectionStyles}>
        <div>
          <Checkbox
            checked={pipelinesAllowed}
            disabled={disabled}
            onChange={this.onChangePipelinesAllowed}>
            Allow all pipelines
          </Checkbox>
        </div>
        {
          !pipelinesAllowed && (
            <div style={{marginTop: 5}}>
              {
                pipelines.map(this.renderPipelineSelector)
              }
              <div style={{marginTop: 5}}>
                {
                  disabled
                    ? <span className="cp-text-not-important">Add pipeline</span>
                    : <a onClick={onAddPipeline}>Add pipeline</a>
                }
              </div>
            </div>
          )
        }
      </div>
    );
  };

  renderToolSelector = (toolId, idx) => {
    const {disabled} = this.props;
    const {tools} = this.state;
    const excludeToolIds = new Set(tools.filter((t) => t !== toolId));
    const excludeTools = this.availableTools
      .filter((t) => excludeToolIds.has(t.id))
      .map((t) => t.dockerImage);
    const onChange = (newToolId) => {
      const newTools = tools.slice();
      newTools[idx] = newToolId;
      this.onChangeTools(newTools);
    };
    const onRemove = () => {
      const newTools = tools.slice();
      newTools.splice(idx, 1);
      this.onChangeTools(newTools);
    };
    return (
      <DockerImageSelector
        key={`tool-${idx}`}
        style={{marginTop: 5}}
        disabled={disabled}
        toolId={toolId}
        imagesToExclude={excludeTools}
        showDelete
        onChange={onChange}
        onRemove={onRemove}
      />
    );
  };

  renderToolsSection = () => {
    const {disabled} = this.props;
    const {toolsAllowed, tools} = this.state;
    const onAddDockerImage = () => {
      this.onChangeTools(tools.slice().concat([undefined]));
    };
    return (
      <div style={sectionStyles}>
        <div>
          <Checkbox
            checked={toolsAllowed}
            disabled={disabled}
            onChange={this.onChangeToolsAllowed}>
            Allow all docker images
          </Checkbox>
        </div>
        {
          !toolsAllowed && (
            <div style={{marginTop: 5}}>
              {
                tools.map(this.renderToolSelector)
              }
              <div style={{marginTop: 5}}>
                {
                  disabled
                    ? <span className="cp-text-not-important">Add docker image</span>
                    : <a onClick={onAddDockerImage}>Add docker image</a>
                }
              </div>
            </div>
          )
        }
      </div>
    );
  };

  render () {
    const {
      className,
      style,
      sid
    } = this.props;
    if (!sid) {
      return null;
    }
    return (
      <div
        className={classNames(className)}
        style={style}
      >
        {this.renderPipelinesSection()}
        <div className="cp-divider horizontal" />
        {this.renderToolsSection()}
      </div>
    );
  }
}

ConfigureRunAsPermissions.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  disabled: PropTypes.bool,
  sid: PropTypes.object,
  onChange: PropTypes.func
};

export default ConfigureRunAsPermissions;
