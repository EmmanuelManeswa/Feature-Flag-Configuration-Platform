import { describe, expect, it } from "vitest";
import { parseSseEvent } from "@/lib/sse-client";

describe("parseSseEvent", () => {
  it("parses a named event with a single-line JSON data payload", () => {
    const raw = 'event:flag-change\ndata:{"flagId":"abc","type":"UPDATED"}';
    expect(parseSseEvent(raw)).toEqual({
      eventName: "flag-change",
      data: '{"flagId":"abc","type":"UPDATED"}',
    });
  });

  it("defaults to eventName \"message\" when no event: line is present", () => {
    const raw = "data:hello";
    expect(parseSseEvent(raw)).toEqual({ eventName: "message", data: "hello" });
  });

  it("joins multiple data: lines with a newline, per the SSE spec", () => {
    const raw = "event:multi\ndata:line one\ndata:line two";
    expect(parseSseEvent(raw)).toEqual({ eventName: "multi", data: "line one\nline two" });
  });

  it("ignores comment lines (heartbeats) and returns null when there is no data", () => {
    expect(parseSseEvent(":hb 2026-01-01T00:00:00Z")).toBeNull();
  });

  it("ignores a comment line interleaved with real data", () => {
    const raw = ":hb 2026-01-01T00:00:00Z\nevent:flag-change\ndata:{}";
    expect(parseSseEvent(raw)).toEqual({ eventName: "flag-change", data: "{}" });
  });

  it("returns null for a blank block", () => {
    expect(parseSseEvent("")).toBeNull();
  });
});
