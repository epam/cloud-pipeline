import LifeCycleRules from '../../pipelines/browser/forms/life-cycle-rules';

interface TransitionRulesProps {
  storageId: number;
  readOnly?: boolean;
}

function TransitionRules({storageId, readOnly = false}: TransitionRulesProps) {
  return <LifeCycleRules storageId={storageId} readOnly={readOnly} />;
}

export {TransitionRules};
export type {TransitionRulesProps};
