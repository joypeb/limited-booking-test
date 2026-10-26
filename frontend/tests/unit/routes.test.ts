import { describe, expect, it } from "vitest";
import { matchRoute } from "../../src/lib/proxy/routes";

const uuid = "aBcDeF01-2345-6789-aBcD-eF0123456789";

describe("전달 경로 허용 목록", () => {
  it.each([
    ["POST", ["resources"], "/api/resources"],
    ["GET", ["resources", uuid], `/api/resources/${uuid}`],
    ["POST", ["reservations"], "/api/reservations"],
    ["GET", ["reservations", uuid], `/api/reservations/${uuid}`],
    ["POST", ["reservations", uuid, "cancel"], `/api/reservations/${uuid}/cancel`],
  ])("%s %j를 정확한 업스트림 경로와 UUID 원문으로 전달한다", (method, segments, upstreamPath) => {
    expect(matchRoute(method as string, segments as string[])).toEqual({ method, upstreamPath });
  });

  it.each<[string, string[]]>([
    ["GET", ["resources"]],
    ["GET", ["reservations"]],
    ["DELETE", ["resources", uuid]],
    ["DELETE", ["reservations", uuid]],
    ["PUT", ["resources", uuid]],
    ["PUT", ["reservations", uuid]],
    ["GET", ["resources", ".."]],
    ["POST", ["..", "resources"]],
    ["GET", ["resources", `${uuid}%2Fcancel`]],
    ["GET", ["resources%2F", uuid]],
    ["GET", ["reservations", "abc"]],
    ["GET", ["resources", "abc"]],
    ["GET", ["resources", uuid, "extra"]],
    ["POST", ["reservations", uuid]],
    ["POST", ["reservations", uuid, "cancel", "extra"]],
    ["POST", ["reservations", "cancel"]],
    ["GET", []],
    ["POST", []],
  ])("%s %j는 허용하지 않는다", (method, segments) => {
    expect(matchRoute(method, segments)).toBeNull();
  });
});
