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
- **Election night bar** (Settings → Election Night Bar): from 6 PM ET on November 3 through
  November 6, a Balance of Power lower third (Senate, House, governors) sits on every channel.
  Test it from Settings → Test Center → Election Race Calls → Balance of Power bar.
- **Sports scores** (Settings → Sports Scores): pick leagues for the Nyx News Network ticker and
  star your teams for score and final-score pop-ups. Uses ESPN's public scoreboard data
  (unofficial, for personal use).
- **Test Center** (Settings → Test Center): run any weather alert, a race call for each party, or the
  sports pop-ups and crawl. Each test jumps to a random channel and is clearly marked TEST.
- **Nyx Traffic & Transit** (channel 6): press OK to switch views.
  - Live Traffic: a TomTom traffic-flow map and nearby incidents for your Weather Location. Needs a
    free TomTom developer key (developer.tomtom.com), typed into Settings → Traffic Map Key.
  - Transit boards with a NyxVision-drawn map and live line status, no key needed:
    New York City Subway (MTA alerts + station data from data.ny.gov), Boston subway (MBTA V3 API:
    routes, live train positions, alerts) and Philadelphia Regional Rail (SEPTA TrainView).
    Atlanta MARTA rail: NyxVision-drawn map of the Red, Gold, Blue and Green lines; live trains, line
    status and next trains need a free MARTA developer key (Settings → MARTA Train Key).
    Under the lines, NyxVision draws water and major highways from the U.S. Census Bureau's public-domain
    TIGER map data (downloaded once per city, then saved on the device).
    If your Weather Location is in one of those cities, its board comes first, marked YOUR AREA.
    Official agency logos and maps aren't copied; line colors are the agencies' published colors.
- **Channel 1, NyxVision Preview**: what's on every channel now, with a scrolling guide and music.
- **Channel 7, Nyx Classic Theater**: public-domain feature films from the Internet Archive, scheduled
  like live TV (a new lineup every day) and played inside NyxVision.
- **Local on the 8s** (Settings → Alerts & Tickers): a corner forecast every ten minutes.
- **Games** (Main Menu): Chess vs Nyx (Easy/Medium/Hard, game saved) and Nyx & the Mice.
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

## Pay-Per-View and the guide download

- **Classic Theater Pay-Per-View** (Settings → TV & Guide): channel 7 shows a 2000s pay-per-view order screen for each movie. Press OK and the box "dials out" (with modem sounds) and confirms the order, then the movie starts. It's free and nothing is ordered or charged. Turn it off to play movies straight away.
- **Daily Guide Download** (Settings → TV & Guide): the first start each day shows an "Updating Program Guide" screen while listings, weather and movies load. OK skips it. **Update Program Guide Now** runs it any time.
- Classic Theater skips propaganda, hate films, atrocity footage and adult films, by search filter and by title, subject and description checks.

## On-screen remote (tablet)

A round **REMOTE** button floats on screen on touch devices. Drag it anywhere; tap it to open a 2000s satellite-style remote (GUIDE, MENU, arrows, SELECT, VOL, CH, PREV CH, number pad, channel shortcuts WX/NEWS/TRAF/MOV/PRV). Drag the remote by its top edge; PWR or the button closes it. **Settings → On-Screen Remote Button** turns the button off and back on.

## Channel 8: Nyx Tape Deck (Spotify)

Your Spotify playlists on a cassette deck. The Spotify app (phone/tablet version) plays the music; NyxVision shows the deck, reels, label and J-card, and sends play/pause/skip. Uses Spotify's App Remote SDK 0.8.0 (Apache 2.0, in `app/libs`).

Setup: at developer.spotify.com/dashboard (Premium account) create an app, check **Android**, redirect URI `http://127.0.0.1:8898/callback`, Android package `com.retrocable.box` with SHA1 `89:73:96:81:40:5B:13:77:BE:88:E2:DC:25:25:9F:96:36:5A:76:35`. Enter the Client ID in Settings → Service Keys → Spotify Client ID. Keys: OK play/pause, ◀▶ skip, INFO tape shelf. Leaving channel 8 pauses the tape; coming back resumes it. On Google TV, channel 8 offers to open Spotify's TV app instead.

Spotify rules NyxVision follows: credits Spotify on the deck and shelf, links back to the album ("Open in Spotify"), shows terms & privacy before connecting (Settings → Service Keys → Spotify Terms & Privacy), offers Settings → Service Keys → Disconnect Spotify, keeps Spotify data in memory only, and plays no NyxVision sounds over the music (weather warnings pause the tape).

## Channel 9: Nyx Toons
Public-domain cartoons from the Internet Archive's Classic Cartoons collection, back to back all day. Wartime propaganda and racist caricature cartoons are filtered out by title, subject and a list of known titles. The guide shows half-hour blocks listing the cartoons; each cartoon's title pops up in the corner when it starts.

## Message Center
Main Menu → Messages. A monthly bill on the 1st (pay it with a chin scratch: nothing is charged), weekly notes from Nyx, Rex and the cable company, birthday messages for Bailey and Taylor, holiday greetings, election night notice and pay-per-view receipts.

