import { ProxyEndpoint } from "../types";

export function parseProxyUrl(
    proxyUrl?: string,
    getAuth?: () => { username?: string; password?: string }
): ProxyEndpoint | undefined {
    if (!proxyUrl) return undefined;

    let host: string;
    let port: number;

    try {
        // Try parsing as URL (with or without protocol)
        const urlStr = proxyUrl && proxyUrl.startsWith('http') ? proxyUrl : `http://${proxyUrl}`;
        const proxyUrlObj = new URL(urlStr);
        host = proxyUrlObj.hostname;
        port = proxyUrlObj.port ? parseInt(proxyUrlObj.port, 10) : 80;
    } catch {
        // Fallback: parse as host:port
        const [hostPart, portPart] = proxyUrl.split(':');
        host = hostPart;
        port = portPart ? parseInt(portPart, 10) : 80;
    }

    const auth = getAuth?.();

    return {
        host,
        port,
        username: auth?.username,
        password: auth?.password,
    }
}