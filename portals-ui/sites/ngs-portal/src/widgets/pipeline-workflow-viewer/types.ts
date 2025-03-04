import type { CWLVersion } from 'cwlts/mappings/v1.0';

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
