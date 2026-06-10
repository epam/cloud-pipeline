import type {CSSProperties} from 'react';

export type PermissionsMask = number;

export type MaskedObject = {
  mask: PermissionsMask;
};

export type LoadableObject = {
  pending: boolean;
  loaded: boolean;
  error?: string;
};

export type CommonProps = {
  className?: string;
  style?: CSSProperties;
};
