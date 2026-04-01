import type { RunDetailPayload } from './api';

/**
 * Mirrors web UI `diskSizeAllowsPause` / `getDiskSizeThresholdConfigurationRestrictions`
 * (preference `launch.job.disk.size.thresholds`).
 */
export function diskSizeAllowsPause(
  thresholdsJson: string | undefined,
  nodeDisk: number | undefined,
  preferenceFetchSucceeded: boolean
): boolean {
  const diskSize = nodeDisk !== undefined && !Number.isNaN(Number(nodeDisk)) ? Number(nodeDisk) : 0;
  if (!diskSize) {
    return true;
  }
  if (!preferenceFetchSucceeded) {
    return false;
  }
  let thresholds: Array<{ threshold?: number; pause?: boolean }> = [];
  if (thresholdsJson) {
    try {
      const parsed = JSON.parse(thresholdsJson) as unknown;
      if (Array.isArray(parsed)) {
        thresholds = parsed as Array<{ threshold?: number; pause?: boolean }>;
      }
    } catch {
      /* ignore */
    }
  }
  const getValue = (v: boolean | undefined): boolean => (v === undefined ? true : v);
  const merged = thresholds
    .filter((c) => (c.threshold ?? 0) <= diskSize)
    .reduce(
      (result, configuration) => ({
        pause: getValue(result.pause) && getValue(configuration.pause),
      }),
      { pause: true as boolean }
    );
  return merged.pause === true;
}

/**
 * Same rules as web UI `canPauseRun` in `client/src/components/runs/actions/stopRun.js`
 * (state only; owner / ACL are assumed OK for runs listed for the signed-in user).
 */
export function canPauseRunDetail(
  run: RunDetailPayload,
  diskThresholdsJson: string | undefined,
  diskPreferenceFetchSucceeded: boolean
): boolean {
  const status = (run.status || '').toLowerCase();
  const commit = (run.commitStatus || '').toLowerCase();
  if (status !== 'running' || commit === 'committing') {
    return false;
  }
  if (!run.initialized || !run.podIP) {
    return false;
  }
  const inst = run.instance;
  if (!inst || inst.spot === undefined || inst.spot) {
    return false;
  }
  if ((run.nodeCount ?? 0) > 0) {
    return false;
  }
  if (run.parentRunId !== undefined && run.parentRunId !== null && run.parentRunId > 0) {
    return false;
  }
  const hasAutoscale = (run.pipelineRunParameters ?? []).some(
    (p) => p.name === 'CP_CAP_AUTOSCALE' && String(p.value ?? '') === 'true'
  );
  if (hasAutoscale) {
    return false;
  }
  const nodeDisk = inst.nodeDisk !== undefined ? Number(inst.nodeDisk) : 0;
  return diskSizeAllowsPause(diskThresholdsJson, nodeDisk, diskPreferenceFetchSucceeded);
}

/**
 * Same state checks as web UI `getRunActions.js` for PAUSED → RESUME.
 */
export function canResumeRunDetail(run: RunDetailPayload): boolean {
  if ((run.status || '').toUpperCase() !== 'PAUSED') {
    return false;
  }
  if (!run.initialized) {
    return false;
  }
  if ((run.nodeCount ?? 0) > 0) {
    return false;
  }
  if (run.parentRunId !== undefined && run.parentRunId !== null && run.parentRunId > 0) {
    return false;
  }
  const inst = run.instance;
  if (!inst || inst.spot === undefined || inst.spot) {
    return false;
  }
  return true;
}

export function isSshBlockedByPauseState(status: string | undefined): boolean {
  const u = (status || '').toUpperCase();
  return u === 'PAUSED' || u === 'PAUSING' || u === 'RESUMING';
}
