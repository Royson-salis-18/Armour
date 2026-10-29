"""Joins the scene recordings into one video with the voiceover and subtitles: ARMOR-demo.mp4.

Usage: python3 make_video.py <video dir> <narration dir> <output.mp4>
"""
import json, os, subprocess, sys

VID, NAR, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
scenes = json.load(open(os.path.join(os.path.dirname(__file__), "scenes.json")))
TMP = os.path.join(VID, "_clips")
os.makedirs(TMP, exist_ok=True)
W, H = 720, 1600


def run(*args):
    subprocess.check_call(["ffmpeg", "-y", "-loglevel", "error", *args])


def duration(path):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path]).strip())


def frames_to_mp4(folder, out):
    """Fallback capture: screenshots with their timestamps become a variable-rate clip."""
    times = [float(t) for t in open(os.path.join(folder, "times")).read().split()]
    pngs = sorted(f for f in os.listdir(folder) if f.endswith(".png"))
    n = min(len(times), len(pngs))
    with open(os.path.join(folder, "list.txt"), "w") as f:
        for i in range(n):
            d = (times[i + 1] - times[i]) if i + 1 < n else 0.5
            f.write(f"file '{os.path.abspath(os.path.join(folder, pngs[i]))}'\nduration {max(d, 0.05):.3f}\n")
    run("-f", "concat", "-safe", "0", "-i", os.path.join(folder, "list.txt"), "-vsync", "vfr", "-pix_fmt", "yuv420p", out)


clips, srt, t = [], [], 0.0
for i, s in enumerate(scenes):
    sid = s["id"]
    src = os.path.join(VID, sid + ".mp4")
    if not os.path.exists(src) and os.path.isdir(os.path.join(VID, sid)):
        src = os.path.join(TMP, sid + "-frames.mp4")
        frames_to_mp4(os.path.join(VID, sid), src)
    if not os.path.exists(src):
        print("missing scene", sid)
        continue
    audio = os.path.join(NAR, sid + ".mp3")
    try:
        length = max(duration(src), duration(audio) + 0.6)
    except subprocess.CalledProcessError:
        print("skipping broken scene", sid)
        continue
    clip = os.path.join(TMP, sid + ".mp4")
    # Same size and frame rate for every clip; the narration starts 0.3 s in and is padded with silence to the clip's end.
    run(
        "-i", src, "-i", audio,
        "-filter_complex",
        f"[0:v]scale={W}:{H}:force_original_aspect_ratio=decrease,pad={W}:{H}:(ow-iw)/2:(oh-ih)/2:color=0x0A0A0B,fps=30,"
        f"tpad=stop_mode=clone:stop_duration=30,trim=duration={length:.2f},setpts=PTS-STARTPTS,"
        f"fade=t=in:st=0:d=0.25,fade=t=out:st={length - 0.25:.2f}:d=0.25[v];"
        f"[1:a]adelay=300|300,apad,atrim=duration={length:.2f},aresample=48000[a]",
        "-map", "[v]", "-map", "[a]", "-c:v", "libx264", "-preset", "medium", "-crf", "24", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "128k", "-ac", "2", clip,
    )
    clips.append(clip)

    def ts(x):
        ms = int(round(x * 1000)); return f"{ms // 3600000:02d}:{ms // 60000 % 60:02d}:{ms // 1000 % 60:02d},{ms % 1000:03d}"
    srt.append(f"{i + 1}\n{ts(t + 0.3)} --> {ts(t + length - 0.2)}\n{s['text']}\n")
    t += length

with open(os.path.join(TMP, "list.txt"), "w") as f:
    f.writelines(f"file '{os.path.abspath(c)}'\n" for c in clips)
with open(os.path.join(TMP, "subs.srt"), "w") as f:
    f.write("\n".join(srt))
run("-f", "concat", "-safe", "0", "-i", os.path.join(TMP, "list.txt"), "-i", os.path.join(TMP, "subs.srt"),
    "-map", "0", "-map", "1", "-c", "copy", "-c:s", "mov_text", "-metadata:s:s:0", "language=eng", "-movflags", "+faststart", OUT)
print(f"{OUT}: {duration(OUT):.1f} s, {os.path.getsize(OUT) / 1e6:.1f} MB, {len(clips)} scenes")
