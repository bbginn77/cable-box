# Cable Box for Google TV

An early-2000s digital cable box interface for the onn Google TV stick: main menu,
program guide, channel surfing, On Demand, favorites and settings. It opens your real
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

Updating later: rebuild, then repeat step 3. It installs over the old version and keeps
your favorites and settings.

## Backup and restore plan

Nothing on the stick is modified apart from installing one app, so restoring is simple:

- **Remove the cable box:** Settings → Apps → Cable Box → Uninstall.
- **Undo the sideload permission:** Settings → Apps → Security & restrictions → Unknown sources → Downloader off.
- Your streaming apps, logins, inputs and Google TV home screen are never touched.

The optional extras below would each get their own written plan and your approval first:
- Opening the cable box with the Home button (button-remapping app, removable any time).
- Making it the true home screen (needs Developer Options and ADB; reversible with one command).

## Project layout

- `app/src/main/assets/www/index.html`: the whole interface (HTML, CSS and JavaScript).
- `app/src/main/java/.../MainActivity.kt`: full-screen web view, remote buttons, opening apps.
- `app/cablebox.keystore`: a fixed signing key so updates install over each other. Personal use only.
