# TDD Evidence Report — @-mention tagging (#7)

## Source plan
Derived from GitHub issue #7 (feat: tag users with @ and scrollable dropdown) and the plan
locked during the planning session (mention target = `username` string, inline token =
`@{username}`, 280-char budget includes token metadata). No `*.plan.md` file was used.

## User journeys
1. As a user, typing `@` in the composer opens a scrollable dropdown of known users,
   filtered by what I type, so I can quickly mention someone.
2. As a user, selecting a name inserts a mention that renders as a styled chip inside the
   composer, distinct from normal text.
3. As a user, when a mentioned user changes their nickname, previously tagged posts render
   the new alias (mentions reference the stable username).
4. As a reader, mentions in the timeline render as styled `@alias` and are tappable.

## Task report

| Task | Summary | Validation command | Result |
|------|---------|--------------------|--------|
| Mention model | token/parse/findAll/activeMention/insert/deleteBefore/visualize+offset-mapping/filter | `./gradlew testDebugUnitTest --tests com.bitter.model.MentionTest` | RED (compile: `Mention` unresolved) then GREEN |
| Candidates + filter | `Mention.Candidate` + prefix filter on display name and username | same test class | GREEN |
| ViewModel candidates | `TimelineViewModel.candidates` derived from `displayNames` | `./gradlew testDebugUnitTest` | GREEN |
| Composer UI | `MentionComposer` (BasicTextField + chip VisualTransformation + dropdown + token-aware backspace) | `./gradlew assembleDebug` | GREEN |
| Timeline rendering | `PostContent` resolves `@{username}` -> styled clickable `@alias` | `./gradlew assembleDebug` | GREEN |

### RED evidence
`./gradlew testDebugUnitTest --tests "com.bitter.model.MentionTest"` failed at
`:app:compileDebugUnitTestKotlin` with `Unresolved reference 'Mention'` (production class did
not exist yet). Compile-time RED.

### GREEN evidence
After implementing `app/src/main/java/com/bitter/model/Mention.kt`, the same target compiled
and passed (22 tests). Full suite and APK build:
`./gradlew testDebugUnitTest` -> BUILD SUCCESSFUL
`./gradlew assembleDebug` -> BUILD SUCCESSFUL

## Test specification

| # | What is guaranteed | Test | Type | Result |
|---|--------------------|------|------|--------|
| 1 | `token()` wraps username in `@{...}` | `MentionTest.kt` | unit | PASS |
| 2 | `parse()` splits plain text and mentions; empty content -> empty list | `MentionTest.kt` | unit | PASS |
| 3 | `findAll()` reports exact token ranges | `MentionTest.kt` | unit | PASS |
| 4 | `activeMention()` detects partial mention, bare `@`, and returns null after a closed token / no `@` | `MentionTest.kt` | unit | PASS |
| 5 | `insert()` replaces the active range with the token | `MentionTest.kt` | unit | PASS |
| 6 | `deleteBefore()` removes a whole token when cursor follows it, else one char | `MentionTest.kt` | unit | PASS |
| 7 | `visualize()` resolves alias and maps raw<->visual offsets across one and multiple mentions; falls back to username | `MentionTest.kt` | unit | PASS |
| 8 | `filter()` matches display-name/username prefix (case-insensitive), returns all on empty query, empty on no match | `MentionTest.kt` | unit | PASS |

## Coverage

Jacoco (`./gradlew jacocoTestReport`) for the new `com.bitter.model.Mention` class:
- Instruction: 99.7% covered (622/624)
- Branch: 83.9% covered (47/56)
- Line: 100% covered (75/75)

Known gaps: the Compose UI wiring (`MentionComposer`, `MentionDropdown`, `MentionTransformation`,
`PostContent`) is not unit-tested (no Compose UI test harness in this module); it is verified via
`assembleDebug` compilation and covered indirectly by the pure-logic tests in `Mention.kt`.

## Merge evidence
No commits were made (committing was not requested). The RED -> GREEN evidence above is
preserved in this report for the eventual PR body/squash.
