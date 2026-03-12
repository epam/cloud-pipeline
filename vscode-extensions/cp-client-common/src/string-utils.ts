export function indent(n: number, str: string): string {
    return str.split('\n').map(line => ' '.repeat(n) + line).join('\n');
}