# OpenStream

**An IPTV player for Android TV that anyone can use — including people who have never touched a setting in their life.**

[فارسی](README.fa.md) · English

OpenStream is a bare player: it ships with **no channels, no playlists and no links**. You bring your own M3U playlist or Xtream Codes subscription, and the app turns it into something that feels like a normal satellite receiver: press ▲▼ to change channel, press **OK** to see the list.

The whole interface is Persian (right-to-left) and designed for a 10-foot screen with a plain remote — no mouse, no tiny buttons, no technical jargon.

## Features

- **Add a playlist from your phone, no typing on the remote.** The TV shows a QR code. Scan it, paste the link on a simple web page, and the playlist appears on the TV.
- **M3U / M3U8 and Xtream Codes.** Paste a playlist link or text, or log in with an Xtream server, username and password.
- **Several playlists at once.** Playlists are merged (duplicate links are removed), each one also gets its own category, and any of them can be deleted later.
- **HLS and DASH playback** (Media3 / ExoPlayer), with automatic reconnect and plain-language error messages ("this link no longer exists", "this channel is not compatible with your TV").
- **Know which channels work before you press play.** Channels are shown page by page; when a page opens, its channels are probed in the background and marked 🟢 working / 🔴 broken / 🟠 checking. Channels known to be broken are skipped when you press ▲▼.
- **Search** by name, on the TV or from your phone. Persian-aware: Arabic `ي`/`ك` match `ی`/`ک`, half-spaces and diacritics are ignored.
- **Favourites** (hold **OK** on a channel) and **categories** from the playlist.
- **Opens on the last channel you watched**, like a TV.
- Jump to a channel with the **number keys**.

## Remote control

| Key | Action |
| --- | --- |
| ▲ / ▼ | Next / previous channel (broken ones are skipped) |
| 0–9 | Go to channel number |
| OK | Open the channel list |
| Hold OK (in the list) | Add / remove favourite |
| ▼ on the last row of a page | Next page (▲ on the first row goes back) |
| Back | Close the list / go back |

## How it works

```
 phone ── QR / web page ──▶ LocalWebServer ──▶ AppViewModel ──▶ playlists/<id>.m3u
                                                   │
                      M3uParser ◀── XtreamClient ──┤
                                                   ▼
                          groups · search · favourites · health state
                                                   │
                            ┌──────────────────────┴───────────────┐
                       PlayerScreen (ExoPlayer)             ChannelBrowser
                                                         (pages + StreamProbe)
```

| File | Role |
| --- | --- |
| `AppViewModel.kt` | State of the whole app: playlists, channels, groups, favourites, search, health |
| `LocalWebServer.kt` | Tiny HTTP server on the home network used by the QR / phone page |
| `M3uParser.kt` | Streaming (line-by-line) M3U parser, safe for very large lists |
| `XtreamClient.kt` | Logs in to Xtream Codes and converts live channels to an M3U playlist |
| `PlayerScreen.kt` | Fullscreen player, key handling, info bar, retry / error logic |
| `ChannelBrowser.kt` | Categories sidebar, paged vertical list, search bar |
| `Health.kt` | `StreamProbe`: checks a stream without playing it (playlist → first segment) |
| `Search.kt` | Persian-aware text normalisation and search |
| `SetupScreen.kt`, `QrCode.kt` | The QR / "add playlist" screen |

Built with Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), Media3 (ExoPlayer, HLS, DASH), Coil and ZXing. No database: playlists are plain files in the app's private storage.

## Build and run

Requirements: JDK 17+ (24 works), Android SDK (API 36), an Android TV running Android 6.0+ (API 23).

```bash
# debug build
./gradlew assembleDebug

# optimized build (much faster on TV hardware — Compose is slow in debug)
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/`. The release build is signed with the debug key, so it installs directly for testing.

### Install on a TV over Wi-Fi

1. On the TV: *Settings → Device Preferences → About*, press **Build** seven times, then enable **USB debugging** in *Developer options*.
2. On your computer (same network):

```bash
adb connect <TV-IP>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

Accept the "Allow debugging?" prompt on the TV.

## Using the app

1. Open the app. The TV shows a QR code and an address like `http://192.168.1.20:8080`.
2. Scan the QR code with a phone on the same Wi-Fi.
3. Paste your playlist link (or Xtream details) and press send. Channels appear on the TV.
4. Press **OK** at any time to browse, search and manage playlists (*Add playlist* in the sidebar).

## Legal

OpenStream is a media player. It does not provide, host, index or recommend any channel or stream, and you are responsible for having the right to watch whatever you add. The "send to TV" page is served only on your local network and is never exposed to the internet.

## Roadmap

- Program guide (EPG / XMLTV) with a simple "now / next" line
- "Recently watched" category
- Parental PIN and a "favourites only" mode
- Automatic periodic refresh of playlists

## License

MIT — see [LICENSE](LICENSE).
