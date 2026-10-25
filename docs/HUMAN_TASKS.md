# Human tasks and manual device tests

These tests have **not** been done yet: no Android device or emulator was available in the build environment. The JVM unit tests and the APK build were run. See `UPGRADE_CHECKLIST.md`.

## Setup
1. Install `app/build/outputs/apk/debug/app-debug.apk` with `adb install -r app-debug.apk`.
2. Android 13+ with a sideloaded APK: go to *App info → ⋮ → Allow restricted settings*, then grant **notification access** (Profile → Notification access, or the in-app guide).
3. Allow Attune's own notifications when asked. Optionally turn off battery optimisation for Attune.

## AI Mode and decisions (core feature)
1. **AI Mode playground:** Profile → AI Mode → *Try it*. Check each of these:
   - "call me right now, it's urgent" from Mom → **Let through**.
   - "Flat 50% off ends tonight" from Amazon → **Held**.
   - "call me" → **Kept visible** with focus risk **Unknown**.
   - `https://bit.ly/abc` → **Kept visible**, and link "can't verify".
2. **Real notifications:** start a 25-minute session. From a second phone:
   - Send a casual WhatsApp message → it should disappear from the shade and appear under Queue → *Held*. (It may still buzz or flash once; this is an Android limit.)
   - Send "Please call me, it's an emergency" → it should stay in the shade (safety rule).
   - Send "hi" → it should stay in the shade and appear under *To review*.
3. **Rules come first:** add the sender's name as a priority contact (Rules) → their casual messages now come through. Then, on any held Instagram item, choose *Correct it → Always hold this type* (this creates a Social media hold rule shown under Rules), and trigger another Instagram notification → it is held with "Matches your rule".
4. **Before You Open:** open a held item and check:
   - risk chip, signals, reasons, and "Decided by"
   - "A rule overrode the AI" when a rule decided
   - Open now / Delay / Dismiss each work, and double taps do nothing extra.
5. **Feedback:** *Correct it → Actually important* on a held WhatsApp message → Profile → Learned preferences shows WhatsApp at about +0.33. Delete it → the entry disappears.
6. **Pause and end:** pause → new notifications are not held. End the session → one summary notification appears ("N notifications waited for you"), and tapping it opens the Queue.
7. **AI Mode off:** turn it off on Home → decisions show "Category rules (AI Mode off)".

## Persistence and migration
1. **Timer:** start a session, force-stop Attune and reopen it → the remaining time is correct. Lock the screen through a full session → it completes on time and the completion notification appears.
2. **Upgrade:** install the previous v2 build (commit `f5cb7a4`), create sessions and queue items, then install this build → the old data is still there, and the energy history shows 1–5 scores.

## Reports and profile
1. Run a few sessions with check-ins. Then check Insights → Reports:
   - Daily / Weekly / Monthly switch correctly, and the arrows move between periods.
   - Tapping a bar shows exact values.
   - *Share summary* contains counts only.
2. Insights → Profile shows score parts, streak, peak hours (after 3 sessions), energy and risk distribution.
3. Turn demo mode on → the "Demo sample" chip shows the DEMO-labelled data. "My activity" must not include demo numbers.

## Privacy
Privacy center → *Remove stored notification text now* → queue items show "Content removed". Then delete activity → reports go back to empty, while settings and demo data remain.

## Other
- Collect consented, labelled real notifications before claiming real-world accuracy. Retrain with `ml/train.py` afterwards.
- App signing and Play Console (post-prototype). Note that Google Play requires a policy justification for notification listener access.
