import sounddevice as sd
import numpy as np
from faster_whisper import WhisperModel

SAMPLE_RATE = 6000
DURATION = 1.5  # seconds

print("🎧 Listening for wake phrase: 'hey rider'")

model = WhisperModel("small.en", device="cpu")

def record_audio():
    audio = sd.rec(
        int(DURATION * SAMPLE_RATE),
        samplerate=SAMPLE_RATE,
        channels=1,
        dtype=np.float32,
    )
    sd.wait()
    return audio.flatten()

while True:
    audio = record_audio()

    segments, _ = model.transcribe(audio, language="en")

    for segment in segments:
        text = segment.text.lower()
        print("Heard:", text)

        if "hey rider" in text:
            print("🔥 WAKE PHRASE DETECTED!")
            # TODO: trigger Android app here
