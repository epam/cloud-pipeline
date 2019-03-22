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
import Graph from './Graph';
import GraphData from '../../../../../models/pipelines/Graph';
import {inject, observer} from 'mobx-react';
import LoadingView from '../../../../special/LoadingView';
import {Alert} from 'antd';
import cytoscape from 'cytoscape';
import cydagre from 'cytoscape-dagre';
import styles from './Graph.css';

@inject((inherit, params) => ({
  graph: new GraphData(params.pipelineId, params.version, true)
}))
@observer
export default class LuigiGraph extends Graph {

  _graphConfig = {
    container: null,
    boxSelectionEnabled: true,
    selectionType: 'single',
    wheelSensitivity: 0.1,
    elements: null
  };

  _cytoscapeGraph;
  _graphInitialized = false;
  _graphData;

  _zoomTimeoutMs = 50;
  _selectTimeoutMs = 50;
  _zoomTimeout = null;
  _selectTimeout = null;
  _zoomStep = 0.1;
  _maxZoomLevel = 1;
  _minZoomLevel;
  _width;
  _height;

  generateGraphStylesheet = (zoomLevel) => {
    return cytoscape.stylesheet()
      .selector('node')
      .css({
        'shape': 'roundrectangle',
        'width': 'label',
        'height': 'label',
        'content': 'data(name)',
        'text-wrap': 'wrap',
        'text-valign': 'center',
        'text-halign': 'center',
        'padding': 16,
        'font-size': 16,
        'font-family': 'monospace',
        'border-width': 1.0 / zoomLevel,
        'border-color': '#bbbbbb'
      })
      .selector('node[main = "true"]')
      .css({
        'background-color': '#c1bfbf',
        'border-color': '#515151',
        'border-width': 2.0 / zoomLevel
      })
      .selector('node[main = "false"]')
      .css({
        'background-color': '#e8e8e8'
      })
      .selector('node[status = "SUCCESS"]')
      .css({
        'background-color': '#A6FCA6',
        'border-color': '#049F04'
      })
      .selector('node[status = "RUNNING"]')
      .css({
        'background-color': '#FDFDA6',
        'border-color': '#C39500'
      })
      .selector('node[status = "FAILURE"]')
      .css({
        'background-color': '#FDBBBB',
        'border-color': '#ff0000'
      })
      .selector('node[status = "STOPPED"]')
      .css({
        'background-color': '#FDBBBB',
        'border-color': '#ff0000'
      })
      .selector('node:selected')
      .css({
        'background-color': '#7D7D7D',
        'color': '#ffffff'
      })
      .selector('node:selected[main = "false"]')
      .css({
        'border-width': 0
      })
      .selector('node:selected[status = "SUCCESS"]')
      .css({
        'background-color': '#0A810A',
        'color': '#ffffff',
        'border-width': 1.0 / zoomLevel,
        'border-color': '#0A810A'
      })
      .selector('node:selected[status = "RUNNING"]')
      .css({
        'background-color': '#C39500',
        'color': '#ffffff',
        'border-width': 1.0 / zoomLevel,
        'border-color': '#C39500'
      })
      .selector('node:selected[status = "FAILURE"]')
      .css({
        'background-color': '#980202',
        'color': '#ffffff',
        'border-width': 1.0 / zoomLevel,
        'border-color': '#980202'
      })
      .selector('node:selected[status = "STOPPED"]')
      .css({
        'background-color': '#980202',
        'color': '#ffffff',
        'border-width': 1.0 / zoomLevel,
        'border-color': '#980202'
      })
      .selector('edge')
      .css({
        'target-arrow-shape': 'triangle',
        'width': 1.0 / zoomLevel,
        'line-color': '#515151',
        'target-arrow-color': '#515151',
        'curve-style': 'bezier'
      });
  };

  applyStyleToGraph (zoomLevel, shouldLayout = false) {
    this._cytoscapeGraph.style(this.generateGraphStylesheet(zoomLevel));
    if (shouldLayout) {
      this._cytoscapeGraph.layout({
        name: 'dagre',
        rankDir: 'TB',
        fit: true
      });
    }
  }

  draw () {
    this.onFullScreenChanged();
  }

  renderGraph () {
    if (!this._graphData && !this._error && this.props.graph.pending) {
      return <LoadingView />;
    }
    if (this._error) {
      return <Alert type="warning" message={this.props.graph.error} />;
    }
    return <div className={styles.luigiGraphStyle} ref={this.initializeGraph} />;
  }

  onFullScreenChanged () {
    this._cytoscapeGraph.resize();
    this._width = this._cytoscapeGraph.width();
    this._height = this._cytoscapeGraph.height();
    this._cytoscapeGraph.minZoom(0.0001);
    this._cytoscapeGraph.fit(this._graphData, 10);
    this.applyStyleToGraph(this._cytoscapeGraph.zoom());
    this._minZoomLevel = this._cytoscapeGraph.zoom();
    this._cytoscapeGraph.minZoom(this._minZoomLevel);
    this.checkZoomButtonsState();
    this._cytoscapeGraph.resize();
  };

  onSelect = (event) => {
    if (this._selectTimeout) {
      clearTimeout(this._selectTimeout);
      this._selectTimeout = null;
    }
    this._selectTimeout = setTimeout(() => {
      this.onSelectHandler(event);
    }, this._selectTimeoutMs);
  };

  onSelectHandler (event) {
    const data = event.type === 'select' ? event.cyTarget.data() : null;
    if (this.props.onSelect) {
      this.props.onSelect(data);
    }
  };

