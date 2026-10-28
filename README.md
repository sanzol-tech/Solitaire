# Solitaire

Classic Klondike solitaire for Android: draw 1 / draw 3, tap or drag, undo, clock,
automatic finish and sound. Plain Java, no third-party runtime dependencies, no
permissions. UI in English and Spanish.

| Item | Value |
|---|---|
| Package | `ar.sanzol.solitaire` |
| Application ID | `ar.sanzoltech.solitaire` |
| Toolchain | AGP 9.4.1, Gradle 9.8.0, Java 21 |
| SDK | compile/target 37, min 31 |

## Layout

The Git repository is this project folder. The workspace around it holds what
never enters Git:

```
Solitaire\
  make_release.bat        builds and stages a signed release
  keystore\               create_solitaire_keystore.bat + solitaire-release.jks
  release\<version>\      staged APK + mapping.txt
  docs\                   audit notes
  sources\Solitaire\      this project (Git repository)
```

In the project, `game/` is the pure Java model (unit-tested), `ui/` the views.

## Build

```bash
./gradlew testDebugUnitTest lint
./gradlew assembleDebug
```

## Release

1. Once: `keystore\create_solitaire_keystore.bat` creates `keystore\solitaire-release.jks`.
   Back it up outside the PC; losing it means the app can never be updated.
2. Once: copy `keystore.properties.example` to `keystore.properties` and set the passwords.
3. Each release: bump `versionCode`/`versionName`, then run `make_release.bat` from the
   workspace (or `./gradlew :app:stageRelease`). It builds the minified release, signs it
   (v2 only), verifies the signature and stages `release\<version>\solitaire-<version>.apk`
   with its `mapping.txt`. Keep the `mapping.txt` private: it is the only way to read a
   release stack trace.

Without `keystore.properties` the release still builds, minified but unsigned.
