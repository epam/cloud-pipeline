import type { CWLStep } from '../../types';
import { CWLPort } from '../cwl-port';

type Props = {
  step: CWLStep;
  disabled?: boolean;
};

export function CWLInputPorts({ step, disabled }: Props) {
  return (
    <div className="flex flex-col gap-1">
      {step.inputs.map((input, index) => (
        <CWLPort key={index} disabled={disabled} port={input} />
      ))}
    </div>
  );
}
