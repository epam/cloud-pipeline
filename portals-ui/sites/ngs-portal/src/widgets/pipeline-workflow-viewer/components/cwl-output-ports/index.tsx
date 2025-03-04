import type { CWLStep } from '../../types';
import { CWLPort } from '../cwl-port';

type Props = {
  step: CWLStep;
  disabled?: boolean;
};

export function CWLOutputPorts({ step, disabled }: Props) {
  return (
    <div className="flex flex-col gap-1">
      {step.outputs.map((output, index) => (
        <CWLPort key={index} disabled={disabled} port={output} />
      ))}
    </div>
  );
}
