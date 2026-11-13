# GitHub development

Repository: [miviy/Perihelion](https://github.com/miviy/Perihelion). Keep its visibility **Private**.

Run Git commands from the Perihelion project directory, which contains `core/`,
`forge/`, and `gradlew`. Donor checkouts are outside this repository.

## Save and push an update

```sh
git status
git diff
git add path/to/changed-file
git diff --cached
git commit -m "Describe the change"
git push
```

Stage each file you intend to include. Before committing code changes, run:

```sh
./gradlew --no-daemon check assemble
```

Use JDK 17 for Gradle and install JDK 8 for the compiler and game. See
[build setup](BUILD.md) for toolchain details.

## Work on a feature branch

Start with a clean working tree:

```sh
git switch main
git pull --ff-only
git switch -c feature/my-change
```

Commit your changes, then publish the branch:

```sh
git push -u origin feature/my-change
```

Open a pull request on GitHub to review the diff and build results before merging.
After a merge, switch back to `main` and run `git pull --ff-only`. Pull before
editing locally if you made changes through GitHub's web editor.

## Builds and downloads

The **Build and tests** workflow runs on pushes and pull requests. It can also be
started from the repository's **Actions** tab. A successful run verifies the
distribution and uploads the client JAR and test reports as a private repository
artifact, retained for 14 days. Use `Perihelion-…jar` from the download in a
Minecraft 1.8.9 Forge 11.15.1.2318 profile.

Desktop smoke runs remain separate from GitHub's build. Run the packaged client
locally when changing rendering, input, Mixins, or gameplay behavior.

## Local files and access

`.gitignore` excludes game runs, worlds, account vaults and keys, environment
files, build outputs, caches, local tools, and backups. Review `git diff --cached`
before every commit. Keep credentials in macOS Keychain or GitHub Actions secrets.

Invite collaborators only when you intend to give them access. Keep GitHub Pages
disabled and avoid changing repository visibility to Public. A private repository
still permits authorized collaborators to read and clone its contents.
