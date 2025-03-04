import { useMemo, useRef, useState } from 'react';
import classNames from 'classnames';
import { base64toString } from '@cloud-pipeline/core';
import type { WorkflowModel } from 'cwlts/models';
import { WorkflowFactory, isType } from 'cwlts/models';
import yaml from 'js-yaml';
import {
  SVGArrangePlugin,
  SVGValidatePlugin,
  SVGNodeMovePlugin,
  SVGEdgeHoverPlugin,
  SVGPortDragPlugin,
  Workflow,
  SelectionPlugin,
  ZoomPlugin,
  DeletionPlugin,
} from '@cwl-svg';
import type { Workflow as WorkflowClass } from 'cwl-svg';
import CWLProperties from './components/cwl-properties';
import { CWLCommandLineTool } from './components/cwl-command-line-tool';
import { ArrowsPointingInIcon, ArrowsPointingOutIcon } from '@heroicons/react/24/outline';
import type { CommandLineTool, Process } from 'cwlts/mappings/v1.0';
import type { CommonProps } from '@cloud-pipeline/components';
import type { ModelJson } from './types';
import 'cwl-svg/src/assets/styles/theme.scss';
import 'cwl-svg/src/plugins/selection/theme.scss';
import './styles.css';
import { PageSpinner } from '../../shared/ui';

type Props = CommonProps & {
  mainFile: string | undefined;
  pending?: boolean;
};

export function PipelineWorkflowViewer({ pending, className, mainFile }: Props) {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const [model, setModel] = useState<WorkflowModel | Pick<WorkflowModel, 'cwlVersion'> | undefined>(undefined);
  const [workflow, setWorkflow] = useState<WorkflowClass | undefined>();
  const [selected, setSelected] = useState<CommandLineTool>();
  const [fullScreen, setFullScreen] = useState(false);

  const initializeModel = (modelJson: ModelJson): WorkflowModel | Pick<WorkflowModel, 'cwlVersion'> => {
    if (!modelJson) {
      return {
        cwlVersion: 'v1.0',
      };
    }
    if (/^CommandLineTool$/i.test(modelJson.class ?? '')) {
      const model = WorkflowFactory.from({
        cwlVersion: modelJson.cwlVersion ?? 'v1.0',
        outputs: [],
        inputs: [],
      } as Process);
      const step = model.addStepFromProcess(modelJson as unknown as Process);
      if (modelJson.id) {
        model.changeStepId(step, `${modelJson.id}`);
      }
      model.steps[0].in.forEach((input) => {
        if (isType(input, ['File', 'Directory'])) {
          model.createInputFromPort(input);
        } else {
          model.exposePort(input);
        }
      });
      model.steps[0].out.forEach((output) => {
        model.createOutputFromPort(output);
      });
      return model;
    }
    return WorkflowFactory.from(modelJson as unknown as Process);
  };

  const initializeWorkflowEventListeners = (
    workflow: unknown,
    model: WorkflowModel | Pick<WorkflowModel, 'cwlVersion'>,
  ) => {
    if (!workflow) {
      return;
    }
    /* eslint-disable */
    const selectionPlugin = workflow.getPlugin(SelectionPlugin);  
    selectionPlugin.registerOnSelectionChange((selectedNode: unknown) => {  
      if (model && selectedNode?.dataset?.id) {  
        const selection = model.findById(selectedNode.dataset.id);  
        setSelected(selection);
      } else {
        setSelected(undefined);
      }
    });
    /* eslint-enable */
  };

  const initializeGraph = (node: SVGSVGElement) => {
    if (model || workflow) {
      return;
    }
    try {
      if (mainFile && node) {
        svgRef.current = node;
        const response = yaml.load(base64toString(mainFile, true));
        const model = initializeModel(response as ModelJson);
        setModel(model);
        /* eslint-disable */
        const workflow = new Workflow({
          svgRoot: svgRef.current,
          model: model,
          plugins: [
            new SVGArrangePlugin(),
            new SVGPortDragPlugin(),
            new SVGNodeMovePlugin(),
            new SVGEdgeHoverPlugin(),
            new SVGValidatePlugin(),
            new SelectionPlugin(),
            new ZoomPlugin(),
            new DeletionPlugin(),
          ],
        }) as WorkflowClass;
        /* eslint-enable */
        setWorkflow(workflow);
        workflow.fitToViewport();
        initializeWorkflowEventListeners(workflow, model);
        workflow.draw();
      }
    } catch (error) {
      console.warn('Error parsing CWL:', error instanceof Error ? error.message : error);
      setWorkflow(undefined);
    }
  };

  const commandLineTool = useMemo(() => {
    /* eslint-disable */
    if (!selected?.run) {
      return undefined;
    }
    return selected.run;
    /* eslint-enable */
  }, [selected]) as CommandLineTool;

  const onFullScreenChanged = () => {
    setFullScreen((prev) => !prev);
    if (workflow) {
      setTimeout(() => workflow.fitToViewport());
    }
  };

  const cwlProperties = useMemo(() => {
    if (commandLineTool) {
      return [
        {
          key: 'properties',
          title: 'Command Line Tool properties',
          buttonTitle: 'Properties',
          component: <CWLCommandLineTool disabled={true} tool={commandLineTool} step={selected} />,
        },
      ];
    }
    return [];
  }, [commandLineTool, selected]);

  if (pending) {
    return <PageSpinner />;
  }

  return (
    <div
      className={classNames(className, 'cwl-resizer', {
        ['full-screen']: fullScreen,
      })}>
      <div className="cwl-container">
        <CWLProperties title="Command Line Tool properties" buttonTitle="Properties" properties={cwlProperties} />
        <svg className="cwl-workflow h-full w-full flex flex-grow" ref={initializeGraph} id="cwl-workflow" />
        <div
          className={classNames('expand-button', {
            ['full-screen']: fullScreen,
          })}
          onClick={onFullScreenChanged}>
          {fullScreen ? (
            <ArrowsPointingInIcon className="cursor-pointer h-4 w-4" />
          ) : (
            <ArrowsPointingOutIcon className="cursor-pointer h-4 w-4" />
          )}
        </div>
      </div>
    </div>
  );
}