  onZoom = () => {
    if (this._zoomTimeout) {
      clearTimeout(this._zoomTimeout);
      this._zoomTimeout = null;
    }
    this.checkZoomButtonsState();
    this._zoomTimeout = setTimeout(() => {
      this.onZoomHandler();
    }, this._zoomTimeoutMs);
  };

  onZoomHandler () {
    this.applyStyleToGraph(this._cytoscapeGraph.zoom());
    this.checkZoomButtonsState();
  }

  checkZoomButtonsState () {
    const canZoomIn = this._cytoscapeGraph.zoom() < this._cytoscapeGraph.maxZoom();
    const canZoomOut = this._cytoscapeGraph.zoom() > this._cytoscapeGraph.minZoom();
    this.setState({canZoomIn, canZoomOut});
  }

  correctZoomLevel (value) {
    if (!this.props.fitAllSpace) {
      return Math.min(this._maxZoomLevel, Math.max(this._minZoomLevel, value));
    } else {
      return Math.max(this._minZoomLevel, value);
    }
  }

  zoomIn () {
    this._cytoscapeGraph.zoom({
      level: this.correctZoomLevel(this._cytoscapeGraph.zoom() + this._zoomStep),
      renderedPosition: {x: this._width / 2.0, y: this._height / 2.0}
    });
  };

  zoomOut () {
    this._cytoscapeGraph.zoom({
      level: this.correctZoomLevel(this._cytoscapeGraph.zoom() - this._zoomStep),
      renderedPosition: {x: this._width / 2.0, y: this._height / 2.0}
    });
  };

  initializeGraph = (container) => {
    if (container) {
      const config = this._graphConfig;
      config.container = container;
      cydagre(cytoscape);
      this._cytoscapeGraph = cytoscape(config);
      if (!this.props.fitAllSpace) {
        this._cytoscapeGraph.maxZoom(this._maxZoomLevel);
      }
      this.buildGraphData();
    }
  };

  updateData () {
    this._graphInitialized = false;
    this.buildGraphData();
  }

  buildGraphData = () => {
    if (!this.props.graph.pending && !this.props.graph.error) {
      const {tasks} = this.props.graph.value;
      const createNode = ({taskName, toolName, id}, isMainTask, additionalInfo) => {
        const task = isMainTask ? `${taskName}.Result` : taskName;
        const name = toolName ? `${task}.${toolName}` : task;
        return {data: {id: `${id}`, name: name, main: `${isMainTask}`, ...additionalInfo}};
      };
      const createEdge = (source, target, id) => {
        return {
          data: {
            id: `${source}${target}${id}`,
            weight: 1,
            source: `${source}`,
            target: `${target}`
          }
        };
      };
      let edgeId = 1;
      this._graphData = {
        nodes: [],
        edges: []
      };
      for (const task of tasks) {
        const toolName = task.tool ? task.tool.image : '';
        const nodeData = {taskName: task.task.name, toolName: toolName, id: task.task.id};
        const additionalInfo = this.props.getNodeInfo ? this.props.getNodeInfo(task) : {};
        this._graphData.nodes.push(createNode(nodeData, !task.parents, additionalInfo));
        if (!task.parents) {
          continue;
        }
        for (const parent of task.parents) {
          if (!parent) {
            continue;
          }
          this._graphData.edges.push(createEdge(task.task.id, parent, edgeId));
          edgeId += 1;
        }
      }
      this.initializeGraphWithData();
    }
  };

  initializeGraphWithData = () => {
    if (this._cytoscapeGraph && this._graphData) {
      if (!this._graphInitialized) {
        this._cytoscapeGraph.json({elements: this._graphData});
        this._cytoscapeGraph.on('zoom', this.onZoom);
        this._cytoscapeGraph.on('select', 'node', this.onSelect);
        this._cytoscapeGraph.on('unselect', 'node', this.onSelect);

        this._width = this._cytoscapeGraph.width();
        this._height = this._cytoscapeGraph.height();
        this.applyStyleToGraph(this._cytoscapeGraph.zoom(), true);
        this._minZoomLevel = this._cytoscapeGraph.zoom();
        this._cytoscapeGraph.minZoom(this._minZoomLevel);

        if (this.props.selectedTaskId) {
          const node = this._cytoscapeGraph.$(`node[internalId = "${this.props.selectedTaskId}"]`);
          if (node) {
            node.select();
          }
        }
        this._graphInitialized = true;
        if (this.props.onGraphReady) {
          this.props.onGraphReady(this);
        }
      } else {
        this._cytoscapeGraph.json({elements: this._graphData});
      }
    }
  };

  getImage = () => {
    if (this._cytoscapeGraph) {
      return this._cytoscapeGraph.png();
    }
    return null;
  };

  base64Image () {
    if (this._cytoscapeGraph) {
      return this._cytoscapeGraph.png();
    }
    return '';
  };

  get imageSize () {
    return {
      width: this._width,
      height: this._height
    };
  };

  componentDidUpdate () {
    this._error = !!this.props.graph.error;
    this.buildGraphData();
  }

  componentWillUnmount () {
    if (this._cytoscapeGraph) {
      this._cytoscapeGraph.off('zoom', this.onZoom);
      this._cytoscapeGraph.off('select', this.onSelect);
      this._cytoscapeGraph.off('unselect', this.onSelect);
      this._cytoscapeGraph.destroy();
    }
  }
}
