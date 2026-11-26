#!/usr/bin/env python3
from pathlib import Path
import re

gradle = Path("android/app/build.gradle")
manifest = Path("android/app/src/main/AndroidManifest.xml")
variables = Path("android/variables.gradle")
root_gradle = Path("android/build.gradle")
wrapper = Path("android/gradle/wrapper/gradle-wrapper.properties")

if not gradle.exists() or not manifest.exists():
    raise SystemExit("Android project not found. Run npx cap add android first.")

# Android SDK versions
if variables.exists():
    v = variables.read_text()

    for key, version in (
        ("compileSdkVersion", 36),
        ("minSdkVersion", 23),
    ):
        pattern = rf"({key}\s*=\s*)\d+"
        v, count = re.subn(
            pattern,
            lambda m: m.group(1) + str(version),
            v,
            count=1,
        )
        if count != 1:
            raise SystemExit(f"{key} introuvable.")

    variables.write_text(v)

# Android Gradle Plugin
if root_gradle.exists():
    root = root_gradle.read_text()
    pattern = r"(com\.android\.tools\.build:gradle:)[^'\"]+"
    root, count = re.subn(
        pattern,
        lambda m: m.group(1) + "8.10.1",
        root,
        count=1,
    )
    if count != 1:
        raise SystemExit("Android Gradle Plugin introuvable.")
    root_gradle.write_text(root)

# Gradle Wrapper
if wrapper.exists():
    w = wrapper.read_text()
    w, count = re.subn(
        r"(?m)^distributionUrl=.*$",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.11.1-all.zip",
        w,
        count=1,
    )
    if count != 1:
        raise SystemExit("Gradle distributionUrl introuvable.")
    wrapper.write_text(w)

# Media3 dependencies
g = gradle.read_text()

deps = [
    "    implementation 'androidx.media3:media3-exoplayer:1.11.1'",
    "    implementation 'androidx.media3:media3-session:1.11.1'",
]

for dep in deps:
    coordinate = dep.split("'")[1].rsplit(":", 1)[0]
    if coordinate not in g:
        marker = "dependencies {"
        if marker not in g:
            raise SystemExit("Bloc dependencies introuvable.")
        g = g.replace(marker, marker + "\n" + dep, 1)

gradle.write_text(g)

# Media3 permissions
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

# MediaSessionService
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

print("Media3, Android API 36, AGP 8.10.1 et Gradle 8.11.1 configurés.")
