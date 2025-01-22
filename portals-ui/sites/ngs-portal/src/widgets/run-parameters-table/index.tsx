import { useMemo } from 'react';
import type { CommonProps } from '@cloud-pipeline/components';
import type { Run } from '@cloud-pipeline/core';

type Props = CommonProps & {
  run?: Run;
};

export default function RunParametersTable({ run }: Props) {
  const filteredParameters = useMemo(() => {
    if (!run?.pipelineRunParameters) {
      return [];
    }
    return run.pipelineRunParameters?.filter(
      (parameter) => parameter.name && parameter.value,
    );
  }, [run]);
  return (
    <div>
      <table className="table-auto">
        <tbody>
          {filteredParameters.map((parameter) => (
            <tr key={parameter.name}>
              <td>
                <b>{parameter.name}:</b>
              </td>
              <td className="pl-2">{parameter.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
