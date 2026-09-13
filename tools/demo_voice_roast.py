"""Simulate the post-block Gemini -> ElevenLabs roast path using local.properties keys.

Pulls today's blocks, steps, sleep, screen time, and calendar from the debug
app database on a connected phone (same Room DB the blocker uses).
"""

from __future__ import annotations

import json
import os
import random
import re
import sqlite3
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "tools" / "demo_output"
OUTPUT_MP3 = OUTPUT_DIR / "demo_roast.mp3"
PACKAGE = "com.foxtrotalpha.reelsblocker"

SYSTEM_PROMPT = (
    "You write one spoken line for a phone that just blocked a short-form video. "
    "Slightly rude, curt, quirky, a bit mean, and funny. Maximum twelve words. "
    "No quotes, no emoji, no hashtags. Write numbers as words. Output only the line. "
    "Use only the assigned roast angle. Unless the angle is step count, "
    "do not mention steps, walking, or grass."
)

GEMINI_URL = (
    "https://generativelanguage.googleapis.com/v1beta/models/"
    "gemini-flash-lite-latest:generateContent"
)

APP_LABELS = {
    "com.instagram.android": "Instagram",
    "com.google.android.youtube": "YouTube",
    "com.google.android.apps.youtube.kids": "YouTube",
    "app.revanced.android.youtube": "YouTube",
}


def load_local_properties() -> dict[str, str]:
    path = ROOT / "local.properties"
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def sdk_dir() -> Path | None:
    sdk = load_local_properties().get("sdk.dir", "")
    if sdk:
        sdk = sdk.replace("\\:", ":").replace("\\\\", "\\")
        return Path(sdk)
    env = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    return Path(env) if env else None


def adb_bin() -> str:
    sdk = sdk_dir()
    if sdk is not None:
        candidate = sdk / "platform-tools" / "adb.exe"
        if candidate.exists():
            return str(candidate)
        posix = sdk / "platform-tools" / "adb"
        if posix.exists():
            return str(posix)
    return "adb"


def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [adb_bin(), *args],
        check=check,
        capture_output=True,
        text=True,
    )


def adb_bytes(*args: str) -> bytes:
    result = subprocess.run(
        [adb_bin(), *args],
        check=True,
        capture_output=True,
    )
    return result.stdout


def require_device() -> None:
    listing = adb("devices").stdout.strip().splitlines()
    ready = [line for line in listing[1:] if line.endswith("\tdevice")]
    if not ready:
        raise RuntimeError("No phone in adb device mode. Plug it in and allow USB debugging.")


def pull_app_file(relative_path: str, dest: Path) -> bool:
    try:
        payload = adb_bytes("exec-out", "run-as", PACKAGE, "cat", relative_path)
    except subprocess.CalledProcessError:
        return False
    if not payload or payload.startswith(b"run-as:"):
        return False
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(payload)
    return dest.stat().st_size > 0


def pull_database(work_dir: Path) -> Path:
    db_path = work_dir / "foxtrot_alpha.db"
    if not pull_app_file("databases/foxtrot_alpha.db", db_path):
        raise RuntimeError(
            "Could not read foxtrot_alpha.db from the phone. "
            "Install a debug build and keep USB debugging on.",
        )
    pull_app_file("databases/foxtrot_alpha.db-wal", work_dir / "foxtrot_alpha.db-wal")
    pull_app_file("databases/foxtrot_alpha.db-shm", work_dir / "foxtrot_alpha.db-shm")
    return db_path


def phone_today() -> str:
    result = adb("shell", "date", "+%Y-%m-%d", check=False)
    date = result.stdout.strip()
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", date):
        return date
    return time.strftime("%Y-%m-%d")


