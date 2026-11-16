# GitHub development

Repository: [miviy/Perihelion](https://github.com/miviy/Perihelion). Keep its visibility **Public**.

Use a task branch, run relevant checks, and open a pull request into `main`.
Agents automatically commit, push and open or update PRs for requested work;
merging requires the user's instruction. Do not push directly to `main`.

## Start a task

Run Git commands from the Perihelion project directory, which contains `core/`,
`forge/`, and `gradlew`. Donor checkouts are outside this repository and read-only.
Check `git status` and the current branch before editing. For a new task with a
clean working tree:

```sh
git fetch origin
git switch -c feature/my-change origin/main
```

Use `fix/` for bug fixes and `chore/` for maintenance. Continue a task's existing
branch for its follow-ups. If another task or unrelated edits occupy the checkout,
create an isolated worktree rather than stashing or moving that work:

```sh
git fetch origin
git worktree add -b feature/my-change .tools/worktrees/my-change origin/main
```

## Verify and publish

Review the diff and stage only files or hunks belonging to this task. Keep
credentials, game data and unrelated changes out of commits.

- Documentation changes: review the diff, commands and links; run `git diff --check`.
- Java or build changes: run `./gradlew --no-daemon check assemble`, plus focused
  verification for the behavior being changed.
- Rendering, input, Mixins or gameplay changes: also run the packaged client and
  verify the affected behavior. Inspect screenshots for visual changes and check
  affected controls and saved layouts/settings. When launching for user testing,
  confirm the client stays running and leave it open.
- GitHub Actions changes: validate workflow syntax with `actionlint` when available
  and confirm the PR's GitHub Actions result. If validation is unavailable or a
  check fails, disclose that limitation.

Use JDK 17 for Gradle and JDK 8 for the compiler and game. See [BUILD.md](BUILD.md)
for toolchain details. Distinguish local tests, packaged runtime checks and
live-server results; one does not establish the others.

<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:f0552cbc907fd7fbac79c4d6f772b8d58fc9c3d051736159da3e83b37235b74d -->

```sh
git diff --check
git add path/to/changed-file
git diff --cached
git -c user.name=miviy -c user.email=267354159+miviy@users.noreply.github.com var GIT_AUTHOR_IDENT
git -c user.name=miviy -c user.email=267354159+miviy@users.noreply.github.com var GIT_COMMITTER_IDENT
git -c user.name=miviy -c user.email=267354159+miviy@users.noreply.github.com commit -m "Describe the change"
git push -u origin HEAD
```

<!-- /coderabbit-ai-generated:v1 -->

Open a PR targeting `main` using the connected GitHub tools, the GitHub website,
or `gh pr create --base main` if GitHub CLI is installed. Pushing a branch alone
does not create a PR. Check for an existing PR before opening another one.

Explain the problem, resulting behavior, validation and material limitations.
Use a draft if verification is incomplete or known failures remain. Report the
PR link and actual CI result; do not describe queued or running checks as passed.
Fix failures introduced by the task, and identify unrelated failures separately.

<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:5699903d21171d72b8683f03044bb120bbbb8b19a91e00c4b6655aaa5a9120f1 -->

## Gradle Dependabot updates

For each Gradle Dependabot PR, inspect the proposed versions and sources before
running Gradle. Generate SHA-256 dependency verification metadata in a disposable
VM or container with no credentials, tokens, sensitive files, host home-directory
mounts or Docker socket access. A clean checkout alone is not isolation: metadata
generation downloads and executes dependency and plugin code before its new
checksums can be reviewed.

Inside that environment, check out the exact PR commit (replace `PR_NUMBER` with
the PR number). If it changes the wrapper, complete the **Wrapper upgrades**
review below before executing it. Use no authenticated GitHub CLI session or
credentials; this public repository can be cloned anonymously. Then run:

```sh
git clone https://github.com/miviy/Perihelion.git
cd Perihelion
git fetch origin pull/PR_NUMBER/head
git checkout --detach FETCH_HEAD
# Review wrapper changes as described below before running Gradle.
./gradlew --no-daemon --write-verification-metadata sha256 check assemble
git diff -- gradle/verification-metadata.xml
```

Review every added or changed checksum in `gradle/verification-metadata.xml`
against the intended dependency/version and trusted upstream artifacts or published
checksums. Generated hashes alone do not establish trust. Investigate unexpected
changes, removals or new verification exemptions; do not disable verification to
make an update pass. After reviewing the diff and upstream checksums, transfer
only the reviewed metadata file to a clean local checkout of the same PR commit.
For a wrapper upgrade, also reapply the reviewed `checkWrapper` checksum pin and
failure-message version in `build.gradle`, plus the reviewed wrapper version and
checksum notes in `docs/BUILD.md`, before running the local build. Verify the
local PR wrapper against the official checksum as described below. Do not copy
the environment's caches, binaries or other generated files. Destroy
the disposable environment afterward. See [BUILD.md](BUILD.md#checksums) and
[Gradle dependency verification](https://docs.gradle.org/8.4/userguide/dependency_verification.html).

### Wrapper upgrades

For every wrapper upgrade, including one proposed by the Gradle Dependabot
configuration, review `gradle/wrapper/gradle-wrapper.properties`, the wrapper scripts
and `gradle/wrapper/gradle-wrapper.jar`. Follow the
[Gradle wrapper upgrade procedure](https://docs.gradle.org/8.4/userguide/gradle_wrapper.html#sec:upgrading_wrapper),
including a second wrapper task invocation when needed to refresh the JAR and scripts.
Before running a changed wrapper JAR, compare its SHA-256
(`sha256sum gradle/wrapper/gradle-wrapper.jar`, or
`shasum -a 256 gradle/wrapper/gradle-wrapper.jar` on macOS) with the checksum
published by Gradle at
`https://services.gradle.org/distributions/gradle-VERSION-wrapper.jar.sha256`,
substituting the reviewed target version. Verify `distributionSha256Sum` separately
against Gradle's checksum for the selected distribution ZIP.

Only after the JAR matches the official checksum, update the literal `expected`
SHA-256 in `build.gradle`'s `checkWrapper` task and the version in its failure
message. Keep the expected checksum pinned and reviewed in the PR; do not derive
it from the local JAR at build time or automatically accept a generated value.
Update the pinned wrapper version and checksum notes in [BUILD.md](BUILD.md#checksums).
Run `./gradlew --no-daemon checkWrapper` to verify the updated pin.

### Validate and push reviewed checksums

In the local PR checkout, inspect the transferred metadata diff again and run the
normal build without metadata writing, then stage only reviewed changes:

```sh
./gradlew --no-daemon check assemble
git diff --check
git add gradle/verification-metadata.xml
# For wrapper upgrades, also stage the reviewed wrapper, build.gradle and documentation changes.
git diff --cached
```

Use the identity checks and commit command in **Verify and publish** above, then
`git push` the reviewed commit to the checked-out Dependabot PR branch. Confirm
the checksum changes appear in that PR and its build passes before merging.
Keep the `gradle` entry in `.github/dependabot.yml` unchanged for this procedure.

## Review and merge

<!-- /coderabbit-ai-generated:v1 -->

Review the PR diff and wait for **Build and tests / build** to pass before merging.
Resolve review conversations. Use a squash merge for one commit per task; GitHub
then deletes the merged task branch. Agents merge only when the user asks them to.

After merging, update a clean local checkout:

```sh
git switch main
git pull --ff-only
git fetch --prune
```

Do not switch or pull over another task's uncommitted work. Remove an isolated
worktree only after its task is merged and the worktree is clean. Never force-push
or rewrite shared history unless explicitly requested.

## Main-branch safeguards

GitHub branch protection should require PRs, the `build` status check and resolved
review conversations, and block force pushes and branch deletion on `main`.
For a solo maintainer, do not require another person's approval: authors cannot
approve their own PRs.

The public repository supports server-side rulesets. `Protect-main` limits updates
to repository admins through pull requests and blocks force pushes and deletion.
Require the GitHub Actions `build` check against an up-to-date branch before
merging, including for repository admins. Keep build checks running on every PR
so this requirement can be satisfied.

The versioned `.githooks/pre-push` guard rejects direct pushes and deletion of
`main` from this local repository, including linked worktrees. It does not restrict
GitHub's website, API or other clones. Install it in a new clone after checking
that it will not overwrite an existing hook or conflict with `core.hooksPath`:

```sh
git config --get core.hooksPath
ls -l "$(git rev-parse --git-path hooks)/pre-push"
# If no custom hooks path or pre-push hook exists:
cp .githooks/pre-push "$(git rev-parse --git-path hooks)/pre-push"
chmod +x "$(git rev-parse --git-path hooks)/pre-push"
```

Do not bypass the guard with `--no-verify`. Keep server-side protection active.

## Builds and downloads

The **Build and tests** workflow runs on PRs targeting `main`, pushes to `main`
and manual dispatch. Feature-branch pushes are tested through their PR, avoiding
duplicate push and PR builds. CI always runs the build, including documentation
PRs, so a required check is never omitted by a path filter.

Successful runs verify the distribution and upload the client JAR and test reports
as public-repository artifacts retained for 14 days. Treat logs, reports and
artifacts as publicly accessible; never include credentials or personal data. Use `Perihelion-…jar` in a Minecraft
1.8.9 Forge 11.15.1.2318 profile. Desktop smoke checks remain separate from CI.

## Local files and access

`.gitignore` excludes game runs, worlds, account vaults and keys, environment
files, build outputs, caches, local tools, worktrees and backups. Review staged
changes before every commit. Keep credentials in macOS Keychain or GitHub Actions
secrets.

The repository is public: anyone can read and clone it or propose a change through
a fork. Grant collaborator access only when write access is actually needed. Keep
GitHub Pages disabled unless publishing a site is explicitly requested.

Use the maintainer pseudonym `miviy` and the GitHub noreply commit email shown
above. Keep email privacy and email-exposure blocking enabled in GitHub account
settings. Use relative paths in documentation, not computer usernames or home
folders. Review screenshots, reports and commit metadata before publishing them.
Removing private information from a current file does not remove it from older
commits; shared-history rewrites require explicit authorization.

Secret scanning, push protection and private vulnerability reporting are enabled.
Use GitHub's private vulnerability reporting flow for security issues instead of
posting credentials or exploit details publicly. CodeQL scans Java, Python and
GitHub Actions. Dependabot proposes updates; updates still require review and CI.
Preserve third-party license notices. Public visibility does not assign a license
to the project's own sources.

AGENTS.md authorizes routine task-branch commits, pushes and PR creation, but cannot
override Codex or T3 permission controls. If the host blocks an action, report the
exact restriction and preserve the local work; do not repeatedly retry or bypass it.
