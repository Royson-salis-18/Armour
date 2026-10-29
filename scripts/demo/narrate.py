"""Generates the voiceover: one MP3 per scene from scenes.json, plus durations.txt ("id seconds") for the recorder."""
import asyncio, json, os, subprocess, sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "narration"
VOICE = os.environ.get("VOICE", "en-US-GuyNeural")
scenes = json.load(open(os.path.join(os.path.dirname(__file__), "scenes.json")))
os.makedirs(OUT, exist_ok=True)

async def neural(text, path):
    import edge_tts
    await edge_tts.Communicate(text, VOICE, rate="+4%").save(path)

def duration(path):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path]).strip())

with open(os.path.join(OUT, "durations.txt"), "w") as d:
    for s in scenes:
        mp3 = os.path.join(OUT, s["id"] + ".mp3")
        try:
            asyncio.run(neural(s["text"], mp3))
        except Exception as e:  # no neural voice available: fall back to espeak so the video still has narration
            print("neural voice failed:", e, file=sys.stderr)
            wav = mp3[:-4] + ".wav"
            subprocess.check_call(["espeak-ng", "-v", "en-us", "-s", "160", "-w", wav, s["text"]])
            subprocess.check_call(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, mp3])
        d.write(f'{s["id"]} {duration(mp3):.2f}\n')
        print(s["id"], "ok")
