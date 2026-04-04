# Smart Scheduler

Simple Spring Boot service for creating Google Calendar tasks and inferring task priority with Hugging Face.

## What It Does

- Creates events in Google Calendar
- Infers task priority as `URGENT`, `HIGH`, `MEDIUM`, or `LOW` when priority is missing
- Lists next-day calendar events with Google metadata like color and derived priority
- Provides a test endpoint for checking the raw Hugging Face priority response

## What It Uses

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring WebFlux `WebClient`
- Google Calendar API
- Google OAuth desktop flow
- Gson
- Hugging Face Inference API
- Maven

## Requirements

- Java 21
- Maven or the included `./mvnw`
- Google Cloud OAuth credentials JSON for Calendar access
- Hugging Face API key
- Google Calendar authorization completed once through the auth endpoint

## Local Config

Create `application-local.properties` either in the project root or under `src/main/resources`.

```properties
huggingface.api-key=your-huggingface-api-key
google.oauth.credentials-path=/absolute/path/to/credentials.json
```

Important app settings already exist in [application.properties](/Users/himanshu/workspace/scheduler/src/main/resources/application.properties):

- `server.port=9090`
- `google.calendar.time-zone=Asia/Kolkata`
- `huggingface.model=facebook/bart-large-mnli`

## Run

```bash
./mvnw spring-boot:run
```

Server starts on:

```text
http://localhost:9090
```

## API

### 1. Authorize Google Calendar

`POST /auth/google`

Starts the Google OAuth flow and stores tokens locally.

Response:

```text
Google Calendar authorization completed.
```

### 2. Create Task

`POST /tasks`

Input:

```json
{
  "title": "Submit project demo",
  "description": "Need to finish and send before noon",
  "date": "2026-04-06",
  "time": "10:00",
  "createMeetLink": false,
  "attachmentUrls": [
    "https://example.com/file"
  ]
}
```

Notes:

- `date` and `time` are used to create the calendar event
- if `priority` is missing, the service infers it from task details
- if `colorId` is missing, it is derived from priority

Output:

```json
{
  "message": "Event created",
  "eventLink": "https://calendar.google.com/...",
  "priority": "HIGH",
  "description": "Need to finish and send before noon"
}
```

### 3. Test Priority Inference

`POST /tasks/priority/test`

Input:

```json
{
  "title": "Pay electricity bill",
  "description": "Due tonight",
  "date": "2026-04-06",
  "time": "20:00"
}
```

Output:

```json
{
  "task": {
    "title": "Pay electricity bill",
    "description": "Due tonight",
    "date": "2026-04-06",
    "time": "20:00"
  },
  "submittedPriority": null,
  "inferredPriority": "URGENT",
  "huggingFaceResponse": "[{\"label\":\"urgent\",\"score\":0.41}]"
}
```

### 4. Get Next Day Events

`GET /events/next-day`

Output:

```json
[
  {
    "id": "abc123",
    "summary": "Submit project demo",
    "description": "Need to finish and send before noon",
    "colorId": "6",
    "priority": "HIGH",
    "location": null,
    "status": "confirmed",
    "start": "2026-04-06T10:00:00+05:30",
    "end": "2026-04-06T11:00:00+05:30",
    "htmlLink": "https://calendar.google.com/..."
  }
]
```

## Priority Mapping

- `URGENT` -> color `11`
- `HIGH` -> color `6`
- `MEDIUM` -> color `5`
- `LOW` -> color `2`

Existing Google Calendar events returned by `/events/next-day` use the same color mapping to derive priority when possible.
