# How to get full MyTube source

This repository holds the **MyTube branding & UI changes** on top of LibreTube.

## Option A — Overlay onto LibreTube

```bash
git clone --depth 1 https://github.com/libre-tube/LibreTube.git MyTube
cd MyTube
```

Then replace these paths with the files from this repo:

- `README.md`
- `app/build.gradle.kts` (display name only)
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/github/libretube/ui/activities/AboutActivity.kt`
- `app/src/main/res/layout/activity_about.xml`
- `app/src/main/res/layout/fragment_library.xml`
- `app/src/main/java/com/github/libretube/ui/fragments/LibraryFragment.kt`
- App icons under `app/src/main/res/mipmap-*/ic_launcher*.png`

## What changed

- App display name → **MyTube**
- New launcher icons (transparent background)
- Library tab → Profile hub (Settings + About buttons)
- About page → MyTube branding, **mokka coding** creator, feature highlights
- Package ID / Piped endpoints **unchanged**

## Creator

**mokka coding**
