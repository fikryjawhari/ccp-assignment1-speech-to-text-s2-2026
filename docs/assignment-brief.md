# Assignment 1: Java Web Project

> Verbatim text of the COMP3011 Assignment 1 brief (extracted from the course PDF).
> This file is the source of truth for marking criteria; `../README.md` is the synthesised version.
## Description
In this assignment, your goal is to build a website that converts speech to text using an external Cloud STT (speech-to-text) service.

As well as hosting a functioning web page, a number of Cloud API endpoints must also be developed to enable programmatic interaction with your site.

## Aims
You must clearly demonstrate key principles of Java and Cloud programming:

The ability to develop a minimal website front end using html and javascript to access the device microphone and upload audio data via a REST API to your backend.
Your Java backend reliably implementing a REST-like API end point to receive audio data from browser clients, convert this data to text via a third party Cloud service, then return text data back to the browser for display.
Implementation of additional API endpoints specified in YAML that report uptime, runtime statistics and facilitate graceful shutdown.
The ability to do this reliably with concurrent overlapping page requests and API queries that do not block each other waiting for HTTP network traffic.
Protection of a third party API key within your website that does not leak to the browser, or to API clients, or by by having them in code or logs.
At the end of this assignment you will have experienced how to apply Java foundational knowledge to a specific requirement, how to research multiple tools and frameworks to bring a programming requirement to reality and gained exposure to some Cloud deployment challenges involving performance, concurrency and security. The skills required to adapt your knowledge to new technologies, and research, test and experiment with practical realisations of working code by troubleshooting problems with incomplete knowledge are essential to professional practice.

The demonstrable goal of the assignment is a working implementation! If you have a working implementation but do not know how it works, then you have not benefitted from this assignment - see notes on the best way of using the work of others and on the use of generative AI below.

## Specification
This assignment is interactive, in that you can enter your work for a checkup anytime (24/7) and gain feedback on how your functionality is progressing, as many times as you want. Login to TITAN to do this and choose Assignment 1. The feedback is not there to determine your final mark, but rather to help you progress and to let you know when you have achieved certain components working correctly (basic functional testing) and can move on to the next thing or pursue enhancements. Because of this mechanism, certain aspects of your web page and API endpoints must be delivered exactly to specification so that they are easily machine testable.

### Language and Framework
This assignment must be developed in Java using the Spring Framework.
Evidence of professional engineering practices is required in your code:
Clear package structure and naming.
Adherence to standard style practices including appropriate commenting.
Dependency injection through constructors and Spring annotations.
Configuration (between say local and TITAN runs) through profiles and environment variables, rather than commented in/out pieces of code.

### Packaging and Deployment
Your entire runnable website must be provided to TITAN as a single Executable JAR (also known as a Fat or Uber JAR). Package up a JAR as many times as you like and check it with TITAN. TITAN will only remember your high water mark (your best effort) so even if you break your code and functionality goes backwards before the assignment deadline, it does not matter, but please hand in your best working code.
Your hand in must be provided as a readable GitHub repository URL to Gradescope. Whilst your git commit history and state of your repository do not have to look like a perfect software engineering effort throughout the entire assignment period, it is good practice and highly encouraged to use GitHub as a repository and backup source throughout the semester for everything you do. If in doubt, we will look at your Github repository for evidence of original work. One huge commit of "brand new" code just before the deadline with only general comments and no evidence of incremental changes is not a good look. Also, arguing that you have lost your source code because of a lost or stolen device is no excuse. This course is teaching you about Cloud services, so let's make sure you use one to safeguard your source code!

### Web Page Specification
A single web page must be served at http://localhost:8080/
The web page must provide an intuitive mechanism to start recording audio from the client device microphone, indicate when recording is taking place (so the user knows when to start talking), and expose a way of stopping the recording.
Upon completion of the recording, a text transcription of the recording obtained from the https://api.openai.com/v1/audio/transcriptions Cloud API must be displayed on the page.
For a recording of less than one minute, this displayed result is expected within 5 seconds of stopping the recording.
The web page should automatically return to a state able to accept a new recording.

### Web API Endpoints and Functionality
Additional endpoints YAML specification: [`assignment1api.yaml`](assignment1api.yaml).
 
## Non-functional Specifications
The https://api.openai.com/v1/audio/transcriptions API is a commercial API that requires a bearer token to use it. A valid token is provided to you on TITAN via the OS environment variable OPENAI_API_KEY. You must ONLY use this dynamically at runtime. i.e. You may read it off the environment and store it in process variables but in the interests of adhering to Cloud security principles and practices do not output it, log it or attempt to exfiltrate it from TITAN.
You may devise alternative solutions for local testing, but show evidence of good software engineering practices to control configuration and deployment differences going from local testing to TITAN testing.
Provide a high performance implementation (able to handle > 200 concurrent blocking HTTP requests) within the confines of a single Java process.
 
## Advanced Topics (not assessed)
How would you modify the YAML API specification to allow statistics to be gathered for different users?
How could you distinguish between different users accessing the web page?
What is bad about exposing /api/v1/admin/shutdown and can you think of a safer way to implement this functionality in a cloud environment?
 
## Hints, Tips and Tricks
The specification requires a Java Spring Framework application. You are strongly encouraged to do create a new Spring Boot project using spring-boot-starter-web as your initial dependency bundle and Maven as your build tool. You should already have some exposure to this from Practical 3. Smart engagement with gen-AI and online resources provides many avenues for quickly learning the particulars of Spring Boot and Maven, and — most importantly — identifying and troubleshooting problems and technical hurdles along the way.
As well as the Spring Tools version of Eclipse as your IDE, a couple of other installed tools on your machine may come in useful if you like scripting builds/cleans/git commands etc. instead of using the IDE for everything:
A standalone install of Maven.
A shell window with JAVA_HOME set in the environment and Maven and git on the PATH (e.g. GitBash on Windows).
As with any software development, start small and build up. Come up with a static web page and a single API GET request and try it in TITAN to get feedback on your progress, then take on the functionality required by the specification, checking whether you have met each new functional requirement as you go.
Gradually build up features as your application develops. Think about the design and be prepared to throw things away and refactor things. The best software is developed this way, with the final quality often determined by what you are brave enough to throw away and rewrite after you have settled on the right design by trying a few different approaches.
This assignment deliberately does not tell you "what to do", only "what to make". You must figure out what to do in order to make it. Be inquisitive and be prepared to make mistakes — two of the best ingredients you need for learning new things