### Nyx Toons logo and commercial breaks
A corner logo shows during cartoons; at :00, :15, :30 and :45 Nyx pops out and says meow. After every cartoon there's a break: "We'll be right back!", about 2 minutes of commercials, then "And now, back to Nyx Toons!".
Commercials (Settings → TV & Guide → Nyx Toons Commercials): **Classic Clips** plays dated 2006–2009 kids-TV commercial-break compilations from YouTube (fan uploads: Nickelodeon, Cartoon Network, YTV, Teletoon, VRAK) in YouTube's own player, matched to season, time of day (late-night compilations after 9 PM) and region; Canada + French mixes in French-Canadian clips. Missing or blocked clips fall back to **NyxVision Originals**: made-up products (Frosted Nyx-O's, Zoomie Racers, the Purr-Wow Super Towel…) with their own jingles, also by season, time of day and region.

## Channel 3: Nyx Twilight
A relaxing overnight-style weather channel: slow, tilted aerial flights over NASA's Black Marble night-lights imagery (public domain, via NASA GIBS), region by region across the US and Canada (Canada first in Canada mode). Each region gets 45 seconds: city temperatures and conditions fade in over the map (Open-Meteo), then live radar (Iowa Environmental Mesonet NEXRAD composite, US). A bottom bar cycles Tonight / Hourly / This Week for each city, and a neon "Twilight" card airs at the top of the hour and every 12 minutes. Music: an original ambient "Twilight" loop (deep sub-bass, warm pads in B minor, soft bells), composed on the device. OK turns the music on or off.
Twilight schedule: a neon station ID at :00 and :30 (letters flicker on, then NYX, then the logo), and a 2-minute commercial break from :28–:30 and :58–:00 (adult/overnight era clips, or NyxVision's own infomercials as fallback; the music pauses during breaks). Segments rotate: tilted Tonight's Forecast map, a flat "Sat / Rad: Past Hour" map (US), and every fourth slot a curved-Earth "Satellite: Clouds" globe. The bar pages are Now (feels-like, humidity), Tonight/Today, Hourly, Tomorrow and This Week. Music retuned to the user's reference: Bm → A → F#m → A, a chord every ~2 seconds, sub-bass, pads and sparse high notes.

### Your own Twilight music
Settings → TV & Guide → **Send Twilight Music**: the TV shows a QR code and an address (e.g. http://192.168.1.23:8088/). Open it on a phone on the same Wi-Fi, pick one or more music files and tap Send to TV. Files are stored only in NyxVision's private storage on that device (never in this repo), play in filename order and loop. **Twilight Music: Built-in / My Music** switches between them; OK on the Send screen deletes all files. The upload page only runs while that screen is open.
Twilight feature segments (every third stop): Satellite: Clouds globe, **Water Vapor** (Open-Meteo 500 hPa humidity: orange = dry, white/blue = moist), **Current Winds** (animated 10 m wind streaks), **Current Temperatures** (color map with city temps), **Jet Stream** (animated 250 hPa winds over 50 mph on the globe) and **Earthquakes: Past 24 Hours** (USGS M2.5+ feed, pulsing rings and a list). The weather grid (198 points) refreshes hourly.
**3D city close-ups** (Twilight feature segment): real downtown building footprints and heights plus streets from OpenStreetMap (© OpenStreetMap contributors, ODbL) fetched through the Overpass API, drawn at night with three.js r128 (MIT, `assets/www/three.min.js`, license in `THREE-LICENSE.txt`): lit windows, glowing streets, city lights to the horizon, a slow orbiting camera, and live temperatures for the city and nearby neighborhoods (Open-Meteo). Cities: LA, New York, Chicago, Atlanta, Seattle, San Francisco, Houston, Dallas, Miami, Denver, Boston, Philadelphia, Las Vegas, Washington (plus Toronto, Montréal, Vancouver in Canada mode). One city downloads every few minutes while Twilight is on and is cached on the device.

## The Needle (NNN, ch 5)

On Nyx News Network press OK → News Desk → **Midterms 2026: The Needle** (or ◀ ▶ from the results maps).
Three dials (Senate, House, Governors) point by the races already called plus the races where someone is
currently ahead, among the seats on the ballot this year. It is **not a forecast**. The needles wobble while
races are open and settle as they're called. Results: unofficial, from civicAPI.org (free, no key), refreshed
about once a minute while the screen is open.

## Georgia Legislature (NNN, ch 5)

News Desk → **Georgia Legislature** (or ◀ ▶ in Midterms 2026). Needles for all 180 State House and 56 State Senate
seats, seats won vs. the pre-election split (House R 99 · D 81, Senate R 33 · D 23), flips (incumbents beaten by the
other party; open seats can't be checked) and the closest uncalled races. Results: civicAPI.org race search for
Georgia, each district fetched a few at a time, refreshed every ~2.5 minutes while the page is open.

**GA House / GA Senate results boards:** big seat counts racing to the majority (91 / 29), a won + leading bar,
seats flipped each way, and a seat chart with one box per district (solid = won, dark = leading, grey = no results,
gold outline = flipped). ▲▼ picks a district, CH ▲▼ jumps 10, or tap a box.
