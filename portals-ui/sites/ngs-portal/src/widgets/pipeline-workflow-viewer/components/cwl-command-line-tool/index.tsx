import { Input } from 'antd';
import { useMemo } from 'react';
import { CWLInputPorts } from '../cwl-input-ports';
import { CWLOutputPorts } from '../cwl-output-ports';
import type { CommandLineTool } from 'cwlts/mappings/v1.0';
import type { CWLStep } from '../../types';

type Props = {
  disabled?: boolean;
  tool: CommandLineTool;
  step: unknown;
};

export function CWLCommandLineTool({ tool, disabled = true }: Props) {
  const currentStep = useMemo(() => {
    if (!tool) {
      return null;
    }
    let step = tool;
    if (/^workflow$/i.test(tool.class)) {
      // eslint-disable-next-line
      step = (tool.steps || [])[0];
    }
    return step;
  }, [tool]) as unknown as CWLStep;

  const renderDockerImage = () => {
    if (!currentStep) {
      return null;
    }
    const { docker } = currentStep;
    if (!docker) {
      return null;
    }
    // eslint-disable-next-line
    const value = (docker.dockerPull as string) ?? '';
    return (
      <div className="flex flex-col">
        <b>Docker image</b>
        <span className="break-all">{value}</span>
      </div>
    );
  };

  const renderBaseCommand = () => {
    if (!currentStep) {
      return null;
    }
    const { baseCommand = [] } = currentStep;
    return (
      <div className="flex flex-col">
        <b>Base command</b>
        {Array.isArray(baseCommand) ? (
          <Input.TextArea className="font-mono" variant="borderless" value={baseCommand.join('\n')} autoSize />
        ) : null}
      </div>
    );
  };

  const renderInputPorts = () => {
    if (!currentStep) {
      return null;
    }
    return (
      <div>
        <div>
          <b>Input ports</b>
        </div>
        <CWLInputPorts disabled={disabled} step={currentStep} />
      </div>
    );
  };

  const renderOutputPorts = () => {
    if (!currentStep) {
      return null;
    }
    return (
      <div>
        <div>
          <b>Output ports</b>
        </div>
        <CWLOutputPorts disabled={disabled} step={currentStep} />
      </div>
    );
  };

  return (
    <div className="flex flex-col gap-1 max-h-[50vh] overflow-auto">
      {renderDockerImage()}
      {renderBaseCommand()}
      {renderInputPorts()}
      {renderOutputPorts()}
    </div>
  );
}
