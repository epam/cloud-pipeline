import type { CWLVersion } from 'cwlts/mappings/v1.0';
import type { CommandLineToolModel } from 'cwlts/models';

export type ModelJson = {
  class: string;
  cwlVersion: CWLVersion;
  id: number;
};

export type CWLPort = {
  id?: number;
  type?: {
    type: string;
  };
};

export type CWLStep = {
  inputs: CWLPort[];
  outputs: CWLPort[];
};

export type CWLCommandLineToolModel = CommandLineToolModel & {
  steps?: CWLCommandLineToolModelStep[];
  docker: string;
  run: CWLCommandLineToolModel;
};

export type CWLCommandLineToolModelStep = Omit<CWLCommandLineToolModel, 'steps'>;
