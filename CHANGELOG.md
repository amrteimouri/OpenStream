# Changelog

All notable changes to OpenStream are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/).

## [0.1.0] — first public release

### Added
- Android TV player with a fully Persian, right-to-left, remote-friendly interface.
- Add playlists from a phone: the TV shows a QR code and a tiny web page on the home network accepts an M3U link, pasted M3U text, or Xtream Codes login.
- Several playlists at once: merged, de-duplicated, one category per playlist, deletable from the add-playlist screen.
- HLS and DASH playback (Media3 / ExoPlayer) with automatic reconnection and plain-language error messages.
- Channel list shown page by page (8 per page) with a background health check: 🟢 working / 🔴 broken / 🟠 checking. Broken channels are skipped with ▲▼.
- Search by name from the TV or from the phone, aware of Persian letter variants.
- Favourites, categories from the playlist, number-key channel jumping, and "open on last channel".
- English and Persian README, architecture notes, and KDoc comments on all code.

### Notes
- No channels, playlists or links are included; the app is a bare player.
- Application id is `com.simpletv.iptv`.

[0.1.0]: https://github.com/amrteimouri/OpenStream/releases/tag/v0.1.0
