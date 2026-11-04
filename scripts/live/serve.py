"""A tiny web remote for the emulator: shows its screen and sends taps, swipes, keys, text and GPS moves back.

Everything lives under /<token>/ (a long random secret); any other path answers 404. It stops by itself after the
time limit. Usage: serve.py PORT TOKEN MINUTES
"""
import io, os, re, subprocess, sys, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from PIL import Image

PORT, TOKEN, MINUTES = int(sys.argv[1]), sys.argv[2], float(sys.argv[3])
END = time.time() + MINUTES * 60
W, H = [int(v) for v in re.findall(r"\d+", subprocess.check_output(["adb", "shell", "wm", "size"]).decode().splitlines()[-1])[-2:]]

frame = b""
frame_lock = threading.Lock()
geo = {"lat": 12.9716, "lon": 77.5946}
recent = []  # timestamps of recent inputs, for a simple rate limit


def grab():
    global frame
    while time.time() < END:
        t = time.time()
        try:
            png = subprocess.check_output(["adb", "exec-out", "screencap", "-p"], timeout=10)
            im = Image.open(io.BytesIO(png)).convert("RGB")
            im.thumbnail((540, 1200))
            buf = io.BytesIO()
            im.save(buf, "JPEG", quality=72)
            with frame_lock:
                frame = buf.getvalue()
        except Exception:
            time.sleep(0.5)
        time.sleep(max(0.0, 0.18 - (time.time() - t)))


def adb(*args):
    subprocess.Popen(["adb", "shell", *args], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def geo_fix():
    subprocess.Popen(["adb", "emu", "geo", "fix", f"{geo['lon']:.6f}", f"{geo['lat']:.6f}"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


PAGE = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "page.html"), "rb").read()
KEYS = {"back": "4", "home": "3", "recents": "187", "enter": "66", "backspace": "67"}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def reply(self, code, body=b"", ctype="text/plain"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        self.end_headers()
        self.wfile.write(body)

    def handle_any(self):
        u = urlparse(self.path)
        prefix = f"/{TOKEN}/"
        if not u.path.startswith(prefix) or time.time() > END:
            return self.reply(404, b"not found")
        route = u.path[len(prefix):]
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        if route == "":
            return self.reply(200, PAGE, "text/html; charset=utf-8")
        if route == "frame.jpg":
            with frame_lock:
                return self.reply(200, frame, "image/jpeg")
        if route == "status":
            return self.reply(200, f'{{"left":{int(END - time.time())},"w":{W},"h":{H}}}'.encode(), "application/json")
        # Inputs: at most 25 a second.
        now = time.time()
        recent[:] = [t for t in recent if now - t < 1]
        if len(recent) >= 25:
            return self.reply(429, b"slow down")
        recent.append(now)
        try:
            if route == "tap":
                adb("input", "tap", str(int(float(q["x"]) * W)), str(int(float(q["y"]) * H)))
            elif route == "swipe":
                ms = max(50, min(3000, int(float(q.get("ms", 300)))))
                adb("input", "swipe", *[str(int(float(q[k]) * (W if k[0] == "x" else H))) for k in ("x1", "y1", "x2", "y2")], str(ms))
            elif route == "key" and q.get("name") in KEYS:
                adb("input", "keyevent", KEYS[q["name"]])
            elif route == "text":
                text = re.sub(r"[^A-Za-z0-9 .,@+\-]", "", q.get("t", ""))[:80]
                if text:
                    adb("input", "text", text.replace(" ", "%s"))
            elif route == "move":
                # About 15 m per press (0.00014 degrees); "dir" is n, s, e or w.
                step = 0.00014
                d = q.get("dir", "")
                geo["lat"] += step if d == "n" else -step if d == "s" else 0
                geo["lon"] += step if d == "e" else -step if d == "w" else 0
                geo_fix()
            else:
                return self.reply(404, b"not found")
        except (KeyError, ValueError):
            return self.reply(400, b"bad request")
        self.reply(200, b"ok")

    do_GET = handle_any
    do_POST = handle_any


threading.Thread(target=grab, daemon=True).start()
geo_fix()
srv = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
threading.Thread(target=srv.serve_forever, daemon=True).start()
print(f"serving on 127.0.0.1:{PORT} for {MINUTES:.0f} min", flush=True)
while time.time() < END:
    time.sleep(2)
srv.shutdown()
