# GitHub development

Repository: [miviy/Perihelion](https://github.com/miviy/Perihelion).

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

```sh
git diff --check
git add path/to/changed-file
git diff --cached
git commit -m "Describe the change"
git push -u origin HEAD
```

Open a PR targeting `main` using the connected GitHub tools, the GitHub website,
or `gh pr create --base main` if GitHub CLI is installed. Pushing a branch alone
does not create a PR. Check for an existing PR before opening another one.

Explain the problem, resulting behavior, validation and material limitations.
Use a draft if verification is incomplete or known failures remain. Report the
PR link and actual CI result; do not describe queued or running checks as passed.
Fix failures introduced by the task, and identify unrelated failures separately.

## Review and merge

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

GitHub currently rejects branch protection for this private repository because
its plan requires an upgrade to GitHub Pro. Until that changes, PR review and
passing CI are workflow requirements rather than server-enforced merge gates.

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

Do not bypass the guard with `--no-verify`. If GitHub Pro becomes available,
enable server-side protection as well.

## Builds and downloads

The **Build and tests** workflow runs on PRs targeting `main`, pushes to `main`
and manual dispatch. Feature-branch pushes are tested through their PR, avoiding
duplicate push and PR builds. CI always runs the build, including documentation
PRs, so a required check is never omitted by a path filter.

Successful runs verify the distribution and upload the client JAR and test reports
as repository artifacts retained for 14 days. Use `Perihelion-…jar` in a Minecraft
1.8.9 Forge 11.15.1.2318 profile. Desktop smoke checks remain separate from CI.

## Local files and access

`.gitignore` excludes game runs, worlds, account vaults and keys, environment
files, build outputs, caches, local tools, worktrees and backups. Review staged
changes before every commit. Keep credentials in macOS Keychain or GitHub Actions
secrets.

Invite collaborators only when you intend to give them access.

AGENTS.md authorizes routine task-branch commits, pushes and PR creation, but cannot
override Codex or T3 permission controls. If the host blocks an action, report the
exact restriction and preserve the local work; do not repeatedly retry or bypass it.
