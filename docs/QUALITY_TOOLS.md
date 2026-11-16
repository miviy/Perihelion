# Free repository quality checks

## OpenSSF Scorecard

The `OpenSSF Scorecard` workflow checks repository security practices on pushes to
`main`, weekly on Mondays, and on manual dispatch. It also runs when its workflow
file changes in a pull request, so setup changes can be verified before merging.

Open the workflow run in GitHub Actions and download the `scorecard-<commit>`
artifact to inspect `results.sarif`. Reports are retained for seven days. These
artifacts belong to a public repository and should be treated as public.

The workflow uses a read-only GitHub token, does not request a personal access
token, and does not publish results to the Scorecard public dataset. Findings do
not automatically block merges. All workflow action references are pinned to
specific commits.

The default GitHub token cannot read every classic branch-protection setting.
Treat an unavailable protection check as incomplete evidence, rather than proof
that the branch is unprotected. Do not add a broad personal access token merely
to improve the score.

## Other services

Codecov is not connected by this workflow. It requires a separate account
connection and verified coverage-report generation before coverage uploads can
be enabled. Do not put upload tokens in source control or expose secrets to
untrusted pull requests.

SonarQube's OSS plan and Greptile's OSS program require an appropriate project
license. Preserve existing third-party notices and resolve the project's own
license before applying. A public repository is not itself a license grant.
