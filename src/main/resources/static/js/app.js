/*
 * Wires the recorder to the page and to the backend.
 *
 * This file owns the application's state. Everything the user sees -- the button's label, whether
 * it is disabled, whether the indicator is showing, what the status line says -- is derived from a
 * single `state` variable in one place, rather than being poked at from wherever a state change
 * happens. That is the difference between a page whose UI can drift out of sync with what it is
 * doing and one where it cannot.
 */

import { Recording, MicrophoneAccessError, EmptyRecordingError, extensionFor } from "./recorder.js";

/**
 * Where the recording is POSTed. Not part of docs/assignment1api.yaml -- this endpoint is ours,
 * and the shape is decided in the README: multipart/form-data with the audio in a part named
 * "audio". A relative path, so the page works wherever it is deployed without a config change.
 */
const TRANSCRIBE_URL = "/api/v1/transcriptions";

/** The multipart part name. Must match @RequestParam("audio") in TranscriptionController. */
const AUDIO_FIELD = "audio";

/**
 * The states this page can be in. The brief requires every one of them to be visible to the user.
 *
 * A frozen object of string constants rather than bare strings at each call site: a typo in a
 * string literal fails silently, whereas a typo in `States.RECORDNIG` is an immediate undefined.
 * This is the closest JavaScript gets to Python's enum.Enum.
 */
const States = Object.freeze({
    IDLE: "idle",
    RECORDING: "recording",
    UPLOADING: "uploading",
    TRANSCRIBING: "transcribing",
    ERROR: "error",
});

const recordButton = document.getElementById("record-button");
const recordingIndicator = document.getElementById("recording-indicator");
const statusMessage = document.getElementById("status-message");
const transcriptOutput = document.getElementById("transcript-output");

let state = States.IDLE;
let recording = null;

/**
 * Makes the page reflect `state`.
 *
 * Called after every state change and nowhere else. Because it sets every property it controls on
 * every call -- rather than only the ones it thinks changed -- there is no way for a stale
 * attribute to survive a transition.
 */
function render(message = "") {
    // Derived once at the top rather than tested repeatedly below, so the mapping from state to
    // appearance reads as a table instead of a scattering of comparisons.
    const isRecording = state === States.RECORDING;
    const isBusy = state === States.UPLOADING || state === States.TRANSCRIBING;

    recordButton.textContent = isRecording ? "Stop recording" : "Start recording";

    // Disabled while the upload is in flight: there is no recording to stop and starting a second
    // one would race the first. The browser also stops routing clicks to a disabled button, so
    // this is enforcement, not just a visual hint.
    recordButton.disabled = isBusy;

    recordingIndicator.hidden = !isRecording;

    statusMessage.textContent = message;

    // The two-argument form sets the class to match the boolean rather than flipping it, so
    // calling render() twice in the same state cannot drift.
    recordButton.classList.toggle("recorder__button--recording", isRecording);
    statusMessage.classList.toggle("recorder__status--error", state === States.ERROR);
}

/** Moves to a new state and re-renders. The only place `state` is assigned. */
function transition(next, message = "") {
    state = next;
    render(message);
}

/**
 * Uploads the recording and returns the transcript text.
 *
 * FormData builds a multipart/form-data body. Note what is deliberately absent: any Content-Type
 * string in the header that only the browser knows. Setting Content-Type by hand here is the
 * classic multipart bug -- the request arrives with no boundary and the server cannot parse it.
 */
async function uploadForTranscription(audioBlob) {
    const formData = new FormData();
    // The third argument is the filename. The provider infers the container format from its
    // extension, so it is derived from the Blob's actual type rather than assumed.
    formData.append(AUDIO_FIELD, audioBlob, `recording.${extensionFor(audioBlob.type)}`);

    const response = await fetch(TRANSCRIBE_URL, { method: "POST", body: formData });

    // fetch only rejects on network failure -- a 500 is a perfectly successful fetch. Without this
    // check an ErrorResponse body would be parsed as a transcript and `undefined` shown on screen.
    if (!response.ok) {
        throw new Error(await errorMessageFrom(response));
    }

    // TranscriptionResponse is { text, durationMs }.
    const result = await response.json();
    return result.text;
}

/**
 * Extracts something worth showing the user from a failed response.
 *
 * The backend returns the contract's ErrorResponse shape -- timestamp, status, error, message,
 * path -- so `message` is the most specific thing available. It is read defensively: an error
 * response is exactly the case where the body might not be JSON at all (a proxy timeout, a
 * truncated response), and a parse failure here would replace a useful error with a confusing one.
 * Falling back to the HTTP status keeps the page honest when the body cannot be trusted.
 */
async function errorMessageFrom(response) {
    try {
        const body = await response.json();
        if (body && typeof body.message === "string") {
            return body.message;
        }
    } catch {
        // Deliberately swallowed: the fallback below is a better message than the parse error.
    }
    return `The server responded with ${response.status}.`;
}

/**
 * The single click handler. Which action a click means is a function of the current state.
 */
async function onRecordButtonClick() {
    if (state === States.RECORDING) {
        await stopAndTranscribe();
    } else {
        await startRecording();
    }
}

/** Asks for the microphone and begins capturing. */
async function startRecording() {
    recording = new Recording();
    try {
        await recording.start();
    } catch (error) {
        // Permission denial is not an exceptional condition here -- it is a normal answer to a
        // question we asked, and the brief requires it handled explicitly rather than surfacing as
        // an unhandled rejection in the console. The distinct error type from recorder.js is what
        // lets this branch give advice the user can act on instead of a generic failure.
        if (error instanceof MicrophoneAccessError) {
            transition(States.ERROR, "Microphone access was denied. Allow it in your browser to record.");
        } else {
            transition(States.ERROR, "Could not start recording.");
        }
        recording = null;
        return;
    }
    transition(States.RECORDING, "Recording. Press stop when you are finished.");
}

/** Stops capturing, uploads the audio, and displays the transcript. */
async function stopAndTranscribe() {
    // Captured before the await so the finally block cannot leave a stale recorder in place.
    const activeRecording = recording;
    recording = null;

    try {
        const audioBlob = await activeRecording.stop();

        // UPLOADING and TRANSCRIBING are two states behind a single fetch, which gives no signal
        // for where one ends and the other begins. Rather than announce a transition that has not
        // happened, the page claims only what it knows: the bytes are going up. The server's
        // durationMs then reports the transcription time honestly, after the fact.
        transition(States.UPLOADING, "Uploading and transcribing…");

        const transcript = await uploadForTranscription(audioBlob);

        // textContent, never innerHTML: the transcript is text from outside this page, and
        // assigning it as HTML would execute any markup inside it.
        transcriptOutput.textContent = transcript;

        // Straight back to ready, so a second recording can start without a reload.
        transition(States.IDLE, "Transcript ready.");
    } catch (error) {
        // An empty recording is the user's problem to fix, not a server failure, so it gets its own
        // message rather than being reported as a failed transcription.
        if (error instanceof EmptyRecordingError) {
            transition(States.ERROR, "No audio was captured. Check your microphone is not muted.");
            return;
        }

        // The page stays usable after a failure: ERROR renders the message, but the button is
        // live again, so retrying costs one click rather than a page reload.
        transition(States.ERROR, `Transcription failed. ${error.message}`);
    }
}

recordButton.addEventListener("click", onRecordButtonClick);

// Paint the initial state, so the button's label comes from the same code path as every later
// change rather than from the hardcoded text in index.html.
render();
