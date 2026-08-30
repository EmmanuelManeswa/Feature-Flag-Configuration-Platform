# Production readiness

The README's [Production readiness](../README.md#production-readiness) section is the quick
summary table. This is the fuller version, including the one piece of "what would need to
change" that's referenced directly from code comments: hardening audit-log immutability.

## Audit log immutability: compile-time today, DB-enforced for real production

`AuditLogRepository` extends `AppendOnlyRepository<T, ID>` instead of `JpaRepository`, so there
is no `delete`/`deleteById`/`deleteAll` method anywhere in its type hierarchy — no application
code can call one, now or in a future change, because it doesn't exist to call. That closes the
gap at the *application* layer.

It does not close the gap at the *database* layer: a raw SQL `DELETE FROM audit_logs` (a
mis-scoped migration, a DBA running an ad-hoc query, a compromised credential with direct DB
access) would still work. For a real production deployment, the next layer down would be a
Postgres rule or trigger that rejects `UPDATE`/`DELETE` on the table outright:

```sql
CREATE OR REPLACE FUNCTION reject_audit_log_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit_logs rows are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_no_update
  BEFORE UPDATE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();

CREATE TRIGGER audit_logs_no_delete
  BEFORE DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();
```

Not added in this project: it's genuinely more machinery (a migration, a function, two
triggers, a test that specifically tries to violate it) than a take-home assessment needs to
make the point, and the compile-time guarantee already demonstrates the reasoning. It's the
obvious next step, not a gap that was missed.

## Scaling

Both the backend and frontend are already stateless — no in-memory session, no sticky-session
requirement — so horizontal scaling is "run more instances behind a load balancer," not a
redesign. The one shared-state dependency in the evaluation hot path is Redis, and the whole
point of ADR-002's design is that losing Redis degrades performance, not correctness or
availability.

What would actually break first under real load, roughly in order:

1. **Postgres connection pool** (HikariCP, currently unbounded default sizing) — first thing to
   tune explicitly once there's more than a handful of backend instances.
2. **Single Redis instance** — no failover; a single Redis restart is currently a brief latency
   blip (Postgres fallback), not an outage, but a sustained Redis loss with no replica means
   every evaluation pays the Postgres-fallback cost until it's back.
3. **Single Postgres instance** — no read replicas; all reads and writes hit one primary.

## Backups & recovery

Not configured in this project (a `docker compose down -v` genuinely deletes local dev data,
which is the correct behavior for local dev). For production: automated Postgres backups with
point-in-time recovery (most managed Postgres offerings provide this out of the box), and a
documented, *tested* restore procedure — a backup that's never been restored from isn't a backup
you can trust in an incident. Redis needs no backup story at all given its role here (see
ADR-002: it's a disposable cache, not a store of record) — a lost Redis instance is a cold cache,
not lost data.

## Observability

Already in place: structured JSON logs (docker/prod profile), correlation IDs propagated
end-to-end (request → logs → error responses → audit rows), Actuator health/liveness/readiness,
Micrometer counters and a timer on the evaluation path
(`feature_flag.cache.requests`, `feature_flag.evaluations`, `feature_flag.evaluation.latency`),
`/actuator/prometheus` exposed for scraping.

What's missing for real production: shipping those logs/metrics somewhere durable (today they go
to stdout, which is fine for `docker compose logs` but not for incident response after a
container recycles) — OpenTelemetry Collector → a backend (self-hosted or vendor) would be the
natural next step, and the structured JSON shape already used here is what OTel collectors
expect.

## Secrets management

Today: environment variables, sourced from a gitignored `.env` file, required at application
startup with no insecure fallback (see `docs/security.md`). For production: a real secrets
manager (cloud KMS-backed, e.g. AWS Secrets Manager / GCP Secret Manager / HashiCorp Vault)
rather than environment variables on a disk, with rotation support `JWT_SECRET` doesn't
currently have (rotating it today invalidates every outstanding token immediately, with no
grace-period dual-key verification).

## Deployment

Today: `docker compose up`, single instance of each service, manual. For production: Kubernetes
(or an equivalent orchestrator) with the test suites in this repo running as a CI merge gate,
and a rolling or blue-green deploy strategy so a bad deploy doesn't take evaluation traffic down.

## Rate limiting / abuse control

Not implemented — a real gap, not a silent omission (see `docs/security.md`). Highest-priority
additions: `/api/v1/auth/login` (brute-force resistance) and `/api/v1/ai/rule-proposals`
(cost/abuse control, more relevant if a paid AI provider were ever added behind the existing
`AiProvider` abstraction — the mock and Docker Model Runner paths used today have no per-request
cost to abuse).
