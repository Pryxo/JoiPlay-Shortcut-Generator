# Architecture

The companion is a single-activity native Android application implemented with
platform APIs. It intentionally has no WebView and no runtime third-party
dependencies.

## Data flow

1. `JoiPlayRepository` queries
   `content://cyou.joiplay.joiplay.library/games` on a background executor.
2. Provider cursor rows become immutable `Game` values.
3. `MainActivity` renders Library or Settings from that state.
4. Launch actions send the game `id` to
   `cyou.joiplay.joiplay.activities.ShortcutActivity`.
5. `ShortcutFileFactory` creates deterministic Daijishō player-template text.
6. `ShortcutExporter` writes through a user-selected `content://` document or
   persisted document-tree URI.

The app never reads JoiPlay's private JSON directly and never mutates JoiPlay's
library.

## Provider schema

The app accepts the fixed columns `_id`, `id`, `title`, `folder`, `execFile`,
`path`, `icon`, `version`, `type`, `scoped`, `date`, `playCount`, `isFolder`,
`parentGame`, `launchComponent`, and `launchIntentUri`. Missing optional columns
degrade to empty/default values so minor provider revisions fail gracefully.

Game artwork is opened through the provider's read-only
`content://cyou.joiplay.joiplay.library/icons/<game-id>` endpoint. The provider
resolves the registered icon inside JoiPlay's process, avoiding broad storage
permissions and Android scoped-storage inconsistencies in the companion app.
