# Economy CI/CD: Jenkins migration (KAN-71)

GitHub Actions execution is disabled and all four legacy workflow files have
been removed. Do not dispatch, re-run, or use historical Actions artifacts
or check results. The active Jenkins-only build and required-check routes
are verified; final release-provider and diagnostic acceptance remains
tracked in [KAN-71](https://nstut.atlassian.net/browse/KAN-71).

## Active native CI

- Reviewed central `NsTut-CICD/jenkins/migration/economy/Economy.CI.Jenkinsfile`
  runs on a qualified general Linux container with the 4 GiB worker and
  physical-memory admission labels, without a privileged Docker socket
  or publishing credentials. The repository Jenkinsfile is not the
  authority for host admission or release.
- `full` runs exact-source admission, Python and OpenUI dependency checks,
  pinned Maven bootstrap, shared JVM tests, Forge GameTest, five native
  loader builds and five actual client/server joins. Failures prevent a
  successful release bundle. A successful full run archives the five
  exact-version production JARs, SHA-256 checksums and provenance.
- The immutable Economy `openui_version` determines the reviewed exact
  OpenUI source: 0.0.13 at
  `1c44920fcc40e486ec5edf1cf82b98c1b633d5bd` or 0.0.16 at
  `40bb62363c705bd18221ef02f0e3e5605777e28c`. No floating coordinate.
  The reviewed build-only Foojay compatibility shim preserves runtime source.
  The pinned Maven cache uses exact pin, recipe, JDK and artifact hashes,
  with separate main and owner-PR workspace slots; manual and tag runs
  remain cold. A missing cache rebuilds the pinned dependency.
- The protected HMAC dispatcher admits canonical main pushes, tags and
  same-repository owner PRs. A signed host source attestation, immutable
  source SHA and reviewed central control are required; external fork PRs
  never execute on private workers.
- Strict branch protection requires `jenkins/economy/native-full` from the
  Jenkins GitHub App. Running signed owner PRs report in-progress, then
  the same check completes from verified native evidence. Success requires
  all five hashed artifacts. Failed or superseded builds cannot supply green
  checks for another head.

## Manual credential-free diagnostics

The protected cloud-1 host exposes the reviewed root-only
`jenkins/migration/economy/manual_request.py` CLI. It resolves the exact
canonical main source and reviewed control, rather than trusting a caller's
arbitrary ref. Supported modes:

| Mode | Purpose |
| --- | --- |
| harness | Python contract tests |
| dependency | OpenUI dependency metadata checks |
| shared | Pinned Maven bootstrap and shared JVM tests |
| build | One explicitly selected loader build |
| live-join | One explicitly selected loader's real client/server diagnostic |
| full | Complete five-loader builds, joins, tests and release evidence |

The default manual request never publishes. A separate explicit full
`--release` request creates an immutable root-only release intent; it is
still subject to protected exact-source acceptance and all provider gates.
Do not issue a release request simply to exercise CI.

## Workflow replacement map

| Removed Actions workflow | Native replacement |
| --- | --- |
| build-and-release.yml | Signed full native CI and protected release broker |
| live-join-test.yml | Full joins and manual per-loader diagnostics |
| openui-dependency.yml | Dependency stage in full CI and manual dependency mode |
| verification-harness.yml | Harness stage in full CI and manual harness mode |

## Release authority and rollback

The protected host release broker is separate from the worker. It requires
the original signed eligible version event or exact authorized manual intent,
a successful source-bound full matrix, the verified App check, five JAR
hashes, the matching version/changelog and immutable tag conditions.
GitHub publishing uses a repository-scoped App installation token;
CurseForge uses the approved Key Vault identity. No secrets are injected
into Jenkins workers. Durable provider receipts prevent duplicate uploads
and quarantine ambiguous responses instead of blindly retrying.

A successful native run alone does not authorize release. Existing versions
must not be republished. Live provider publication/reconciliation and
remaining manual/cache acceptance are still required before final Jira
closure.

Rollback through reviewed central GitOps: disable the affected protected
route if publication is uncertain, preserve native archives, exact-source
checks and provider receipts, and revert the reviewed change without
reenabling Actions or introducing an alternate publishing identity.

