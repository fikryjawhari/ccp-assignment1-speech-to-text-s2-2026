/*
 * Wires the recorder to the page and to the backend.
 *
 * This file owns the application's state. Everything the user sees -- the button's label, whether
 * it is disabled, whether the indicator is showing, what the status line says -- is derived from a
 * single `state` variable in one place, rather than being poked at from wherever a state change
 * happens. That is the difference between a page whose UI can drift out of sync with what it is
 * doing and one where it cannot.
 */

import { Recording, MicrophoneAccessError, extensionFor } from "./recorder.js";

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
    // TODO(you): drive the DOM from `state`. Four things need setting on every call, and they must
    //   all be set every time, including back to their defaults:
    //
    //     recordButton.textContent   -- "Start recording" when idle, "Stop recording" while
    //                                   recording. What should it say while uploading?
    //     recordButton.disabled      -- true whenever pressing it would do something incoherent.
    //                                   Which states are those?
    //     recordingIndicator.hidden  -- the `hidden` property, not style.display. It is the
    //                                   attribute the HTML already declares, and letting CSS keep
    //                                   ownership of display means the two never fight.
    //     statusMessage.textContent  -- the `message` argument.
    //
    //   Also toggle the two state classes, both of which app.css already styles:
    //     recordButton.classList.toggle("recorder__button--recording", <condition>)
    //     statusMessage.classList.toggle("recorder__status--error", <condition>)
    //   The two-argument form of toggle() sets the class to match the boolean rather than
    //   flipping it, which is what you want when deriving from state.
    //
    //   A switch on `state` and a chain of ifs both work. The real decision is whether an unknown
    //   state should fall through silently or be caught -- think about which one you would rather
    //   debug.
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
 * header. The browser must set it itself, because multipart requires a randomly generated boundary
 * string in the header that only the browser knows. Setting Content-Type by hand here is the
 * classic multipart bug -- the request arrives with no boundary and the server cannot parse it.
 */
async function uploadForTranscription(audioBlob) {
    const formData = new FormData();
    // The third argument is the filename. The provider infers the container format from its
    // extension, so it is derived from the Blob's actual type rather than assumed.
    formData.append(AUDIO_FIELD, audioBlob, `recording.${extensionFor(audioBlob.type)}`);

    // TODO(you): send it and return the transcript. In order:
    //
    //   1. `await fetch(TRANSCRIBE_URL, { method: "POST", body: formData })`.
    //   2. Check `response.ok`. This is the trap that catches everyone: fetch only rejects on a
    //      network failure. A 500 from the server is a perfectly successful fetch, so without this
    //      check an error response sails through and you try to read a transcript out of an
    //      ErrorResponse. Throw an Error when it is not ok.
    //   3. On the error path, decide how much to tell the user. The backend returns the
    //      ErrorResponse shape from the contract -- timestamp, status, error, message, path -- so
    //      `(await response.json()).message` is available. Trade-off: that message is more
    //      specific and more useful, but it is server-authored text going straight onto the page,
    //      and reading the body can itself throw if the response is not JSON. A fixed client-side
    //      message is safer and less useful. Pick one and be able to justify it.
    //   4. On success, parse the JSON and return the `text` field -- TranscriptionResponse is
    //      { text, durationMs }.
}

/**
 * The single click handler. Which action a click means is a function of the current state.
 */
async function onRecordButtonClick() {
    // TODO(you): implement the two branches.
    //
    //   If idle: construct a `new Recording()`, keep it in the module-level `recording` variable,
    //   `await recording.start()`, then transition to RECORDING. Wrap it: recording.start() throws
    //   MicrophoneAccessError when the user denies permission, and the brief requires that failure
    //   handled explicitly rather than left as an unhandled rejection. `instanceof
    //   MicrophoneAccessError` distinguishes it from anything else that went wrong.
    //
    //   If recording: `await recording.stop()` for the Blob, transition to UPLOADING, then to
    //   TRANSCRIBING, call uploadForTranscription, put the result in transcriptOutput.textContent,
    //   and transition back to IDLE -- the brief requires the page return to a ready state
    //   automatically so a new recording can start immediately.
    //
    //   Two decisions worth making consciously:
    //
    //   - UPLOADING and TRANSCRIBING are separate states in the enum, but a single fetch call
    //     gives you no signal for where one ends and the other begins. You can set UPLOADING
    //     before the fetch and TRANSCRIBING when it resolves, but that is a lie -- by the time it
    //     resolves, transcribing is finished. Options: use the response durationMs to report
    //     honestly after the fact, collapse the two states, or find a real signal. There is no
    //     clean answer here; choose one and be ready to defend it.
    //
    //   - Where does the failure path leave the page? ERROR is a state, but a page stuck in it is
    //     a page the user has to reload. Does it return to IDLE, and if so, when?
    //
    //   Use textContent, never innerHTML. The transcript is text from outside this page; assigning
    //   it as HTML would execute any markup inside it. textContent cannot.
}

recordButton.addEventListener("click", onRecordButtonClick);

// Paint the initial state, so the button's label comes from the same code path as every later
// change rather than from the hardcoded text in index.html.
render();
