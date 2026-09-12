/*
 * Microphone capture, wrapped so the rest of the application never touches MediaRecorder directly.
 *
 * MediaRecorder is an event-driven API: you call start(), it fires "dataavailable" events carrying
 * chunks of encoded audio, and a "stop" event once it has flushed the last one. Nothing in it
 * returns a promise. app.js is written in async/await, so the bridge between the two paradigms
 * happens here, once, rather than being scattered through the calling code as nested callbacks.
 *
 * The technique of wrapping an event in `new Promise(resolve => target.onevent = resolve)` is the
 * standard way to promisify a one-shot DOM event (MDN, "Using Promises").
 */

/**
 * Thrown when the user denies microphone access, or no microphone exists.
 *
 * A distinct class rather than a flag, so app.js can tell "you said no" apart from "the network
 * failed" with an instanceof check and show the user a message that is actually actionable.
 */
export class MicrophoneAccessError extends Error {
    constructor(cause) {
        super("Microphone access was not granted.");
        this.name = "MicrophoneAccessError";
        this.cause = cause;
    }
}

/**
 * How often MediaRecorder should hand over a chunk of encoded audio, in milliseconds.
 *
 * One second is a compromise: short enough that a long recording is collected incrementally rather
 * than assembled in one piece at stop time, long enough that the event does not fire often enough
 * to matter. The value is not sent anywhere and does not affect the resulting audio -- concatenating
 * the chunks reproduces the same stream either way.
 */
const CHUNK_INTERVAL_MS = 1000;

/**
 * What to ask the microphone for.
 *
 * Every value here exists to make the upload smaller, which is the only client-side cost this
 * application can control: the recording has to cross the network before transcription can even
 * begin, and that transfer sits inside the five-second budget the brief sets.
 *
 * Mono because speech carries no stereo information worth keeping, and a second channel is a
 * second channel of bytes. 16 kHz because speech energy lives below 8 kHz and the Nyquist limit
 * makes anything above that sample rate redundant -- it is also the rate speech recognition models
 * are trained at, so the downsampling loses nothing the provider would have used. The noise and
 * echo processing is the browser's own, applied before encoding, and a cleaner signal compresses
 * better as well as transcribing better.
 *
 * These are requests, not guarantees: a browser that cannot honour one ignores it rather than
 * failing, which is why none of them can break recording on a device that does not comply.
 */
const AUDIO_CONSTRAINTS = {
    channelCount: 1,
    sampleRate: 16000,
    noiseSuppression: true,
    echoCancellation: true,
};

/**
 * Target bitrate for the encoded audio.
 *
 * Opus is designed for speech and stays intelligible far below what music needs; 24 kbps mono is a
 * widely used setting for voice and is well above the point where transcription accuracy suffers.
 * Left unset, MediaRecorder picked roughly 250 kbps, making a sixteen-second recording about
 * 500 KB -- ten times larger than it needs to be, all of it network time inside the latency budget.
 */
const AUDIO_BITS_PER_SECOND = 24000;

/**
 * Thrown when a recording finished with no audio data in it.
 *
 * Distinct from MicrophoneAccessError: permission was granted and the recorder ran, but nothing
 * came out of it. The user-facing advice is different, so the type is different.
 */
export class EmptyRecordingError extends Error {
    constructor() {
        super("The recording contained no audio.");
        this.name = "EmptyRecordingError";
    }
}

/**
 * Captures a single recording from the microphone.
 *
 * One instance is one recording: start() then stop(), not reused. That is deliberate -- a recorder
 * that can be restarted has to defend against being started twice, and the extra state is not
 * worth it when constructing a new one is free.
 */
export class Recording {
    #mediaRecorder = null;
    #stream = null;
    #chunks = [];

