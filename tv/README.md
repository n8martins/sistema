# Sistema Home

Your Google TV, stripped down to what matters.

Hello Nicolas. The time. Today's Miami weather. A quote dealt fresh every time the TV wakes up. Your six apps and your HDMI ports. Nothing else. No sponsored rows, no "top picks for you", no ads.

![Sistema Home](preview/home.jpg)

The design is SISTEMA's own system, flipped dark for a room at night: Fraunces for the greeting and quotes, a clean sans for the clock, mono for the small labels, paper on ink, terracotta accents. The background follows the real sun. It glows warm at sunrise, stays soft through the day, turns terracotta at golden hour and goes deep after dark.

## Setup (once, about 10 minutes)

**On the TV**

1. Settings > System > About. Scroll to **Android TV OS build** and press OK 7 times until it says you're a developer.
2. Settings > System > Developer options. Turn on **USB debugging** (and **Network debugging** if your TV shows it).

**On your Mac** (same Wi-Fi as the TV)

```bash
brew install android-platform-tools      # gives you adb
git clone https://github.com/n8martins/sistema.git
cd sistema/tv
./setup.sh                               # uses 192.168.4.40 by default
```

When the TV asks **Allow USB debugging?**, tick **Always allow from this computer** and press **Allow**.

That's it. Press Home on the remote.

## What setup.sh does

1. Installs Sistema Home and opens it.
2. **Checks it's actually running.** If it isn't, it stops right there and changes nothing else.
3. Makes it your home screen and turns off the stock Google TV home.
4. Shows you the bloat it found and asks before turning it off:

| Package | What it is |
| --- | --- |
| `com.google.android.tvrecommendations` | Sponsored rows and the recommendation feed |
| `com.google.android.videos` | Google TV movie store |
| `com.google.android.youtube.tvmusic` | YouTube Music (you're on Spotify) |
| `com.google.android.youtube.tvkids` | YouTube Kids |
| `com.google.android.play.games` | Play Games |
| `com.google.android.feedback` | Feedback reporter |
| `com.android.printspooler` | Printing |
| `tv.pluto.android`, `com.tubitv` | Preloaded free TV apps |

Add `--ambient` to also kill Google's Ambient Mode screensaver.

**Never touched:** Play Store, Play Services, Chromecast built-in, Google Assistant and voice search, the remote service, TV settings, and the TV's own input and tuner apps. Casting from your phone and the mic button keep working.

Nothing is deleted. Everything is *disabled*, and a snapshot of every package on the TV is saved in `tv/backups/` first.

## Using it

* **OK** opens an app.
* **Hold OK** (or press **Menu**) on a favorite to move it left or right, remove it, or open its app info.
* **All apps** shows everything installed. Hold OK on any app to pin it to Favorites.
* **Inputs** switches HDMI ports directly. If a console or box is connected over HDMI-CEC, its name shows under the port.
* Go **up** to the quote and press OK for another one. A new quote is dealt every time the TV wakes, and nothing repeats until you've seen all 45.
* **Back** jumps to your first app.

If one of your six apps isn't installed, its tile says so and opens Google Play.

## Make it yours

Change the name, city, weather location, units or favorites without rebuilding:

```bash
adb -s 192.168.4.40:5555 shell am start -n com.sistema.home/.HomeActivity \
  --es city "Brickell" --es lat 25.7617 --es lon -80.1918 --es units f
```

`--es favorites "com.netflix.ninja,com.google.android.youtube.tv"` sets the favorites row in one shot. Package names are in `setup.sh`.

## Undo

```bash
./restore.sh                 # everything back on, Sistema removed
./restore.sh --keep-app      # Google TV home back, Sistema stays installed
```

## Good to know

* A big TV software update can switch the stock launcher back on. Run `./setup.sh` again. It's safe to run as many times as you want.
* Holding Home for the Google TV dashboard is part of the stock launcher, so that shortcut goes away. Settings is one press away in the bottom row.
* Weather comes from Open-Meteo. Free, no account, no API key.
* If the TV only offers **Wireless debugging** with a pairing code, run `adb pair 192.168.4.40:<pairing port>`, type the code, then `./setup.sh 192.168.4.40:<port>` with the port shown on that screen.

## Under the hood

* `launcher/` is a standard Android project (Java, no dependencies). Open it in Android Studio or run `./gradlew assembleDebug`.
* `launcher/app/src/test/` boots the real launcher on Robolectric at 1920x1080, drives it like a remote, checks focus, the options sheet, HDMI switching and the weather parser, and renders the screenshots in `preview/`.
* `dist/sistema-home.apk` is the prebuilt APK that `setup.sh` installs. `tools/build-apk.sh` rebuilds it without Android Studio.
* GitHub Actions (`.github/workflows/tv-launcher.yml`) runs the tests and builds the APK on every change to `tv/`.
