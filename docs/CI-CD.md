# Economy CI/CD — Jenkins authority

Economy builds and release publication are managed by the reviewed
[`UpperMoon0/NsTut-CICD`](https://github.com/UpperMoon0/NsTut-CICD) control
repository, tracked under
[KAN-71](https://nstut.atlassian.net/browse/KAN-71).

## Build and check authority

- **GitHub Actions is disabled repository-wide.** The four former YAML
  workflows (`build-and-release`, `live-join-test`,
  `openui-dependency`, `verification-harness`) have been retired.
- Canonical push, version-tag and owner same-repository PR events arrive via
  the protected **HMAC-verified GitHub webhook** and are independently checked
  against canonical repository identity, exact source SHA and reviewed control.
  Untrusted fork PR code is **never** run on private/self-hosted runners.
- The fixed central Jenkins job
  `UpperMoon0-economy-migration/economy-native-ci` executes on an explicitly
  qualified `ci-role-general && ci-os-linux` worker. Jenkins agents have no
  GitHub release token, Azure identity, CurseForge API token or Docker socket.
- The **full** native gate runs the Python harness, dependency checks,
  immutable OpenUI Maven bootstrap, shared JVM tests, all five loader builds,
  Forge GameTest, and five real client/server joins. The successful build
  archives exactly five matching Economy JARs plus
  `release-output/provenance.json` and `release-output/SHA256SUMS`.
- A separate root-only service checks the exact terminal Jenkins evidence,
  source/control SHA, five binaries and checksums before publishing
  `jenkins/economy/native-full` to the exact Git commit with an Economy-only
  short-lived **GitHub App installation token** issued through Azure Key Vault.
  Never substitute a personal access token.

## Manual CI

On the authorized cloud-1 host, the protected control source provides:

```sh
sudo python3 -I /opt/nstut-jenkins/gitops/repo/jenkins/migration/economy/manual_request.py --mode full
```

A targeted diagnostic can use `--mode dependency`, `--mode harness`,
`--mode shared`, or `--mode live-join --target forge-1.20.1`.
These cannot substitute for the full required check.

## Releases

New canonical version bumps or immutable matching version tags are candidates
for publication **only after** the same exact-source full Jenkins build passes,
the GitHub App check is successful and the protected broker verifies every
release JAR, mod descriptor and SHA-256 digest. Both GitHub Release and
CurseForge publication use the centrally managed
`NsTut-CICD` Azure Key Vault identity. Durable provider receipts protect
against duplicate or uncertain uploads. GitHub releases begin as drafts.

The manual `--mode full --release` path records a protected, one-time,
source-bound release intent, but **does not publish immediately**. Do not
attempt to republish an existing version (including v0.0.21).

## Failure response

If native acceptance fails, fix and rerun the **reviewed** Jenkins source.
Do not enable GitHub Actions to bypass a failure or claim a green check without
source-bound artifacts. If publishing fails after a partial upload, preserve
the protected receipt and reconcile provider state before retrying; do not
delete release history or backups to force a repeat. Keep credentials in
Azure Key Vault and the issuer on the protected host only.

Before closing the migration ticket, verify an exact-head required App check,
five genuine native join receipts, the published artifact/rollback contract,
GitHub Actions disabled, all legacy workflow files absent, and no stale
required checks pointing at the retired Actions jobs.