    /**
     * Asks for the microphone and begins recording.
     *
     * @throws {MicrophoneAccessError} if permission is denied or no device is available.
     */
    async start() {
        try {
            // Prompts the user on first use; the browser remembers the answer per origin. Resolves
            // with a MediaStream -- a live handle on the microphone, not audio data.
            this.#stream = await navigator.mediaDevices.getUserMedia({ audio: AUDIO_CONSTRAINTS });
        } catch (error) {
            // getUserMedia rejects with NotAllowedError (denied) or NotFoundError (no device).
            // Both mean the same thing to the user, so they collapse into one error type.
            throw new MicrophoneAccessError(error);
        }

        // Ask for a specific container rather than accepting whatever the browser picks.
        //
        // This is not a preference, it is a correctness requirement. The upload is named from its
        // MIME type and the provider infers the container from that name, so we have to KNOW the
        // format, not guess it. Left to itself, Firefox records Ogg/Opus while reporting an empty
        // mimeType, which was sent as "recording.webm" and rejected by OpenAI with "Audio file
        // might be corrupted or unsupported" -- the bytes were a valid Ogg stream wearing the wrong
        // name. Observed 2026-09-12; the file began "OggS" rather than the WebM magic 1A 45 DF A3.
        const mimeType = supportedMimeType();
        this.#mediaRecorder = mimeType
            ? new MediaRecorder(this.#stream, { mimeType, audioBitsPerSecond: AUDIO_BITS_PER_SECOND })
            : new MediaRecorder(this.#stream, { audioBitsPerSecond: AUDIO_BITS_PER_SECOND });

        // Chunks arrive as the recording runs. Collect them; they are only useful concatenated.
        this.#mediaRecorder.addEventListener("dataavailable", (event) => {
            if (event.data.size > 0) {
                this.#chunks.push(event.data);
            }
        });

        // The timeslice matters for long recordings. Called with no argument, MediaRecorder buffers
        // the entire recording internally and emits it as a single "dataavailable" just before
        // "stop" -- fine for a three-second clip, but it means a sixteen-second one exists only as
        // one large blob assembled at the last moment. Passing a timeslice makes chunks arrive
        // every second during the recording, so the data is already collected by the time stop is
        // called and there is nothing left to flush.
        this.#mediaRecorder.start(CHUNK_INTERVAL_MS);
    }

    /**
     * Stops recording and resolves with the complete audio as a Blob.
     *
     * @returns {Promise<Blob>} the encoded recording, in whatever container the browser chose.
     */
    stop() {
        return new Promise((resolve, reject) => {
            this.#mediaRecorder.addEventListener("stop", () => {
                // Release the microphone. Without this the browser's recording indicator stays lit
                // and the device is held open -- a real bug users notice immediately.
                this.#stream.getTracks().forEach((track) => track.stop());

                // mimeType is whatever the browser actually picked. It is carried onto the Blob
                // rather than hardcoded, so the backend is told the truth about what it is being
                // sent -- and since start() asked for a specific container, this is now the type we
                // requested rather than a surprise.
                const blob = new Blob(this.#chunks, { type: this.#mediaRecorder.mimeType });

                // An empty recording means no audio was captured at all -- a muted or absent input
                // device, most often. Uploading it wastes a provider call that can only come back
                // with an empty transcript, and the resulting blank panel looks identical to a
                // transcription that silently failed. Failing here names the real cause instead.
                if (blob.size === 0) {
                    reject(new EmptyRecordingError());
                    return;
                }

                resolve(blob);
            }, { once: true });

            this.#mediaRecorder.stop();
        });
    }
}

/**
 * Picks the first container this browser can record that the transcription provider accepts.
 *
 * Ordered by preference, not by popularity: WebM/Opus first because Chrome and Firefox both
 * produce it and it is the most compact of the three, then Ogg/Opus which Firefox prefers, then
 * mp4 which is all Safari offers. Every entry is on OpenAI's accepted list (mp3, mp4, mpeg, mpga,
 * m4a, wav, webm) or is a container it decodes -- there is no point recording a format that will
 * be rejected after the upload has already been paid for.
 *
 * Returns undefined when the browser supports none of them, in which case the caller lets
 * MediaRecorder choose and we fall back to whatever it reports. That path is a last resort, and it
 * is the one that produced the mislabelled Ogg file described in start().
 *
 * isTypeSupported is itself absent on very old browsers, hence the optional-call guard.
 */
function supportedMimeType() {
    const candidates = [
        "audio/webm;codecs=opus",
        "audio/ogg;codecs=opus",
        "audio/mp4",
    ];
    return candidates.find((type) => MediaRecorder.isTypeSupported?.(type));
}

/**
 * Maps a MIME type to a file extension.
 *
 * The upload needs a filename because the transcription provider infers the container format from
 * its extension -- send "recording" with no suffix and the call is rejected upstream even though
 * the bytes are fine. mimeType can carry codec parameters ("audio/webm;codecs=opus"), so only the
 * part before the semicolon is meaningful here.
 */
export function extensionFor(mimeType) {
    const base = mimeType.split(";")[0];
    switch (base) {
        case "audio/mp4":  return "mp4";
        case "audio/mpeg": return "mp3";
        case "audio/ogg":  return "ogg";
        default:           return "webm";
    }
}
