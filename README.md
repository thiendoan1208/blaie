# Blaie: AI-Powered Note-Taking & Task Management Application

Blaie is an AI-driven, cross-platform productivity application designed to eliminate the friction of organizing daily life. Users can instantly capture unstructured inputs (voice memos, photos, and text notes) and a background AI workflow automatically categorizes and structures them into actionable tasks, notes, calendar events, and reminders.

The system features a **Modular Monolith backend (Java 25, Spring Boot)**, an **interactive 3D web frontend (Next.js, Three.js)**, and a **cross-platform mobile client (React Native)**.


## 🏛️ System Architecture

The project is structured following the **C4 Software Architecture Model**, divided into decoupled containers:

                               +-------------------+
                               | User (Web/Mobile) |
                               +-------------------+
                                   |           |
               Captures voice/img  |           | Renders interactive
               saves in <2s        v           v 
           +-------------------------+       +-------------------------+
           |   React Native Client   |       |   Next.js 16 Client     |
           +-------------------------+       +-------------------------+
                        |                                 |
                        +----------------+----------------+
                                         | HTTPS APIs
                                         v
                         +-------------------------------+
                         |   Spring Boot Backend API     |
                         | (Spring Modulith / Java 25)   |
                         +-------------------------------+
                           |      |               |      |
             Read/Write    |      | Save Files    |      | Enqueue Job
             Data          v      v               v      v
              +--------------+  +-----------+  +-----------+
              | PostgreSQL   |  |  AWS S3   |  |   Redis   | (Job Queue / Cache)
              | (Primary DB) |  | (Storage) |  +-----------+
              +--------------+  +-----------+        |
                                                     | Consume Job
                                                     v
                                       +---------------------------+
                                       |   Background AI Worker    |
                                       +---------------------------+
                                          |          |          |
                           Call LLM APIs  v          v          v Send FCM Reminders
                                    +---------+  +---------+  +---------+
                                    | OpenAI  |  | Whisper |  | APNs/FCM|
                                    +---------+  +---------+  +---------+


## 🚀 Key Features

### 1. Asynchronous Ingestion Pipeline
- **Instant Input Capture:** Instantly captures voice transcripts, camera photos, and raw text.
- **Low-Latency Ingestion:** The Ingestion API stores references and responds in under 2 seconds with an HTTP `202 Accepted` status, immediately assigning a `PROCESSING` state.
- **Asynchronous Workers:** Event-driven background workers asynchronously transcribe audio, parse images (OCR/Vision), and extract structured metadata.

### 2. Spring Modulith & Domain-Driven Design (DDD)
- **Modular Monolith Backend:** Structured using **Spring Modulith** to maintain strictly isolated packages (`auth`, `authz`, `capture`, `processing`, `tasks`, `notes`).
- **Architectural Guardrails:** Enforces runtime boundaries between domain layers, making the system highly testable, maintainable, and ready to scale.

### 3. Interactive 3D Knowledge Graph
- **3D Interactive Web Canvas:** Built with **Three.js** and **React Three Fiber (R3F)** on the web frontend.
- **Visual Mind Map:** Represents notes, tasks, and calendar events as interactive 3D nodes connected by logical relationships, enabling users to visually explore their "second brain."

### 4. Advanced Testing & Validation
- **Database Integration Tests:** Uses **Testcontainers** to spin up clean Docker containers for PostgreSQL and Redis during integration testing.
- **Architectural Rules Verification:** Incorporates **ArchUnit** to programmatically verify architectural compliance and prevent illegal dependencies between modules.
- **E2E and Frontend Validation:** Employs **Playwright** and **Vitest** to validate web interface behaviors and E2E system flows.
