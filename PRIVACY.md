# Privacy

JoiPlay Shortcut Generator is designed to work offline.

## Data the app reads

The app queries the read-only JoiPlay library provider for game metadata such
as title, ID, runtime, path, date, and play count. This data is used to render
the Library screen and create shortcuts. It is not transmitted anywhere.

## Data the app stores

Android preferences store the selected theme, output type, sort order, tap
behavior, folder-entry visibility, and the URI permission for a user-selected
output folder. Android may include those preferences in device backup unless
the user disables backup at the operating-system level.

## Files

The app writes shortcut text files only after the user chooses a document or
grants access to a folder through Android's Storage Access Framework. It does
not request all-files access.

## Network and tracking

The app declares no Internet permission and includes no analytics, advertising,
account, telemetry, or crash-reporting SDK.

