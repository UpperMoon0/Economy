# Economy CI/CD: Jenkins migration (KAN-71)

The original four Actions workflows remain **unavailable**. Do not dispatch,
re-run, or use GitHub Actions runs as acceptance evidence. This repository
contains a **nonpublishing** Jenkins candidate, not yet an authorized cutover.

## Native candidate

- Jenkinsfile selects the qualified general Linux worker label
  ci-role-general && ci-os-linux, never Windows or privileged host Docker.
- CI_MODE=full runs source attestation, Python policy and OpenUI checks, pinned
  OpenUI v0.0.16 (`40bb62363c705bd18221ef02f0e3e5605777e28c`) Maven publication, shared JVM tests, and all five loader builds with
  actual client/server joins and Forge GameTest. Failures accumulate per target
  but fail the overall build.
- CI_MODE=dependency runs standalone OpenUI dependency unit tests.
- CI_MODE=harness runs Python source-policy tests and py_compile.
- CI_MODE=live-join runs pinned OpenUI bootstrap plus standalone five-target
  live-join / Forge GameTest diagnostics.
- Source SHA, worker identity, build number, logs and artifact fingerprints are
  recorded. Provided SOURCE_SHA mismatches fail closed.
- The candidate rejects all PRs until a host-approved, exact-head same-repo
  PR admission route has native negative-path proof. Forks never execute on
  private Jenkins workers.

## Required work before cutover

Tracked by KAN-74..85. Source-only checks do not mean a ticket is complete.

1. Protected host webhook routing must map original PR/main/tag/manual events,
   paths and modes once. The registry currently indexes reviewed main only.
   Keep PR-only supersession, never cancel main/tag, reject untrusted forks.
2. Run every mapping as a numbered Jenkins build at the exact source SHA,
   archive outputs and prove negative event/status behavior. Confirm Java 21,
   NeoForge Java 25 toolchain and Python 3.12 parity. The first cloud-1 worker
   inspection found Java 21 and Python 3.13, not Python 3.12.
3. Publisher must report distinct Economy build, OpenUI dependency and Python
   verification contexts only on the verified exact commit, with failed-check
   propagation and stale-head rejection. Replace legacy required checks only
   after real native checks.
4. Release publishing stays outside this unprivileged Jenkinsfile. Implement a
   protected exact-main/tag gate (version bump, tag/version match, manual true
   on main, negative cases) and host-scoped GitHub Release/CurseForge brokers,
   with idempotent retries, verified five-target assets, receipts and rollback.
5. Retire the four Actions source files only after all trigger and publishing
   paths are proven natively. Preserve rollback evidence. Never run Actions.

## Workflow map

| Legacy Actions workflow | Candidate mode / pending host route |
| --- | --- |
| build-and-release.yml | full; separate protected release broker |
| live-join-test.yml | live-join; manual admission |
| openui-dependency.yml | dependency; PR-main/main/manual |
| verification-harness.yml | harness; PR/manual |

**Trust boundary:** Economy source can validate code but cannot authorize
protected statuses, trusted events, or release publication. The central host
dispatcher must reverify exact SHA, canonical repository identity, and event.
