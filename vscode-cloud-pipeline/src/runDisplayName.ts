/**
 * Compact tool/image label for tree and quick pick: drop leading registry host.
 * E.g. `registry:443/library/tool:version` → `library/tool:version`
 */
export function shortRegistryImageRef(ref: string): string {
  const t = ref.trim();
  if (!t) {
    return t;
  }
  let m = /^[\w.]+:\d+\/(.+)$/.exec(t);
  if (m) {
    return m[1];
  }
  m = /^([a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?\.[a-zA-Z]{2,}|localhost)(?::\d+)?\/(.+)$/.exec(t);
  if (m) {
    return m[2];
  }
  return t;
}

export function runListDisplayName(pipelineName: string | undefined, dockerImage: string | undefined): string {
  const raw = (pipelineName ?? dockerImage ?? 'CMD').trim();
  if (!raw || raw === 'CMD') {
    return raw || 'CMD';
  }
  return shortRegistryImageRef(raw);
}
