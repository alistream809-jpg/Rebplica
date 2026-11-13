# Replica

Android app that analyses an edit video and rebuilds it as an Alight Motion XML project.

What it really does on the device (no cloud, no mock data):

- Decodes every frame (MediaCodec) and detects hard cuts, light flashes, dips to black.
- Estimates global motion per frame (shake and zoom) and writes it as keyframes.
- Extracts the edit's song (lossless AAC copy to .m4a, WAV fallback), detects tempo and beats.
- Writes an Alight Motion scene: one layer per clip, overlay layers, audio layer, beat bookmarks.
- Opens the XML in Alight Motion, or saves/shares it.

## Get the APK

1. Create a GitHub repository and push this folder to the `main` branch.
2. Open the **Actions** tab. The `Build APK` workflow runs automatically (or run it manually).
3. Download the APK from the run's **Artifacts** (`Replica-APK`) or from the **Releases** page.

Requires Android 10 (API 29) or newer.

## Local build

```
gradle assembleRelease
```
(JDK 17, Android SDK 34, Gradle 8.7)
