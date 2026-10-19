import {generateRunValueComponent} from './common';

// a run has a digest only if its pod was pinned to it at the launch
const RunDockerImageDigest = generateRunValueComponent(
  'actualDockerImageDigest',
  {
    check: (run) => Boolean(run && run.actualDockerImageDigest)
  }
);

export default RunDockerImageDigest;
