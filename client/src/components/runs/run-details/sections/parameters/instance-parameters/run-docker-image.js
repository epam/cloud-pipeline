import React from 'react';
import {generateRunValueComponent} from './common';
import DockerImageLink from '../../../../logs/DockerImageLink';

const RunDockerImage = generateRunValueComponent(
  'dockerImage',
  {
    render: (dockerImage) => <DockerImageLink path={dockerImage} />
  }
);

export default RunDockerImage;
