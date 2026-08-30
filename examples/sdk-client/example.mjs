/**
 * Runnable demo of FeatureFlagClient — what an application team would do
 * to consume the evaluation API, end to end. Requires the backend running
 * (native `./mvnw spring-boot:run` or `docker compose up`) and the demo
 * seed data (applied automatically by Flyway).
 *
 * Run with: node example.mjs
 */
import { FeatureFlagClient } from "./client.mjs";

const BASE_URL = process.env.FFP_API_URL ?? "http://localhost:8080";

async function main() {
  const client = new FeatureFlagClient(BASE_URL);

  console.log(`Connecting to ${BASE_URL} ...`);
  const user = await client.login("viewer@example.com", "Password123!");
  console.log(`Logged in as ${user.email} (${user.role})\n`);

  const flagsPage = await client.listFlags({ size: 50 });
  const rolloutFlag = flagsPage.content.find((flag) => flag.type === "PERCENTAGE_ROLLOUT");

  if (!rolloutFlag) {
    console.log("No PERCENTAGE_ROLLOUT flag found in the seed data — nothing to demo. Exiting.");
    return;
  }

  console.log(
    `Evaluating "${rolloutFlag.key}" (${rolloutFlag.rolloutPercentage}% rollout, ${rolloutFlag.environmentName}) ` +
      "for 8 different users:\n",
  );

  // The point of this loop: percentage rollout is a *deterministic* hash of
  // flagKey:environment:stableIdentifier (ADR-001), never Math.random() — so
  // running this twice produces the exact same true/false pattern below,
  // every time, for every user. That's what makes a rollout safe to reason
  // about: a given user doesn't flicker between on and off across requests.
  for (let i = 1; i <= 8; i++) {
    const stableIdentifier = `demo-user-${i}`;
    const result = await client.evaluate(rolloutFlag.id, { stableIdentifier, attributes: {} });
    const bucket = result.bucket === null ? "—" : `bucket ${result.bucket}`;
    console.log(
      `  ${stableIdentifier.padEnd(14)} -> ${String(result.value).padEnd(5)} ` +
        `(${result.reason}, ${bucket}, ${result.cacheHit ? "cache hit" : "cache miss"}, ${result.evaluationLatencyMicros}µs)`,
    );
  }

  const metrics = await client.getMetrics(rolloutFlag.id);
  console.log(`\nMetrics for "${rolloutFlag.key}": ${metrics.totalEvaluations} evaluations this session`);
  for (const [result, count] of Object.entries(metrics.countsByResult)) {
    console.log(`  ${result}: ${count}`);
  }

  console.log("\nSubscribing to the live flag-change stream for 5 seconds ...");
  console.log("(edit any flag in the app, or via the API, in another terminal to see an event below)");
  const events = [];
  const unsubscribe = await client.streamChanges((eventName, data) => {
    events.push({ eventName, data });
    console.log(`  [${eventName}] ${data}`);
  });

  await new Promise((resolve) => setTimeout(resolve, 5000));
  unsubscribe();

  console.log(`\nReceived ${events.length} stream event(s). Done.`);
}

main().catch((error) => {
  console.error("Demo failed:", error.message);
  if (error.problem) console.error(JSON.stringify(error.problem, null, 2));
  process.exitCode = 1;
});
