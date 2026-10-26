export function isJsonNumberLiteral(text: string): boolean {
  return /^-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?$/.test(text);
}

export function isUuid(text: string): boolean {
  return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(text);
}

export function resourceCreateBody(name: string, capacityText: string): string {
  if (!isJsonNumberLiteral(capacityText)) throw new Error("최초 수량은 숫자 표기여야 합니다");
  return `{"name":${JSON.stringify(name)},"capacity":${capacityText}}`;
}

export function reservationCreateBody(resourceId: string): string {
  return JSON.stringify({ resourceId });
}
