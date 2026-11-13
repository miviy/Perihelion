# Configs

Open ClickGUI with **Right Shift** or **`.gui`**, then select **Configs** at the top right.

Two ordinary presets are installed once when this version first runs:

- **default** captures the existing fresh-install settings, bindings and disabled module states.
- **hypxile** matches the supplied Yuri config using existing GUI controls, enabled states and bindings.

Select either and press **Load**, or use `.config load default` / `.config load hypxile`.
Fresh installs still start with the original defaults; existing working setups remain intact.
Both presets support the same Update, Delete, sharing and recreation actions as any other config.
Deleting one keeps it deleted across restarts and config loads. Small hidden `.installed`
receipts in the config folder remember installation independently of the working config.
Existing same-name files, backups and deleted recovery files are preserved.
See [the conversion notes](HYPXILE_CONFIG.md) for settings that could not be matched.

- **Save new** creates a named preset from your current settings. Existing files are never silently replaced.
- **Load** applies the selected preset and saves the result as your current setup.
- **Update** replaces the selected preset with your current settings after confirmation. The previous valid file is retained as `.json.bak`.
- **Delete** asks for confirmation and moves the file out of the active list; a `.json.deleted-…` recovery copy remains in the folder.
- **Open file / Open folder** use your operating system's associated application or file manager.
- **Copy path** copies the selected file's full path. **Refresh** picks up files copied into the folder.

Configs are readable, pretty-printed JSON under **`config/perihelion/configs/`** inside the
Minecraft instance's game directory. Share a preset by copying its `.json` file. To import
one, paste it into that folder and press Refresh. Names use 1–48 letters, numbers, underscores
or hyphens, without the `.json` extension in commands. Case is preserved; use the displayed
name when loading. Operating-system file associations are needed for Open file.

Presets contain module settings, enabled states, keybinds, GUI appearance and layout.
Loading a preset can therefore enable its modules and change your keybinds. Unknown data
is preserved when supported by the current configuration schema; invalid individual values
fall back to defaults. Malformed files, missing module objects and newer schemas are rejected
before changing your current setup.

## Commands

All client commands use a **dot prefix**. Dot commands and their Tab-completion requests
are handled locally, including unrecognized commands. Ordinary chat and server slash commands
retain their normal behavior. The old `/perihelion` command remains a compatibility alias.

| Command | Action |
| --- | --- |
| `.config list` | List available presets |
| `.config save <name>` | Create a new preset |
| `.config create <name>` | Alias for save |
| `.config load <name>` | Apply a preset |
| `.config update <name>` | Replace an existing preset; retain its backup |
| `.config delete <name>` | Remove a preset from the list; retain a recovery copy |
| `.config save` | Save the current working setup immediately |
| `.config reload` | Reload the current working setup from disk; discard unsaved edits |
| `.help` | List all client commands |
| `.status` | Show runtime and working-config save health |

GUI Update/Delete require confirmation. The corresponding explicit commands execute immediately.

## Autosave and performance

Your **working setup** remains in `config/perihelion/client.json`, so existing installations
keep their settings without migration. Changes mark it dirty. After approximately 750 ms
without another change, one snapshot is captured and queued to the background disk writer.
Idle ticks do not build JSON or write files. Normal shutdown flushes the latest state.

Named presets are explicit snapshots: changing a setting does **not** overwrite a preset
you loaded or imported. Use Update when you want to replace that preset. This lets you try
changes while retaining the original shared setup.

The browser lists files asynchronously on entry, Refresh, and after an operation. It renders
only visible rows and does not scan the folder during drawing. Named-file operations share
the existing serialized configuration worker. Loads apply through the native client-thread
lifecycle; a change made while a load is pending rejects that load instead of overwriting
the newer edit. File-read and validation failures leave the current values intact. Normal filesystem and schema
errors appear in the GUI or command reply.

Writes retain the existing temporary-file, flush and replacement protections. Atomic replacement
is used where the filesystem supports it; the existing non-atomic fallback remains otherwise.
JSON size/depth limits, safe filenames and symlink rejection also apply to presets.

## Verification and rollback

The automated suite passes **202 tests** (155 core, 47 Forge), including named-file backups,
invalid names and links, corrupt/newer profiles, concurrent edits during loading, shutdown
cancellation, and dot-command behavior. The packaged GUI probe exercises real save/load/
update/delete controls, transformed chat interception and local Tab completion. Config controls
remain on-screen at menu scales 0.75, 1.05 and 1.5; small windows adapt the config page's scale
so at least four rows remain visible. The system file opener depends on installed OS file
associations and is not invoked by the automated gameplay probe.

Exact artifacts and run results are recorded in
[config-profiles-20260928.json](verification/config-profiles-20260928.json).

The verified pre-change snapshot is `.backups/before-config-profiles-20260928-084101/`.
Run `python3 scripts/restore-config-profiles.py` from the repository root to check a rollback
without changing files. Add `--apply` to restore only this update's affected source files,
after preserving their current versions. The helper refuses later edits and was exercised
in an isolated copy. Rebuild after restoring. Actual game configs and generated worlds are
not part of the source rollback.
