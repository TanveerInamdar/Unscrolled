"""Simulate the post-block Gemini -> ElevenLabs roast path using local.properties keys."""

from __future__ import annotations

import json
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "tools" / "demo_output"
OUTPUT_MP3 = OUTPUT_DIR / "demo_roast.mp3"

SYSTEM_PROMPT = (
    "You write one spoken line for a phone that just blocked a short-form video. "
    "Slightly rude, curt, and quirky. Maximum twelve words. No quotes, no emoji, "
    "no hashtags. Write numbers as words. Output only the line."
    "Make it slightly hurtful and funny while also being a bit mean."
)

USER_PROMPT = (
    "Blocked Instagram short-form video. Reason: PLAYER_VISIBLE. "
    "Blocks today: 7. "
    "Unproductive screen time today: 1 hours 12 minutes. "
    "Steps today: 3200. "
    "Sleep last night: 5 hours 10 minutes. "
    "Calendar events still remaining today: 2. "
    "Minutes until next event: 40. "
    "Do not repeat these recent lines: Too much scrolling. | Put the phone down."
)

GEMINI_URL = (
    "https://generativelanguage.googleapis.com/v1beta/models/"
    "gemini-flash-lite-latest:generateContent"
)


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


def sanitize_line(raw: str) -> str:
    first = next((part for part in raw.splitlines() if part.strip()), "")
    cleaned = first.strip().strip("\"'`“”")
    cleaned = re.sub(r"\s+", " ", cleaned)
    cleaned = cleaned[:120]
    if not cleaned:
        raise RuntimeError("Gemini returned an empty line")
    return cleaned


def gemini_line(api_key: str) -> str:
    payload = {
        "system_instruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": [{"parts": [{"text": USER_PROMPT}]}],
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

    print("Simulated block: Instagram Reels player visible")
    print("BACK + toast would already have fired on device")
    print("Gemini context:")
    print(f"  {USER_PROMPT}")
    print("Calling Gemini Flash Lite...")
    line = gemini_line(gemini_key)
    print(f"One-liner: {line}")
    print("Calling ElevenLabs Flash v2.5...")
    elevenlabs_speak(eleven_key, voice_id, line)
    print(f"Saved audio: {OUTPUT_MP3}")
    play_mp3()
    print("Opened the MP3 with the default player")
    return 0


if __name__ == "__main__":
    sys.exit(main())
