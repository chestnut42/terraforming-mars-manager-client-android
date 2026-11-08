# Mars Manager for Android

Android client for [terraforming-mars-manager](../terraforming-mars-manager), the
Android counterpart of the iOS app in `../terraforming-mars-manager-client`.

Kotlin, Jetpack Compose (Material 3), OkHttp and kotlinx.serialization. Sign in
uses Credential Manager with "Sign in with Google".

## Features

Same screens as the iOS app:

| Screen      | API                                                    |
|-------------|--------------------------------------------------------|
| Sign in     | Google ID token → `POST /manager/api/v1/login`         |
| Games       | `GET /manager/api/v1/me/games`, opens a game in a Custom Tab |
| New game    | `POST /manager/api/v1/search` (suggestions), `POST /manager/api/v2/game` |
| Leaderboard | `GET /manager/api/v1/leaderboard`                      |
| Profile     | `GET` / `POST /manager/api/v1/me`                      |

Differences from iOS:

- Games are grouped into *Your turn* / *In progress* / *Finished*. Pull to refresh works on every list.
- New accounts get a hint to change the default `Player XXXX` nickname. The server only matches
  default-nickname accounts with each other in search, so a new account can't be found until it
  picks a nickname.
- There is a Sign out button.
- Solar Phase has its own switch, sent as `solarPhase`. Until the iOS fix lands, the iOS app sends
  the Venus Next value for it and ignores its Solar Phase switch.
- Push notifications aren't included yet.

## Setup

### 1. Google Cloud

In one Google Cloud project, create two OAuth 2.0 client IDs:

1. **Web application**: its client ID is the *server client ID*. Google ID tokens are issued with
   this value as `aud`.
2. **Android**: package `xyz.blockthem.mars.manager` and the SHA-1 of your signing key
   (`./gradlew signingReport`). Add the debug key and the release key. Credential Manager won't
   return a token to an app that doesn't match an Android client.

### 2. Server

Google sign-in is off until the server has client IDs configured. Set:

```
MARS_GOOGLE_CLIENT_IDS=<web-client-id>.apps.googleusercontent.com
```

### 3. App

The production values are built in, so you don't need to configure anything:

- API: `https://mars.blockthem.xyz`
- Web client ID: `96331823261-k9udqfveec6v2qiqca48s64qahgh73t1.apps.googleusercontent.com`

Client IDs aren't secrets; they ship inside the APK anyway. Each developer still has to add the
SHA-1 of their own debug key to the Android OAuth client (step 1), or sign-in fails on their builds.

To point the app at another server or client, override either value in `local.properties`
(not committed):

```properties
sdk.dir=/path/to/Android/sdk
# mars.apiBaseUrl=http://10.0.2.2:8080
# mars.googleServerClientId=<web-client-id>.apps.googleusercontent.com
```

The same keys also work as Gradle properties (`-Pmars.googleServerClientId=...`) or as
environment variables (`MARS_GOOGLE_SERVER_CLIENT_ID`, `MARS_API_BASE_URL`).

Note: a plain `http://` base URL (local server) also needs `android:usesCleartextTraffic="true"`.

### 4. Build

Open the project in Android Studio, or use the Gradle wrapper. Gradle 8.11 needs Java 17–21 to
run; Java 25, which ships inside Android Studio, is too new. Point `JAVA_HOME` at a Java 21, for
example the one Android Studio downloads:

```
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home
```

```
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
```

## How auth works

- Google ID tokens expire after about 1 hour. The token is stored in `SharedPreferences`. When it's
  within 2 minutes of expiry, or the server returns 401, the app gets a new one through Credential
  Manager auto sign-in (`filterByAuthorizedAccounts = true`, `autoSelectEnabled = true`) and retries
  the request once. If that fails, the app goes back to the sign-in screen.
- The server prefixes Google users with `google:`, so a Google account and an Apple account are
  **different users** with separate games and Elo.

## Server-side follow-ups

Needed:

1. **Configure `MARS_GOOGLE_CLIENT_IDS`** in production (see above).

Worth considering:

2. **Linking Apple and Google accounts.** Today one person who uses both iOS (Apple) and Android
   (Google) has two accounts. Two ways to fix it: let the iOS app sign in with Google as well, or add
   a link endpoint (for example `POST /manager/api/v1/me/link` with a second provider's ID token).
3. **Richer `Game` message.** `GetGames` only returns `playersCount`. Opponent nicknames and colors,
   board and expansions would make the list readable on both platforms.
4. **Push notifications (later).** FCM support needs a device-token type on
   `UpdateDeviceToken` (today it always stores an APNs *production* token) and an FCM sender
   next to the APN notifiers.

## Layout

```
app/src/main/java/xyz/blockthem/mars/manager/
  api/        MarsApi (OkHttp client), wire models, errors
  auth/       AuthRepository (Credential Manager, token refresh), Jwt
  ui/         MarsNavHost, theme, common helpers
  ui/{signin,games,creategame,leaderboard,profile}/   screens + ViewModels
```
