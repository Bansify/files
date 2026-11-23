# Architecture

## Shape of the app

```
com.example
├── MainActivity.kt          single activity, hosts the Compose navigation graph
├── model/                   FileItem, storage and sort models
└── ui/
    ├── components/          shared widgets (file rows, empty states, dialogs)
    ├── screens/
    │   ├── BrowseScreen.kt              storage overview and shortcuts
    │   ├── DirectoryBrowserScreen.kt    folder-by-folder navigation
    │   ├── CleanScreen.kt               large files, duplicates, junk
    │   ├── ToolsScreen.kt               utilities
    │   ├── SpecialCollectionScreens.kt  images, video, audio, documents, downloads
    │   └── FilePreviewDialogs.kt        preview for images, text and media
    └── theme/               colour, type, shape and theme tokens
```

There is **one activity**. Every screen is a composable destination, so navigation state is
cheap and the back stack behaves predictably.

## Storage access

- `MANAGE_EXTERNAL_STORAGE` is declared for full-folder browsing, together with the usual
  read/write media permissions.
- On Android 11+ the user must grant "All files access" from system settings; the app
  explains why before sending them there.
- A `FileProvider` exposes files for sharing and preview without making them world-readable.
- Nothing is deleted without an explicit confirmation; the clean flow always shows what will
  be removed and how much space it frees.

## Data flow

```
Compose screen  →  filesystem query (java.io.File / MediaStore)  →  immutable UI state
```

- The filesystem is the source of truth; there is no local database to keep in sync.
- Directory reads happen off the main thread and results are mapped to display models before
  they reach the UI.
- Preview reads are size-capped so opening a 2 GB video does not allocate 2 GB.

## Theming

`ui/theme` holds colour, type and shape tokens. Brand blue is `#0494f4`; dark surfaces use
`#212121`. Icons and spacing follow the same scale as the other Bansify apps so the family
looks consistent.

## Deliberate non-goals

- no background indexing service
- no cloud sync inside this app — Drive is a separate product
- no analytics SDK
