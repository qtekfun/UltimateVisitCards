# Changelog

All notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org). Each development phase (docs/07-fases.md) raised the minor version.

## [Unreleased]

## [0.12.0] - 2026-10-11

Ready for publication.

### Added

- GPL-3.0-or-later licence, privacy policy (`PRIVACY.md`), store texts in English and Spanish (`fastlane/`) and the F-Droid build recipe (`fdroid/`).
- The release also ships a signed Android App Bundle of the `play` flavour for Google Play.
- "About" shows the app's own licence.

## [0.11.0] - 2026-10-11

### Added

- Optional app lock with fingerprint, face or screen lock; it asks again after 30 seconds away.
- "Hide in recent apps" (blocks screenshots too).
- "Keep uncropped originals" switch; turning it off frees the space they used.
- Confidence threshold for the fields highlighted in the review.
- "Rebuild search index" and "Possible duplicates" (merge straight from the list).
- "About": version, text recognition engine, privacy and third-party licences.

### Changed

- Release APKs only include ARM libraries: about 24 MB instead of 38–45 MB.

## [0.10.0] - 2026-10-11

### Added

- Full backup to a single `.zip`, optionally encrypted with AES-256, checked by reading it back.
- Restore by combining (never duplicates) or replacing everything, all or nothing.

## [0.9.0] - 2026-10-11

### Added

- Export the current list, filtered or not, to CSV and Excel (`.xlsx`).

## [0.8.0] - 2026-10-11

### Added

- Contact page with quick actions (call, email, website, copy), card history and sharing as vCard.
- Edit contacts, merge two contacts choosing each value, with undo.
- Add a new card to an existing contact.
- "Add to contacts" in the phone's address book and `.vcf` export.

## [0.7.0] - 2026-10-11

### Added

- Search across every field with combinable filters (name, job title, company, country, sector, event, dates).
- Grid or list view; Events, Companies and Sectors screens.

## [0.6.0] - 2026-10-11

### Added

- Quick scan mode for trade fairs: shoot card after card and review them later from the "pending review" queue.

## [0.5.0] - 2026-10-11

### Added

- Review screen with the fields read from the card, duplicate warning and saving as a contact.

## [0.4.0] - 2026-10-11

### Added

- On-device text recognition (ML Kit in `play`, Tesseract in `foss`) and field extraction in five languages.

## [0.3.0] - 2026-10-11

### Added

- Camera capture with automatic card detection and straightening, plus manual cropping.

## [0.2.0] - 2026-10-11

### Added

- Local database with full-text search.

## [0.1.0] - 2026-10-11

### Added

- Project skeleton: `play` and `foss` flavours, navigation, English and Spanish.

[Unreleased]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.12.0...HEAD
[0.12.0]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.11.0...v0.12.0
[0.11.0]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/qtekfun/ultimatevisitcards/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/qtekfun/ultimatevisitcards/releases/tag/v0.1.0
