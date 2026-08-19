# Changelog

## 1.2.1 - 2026-08-20

### Fixed

- Update the iiSU JoiPlay platform definition to launch `ShortcutActivity` with
  the compatible `-e id "%ROM_CONTENT%"` argument syntax.

## 1.2.0 - 2026-08-19

This release adds built-in JoiPlay integration for iiSU.

### Added

- Select and remember an iiSU root folder separately from shortcut output.
- Import the JoiPlay platform definition into `emuladores.json` and the JoiPlay
  package definition into `supported_emulators.json` from the Library header.
- Detect the current integration state on every library refresh and show a
  clear imported status when both definitions are present.
- Support iiSU's current `consoles` wrapper schema as well as legacy root-array
  platform files.

### Changed

- Rename the file-type setting description to **Generated Shortcut Output**.
- Show the iiSU action in the selected app accent color, with a responsive
  layout for wide and narrow screens.

### Reliability

- Validate both JSON documents before writing, update existing JoiPlay entries
  without duplicates, and attempt rollback if the second write fails.
- Add unit coverage for first import, repeated import, incomplete-entry repair,
  wrapper preservation, exact launch commands, and invalid JSON structures.

Install the generator APK and one of the included modified JoiPlay APKs matching
your JoiPlay version. Back up JoiPlay before replacing an existing installation.
