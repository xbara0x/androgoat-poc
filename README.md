<h1 align="center">A Rogue App that explores some AndroGoat project vulnerabilities</h1>

<p align="center">
  <img src="https://github.com/xbara0x/androgoat-poc/blob/master/androgoat-poc.gif" />
</p>

**AndroGoat PoC** is a small Android app that plays the *attacker* against
[AndroGoat](https://github.com/satishpatnayak/AndroGoat), an intentionally vulnerable
Android app by [@satishpatnayak](https://github.com/satishpatnayak). Instead of
exploiting vulnerabilities *inside* one app, it demonstrates **app-vs-app** attacks:
each screen invokes an exported component of AndroGoat **from another process** and
shows exactly what does (and does not) leak.

It is a learning / portfolio piece about the Android IPC attack surface — exported
activities, services, broadcast receivers, content providers, package visibility and
scoped storage — on modern Android.

## Target

| | |
|---|---|
| App | AndroGoat `owasp.sat.agoat` |
| Version | **v2.0.1** (`targetSdk 33`) |
| Release | https://github.com/satishpatnayak/AndroGoat/releases/tag/v2.0.1 |
| APK sha256 | `3e6f6b538b82874dd94c0d8cdadd69ba54d49b59d2c7eb055073892e8a4fc0e0` |

```bash
adb install AndroGoat.apk        # the victim app
adb install app-debug.apk        # this PoC (dev.xbara0x.androgoatpoc)
```

## Exploits

Everything below targets components that AndroGoat declares `android:exported="true"`.
Exploits **1–5 were tapped in this app's real UI** on an Android 13 (API 33) emulator
against AndroGoat v2.0.1; exploit 6 is the one that modern Android deliberately breaks.

| # | Drawer screen | Target component (AndroGoat) | Technique | What it actually does |
|---|---|---|---|---|
| 1 | Unprotected Components | `AccessControl1ViewActivity` (activity) | Explicit `Intent` + `setClassName` | Opens a screen that is supposed to sit behind the app's PIN gate. It does **not** log in or hand over credentials — it reaches a gated *screen*. |
| 2 | Unprotected Components | same activity | Deep link `androgoat://vulnapp` | Same destination reached through the registered URL scheme. |
| 3 | Unprotected Components | `DownloadInvoiceService` (service) | `startService`, falling back to `ContextCompat.startForegroundService` | On O+ the background-start limit refuses the plain `startService`, so the fallback starts it as a *foreground* service the victim never opted into. The victim enqueues the download (which succeeds) and is then killed with `ForegroundServiceDidNotStartInTimeException` because it never calls `startForeground()`. |
| 4 | Unprotected Components | `ShowDataReceiver` (receiver) | `sendBroadcast` (explicit) | The receiver runs, but it only shows credentials in a **Toast on AndroGoat's own UI** — nothing is returned to this app. Demonstrates callability, not exfiltration. |
| 5 | Exported ContentProvider | `ContentProviderActivity` provider, authority `owasp.sat.agoat.provider.userpinsprovider` | `ContentResolver.query` | Queries the world-readable `user_pins` table and dumps **usernames + PINs into this app** (observed: `Admin/Admin`, `AndroGoat/AndroGoat`, `root/toor`). Real cross-app credential exfiltration. |
| 6 | Insecure Data Storage | `InsecureStorageSDCardActivity` temp file | Directory scan + read | Tries to read the `users*_tmp` file AndroGoat writes to its *app-scoped* external dir. Blocked by scoped storage on Android 11+; the screen explains why instead of failing silently. |

### Android version notes

- Exploits **1, 2, 3, 4 and 5** work on modern Android (validated on API 33).
- Exploit **3** achieves its effect *and* crashes the victim: the only way to start the
  service cross-app on O+ is `startForegroundService`, and a service that never calls
  `startForeground()` is killed ~5s later. The download survives because it is enqueued
  in `onStartCommand` first.
- Exploit **5** (ContentProvider) is the most impactful: it requires no user interaction
  on the victim and returns data. It works because the provider is exported with no
  `readPermission`.
- Exploit **6** can only succeed on **Android 10 or older** (scoped storage forbids
  cross-app reads of `Android/data/<pkg>/` from Android 11 on, even with
  `MANAGE_EXTERNAL_STORAGE`). On Android 11+ the screen reports the reason.
- Exploits **3 and 4** need the `<queries><package android:name="owasp.sat.agoat"/>
  </queries>` declaration in this app's manifest: on Android 11+ (targetSdk 30+)
  package visibility filtering affects explicit interactions such as starting another
  app's service.

## Build

Built with **AGP 9.4.1 / Gradle 9.6.0** (Kotlin comes from AGP's built-in support, so no
separate `kotlin-android` plugin). It runs on a modern JDK — tested on JDK 25 and 27.

```bash
./gradlew :app:assembleDebug
```

The Android SDK location is read from `local.properties` (`sdk.dir=...`).

## Scope and ethics

This app only attacks **AndroGoat**, a deliberately vulnerable target. It is not a
general-purpose exploitation framework, and it must not be pointed at apps you do not
own or have explicit authorization to test.

## Repository layout

```
app/src/main/java/dev/xbara0x/androgoatpoc/
  MainActivity.kt
  ui/uac/       Unprotected Components  (exploits 1-4)
  ui/ids/       Insecure Data Storage   (exploit 6)
  ui/provider/  Exported ContentProvider (exploit 5)
```