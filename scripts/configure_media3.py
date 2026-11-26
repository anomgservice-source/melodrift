#!/usr/bin/env python3
from pathlib import Path

gradle = Path("android/app/build.gradle")
manifest = Path("android/app/src/main/AndroidManifest.xml")
if not gradle.exists() or not manifest.exists():
    raise SystemExit("Android project not found. Run npx cap add android first.")

g = gradle.read_text()

# Media3 1.11.x requires Android API 23 or newer. Capacitor 6 projects may
# generate minSdkVersion 22, so raise it without changing targetSdkVersion.
variables = Path("android/variables.gradle")
if variables.exists():
    v = variables.read_text()
    import re
    match = re.search(r"(minSdkVersion\s*=\s*)(\d+)", v)
    if match and int(match.group(2)) < 23:
        v = v[:match.start(2)] + "23" + v[match.end(2):]
        variables.write_text(v)
else:
    import re
    pattern = r"(minSdkVersion\s+)(\d+)"
    match = re.search(pattern, g)
    if match and int(match.group(2)) < 23:
        g = g[:match.start(2)] + "23" + g[match.end(2):]
        gradle.write_text(g)
deps = [
    "    implementation 'androidx.media3:media3-exoplayer:1.11.1'",
    "    implementation 'androidx.media3:media3-session:1.11.1'",
]
for dep in deps:
    coordinate = dep.split("'")[1].rsplit(":", 1)[0]
    if coordinate not in g:
        marker = "dependencies {"
        if marker not in g:
            raise SystemExit("Could not find dependencies block in android/app/build.gradle")
        g = g.replace(marker, marker + "\n" + dep, 1)

gradle.write_text(g)

m = manifest.read_text()
permissions = [
    '<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />',
    '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />',
    '<uses-permission android:name="android.permission.WAKE_LOCK" />',
]
for item in permissions:
    name = item.split('"')[1]
    if name not in m:
        m = m.replace("<application", item + "\n    <application", 1)

service = (
    '<service android:name=".Media3PlaybackService" android:exported="true" '
    'android:foregroundServiceType="mediaPlayback">\n'
    '  <intent-filter>\n'
    '    <action android:name="androidx.media3.session.MediaSessionService" />\n'
    '  </intent-filter>\n'
    '</service>'
)
if ".Media3PlaybackService" not in m:
    m = m.replace("</application>", service + "\n    </application>", 1)
manifest.write_text(m)
print("Media3 dependencies, minSdk 23, and MediaSessionService manifest configured.")