def format_duration(ms: int | None) -> str | None:
    if ms is None:
        return None
    total_minutes = max(ms // 60_000, 0)
    hours, minutes = divmod(total_minutes, 60)
    if hours > 0:
        return f"{hours} hours {minutes} minutes"
    return f"{minutes} minutes"


def load_recent_lines(work_dir: Path) -> list[str]:
    prefs = work_dir / "voice_roast_prefs.xml"
    if not pull_app_file("shared_prefs/voice_roast_prefs.xml", prefs):
        return []
    try:
        root = ET.parse(prefs).getroot()
    except ET.ParseError:
        return []
    for node in root.findall("string"):
        if node.get("name") == "recent_lines" and node.text:
            return [part for part in node.text.split("\u001f") if part.strip()][-3:]
    return []


def build_user_prompt(db_path: Path, today: str, recent_lines: list[str]) -> str:
    now_ms = int(time.time() * 1000)
    connection = sqlite3.connect(db_path)
    connection.row_factory = sqlite3.Row
    try:
        latest = connection.execute(
            """
            SELECT package_name, reason
            FROM block_events
            ORDER BY timestamp_ms DESC
            LIMIT 1
            """,
        ).fetchone()
        blocks_today = connection.execute(
            "SELECT COUNT(*) AS n FROM block_events WHERE date = ?",
            (today,),
        ).fetchone()["n"]
        unproductive = connection.execute(
            """
            SELECT COALESCE(SUM(u.foreground_ms), 0) AS total_ms
            FROM daily_app_usage u
            JOIN tracked_apps t ON t.package_name = u.package_name
            WHERE t.is_unproductive = 1 AND u.date = ?
            """,
            (today,),
        ).fetchone()["total_ms"]
        health = connection.execute(
            "SELECT step_count FROM daily_health_metrics WHERE date = ? LIMIT 1",
            (today,),
        ).fetchone()
        sleep = connection.execute(
            """
            SELECT MAX(duration_ms) AS duration_ms
            FROM sleep_sessions
            WHERE date = ?
            """,
            (today,),
        ).fetchone()
        events = connection.execute(
            """
            SELECT start_ms, end_ms, all_day
            FROM calendar_events
            WHERE date = ?
            ORDER BY start_ms ASC
            """,
            (today,),
        ).fetchall()
        usage_rows = connection.execute(
            "SELECT package_name, foreground_ms FROM daily_app_usage WHERE date = ?",
            (today,),
        ).fetchall()
    finally:
        connection.close()

    package_name = latest["package_name"] if latest else "com.instagram.android"
    reason = latest["reason"] if latest else "PLAYER_VISIBLE"
    app_label = APP_LABELS.get(package_name, package_name)
    usage = {row["package_name"]: row["foreground_ms"] for row in usage_rows}
    youtube_packages = (
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "app.revanced.android.youtube",
    )

    upcoming = [
        event
        for event in events
        if not event["all_day"] and event["end_ms"] >= now_ms
    ]
    next_event = next((event for event in upcoming if event["start_ms"] > now_ms), None)
    minutes_until = None
    if next_event is not None:
        minutes_until = max((next_event["start_ms"] - now_ms) // 60_000, 0)

    steps = health["step_count"] if health else None
    sleep_ms = sleep["duration_ms"] if sleep and sleep["duration_ms"] is not None else None

    blocked_ms = usage.get(package_name, 0)
    instagram_ms = usage.get("com.instagram.android", 0)
    youtube_ms = sum(usage.get(pkg, 0) for pkg in youtube_packages)

    foci = [
        "Roast angle for this line: total blocks today. Do not mention steps, walking, or sleep.",
        "Roast angle for this line: total blocks today. Do not mention steps, walking, or sleep.",
        "Roast angle for this line: screen time in the app just blocked, or Instagram vs YouTube. Do not mention steps or walking.",
        "Roast angle for this line: screen time in the app just blocked, or Instagram vs YouTube. Do not mention steps or walking.",
        "Roast angle for this line: total unproductive screen time today. Do not mention steps or walking.",
    ]
    if sleep_ms is not None:
        foci.extend(
            [
                "Roast angle for this line: last night's sleep. Do not mention steps or walking.",
                "Roast angle for this line: last night's sleep. Do not mention steps or walking.",
            ]
        )
    if steps is not None:
        foci.append(
            "Roast angle for this line: step count. You may tell them to walk more. Do not talk about sleep."
        )
    focus = random.choice(foci)

    parts = [
        f"Blocked {app_label} short-form video. Reason: {reason}.",
        f"Blocks today: {blocks_today}.",
        f"Unproductive screen time today: {format_duration(unproductive)}.",
        f"{app_label} screen time today: {format_duration(blocked_ms)}.",
        f"Instagram screen time today: {format_duration(instagram_ms)}.",
        f"YouTube screen time today: {format_duration(youtube_ms)}.",
    ]
    if steps is not None:
        parts.append(f"Steps today: {steps}.")
    sleep_label = format_duration(sleep_ms)
    if sleep_label is not None:
        parts.append(f"Sleep last night: {sleep_label}.")
    parts.append(f"Calendar events still remaining today: {len(upcoming)}.")
    if minutes_until is not None:
        parts.append(f"Minutes until next event: {minutes_until}.")
    parts.append(focus)
    if recent_lines:
        parts.append("Do not repeat these recent lines: " + " | ".join(recent_lines))
    return " ".join(parts)


def sanitize_line(raw: str) -> str:
    first = next((part for part in raw.splitlines() if part.strip()), "")
    cleaned = first.strip().strip("\"'`“”")
    cleaned = re.sub(r"\s+", " ", cleaned)
    cleaned = cleaned[:120]
    if not cleaned:
        raise RuntimeError("Gemini returned an empty line")
    return cleaned


def gemini_line(api_key: str, user_prompt: str) -> str:
    payload = {
        "system_instruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": [{"parts": [{"text": user_prompt}]}],
        "generationConfig": {"temperature": 0.9, "maxOutputTokens": 40},
    }
    request = urllib.request.Request(
        f"{GEMINI_URL}?key={api_key}",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            body = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read()[:300].decode("utf-8", "replace")
        raise RuntimeError(f"Gemini HTTP {error.code}: {detail}") from error

    parts = body["candidates"][0]["content"]["parts"]
    text = "".join(part.get("text", "") for part in parts)
    return sanitize_line(text)


def elevenlabs_speak(api_key: str, voice_id: str, text: str) -> None:
    payload = {
        "text": text,
        "model_id": "eleven_flash_v2_5",
        "voice_settings": {
            "stability": 0.35,
            "similarity_boost": 0.75,
            "style": 0.0,
            "use_speaker_boost": True,
            "speed": 1.05,
        },
    }
    url = (
        f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}"
        "?output_format=mp3_44100_128"
    )
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "xi-api-key": api_key,
            "Accept": "audio/mpeg",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            audio = response.read()
    except urllib.error.HTTPError as error:
        detail = error.read()[:300].decode("utf-8", "replace")
        raise RuntimeError(f"ElevenLabs HTTP {error.code}: {detail}") from error

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    OUTPUT_MP3.write_bytes(audio)


def play_mp3() -> None:
    subprocess.run(
        ["cmd", "/c", "start", "", str(OUTPUT_MP3)],
        check=False,
        cwd=str(OUTPUT_DIR),
    )


def main() -> int:
    props = load_local_properties()
    gemini_key = props.get("GEMINI_API_KEY", "")
    eleven_key = props.get("ELEVENLABS_API_KEY", "")
    voice_id = props.get("ELEVENLABS_VOICE_ID", "") or "pNInz6obpgDQGcFmaJgB"

    if not gemini_key or not eleven_key:
        print("Missing GEMINI_API_KEY or ELEVENLABS_API_KEY in local.properties")
        return 1

    require_device()
    today = phone_today()
    with tempfile.TemporaryDirectory(prefix="foxtrot_roast_") as tmp:
        work_dir = Path(tmp)
        db_path = pull_database(work_dir)
        recent_lines = load_recent_lines(work_dir)
        user_prompt = build_user_prompt(db_path, today, recent_lines)

    print(f"Pulled phone DB for {today}")
    print("Gemini context:")
    print(f"  {user_prompt}")
    print("Calling Gemini Flash Lite...")
    line = gemini_line(gemini_key, user_prompt)
    print(f"One-liner: {line}")
    print("Calling ElevenLabs Flash v2.5...")
    elevenlabs_speak(eleven_key, voice_id, line)
    print(f"Saved audio: {OUTPUT_MP3}")
    play_mp3()
    print("Opened the MP3 with the default player")
    return 0


if __name__ == "__main__":
    sys.exit(main())
