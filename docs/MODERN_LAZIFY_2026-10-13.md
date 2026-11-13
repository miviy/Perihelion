# Modern-Lazify behavior in the Adjust-style player overlay

The existing player table now supports Modern-Lazify's provider-backed BedWars
statistics while retaining Perihelion's appearance, settings, HUD editor, pins,
Friends, target tags and chat context menu.

Enable **HUD → Nearby players**, then open that element's **Options**. On Hypixel,
**Player source → Tab list** also includes valid player profiles outside loaded
entity range. Automatic lookups start in the BedWars pregame/game scoreboard;
main-lobby NPCs and other servers do not trigger requests. Nearby mode still uses
the configured block range. Up to 32 rows are displayed from a bounded roster.

The selectable columns include rank, stars, encounters, K/D, FK/D, W/L, beds
broken/lost, wins, kills, finals, beds, winstreak, network level, server session,
nick hints, provider tags and daily/weekly/monthly ratios and star changes.
BedWars mode selects overall/solo/doubles/threes/fours/4v4 lifetime counts and
winstreaks. Period ratios are overall, matching the donor's session API.
Missing, private, invalid or unpublished fields remain `--`; valid zero remains
zero. A `~` marks a Bordic-estimated winstreak. `Nick?` is a suspicion from an
offline UUID or independent missing-profile responses, not an identity reveal.
Provider outages, malformed responses and wrong UUIDs cannot become nick evidence.

Numeric sorting, ascending/descending order and minimum FKDR/star filters are
available. Pins retain priority; unknown statistics stay visible when filtering
and sort after known values. Thresholds use OR when both are enabled. Encounter
history counts reappearance rather than repeated snapshots, expires after 30
minutes of absence, is bounded to 512 UUIDs and resets on server-address changes.

Right-click a player in chat for statistics, attributed tag reasons, details,
report selection, pins and **Hide player**. Hidden names persist in the normal
settings. `.hud unhide` clears them; `.hud refresh` invalidates provider caches.
Scroll over the table header in chat to page wide columns; scroll over the body
to reach clipped rows. Click targets follow the current row offset. Editor previews
do not initiate requests, commands or alerts.

| Provider | Integration | Credentials |
| --- | --- | --- |
| Abyss | Public player profile, first fallback source | None; donor's HTTP endpoint, UUID only, no keys |
| Prism | Public player profile; rejects mismatched UUIDs | Ephemeral donor-compatible client identifier |
| Bordic | Public cached profiles and per-mode streaks | Optional for cache; own key required for period sessions |
| Hypixel | Official HTTPS player API fallback; existing explicit SkyWars workflow retained | Own key, sent in `API-Key` header |
| Urchin | HTTPS v3 player tags, source and reason attribution | Own key, sent in `X-API-Key` header |
| Seraph | HTTPS blacklist tags, source and reason attribution | Own key, sent in `seraph-api-key` header |

Default local key-file paths, relative to the Minecraft game directory:

- `config/perihelion/hypixel-api-key.txt`
- `config/perihelion/bordic-api-key.txt`
- `config/perihelion/urchin-api-key.txt`
- `config/perihelion/seraph-api-key.txt`

Set their paths in the player overlay's Options. The UI/config/presets contain
paths rather than credentials. Each file contains only your own key. Files are
read with a 4 KiB bound; exceptions and raw provider responses are not logged.
Credentials are sent only to their fixed provider hosts. Redirects are disabled.

Optional gameplay tracking includes join-time possible-party highlights, actual
party-list membership, the `/who` roster, final-kill removal, local nick/tag alerts,
local pregame average-FKDR warnings and locally observed party combat results.
Automatic `/who` and `/party list` are opt-in and run once per pregame after a
short delay. Alerts/results start off. Join timing is only a possible-group hint;
external tags are provider claims, not Perihelion violation detections.

This is the overlay/statistics integration. The donor's Fabric GUI/config import,
Lazify/Nerdify/Mellow visual replacements, automatic real-identity denick matching,
shop-purchase mixin and party-chat threat broadcasting are not imported. Perihelion
retains its own GUI, config/presets, Freecam, NoHurtCam and gameplay modules. No
external keys, authenticated Hypixel session or server bypass proof were supplied.

## Verification

305 regression tests passed (156 core, 149 Forge), with zero failures/errors/skips.
They cover identity checks, modes, zero versus unknown values, star thresholds,
fallback merges, periods, key errors, redirects, HTTP failures, response bounds,
429 cooldown/reset, partial tag outages, JWT/base64 token formats, bounded pending work, expiry, client-owned publication,
cancellation, repeated encounters, chat tracking, filters and config persistence.

A Java 8 check using the production transport and a public fixture UUID fetched
Abyss and Bordic profiles. Prism returned a different UUID with the donor headers;
Perihelion rejected that response. Unauthenticated endpoint probes found Bordic
sessions and Urchin require authentication and Seraph returned HTTP 502. These
checks establish public connectivity and failure handling, not keyed live success.

The final packaged Forge run passed all full-scenario probes, including 26 overlay
checks, 88 chat checks and three stale entity-packet checks, with zero queued work
or subscriptions at shutdown. One preceding full run passed the overlay/chat
checks but failed the existing 10-FPS bridging phase; the exact same artifact
passed that phase on the next full run. This intermittent result is retained in
the verification record and is not a claim of universal gameplay stability.

Packaged Forge and OptiFine results, fixture checks, artifact hashes and rollback
manifest are recorded in `MODERN_LAZIFY_VERIFICATION_2026-10-13.json`. The additional
native overlay fixture exercises cached stats, mode changes, column paging, row
scrolling/hit testing, hidden players and refresh without any network request.

Provider work uses three daemon workers, at most 64 pending player requests, a
256-entry snapshot cache, 90-second pending expiry, a 10-minute success/45-second
failure refresh window and five-minute period refreshes in batches of ten. HTTP
connect/read timeouts are bounded; responses are limited to 2 MiB. Only client
ticks publish immutable results. Retired jobs cannot republish after key changes,
refresh, disable or world retirement. Shutdown cancels workers and disconnects
active requests. No donor managers, event buses or configuration system are used.

A native unload exposed a queued `S19PacketEntityHeadLook` reading a retired world.
The existing after-handoff world/handler guard now also covers head rotation.
Native regressions verify current-world head rotation still applies and stale
head/movement/teleport delivery is ignored during unload.

## Recovery

The pre-edit snapshot contains 557 individually verified files:
`.backups/before-modern-lazify-20261013-154135/source.tar.gz`.

`python3 scripts/restore-modern-lazify.py` checks the archive and affected files.
Explicit `--apply` backs up the affected current files, restores the allowlist and
refuses if later edits would be overwritten. Concurrent Breaker changes are
excluded; only the Modern-Lazify section is removed from shared `DONORS.md`.
Rebuild after restoring.
