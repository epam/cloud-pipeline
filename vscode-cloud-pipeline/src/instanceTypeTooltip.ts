import type { GpuDevicePayload, InstanceTypePayload } from './api';

/** Escape text used inside Markdown bold segments (minimal). */
function mdBoldSafe(s: string): string {
  return s.replace(/\*/g, '\\*');
}

function formatMemory(memory: number | undefined, unit: string | undefined): string | undefined {
  if (memory === undefined || memory === null || Number.isNaN(Number(memory))) {
    return undefined;
  }
  const u = (unit ?? 'GiB').trim() || 'GiB';
  return `${memory} ${u}`;
}

function formatGpu(
  gpu: number | undefined,
  device: GpuDevicePayload | null | undefined
): string | undefined {
  const parts: string[] = [];
  if (gpu !== undefined && gpu > 0) {
    parts.push(String(gpu));
  }
  if (device) {
    const label = [device.manufacturer, device.name].filter(Boolean).join(' ').trim();
    if (label) {
      parts.push(label);
    }
  }
  if (parts.length === 0) {
    return undefined;
  }
  return parts.join(' x ');
}

export function findMatchingInstanceType(
  types: InstanceTypePayload[],
  nodeType: string
): InstanceTypePayload | undefined {
  const nt = nodeType.trim();
  if (!nt) {
    return undefined;
  }
  return (
    types.find((t) => (t.name ?? '').trim() === nt) ??
    types.find((t) => (t.sku ?? '').trim() === nt)
  );
}

export function vcpuOf(t: InstanceTypePayload): number | undefined {
  const v = t.vcpu ?? t.vCPU;
  if (v === undefined || v === null) {
    return undefined;
  }
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
}

/**
 * Markdown for tree tooltip: optional cloud, then one **Node type:** line with hardware in brackets.
 * Example: `Node type: g5.xlarge (CPU: 4, Memory: 16 GiB, GPU: 1 x NVIDIA A10G)`.
 */
export function formatInstanceTooltipBlock(
  catalog: InstanceTypePayload | undefined,
  fallbackNodeType: string,
  cloudProvider?: string
): string {
  const lines: string[] = [];
  const prov = (cloudProvider ?? '').trim();
  if (prov) {
    lines.push(`**Cloud:** ${mdBoldSafe(prov)}`);
  }

  const nameFromCatalog = (catalog?.name ?? '').trim();
  const fb = fallbackNodeType.trim();
  const displayName = nameFromCatalog || fb;

  if (displayName) {
    const hardwareParts: string[] = [];
    if (catalog) {
      const vcpu = vcpuOf(catalog);
      if (vcpu !== undefined) {
        hardwareParts.push(`CPU: ${vcpu}`);
      }
      const mem = formatMemory(catalog.memory, catalog.memoryUnit);
      if (mem) {
        hardwareParts.push(`Memory: ${mdBoldSafe(mem)}`);
      }
      const gpuStr = formatGpu(catalog.gpu, catalog.gpuDevice);
      if (gpuStr) {
        hardwareParts.push(`GPU: ${mdBoldSafe(gpuStr)}`);
      }
    }
    const suffix =
      hardwareParts.length > 0 ? ` (${hardwareParts.join(', ')})` : '';
    lines.push(`**Node type:** ${mdBoldSafe(displayName)}${suffix}`);
  }

  return lines.join('\n\n');
}

export type InstanceCatalogFetchKey = string;

export function instanceCatalogKey(
  regionId: number | null | undefined,
  spot: boolean,
  toolInstances: boolean
): InstanceCatalogFetchKey {
  return JSON.stringify({
    r: regionId ?? null,
    s: spot,
    t: toolInstances,
  });
}
