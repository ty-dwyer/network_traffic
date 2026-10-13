# Network Traffic Monitoring & Anomaly Dashboard — Backend

A Spring Boot API that parses classic PCAP captures, tracks observed devices, detects suspicious traffic patterns, stores packets and alerts in PostgreSQL, and broadcasts live updates over STOMP/WebSocket. The frontend lives in the separate [network dashboard repository](https://github.com/ty-dwyer/network_traffic_frontend).

## Features

- Custom byte-level classic PCAP parser supporting both byte orders, microsecond/nanosecond timestamps, Ethernet VLAN tags, IPv4 options, TCP, and UDP.
- Bounded-memory capture parsing and packet persistence in batches of 500.
- Asynchronous ingestion jobs with status, packet count, timestamps, and failure messages.
- Device tracking and configurable anomaly rules for traffic spikes, port scans, SYN floods, large packets, DNS volume, and unusual destination ports.
- JWT authentication, BCrypt password hashing, and live packet/alert broadcasts over STOMP.
- Filterable, paginated packet and alert search endpoints, alert acknowledgement, and device trust management.
- Flyway database migrations and Swagger UI documentation.

## Tech stack

Java 25, Spring Boot 4, Spring Data JPA, PostgreSQL, Spring Security, JJWT, Spring WebSocket, Flyway, Springdoc OpenAPI, and Maven.

## Setup

Prerequisites: Java 25 and PostgreSQL. Create the database:

```sql
CREATE DATABASE networkDB;
```

Set the database connection and a Base64 JWT secret that decodes to at least 32 bytes:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/networkDB
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
export JWT_SECRET="$(openssl rand -base64 32)"
```

The database URL, username, and password default to local development values. `JWT_SECRET` is required; keep it stable between restarts so existing tokens remain valid. Optional settings include `JWT_EXPIRATION_MS` and these anomaly variables:

```text
ANOMALY_WINDOW_SECONDS=10
ANOMALY_COOLDOWN_SECONDS=60
ANOMALY_TRAFFIC_SPIKE_PACKET_COUNT=100
ANOMALY_PORT_SCAN_UNIQUE_PORTS=20
ANOMALY_SYN_FLOOD_SYN_COUNT=50
ANOMALY_LARGE_PACKET_BYTES=1400
ANOMALY_DNS_VOLUME_PACKET_COUNT=50
ANOMALY_ALLOWED_DESTINATION_PORTS=80,443,53,22
```

Start the API:

```bash
./mvnw spring-boot:run
```

Flyway applies migrations on startup; Hibernate validates the resulting schema. Existing non-empty databases without Flyway history are baselined at version 1 before later migrations are applied.

## API

All endpoints are authenticated except registration, login, and API docs. Use `Authorization: Bearer <token>` for protected REST endpoints.

| Method | Endpoint | Description |
|---|---|---|
| POST | `/auth/register` | Create an account |
| POST | `/auth/login` | Log in and receive a JWT |
| POST | `/ingest/upload` | Upload a PCAP and start an asynchronous job; returns `202 Accepted` and a `Location` header |
| POST | `/ingest?path=...` | Start a job for a PCAP already on the server |
| GET | `/ingestion-jobs/{id}` | Get job status and progress |
| GET | `/ingestion-jobs?page=0&size=20` | Page through recent jobs |
| GET | `/packets/search` | Filter/page packets by `sourceIp`, `destIp`, `protocol`, `from`, and `to` |
| GET | `/alerts/search` | Filter/page alerts by `type`, `acknowledged`, `deviceIp`, `from`, and `to` |
| PATCH | `/alerts/{id}/acknowledge` | Acknowledge an alert |
| GET | `/devices?page=0&size=50&ip=10.0` | Page through devices, optionally filter by IP |
| PATCH | `/devices/{id}/trust` | Set device trust with `{"trusted": true}` or `false` |
| GET | `/packets`, `/alerts` | Legacy unpaged lists retained for dashboard compatibility |
| WS | `/ws` | STOMP endpoint; subscribe to `/topic/packets` and `/topic/alerts` |
| GET | `/swagger-ui.html` | Interactive API documentation |

Search endpoints default to 50 results per page and cap page size at 100. `from` and `to` accept ISO-8601 instants, for example `2026-01-01T00:00:00Z`.

### Example

```bash
curl -X POST localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"password123"}'

curl -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"password123"}'

curl -X POST localhost:8080/ingest/upload \
  -H 'Authorization: Bearer <token>' \
  -F 'file=@sample.pcap'

curl localhost:8080/ingestion-jobs/1 \
  -H 'Authorization: Bearer <token>'
```

## Design notes and limitations

- Packet decoding is implemented manually to demonstrate understanding of the wire format.
- Supports classic PCAP over Ethernet/IPv4. IPv6, PCAPNG, and non-Ethernet link types are not supported; non-IPv4 Ethernet frames are skipped. Captured frames above 16 MiB are rejected.
- Legacy `/packets` and `/alerts` endpoints return all records for compatibility. Use `/search` for bounded result sets.
- Authenticated users currently share the same packet, device, and alert data; the schema does not isolate data by account.
- The frontend currently displays the upload response text. The response still indicates the accepted job; poll the `Location` URL or `/ingestion-jobs/{id}` for progress.
