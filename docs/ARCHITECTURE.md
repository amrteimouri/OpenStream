# OpenStream — architecture notes

This document explains *how the app is put together and why*. For what the app does, see the [README](../README.md).
Code comments and KDoc are written in Persian (the app's audience); this file is the English entry point.

## Overview

A single-activity Compose-for-TV app with one `ViewModel` that owns all state.

```
MainActivity ── AppRoot ──┬── SetupScreen   (QR code, add / delete playlists)
                          └── PlayerScreen  (ExoPlayer, key handling, info bar)
                                  └── ChannelBrowser  (categories, pages, search)

AppViewModel ──┬── LocalWebServer   phone page: add playlist / Xtream login / search
               ├── M3uParser        streaming M3U parser
               ├── XtreamClient     Xtream Codes API  →  M3U file
               └── StreamProbe      "is this channel alive?" check without playing it
```

## State and data flow

`AppViewModel` exposes read-only `StateFlow`s; screens collect them with `collectAsState()` and call plain functions on the ViewModel to change anything. There is no database.

| State | Meaning |
| --- | --- |
| `channels` | All channels from all playlists, merged and de-duplicated by URL. A channel's displayed number is its position + 1. |
| `groups` | `ChannelGroup(name, indices)` — categories hold *indices into `channels`*, not channel objects, so very large playlists stay cheap. Built once when playlists load. |
| `sources` | The playlists the user added. |
| `favorites` | Set of channel **URLs** (stable even when playlists change and numbers shift). |
| `health` | `url → HealthEntry(status, time)` for channels that were checked or played. |
| `searchQuery` / `openBrowser` | Search text, and a counter that opens the channel list (used by phone search). |

### Storage

```
files/playlists/sources.json     [{id, name, count}, …]
files/playlists/<id>.m3u          one M3U file per playlist (Xtream logins are converted to M3U too)
SharedPreferences "tv"            favourites, last watched channel URL
```

Adding a playlist writes `incoming.tmp` first, parses it, and only moves it into place if it contains at least one channel — a broken playlist can never destroy a working one. Playlists from older app versions (`files/playlist.m3u`) are migrated automatically.

## Adding a playlist from the phone

1. The TV starts a tiny HTTP server (`LocalWebServer`, port 8080) and shows its address as a QR code.
2. The phone page posts a form: `url` / `text` (M3U), `xserver` + `xuser` + `xpass` (Xtream), or `q` (search).
3. The server calls back into the ViewModel (`addPlaylist` / `searchFromPhone`) and answers with a plain result page.

The server only listens on the LAN and has no authentication — fine for a home network, not for public Wi-Fi.

## Health checks

A channel can be reachable and still unplayable, so there are two sources of truth:

1. **Probe** (`StreamProbe`): for HLS it fetches the playlist, the first variant playlist and the first media segment; it rejects HTML error pages and too-small segments, and accepts DASH (`.mpd`). It is cheap and does not decode anything.
2. **Real playback**: when ExoPlayer reaches `READY` the channel is marked OK, and when it gives up (fatal error, retries exhausted, or 20 s stall) it is marked FAIL. This overrides the probe.

Details that matter:

- The probe and the player use the **same User-Agent** (`Net.USER_AGENT`) and both follow http→https redirects. When they differed, the probe said "OK" for streams the player then got a 403 for.
- A page of 8 channels is probed only after 500 ms on that page, at most **4 at a time**, with a 15 s timeout each. Leaving the page cancels the work and closes the open sockets.
- Results are reused for 10 minutes.

## Remote control and focus (the tricky part)

- **OK is handled on key-up** in `PlayerScreen`. Handling it on key-down opened the list, and the key-up then landed on the first row as a click — the list closed after half a second.
- Rows in `ChannelBrowser` are plain `Column` children with one `FocusRequester` per *position*. `onPreviewKeyEvent` watches ▼ on the last row and ▲ on the first row to flip pages, and `pendingFocus` puts focus on the right row once the new page has composed.
- Categories change on **focus**, not click, so browsing categories needs no OK presses.
- Sizes are chosen for a 1080p TV, which Compose sees as roughly **960×540 dp**. Eight rows per page is what fits without scrolling.

## Performance notes

- Use the **release** build on real TVs: Compose in debug builds is several times slower.
- Parsing, file I/O and network calls run on `Dispatchers.IO`; the UI shows a black screen until the saved playlists are loaded (`loaded` flag).
- `DefaultLoadControl` uses small buffers (800 ms to start playing) so channel changes feel fast, and channel changes are debounced by 250 ms so fast ▲▲▲ only loads the last channel.

## Adding things

| I want to… | Start in |
| --- | --- |
| support another playlist type | `AppViewModel.addPlaylist` + a new reader next to `XtreamClient` |
| add a new field to the phone page | `LocalWebServer` (`PAGE`, form parsing in `handle`) |
| change what counts as "alive" | `StreamProbe.probe` in `Health.kt` |
| add a category source | `AppViewModel.publish` |
| change colours | `Theme.kt` (`Palette`) |

## Debugging a TV

```bash
adb connect <TV-IP>:5555
adb logcat -d AndroidRuntime:E *:S      # crashes
adb shell pidof com.simpletv.iptv        # is it running?
```

The application id is still `com.simpletv.iptv` (renaming it would make Android treat the app as a new one and wipe users' playlists).
