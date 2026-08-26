# Network Traffic Monitoring & Anomaly Dashboard — Backend

A Spring Boot backend that ingests raw `.pcap` network capture files, parses packet headers **manually at the byte level**, and tracks devices seen on the network, flags anomalies in real time, and pushes live updates to connected clients over WebSocket.

This repo is the backend/API. The frontend dashboard lives in a separate repo: [network-dashboard](https://github.com/ty-dwyer/network_traffic_frontend).

## Features

- **Custom byte-level `.pcap` parser** — reads raw capture files and manually extracts Ethernet/IPv4/TCP/UDP header fields (source/dest IP, ports, protocol, TCP flags) via bit-shifting and masking
- **Device tracking** — automatically identifies and tracks unique devices by source IP, recording first-seen/last-seen timestamps
- **Anomaly detection**
  - Flags new/unrecognized devices the first time they appear on the network
  - Flags devices communicating on non-standard ports (outside an allowed list), once per device
- **Real-time updates** — new packets and alerts are pushed to connected clients over STOMP-over-WebSocket as they're ingested, no polling required
- **File upload** — accepts `.pcap` files directly via HTTP upload, in addition to server-side file paths
- **JWT authentication** — stateless, token-based auth protecting all data endpoints; passwords hashed with BCrypt

## Tech Stack

- Java 25, Spring Boot
- Spring Data JPA + PostgreSQL
- Spring Security + JJWT (JSON Web Tokens)
- Spring WebSocket (STOMP)
- Maven

## Architecture

```
.pcap file (upload or server path)
        │
        ▼
   PcapParser            — reads raw bytes, extracts packet header fields manually
        │
        ▼
PacketIngestionService    — orchestrates parsing, device resolution, anomaly checks, persistence, broadcast
        │
   ┌────┼────────────┬───────────────┐
   ▼    ▼             ▼               ▼
Packet  Device      Alert      SimpMessagingTemplate
(saved) (found/     (created    (broadcasts new packets
         created)    if anomaly  and alerts over
                      detected)  /topic/packets, /topic/alerts)
        │
        ▼
   PostgreSQL (via Spring Data JPA)
        │
        ▼
  REST API (/packets, /alerts) + WebSocket (/ws)
```

**Layers:**
- `entities/` — JPA entities: `Packet`, `Device`, `Alert`, `Protocol` (enum), `User`
- `repositories/` — Spring Data JPA repositories
- `parser/` — `PcapParser`, the manual byte-level packet parser
- `services/` — `PacketIngestionService` (parsing → persistence → alerting → broadcast), `AuthService` (registration/login)
- `controllers/` — REST endpoints
- `configs/` — Spring configuration (WebSocket, CORS, Security, JWT filter, password encoding)
- `dto/` — request/response shapes for the API, decoupled from JPA entities
- `util/` — `JwtUtil`, token generation/validation

## Setup

### Prerequisites
- Java 25 (or adjust `pom.xml` for your installed version)
- PostgreSQL running locally
- Maven (or use the bundled `./mvnw` wrapper)

### 1. Create the database
```sql
CREATE DATABASE networkDB;
```

### 2. Configure `src/main/resources/application.properties`
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/networkDB
spring.datasource.username=postgres
spring.datasource.password=yourpassword
spring.jpa.hibernate.ddl-auto=update
```

### 3. Run
```bash
./mvnw spring-boot:run
```
The API starts on `http://localhost:8080`.

## API Overview

| Method | Endpoint | Auth required | Description |
|---|---|---|---|
| POST | `/auth/register` | No | Create a new user account |
| POST | `/auth/login` | No | Log in, returns a JWT |
| POST | `/ingest?path=...` | Yes | Parse and ingest a `.pcap` file already on the server |
| POST | `/ingest/upload` | Yes | Upload and ingest a `.pcap` file directly |
| GET | `/packets` | Yes | List all ingested packets |
| GET | `/alerts` | Yes | List all generated alerts |
| WS | `/ws` (STOMP) | — | Subscribe to `/topic/packets` and `/topic/alerts` for live updates |

Protected endpoints require an `Authorization: Bearer <token>` header, obtained from `/auth/login`.

### Example: register, log in, and ingest a file

```bash
curl -X POST localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"password123"}'

curl -X POST localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"password123"}'
# → returns a JWT

curl -X POST localhost:8080/ingest/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@sample.pcap"
```

## Notable Design Decisions

- **Manual packet parsing over a library (e.g. pcap4j)** — chosen to demonstrate understanding of the IPv4/TCP/UDP header formats at the byte level, rather than relying on a library to do the interpretation.
- **IPv4-only, no IP options support** — scoped deliberately to keep the parser's byte offsets fixed and predictable; IPv6 and Ethernet-layer (ARP) parsing are natural follow-up extensions.
- **DTOs separate from JPA entities** — request/response shapes (`RegisterRequest`, `LoginRequest`) are decoupled from database entities, so the API contract doesn't leak internal schema details.
- **Stateless JWT auth** — no server-side sessions; every request authenticates independently via its token, matching the REST/WebSocket architecture.

## Known Limitations

- The JWT signing key is generated randomly at startup, so all issued tokens are invalidated on server restart. A production deployment would use a fixed secret from configuration/environment variables.
- Currently supports IPv4 traffic only; IPv6 and Ethernet-layer protocols (e.g. ARP) are not parsed.
- Traffic-volume ("spike") anomaly detection is not yet implemented; the current anomaly rules are new-device and unusual-port detection.
