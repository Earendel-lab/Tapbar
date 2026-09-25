# Contributing to Tapbar

First off, thanks for taking the time to contribute! Tapbar is a small, privacy-first project, and every issue, idea, or pull request helps make it better.

## Ways to Contribute

- **Report a bug** — open an issue with steps to reproduce, your Android version/device, and what you expected to happen.
- **Suggest a feature** — open an issue describing the use case, not just the solution. Explain *why* it would help.
- **Fix a bug or build a feature** — check open issues, especially ones labeled `good first issue` or `help wanted`.
- **Improve documentation** — README clarity, code comments, and setup instructions all count.
- **Translations** — if you'd like to help localize Tapbar, open an issue first so we can coordinate.

## Getting Started

1. **Fork** the repository and clone your fork:
   ```
   git clone https://github.com/<your-username>/Tapbar
   ```
2. **Create a branch** for your change:
   ```
   git checkout -b feature/short-description
   ```
3. **Open the project in Android Studio** and let Gradle sync.
4. Make your changes, then build and run locally on a device or emulator to confirm everything works as expected.

## Development Guidelines

- Keep changes focused — one feature or fix per pull request.
- Match the existing code style (Kotlin conventions, existing naming patterns).
- Avoid adding new dependencies unless necessary; Tapbar aims to stay lightweight.
- Do not introduce analytics, trackers, ads, or any network calls. Privacy and offline-first behavior are core to this project and any PR that compromises them will not be merged.
- Test on a real device if possible, especially for anything involving overlays, permissions, or boot behavior.

## Commit Messages

Write clear, descriptive commit messages, e.g.:
```
Fix tap zone not responding after screen rotation
Add option to resize tap zone from settings
```

## Submitting a Pull Request

1. Push your branch to your fork.
2. Open a pull request against the `main` branch of this repository.
3. In the PR description, explain:
   - What the change does
   - Why it's needed
   - How you tested it
4. Link any related issues (e.g. `Closes #12`).
5. Be responsive to review feedback — small follow-up commits are totally fine.

## Code of Conduct

Be respectful and constructive. Assume good intent, keep feedback focused on the code/idea, and help keep this a welcoming space for contributors of all experience levels.

## Questions?

If anything is unclear, open an issue and ask. There's no such thing as a bad question — better to ask than to guess.

Thanks again for helping make Tapbar better! ⭐
