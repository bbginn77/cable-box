# NyxVision for Google TV

An early-2000s digital cable box interface for the onn Google TV stick: main menu,
program guide, channel surfing, On Demand, favorites and settings. Every channel is a real Pluto TV
channel playing inside the box; the main menu shows local radar and current conditions.
Channel 4 is NyxVision Weather: radar, current conditions, the extended forecast and alerts for the
place set in Settings → Weather Location (US ZIP, Canadian postal code or city), with seasonal music
(press OK on it). US forecasts come from the National Weather Service, others from Open-Meteo.
Channel 5 is Nyx News Network: local stories for the Weather Location and national headlines from
public news feeds (headline, source, short feed summary, photo). OK shows more and a QR code to the full story.
Holiday themes switch on by themselves for 40+ holidays; manage them in Settings → Holiday Themes. It opens your real
streaming apps: DIRECTV, Netflix, YouTube, Hulu, Disney+, HBO Max, Paramount+ and Peacock.

It installs as a normal app. It does **not** root the stick, change developer settings,
disable anything, or replace the Google TV home screen.

## Remote controls (onn remote)

| Button | Full-screen TV | Menus and guide |
|---|---|---|
| ▲ / ▼ | Channel up / down | Move highlight |
| ◀ | Main menu | Back a level / browse earlier |
| ▶ | TV guide | Browse later |
| OK | Show channel banner, press again for guide | Select |
| Back | Last channel | Back a level |
| Home | Leaves the cable box (Google TV home) | Same |

Volume, mute, power and Input keep working normally.

## Step 1: Build the APK (pick one)

### Option A: GitHub, no computer needed for the build
1. Make a free account at github.com and create a new **public** repository named `cable-box`.
2. Upload every file and folder in this project to it (on a computer: "Add file → Upload files",
   then drag the whole folder in). Keep the hidden `.github` folder.
3. Open the **Actions** tab. If asked, click "I understand my workflows, enable them".
   Run **Build Cable Box APK** (it also runs on every upload). It takes about 4 minutes.
4. When it shows a green check, your download link is:
   `https://github.com/YOUR-USERNAME/cable-box/releases/latest/download/cable-box.apk`

### Option B: Android Studio on a computer
Open this folder in Android Studio, wait for it to sync, then Build → Build APK(s).
The file appears in `app/build/outputs/apk/`.

## Step 2: Install on the onn stick

1. On the stick, install **Downloader** (by AFTVnews) from the Google Play Store.
2. Settings → Apps → Security & restrictions → Unknown sources → turn on **Downloader** only.
   This lets Downloader install apps. It is not a developer setting.
3. Open Downloader, type your download link from Step 1, and choose **Install**.
4. Optional: turn the Unknown sources switch for Downloader back off.
5. On the Google TV home screen, find **Cable Box** under Apps, long-press it and
   move it to the front of the row.

Updating later: Settings → Check for Updates downloads the newest build from this repo and opens
Android's installer (Android asks once to allow NyxVision to install its own updates). You can still
repeat step 3 with Downloader. Either way it installs over the old version and keeps your settings.

## Android tablet

The same app works on an Android tablet with touch controls:
- Tap a menu item, setting or show to choose it. Tap the left or right arrows on a setting to change it.
- While watching: tap to show the channel banner and a control bar (Menu, Guide, CH, Last, Fav);
  swipe up/down to change channels, swipe left for the guide, swipe right for the menu.
- In the guide: tap a show to highlight it, tap again to watch or set a reminder; swipe to scroll.
- The footer hints (BACK, MENU, GUIDE...) are tappable. Back on the main menu leaves the app.

Install: on the tablet, open Chrome and go to
`github.com/BBGINN77/cable-box/releases/latest/download/cable-box.apk`, open the download,
allow Chrome to install apps when asked, and choose Install. Updates come from Settings → Check for Updates.

## Backup and restore plan

Nothing on the stick is modified apart from installing one app, so restoring is simple:

- **Remove the cable box:** Settings → Apps → Cable Box → Uninstall.
- **Undo the sideload permission:** Settings → Apps → Security & restrictions → Unknown sources → Downloader off.
- Your streaming apps, logins, inputs and Google TV home screen are never touched.

The optional extras below would each get their own written plan and your approval first:
- Opening the cable box with the Home button (button-remapping app, removable any time).
- Making it the true home screen (needs Developer Options and ADB; reversible with one command).

## Live data

- **Local Weather** (Applications → Local Weather): current conditions, 5-day forecast and any
  active alerts for Columbus, GA 31909 from the National Weather Service's free public data.
  Refreshes every 30 minutes. Alerts turn the bottom crawl red. Channel 4 shows the live temperature.
  To change the location, edit `WXLOC` near the weather section in `assets/www/index.html`.
- **Severe weather alerts** (Settings → Weather Alerts): when the National Weather Service issues a
  warning for your Weather Location, NyxVision interrupts whatever is on with a full-screen alert card,
  then runs a red crawl across the top. Watches get a crawl only. Checks every 2 minutes, U.S. only.
  The sound is NyxVision's own chime, not the broadcast emergency tones.
- **Race call alerts** (Settings → Race Call Alerts): from 6 PM ET on November 3 through November 6,
  2026, a RACE CALLED banner slides up on any channel when civicAPI reports a Senate or governor winner.
- **Sports scores** (Settings → Sports Scores): pick leagues for the Nyx News Network ticker and
  star your teams for score and final-score pop-ups. Uses ESPN's public scoreboard data
  (unofficial, for personal use).
- **Test Center** (Settings → Test Center): run any weather alert, a race call for each party, or the
  sports pop-ups and crawl. Each test jumps to a random channel and is clearly marked TEST.
- **Pluto TV listings**: free Pluto TV channels appear in the guide from channel 200 up.
  They play right inside NyxVision: full screen, in the guide's preview window, and CH ▲/▼
  surfs into them. Pluto's own ads stay in the stream. If a channel won't play, the box offers
  to open it in the Pluto TV app instead. Settings → Pluto TV Plays In switches to always using the app.
  Pluto doesn't publish an official listings feed, so this reads the same public guide data
  their website uses, like many hobby DVR apps do. It can stop working if Pluto changes it;
  the rest of the box keeps working if that happens. Turn it off or show all channels in Settings.

## Project layout

- `app/src/main/assets/www/index.html`: the whole interface (HTML, CSS and JavaScript).
- `app/src/main/java/.../MainActivity.kt`: full-screen web view, remote buttons, opening apps.
- `app/cablebox.keystore`: a fixed signing key so updates install over each other. Personal use only.
