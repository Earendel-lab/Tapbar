# Contributing to Tapbar

Tapbar is a small, privacy-first app focused on quick actions and gestures. It is kept simple on purpose. Please read this before opening an issue, a discussion or a pull request.

## Where things go

- **Bugs:** [Issues](https://github.com/Earendel-lab/Tapbar/issues/new/choose)
- **Feature requests and ideas:** [Discussions, Ideas category](https://github.com/Earendel-lab/Tapbar/discussions/new?category=ideas)
- **Questions and help:** [Discussions, Q&A category](https://github.com/Earendel-lab/Tapbar/discussions/new?category=q-a)

Issues are only for bug reports. Feature requests and questions opened as issues will be closed and redirected to Discussions.

## Before you post

- Search open and closed issues and existing discussions first. Duplicates will be closed.
- Read the README and the [latest release changelog](https://github.com/Earendel-lab/Tapbar/releases/latest). The feature may already exist, or the bug may already be fixed.
- Write in English. Use a translator if you need to.
- Fill in the form completely. Posts with missing details will be closed.
- If I ask a follow-up question, answer within 24 hours or the issue or discussion will be closed. You can reply to reopen it.

## Bug reports

Open an issue and include your device, Android version, ROM, Tapbar version, steps to reproduce, and what you expected vs what happened. A screenshot or screen recording helps a lot.

## Feature requests

Start a discussion in the Ideas category. Explain the problem you are trying to solve and why it belongs in Tapbar, not just the solution you have in mind.

Ideas are considered based on the use case, feedback and discussion in the thread.

These will not be added:

- Anything that needs root, Shizuku, Sui or other helper apps or services
- Advanced or system-level options
- Analytics, trackers, ads or network calls

If your idea needs one of these, it is out of scope and the discussion will be closed.

## Questions

Ask in the Q&A category of Discussions, not in Issues. Check the README first, since most questions are answered there.

## Pull requests

Open an issue first for a bug fix, or a discussion in the Ideas category for a new feature, for anything bigger than a small fix, so you don't spend time on something that won't be merged.

1. Fork the repo and create a branch from `main`.
2. Open the project in Android Studio and let Gradle sync.
3. Make your change and test it on a device or emulator. Test on a real device if it touches overlays, permissions or boot behavior.
4. Open a pull request against `main`. Say what it changes, why, and how you tested it. Link the bug issue (for example `Closes #12`) or the discussion for a feature.

Guidelines:

- One fix or feature per pull request.
- Follow the existing Kotlin style and naming.
- Don't add new dependencies unless there is no other way. Tapbar should stay lightweight.
- Don't add analytics, trackers, ads or network calls. Privacy and offline behavior are core to the app, and a PR that breaks them won't be merged.
- Write clear commit messages, for example `Fix tap zone not responding after rotation`.

## Translations

Start a discussion in the Ideas category first so it can be coordinated.

Translations must be completed as soon as possible once started. Do not open a pull request with half-translated or untranslated strings. Every string must be translated before the pull request is opened, and incomplete translations will be closed.

## Conduct

Be respectful and keep feedback about the code or the idea.
