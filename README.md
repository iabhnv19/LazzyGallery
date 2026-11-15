<div align="center">

# Lazzy Gallery

[![Build Status](https://img.shields.io/badge/build-passing-success)](https://github.com/iabhnv19/lazzy-gallery)
[![Developer](https://img.shields.io/badge/developer-iabhnv19-blue.svg)](https://github.com/iabhnv19)
[![Kotlin Version](https://img.shields.io/badge/kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Compose Version](https://img.shields.io/badge/compose-2026.02-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/min--sdk-24%20%2F%20Android%207.0-orange)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-Apache%202.0-red.svg)](LICENSE)

A high-performance, privacy-focused, open-source Android media gallery application engineered with Jetpack Compose, Material 3, and an offline-first architecture.

Developed by **[iabhnv19](https://github.com/iabhnv19)**.

</div>

---

## Table of Contents

- [Overview](#overview)
- [Screenshots](#screenshots)
- [Core Features](#core-features)
- [Privacy & Local-First Architecture](#privacy--local-first-architecture)
- [Architecture & Design Pattern](#architecture--design-pattern)
- [Deep-Dive Technical Implementation](#deep-dive-technical-implementation)
  - [Scoped Storage & MediaStore Integration](#scoped-storage--mediastore-integration)
  - [Multi-Tier Caching Pipeline](#multi-tier-caching-pipeline)
  - [Background Synchronization](#background-synchronization)
  - [Ambient Background & Visual Effects](#ambient-background--visual-effects)
- [Tech Stack & Dependencies](#tech-stack--dependencies)
- [Project Directory Structure](#project-directory-structure)
- [Installation Guide](#installation-guide)
- [Building & Running from Source](#building--running-from-source)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

Lazzy Gallery is an optimized, privacy-first media management application built natively for Android. Designed to handle large media libraries efficiently, it combines a reactive declarative UI (`Jetpack Compose`), offline-first local database caching (`Room`), hardware-accelerated video playback (`Media3 ExoPlayer`), and background synchronization (`WorkManager`). 

Lazzy Gallery operates entirely offline with zero telemetry, zero analytics tracking, and strict adherence to modern Android storage guidelines.

---

## Screenshots

<div align="center">
  <table border="0">
    <tr>
      <td align="center"><b>Main Library Grid</b></td>
      <td align="center"><b>Media Viewer & EXIF</b></td>
      <td align="center"><b>Advanced Options Menu</b></td>
    </tr>
    <tr>
      <td><img src="docs/images/main_library.png" width="240" alt="Main Library Grid"/></td>
      <td><img src="docs/images/photo_detail.png" width="240" alt="Photo Detail"/></td>
      <td><img src="docs/images/overflow_menu.png" width="240" alt="Overflow Menu"/></td>
    </tr>
  </table>
</div>

---

## Core Features

- **Asynchronous Media Indexing**: Non-blocking queries against the Android MediaStore content provider with projection optimization.
- **Hierarchical Album Organization**: Automatic bucket categorization and folder grouping with real-time item counts.
- **Hardware-Accelerated Video Playback**: Integrated AndroidX Media3 ExoPlayer supporting custom controls, scrubbing, and seamless buffering.
- **Persistent Favorites & Collections**: Local bookmarking state management utilizing SharedPreferences and Room entities.
- **Recycle Bin & Trash Workflow**: Soft deletion mechanism supporting file restoration and secure purge operations.
- **Real-Time Search & Filtering**: Instant query filtering across file names, dates, and media types (Images vs. Videos).
- **Comprehensive EXIF Inspector & Actions**: Metadata extraction parsing camera make/model, ISO, exposure time, aperture, focal length, resolution, and GPS coordinates alongside built-in tools (Rename, Hide, Copy, Open With, Set As, Print, Show on Map, and Slideshow).
- **Ambient Background Visuals**: Dynamic ambient lighting and blur effects rendering immersive visual backdrops in media viewing screens.
- **Material 3 Design System**: Full support for dynamic color schemes, adaptive layouts, and dark/light themes.

---

## Privacy & Local-First Architecture

Lazzy Gallery is built with absolute user privacy as a core tenet:
- **100% Offline Operation**: The application requires zero network permissions for core functionality. It does not communicate with external servers, cloud databases, or API endpoints.
- **Zero Telemetry & Tracking**: No crash reporting tools, analytics SDKs, or advertising libraries are embedded. Your personal photos and metadata never leave your device.
- **Direct Storage Access**: Interacts strictly with local Android Scoped Storage and MediaStore APIs.

---

## Architecture & Design Pattern

Lazzy Gallery strictly adheres to the Model-View-ViewModel (MVVM) architecture pattern combined with Unidirectional Data Flow (UDF):

1. **Presentation Layer (`ui/`)**: Built entirely with Jetpack Compose. Composables remain stateless, observing state streams exposed via Kotlin `StateFlow`.
2. **ViewModel Layer (`ui/viewmodel/`)**: `MediaViewModel` encapsulates all business logic, sorting algorithms, multi-selection states, and filter transformations, executing operations asynchronously within `viewModelScope`.
3. **Data Layer (`data/`)**: `MediaRepository` acts as the single source of truth, coordinating between the system `ContentResolver`, background workers, and the local `Room` database cache.

---

## Deep-Dive Technical Implementation

### Scoped Storage & MediaStore Integration
Lazzy Gallery complies with Android Scoped Storage requirements (API 29+ while maintaining backward compatibility down to API 24). Queries utilize targeted projections (`MediaStore.Images.Media` and `MediaStore.Video.Media`) to minimize memory allocation during cursor traversal. File mutations and deletions leverage `MediaStore.createDeleteRequest` and `MediaStore.createWriteRequest` intents for seamless user-consented batch operations.

### Multi-Tier Caching Pipeline
To eliminate cold-start lag when launching the application or switching fragments, Lazzy Gallery implements a three-tier caching strategy:
1. **Global In-Memory Cache (`GlobalMediaCache`)**: Retains active list references in RAM during navigation transitions.
2. **Instant JSON Disk Snapshot**: Serializes core media metadata to `context.cacheDir` on startup for sub-millisecond retrieval.
3. **Room Database Persistence**: Stores complete media entities locally. If system queries are delayed, cached database records render instantly while background synchronization reconciles delta changes.

### Background Synchronization
Periodic and event-driven data synchronization is managed through `WorkManager` (`MediaSyncWorker`). The application registers content observers on MediaStore directories to detect external file additions or deletions, triggering incremental Room database updates without blocking the main thread.

### Ambient Background & Visual Effects
Media viewing screens utilize custom Compose rendering pipelines featuring dynamic ambient blur filters and floating navigation elements (`FloatingNavigationBar`) with blur mask rendering for an immersive user experience.

---

## Tech Stack & Dependencies

| Category | Technology | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **UI Toolkit** | Jetpack Compose | BOM 2026.02 | Declarative UI rendering & state observation |
| **Design System** | Material 3 | Latest | Material Design specifications & dynamic theming |
| **Navigation** | Navigation Compose | 2.10.1 | Type-safe route navigation & animations |
| **Persistence** | Room | 2.7.1 | Relational database caching & DAO queries |
| **Image Loading** | Coil | 2.7.0 | High-performance bitmap, GIF, & video frame decoding |
| **Video Playback** | Media3 ExoPlayer | 1.5.1 | Hardware-accelerated video rendering |
| **Background Tasks** | WorkManager | 2.9.1 | Reliable background media synchronization |
| **Metadata** | Exifinterface | 1.3.7 | EXIF tag extraction |

---

## Project Directory Structure

```text
com.lazzy.gallery/
│
├── data/
│   ├── local/
│   │   ├── GalleryDatabase.kt    # Room database abstract definition
│   │   ├── MediaDao.kt           # SQL queries and data access methods
│   │   └── MediaEntity.kt        # Room entity model for cached media
│   ├── worker/
│   │   └── MediaSyncWorker.kt    # WorkManager background sync worker
│   ├── MediaModel.kt             # Domain data models (MediaModel, Album, ExifMetadata)
│   ├── MediaRepository.kt        # MediaStore and Room orchestration layer
│   └── MediaStoreThumbnailFetcher.kt # Custom Coil thumbnail fetcher
│
├── ui/
│   ├── components/
│   │   ├── FloatingNavigationBar.kt # Custom floating bottom navigation
│   │   ├── MediaComponents.kt       # Grid layouts, cards, and selection bars
│   │   └── VideoPlayerView.kt       # ExoPlayer Compose wrapper
│   ├── navigation/
│   │   └── GalleryNavigation.kt     # Navigation graph and routing
│   ├── screens/
│   │   ├── AlbumDetailScreen.kt     # Album contents view
│   │   ├── AlbumsScreen.kt          # Album bucket overview
│   │   ├── FavoritesScreen.kt       # Bookmarked media collection
│   │   ├── PhotoDetailScreen.kt     # Immersive viewer and EXIF sheet
│   │   ├── RecentPhotosScreen.kt    # Main timeline grid
│   │   ├── RecycleBinScreen.kt      # Deleted items recovery view
│   │   └── SearchScreen.kt          # Real-time search interface
│   ├── theme/
│   │   ├── Color.kt                 # Material 3 color definitions
│   │   ├── Theme.kt                 # Material theme configuration
│   │   └── Type.kt                  # Typography styles
│   └── viewmodel/
│       └── MediaViewModel.kt        # Central state holder and business logic
│
└── MainActivity.kt                 # Application entry point and permission flows
```

---

## Installation Guide

To install Lazzy Gallery directly on your Android device:

1. Navigate to the **Releases** tab in this GitHub repository.
2. Download the latest `.apk` file (`app-release.apk`).
3. On your Android device, open your device settings and enable **"Install from unknown sources"** for your browser or file manager.
4. Tap the downloaded `.apk` file and complete the installation.
5. Open Lazzy Gallery, grant storage permissions, and enjoy!

---

## Building & Running from Source

### System Requirements

- **Android Studio**: Ladybug, Koala, or newer.
- **Java Development Kit (JDK)**: Version 11 or higher.
- **Android SDK**: Compile SDK 37, Min SDK 24, Target SDK 35.

### Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/iabhnv19/lazzy-gallery.git
   ```
2. Open Android Studio and select **Open an Existing Project** pointing to the repository root.
3. Allow Gradle to synchronize dependencies.
4. Connect an Android device (API 24+) or launch an emulator.
5. Build and execute the `:app` run configuration (`Shift + F10`).

---

## Contributing

Contributions to Lazzy Gallery are welcome. Please adhere to the following workflow:

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/Optimization`).
3. Commit your changes (`git commit -m 'Improve query performance in MediaDao'`).
4. Push to the branch (`git push origin feature/Optimization`).
5. Open a Pull Request for review.

---

## License

Lazzy Gallery is open-source software licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.
