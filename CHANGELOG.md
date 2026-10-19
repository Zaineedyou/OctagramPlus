# Changelog

## [1.5] - 2026-10-19
- Expanded Discord Rich Presence settings beyond account login: enable/disable sharing, activity type and optional name, custom details/state templates, two configurable buttons with URL templates, Discord account status (Online/Idle/Do Not Disturb), and whether presence stays visible while paused.
- Added a live sample preview, save-and-apply action, and restore-Metrolist-defaults control. Settings can be configured before connecting a Discord account.
- Added support for `{song.name}`, `{artist.name}`, `{album.name}`, and `{song.id}` placeholders in presence text and button links.
- Updated the player service to reapply settings immediately to the currently playing track when configuration changes.
- Bumped Android `versionCode` to 15 and `versionName` to 1.5. Release build only.

## [1.4] - 2026-10-19
- Changed the app canvas, native surfaces, splash screen, and night resources to bright matte pastel pink (`#FFC4DC`) instead of near-white pink.
- Replaced gradient-only decoration with the Liquid runtime shader: live backdrop sampling, frost, refraction, edge highlights, and translucent blush tint on the main navigation, Settings, Effects, and onboarding surfaces.
- Replaced manual Discord token entry with browser-based OAuth 2.0 PKCE using the Metrolist-style authorization flow.
- Encrypted Discord OAuth access and refresh tokens with Android Keystore; excluded credentials and expiry metadata from exported settings backups.
- Updated Discord Gateway authentication to use the OAuth `Bearer` token, with heartbeat acknowledgement monitoring, reconnect backoff, and track/playback presence updates.
- Added Discord account status, connect/disconnect controls, and a refreshed in-app What's New entry.
- Bumped Android `versionCode` to 14 and `versionName` to 1.4.

## [1.3] - 2026-10-18
- Introduced the first app-wide pink palette, translucent glass-style surfaces, and Discord Rich Presence.
- Added Android Keystore encryption for Discord credentials and a Discord RPC settings page.
- Bumped Android `versionCode` to 13 and `versionName` to 1.3.

## [1.2]
- Added Liquid Glass-style transparent buttons and music cards.
- Refreshed credits and synchronized the in-app changelog with the application version.

## [1.1]
- Introduced the Octagram+ pastel-pink visual identity, app assets, and branding updates.
- Added a local in-app changelog.
