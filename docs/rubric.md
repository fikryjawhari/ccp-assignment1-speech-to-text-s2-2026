# Assignment 1 Marking Rubric

Source: the COMP3011 Assignment 1 rubric table. Reflowed from the original table (hard line
breaks inside cells removed) so it renders; wording is otherwise unchanged. Grade bands are
cumulative — each band says "in addition to" the one below it.

| Criterion | Points |
| --- | --- |
| [REST API, Java Backend & Cloud STT Implementation](#rest-api-java-backend--cloud-stt-implementation) | 30 |
| [Concurrency & Web Framework Proficiency](#concurrency--web-framework-proficiency) | 30 |
| [Frontend & Client-side REST API Integration](#frontend--client-side-rest-api-integration) | 20 |
| [Code Quality, Comments and Tests](#code-quality-comments-and-tests) | 20 |
| **Total** | **100** |

---

## REST API, Java Backend & Cloud STT Implementation

*30 points.* Assesses the core Java logic: receiving the audio data, reliably integrating with
the Cloud STT API and meeting the YAML specification on the other required API endpoints.

**High Distinction** — In addition to everything at distinction level, regression tests are
provided for your REST API controllers using a stub STT API service where required.

**Distinction** — Exemplary implementation with all REST endpoints reliable and working,
including full and correct error handling as per the YAML spec on all your API calls.

**Credit** — Your Java REST endpoint for audio uploading is reliable. Processing the
request/response through the external Cloud STT API is reliable. All the other endpoints in the
YAML spec are working under basic testing.

**Pass** — Your Java REST endpoint to receive audio data works and the integration with the
external Cloud STT service works under basic testing. Attempts to implement the other endpoints
in the YAML spec are evident, but not all are working.

**Fail** — Your Java REST endpoint to receive audio data is non-functional. There is no attempt,
or a failed attempt, to call the external Cloud STT service. Even if everything else is working,
this is the fundamental capability required of the backend, so must be implemented to a working
level.

---

## Concurrency & Web Framework Proficiency

*30 points.* Assesses the application of a Java Web Framework to manage multiple concurrent
requests that require requests to other cloud services to fulfil, a key principle of
cloud-native architectures.

**High Distinction** — In addition to everything at the distinction level, your code includes
regression tests. One test must simulate greater than 200 simultaneous blocking HTTP requests
through your controller(s) and demonstrate no significant delays and no crashes. At least one
regression test to surface race conditions must be provided too.

**Distinction** — Your code shows sophisticated use of the Java Spring Boot framework for
managing greater than 200 simultaneous blocking HTTP requests, either through
non-blocking/asynchronous request handling or lightweight threading. Performance under load is
demonstrably high, and configuration is minimal and well-optimized for a cloud environment. All
race conditions are clearly understood and efficiently dealt with in your code.

**Credit** — Your code shows effective use of the Java Spring Boot framework for concurrency.
The application handles a moderate volume of concurrent requests gracefully, demonstrating
understanding of the framework's threading/concurrency model. Successful attempts to protect API
calls from race conditions are present, such as stateless controllers or locks on state used
minimally and correctly.

**Pass** — Your code uses the Java Spring Boot framework to handle requests and functions under
minimal load, but does not protect against all race conditions.

**Fail** — A Java framework is either not used or is used inappropriately (e.g., resulting in
blocking/freezing behaviour under multiple concurrent requests). i.e. The code fails to handle
multiple, simultaneous requests without significant delays or crashes.

---

## Frontend & Client-side REST API Integration

*20 points.* Assesses the functionality and design of the client-side implementation and its
ability to correctly interface with the Java backend.

**High Distinction** — Your code shows superior client engineering, demonstrating excellent UX
(e.g., highly accessible design with excellent indication of application state including all
error conditions). It includes client-side optimizations in addition to modern JavaScript
practices (e.g., audio compression, chunking) to minimize network load and latency.

**Distinction** — Excellent client implementation in HTML, CSS and Javascript. In addition to
the credit rubric features, your Javascript code has robust error handling for microphone access
and REST API calls, and makes use of async/await for clean, efficient data upload.

**Credit** — Your client is robust and user-friendly. It reliably captures audio, provides clear
start/stop feedback, and accurately transmits the audio data to a well named REST endpoint.
Successful display of the returned text and some attempts made to handle errors. Good attempts
to split design roles between HTML, CSS and Javascript.

**Pass** — Your client successfully accesses the microphone and can record audio and display
returned text. The recorded audio data is packaged and uploaded to the backend API, but the
process is unreliable or lacks proper error handling, and user feedback is lacking during the
recording process or while waiting for text to display. No great care or understanding of how to
separate HTML, CSS and Javascript design roles.

**Fail** — Unfortunately, your client-side website is non-functional, not correctly accessing
the device microphone, failing to display how to record, or when recording is taking place, or
the returned text transcription. No demonstrable attempt to format and upload audio data via an
HTTP request. Poor use of HTML, CSS and Javascript.

---

## Code Quality, Comments and Tests

*20 points.* Assesses the coding practices of the submission, including readability, comments
that focus on the why rather than the what to demonstrate understanding and conveying knowledge
to future readers, and supporting software engineering practices such as tests.

**High Distinction** — In addition to everything at distinction level, you have included
comprehensive regression testing of system components to requirements (both functional and
non-functional) and either with clear commenting or supporting markdown documentation in the
repository, explain why certain tests were developed, the expected results from them and the
assurances they provide. Your logging approach is used by your regression tests.

**Distinction** — Exceptionally well-engineered solution. All your code adheres to Java coding
conventions, uses design patterns intentionally and appropriately (not over use for the sake of
trying to boost your marks), and shows use of Spring Boot framework features throughout,
including annotations, dependency injection, application properties. Simple but effective Java
build/configuration management is in evidence. Code comments and any supporting repository
markdown documentation justifies architecture choices, potential deployment pitfalls, and shows
a clear grasp of performance and concurrency issues required to robustly use an external Cloud
STT service. Some evidence of regression testing of system components. Security treatment of the
STT API token is exemplary: never leaked, logged, persisted or left in code; only acquired
dynamically as per the specification and used at runtime. A systematic approach to logging API
calls and services used is used.

**Credit** — You have provided clean, readable, well-commented Java code adhering to standard
Java conventions. Solid use of several Spring Boot framework features such as correct
annotations and dependency injection achieve clear decoupling (eg. of controllers from
services). Your comments are succinct and clear, providing a sound description of the backend,
frontend, and necessary cloud credentials. You demonstrate understanding of the "why" behind the
chosen technologies, either with good comments or succinct supporting markdown documentation in
the repository. There is basic logging on the STT call. The STT API token is not leaked to the
client side or to logging outputs, nor is there any persisting or evidence of hardcoding of test
keys in the code.

**Pass** — Your code is functional but could be cleaner (e.g., naming conventions are
inconsistent and non-standard). Basic comments are provided, but they lack detail on the Cloud
API setup or troubleshooting steps. Demonstrates a functional but rudimentary understanding of
how the core components interact. Spring Boot features such as dependency injection are not well
utilised so separation of concerns and loose coupling between components in the code could be
better. The STT API token is not leaked to the client side or to logging outputs.

**Fail** — Your code is poorly structured, undocumented, and difficult to follow. A clear
understanding of how the core components interact is not evident from the code or comments. The
code includes logging, printing, persisting or other forms of leaking of the STT API token that
was supposed to be protected.
