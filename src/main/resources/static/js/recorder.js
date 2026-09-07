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
            this.#stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        } catch (error) {
            // getUserMedia rejects with NotAllowedError (denied) or NotFoundError (no device).
            // Both mean the same thing to the user, so they collapse into one error type.
            throw new MicrophoneAccessError(error);
        }

        this.#mediaRecorder = new MediaRecorder(this.#stream);

        // Chunks arrive as the recording runs. Collect them; they are only useful concatenated.
        this.#mediaRecorder.addEventListener("dataavailable", (event) => {
            if (event.data.size > 0) {
                this.#chunks.push(event.data);
            }
        });

        this.#mediaRecorder.start();
    }

    /**
     * Stops recording and resolves with the complete audio as a Blob.
     *
     * @returns {Promise<Blob>} the encoded recording, in whatever container the browser chose.
     */
    stop() {
        return new Promise((resolve) => {
            this.#mediaRecorder.addEventListener("stop", () => {
                // Release the microphone. Without this the browser's recording indicator stays lit
                // and the device is held open -- a real bug users notice immediately.
                this.#stream.getTracks().forEach((track) => track.stop());

                // mimeType is whatever the browser actually picked: audio/webm on Chrome and
                // Firefox, audio/mp4 on Safari. It is carried onto the Blob rather than hardcoded,
                // so the backend is told the truth about what it is being sent.
                resolve(new Blob(this.#chunks, { type: this.#mediaRecorder.mimeType }));
            }, { once: true });

            this.#mediaRecorder.stop();
        });
    }
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
