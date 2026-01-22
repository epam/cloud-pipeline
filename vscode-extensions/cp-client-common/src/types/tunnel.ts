// Shared types for tunnel operations
export interface ITunnelInfo {
  pid: number;
  parentPid: number | null;
  owner: string;
  runId: number;
  localPort: number;
  remotePort: number;
}

export interface ITunnelConfig {
  runId: number;
  localPort?: number;
  remotePort: number;
  region?: string;
  direct?: boolean;
  ssh?: boolean;
}
