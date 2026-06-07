# Smart Scheduler

Spring Boot REST API for intelligent calendar management. Supports Google Calendar and Microsoft Outlook, AI-powered priority inference, recurring events, delegate access via magic links, and drag-and-drop event updates.

## Features

- **Dual calendar support** — Google Calendar API + Microsoft Graph API
- **Auto-provider detection** — enter an email, DNS MX lookup determines Google or Microsoft
- **AI priority inference** — Hugging Face zero-shot classification (`facebook/bart-large-mnli`) infers URGENT/HIGH/MEDIUM/LOW when priority isn't specified
- **Custom event duration** — set start and end time (defaults to 1 hour)
- **Recurring events** — daily, weekly, weekdays, monthly, yearly with configurable occurrence count
- **Delegate access** — request calendar access from others via magic link emails; create events on their behalf
- **Drag-and-drop** — PATCH endpoint to move events to a new time slot
- **Week view** — list events for any 7-day period
- **Meet/Teams links** — optional video meeting creation with events

## Tech Stack

- Java 21, Spring Boot 4, Maven
- Spring Web MVC + Spring WebFlux WebClient
- Google Calendar API + Google OAuth 2.0
- Microsoft Graph API + Azure AD OAuth 2.0
- Hugging Face Inference API
- Spring Mail (Mailgun/Gmail SMTP)
- Gson, Lombok

## Requirements

- Java 21
- Maven or `./mvnw`
- Google Cloud OAuth credentials (Calendar API enabled)
- Azure AD app registration (optional, for Microsoft calendar)
- Hugging Face API key
- SMTP credentials for delegate magic link emails (Mailgun, Gmail, etc.)

## Local Config

Create `application-local.properties` in the project root:

```properties
# Google OAuth
google.oauth.credentials-path=credentials.json
google.oauth.redirect-uri=http://localhost:9090/auth/google/callback

# Hugging Face
huggingface.api-key=hf_your_key

# Microsoft OAuth (optional)
microsoft.oauth.client-id=your-client-id
microsoft.oauth.client-secret=your-secret
microsoft.oauth.tenant-id=common

# SMTP for delegate emails (Mailgun example)
spring.mail.host=smtp.mailgun.org
spring.mail.port=587
spring.mail.username=mail@sandbox.mailgun.org
spring.mail.password=your-mailgun-password
```

## Run

```bash
./mvnw spring-boot:run
```

Server starts on `http://localhost:9090`.

## API Reference

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/auth/detect-provider?email=...` | MX lookup to detect Google or Microsoft |
| GET | `/auth/google/url` | Get Google OAuth authorization URL |
| GET | `/auth/google/callback` | OAuth callback (redirects to frontend) |
| GET | `/auth/google/status` | Check Google auth status |
| GET | `/auth/microsoft/url` | Get Microsoft OAuth URL |
| GET | `/auth/microsoft/callback` | OAuth callback |
| GET | `/auth/microsoft/status` | Check Microsoft auth status |
| GET | `/auth/status` | Combined auth status for both providers |
| DELETE | `/auth/{provider}/disconnect` | Revoke stored tokens |

### Events

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/tasks` | Create calendar event |
| GET | `/events/next-day?provider=google` | List events for next 24 hours |
| GET | `/events/week?provider=google&startDate=2026-06-07` | List events for 7 days |
| PATCH | `/events/{eventId}` | Update event time (drag-and-drop) |
| POST | `/tasks/priority/test` | Test AI priority inference |

### Create Task Payload

```json
{
  "title": "Standup Call",
  "description": "Daily sync with team",
  "date": "2026-06-07",
  "time": "09:00",
  "endTime": "09:30",
  "priority": "HIGH",
  "provider": "google",
  "recurrence": "WEEKDAYS",
  "recurrenceCount": 52,
  "createMeetLink": true,
  "delegateEmail": "senior@company.com"
}
```

All fields except `title`, `date`, and `time` are optional. If `priority` is omitted, AI infers it. If `endTime` is omitted, defaults to start + 1 hour.

### Delegate Access

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/delegates/request` | Send magic link email to request calendar access |
| GET | `/delegates/validate?token=...` | Validate a magic link token |
| GET | `/delegates/authorize?token=...` | Start OAuth for the calendar owner |
| GET | `/delegates/callback/google` | Delegate OAuth callback (Google) |
| GET | `/delegates/callback/microsoft` | Delegate OAuth callback (Microsoft) |
| GET | `/delegates` | List all authorized delegates |
| DELETE | `/delegates/{email}` | Revoke delegate access |

### Priority Mapping

| Priority | Google Color | Microsoft Importance |
|----------|-------------|---------------------|
| URGENT | 11 (red) | high |
| HIGH | 6 (orange) | high |
| MEDIUM | 5 (yellow) | normal |
| LOW | 2 (green) | low |

## Google Cloud Console Setup

1. Create a project and enable **Google Calendar API**
2. Create OAuth 2.0 credentials (Desktop app)
3. Add authorized redirect URIs:
   - `http://localhost:9090/auth/google/callback`
   - `http://localhost:9090/delegates/callback/google`
4. Download credentials JSON to `src/main/resources/credentials.json`

## Azure AD Setup (Optional)

1. Register an application in Azure Portal
2. Add redirect URIs:
   - `http://localhost:9090/auth/microsoft/callback`
   - `http://localhost:9090/delegates/callback/microsoft`
3. Grant API permission: `Calendars.ReadWrite`
4. Create a client secret

## Project Structure

```
src/main/java/com/smartscheduler/scheduler/
├── SchedulerApplication.java
├── configuration/
│   ├── CorsConfig.java
│   ├── GoogleCalendarConfig.java
│   └── MicrosoftCalendarConfig.java
├── controller/
│   ├── SchedulerController.java
│   └── DelegateController.java
├── model/
│   ├── TaskRequest.java
│   ├── TaskPriority.java
│   ├── CalendarEventResponse.java
│   └── DelegateRequest.java
└── service/
    ├── CalendarService.java
    ├── MicrosoftCalendarService.java
    ├── EventListingService.java
    ├── MicrosoftEventListingService.java
    ├── PriorityService.java
    ├── ProviderDetectionService.java
    ├── DelegateStore.java
    └── EmailService.java
```
