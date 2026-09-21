# Contributing to Outro

Thanks for wanting to help! A few ground rules:

## How to contribute

1. **Fork** the repo and create a branch for your change.
2. Make your change, keeping the existing code style (official Kotlin style).
3. Run the check suite: `python3 scripts/test_108.py build-manual/apk/outro.apk` — it should report 54/54.
4. Open a **pull request** describing what you changed and what device(s) you tested on.

## Reporting bugs

Please include:

- Device model + Android version
- Outro version (e.g. 1.0.8)
- What you were doing when it happened
- A logcat snippet if you can (`adb logcat | grep opus` — the app tags its logs)

## What needs help most

- **Real-device testing** — the app was developed against a single Oppo A96. Reports from Pixels, Samsungs, Xiaomis etc. are gold.
- **Equalizer** — the settings entry exists but the feature isn't implemented.
- **Gradle migration** — the working build is `scripts/manual_build.sh`; making a standard Gradle build work would lower the barrier for everyone.
- **Screenshots** for the README.

## Code of conduct

Be kind. Review the change, not the person. This started as a vibe-coded side project — keep it fun.
