<div align="center">

# MyTube

**Privacy-first YouTube client for Android**

Rebuilt from [LibreTube](https://github.com/libre-tube/LibreTube) with a cleaner identity, profile hub, and enhanced About experience.

[![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android)](https://github.com/keerthan4531-a11y/MyTube)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

</div>

---

## Highlights

- **MyTube branding** — fresh name & icon, same powerful Piped-based backend
- **Smart Library Profile** — profile-style header with quick access to Settings & About
- **Enhanced About** — animated highlights, creator credit, feature showcase
- **No official YouTube API** — privacy-focused alternative frontend
- Subscriptions, playlists, downloads, SponsorBlock and more

## Creator

**mokka coding**

## Full source

This repo contains the **MyTube branding layer**. For the complete buildable Android project:

1. Clone upstream LibreTube, then overlay the files listed in [`APPLY_ON_LIBRETUBE.md`](APPLY_ON_LIBRETUBE.md)
2. Or use the full `MyTube-source.zip` package (complete tree with icons + all changes)

```bash
git clone --depth 1 https://github.com/libre-tube/LibreTube.git MyTube
cd MyTube
# overlay files from this repo (see APPLY_ON_LIBRETUBE.md)
./gradlew assembleDebug
```

Package ID and Piped API endpoints are **unchanged** so the app keeps working.

## Credits

Based on **LibreTube** by the LibreTube team.  
Backend powered by [Piped](https://github.com/TeamPiped/Piped).

## License

GNU GPL v3 (same as upstream LibreTube)
