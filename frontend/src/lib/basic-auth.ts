export function basicAuthHeader(account: string, password: string): string {
  const bytes = new TextEncoder().encode(`${account}:${password}`);
  return `Basic ${btoa(Array.from(bytes, (byte) => String.fromCharCode(byte)).join(""))}`;
}
