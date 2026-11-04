import {generateRunValueComponent} from './common';
import {hasDockerImageDigest} from '../../../../utilities/docker-image-digest';

const RunDockerImageDigest = generateRunValueComponent(
  'actualDockerImageDigest',
  {
    check: hasDockerImageDigest
  }
);

export default RunDockerImageDigest;
