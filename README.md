# 🗳️ SUREVOTE — Secure Electronic Voting Platform

> [![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=java&logoColor=white)](https://www.java.com)
> [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-6DB33F?logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
> [![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql&logoColor=white)](https://www.postgresql.org)
> [![License](https://img.shields.io/badge/License-MIT-blue)](./LICENSE)
> [![Status](https://img.shields.io/badge/Status-Production%20Ready-brightgreen)](.)
> [![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-2088FF?logo=github-actions&logoColor=white)](./github/workflows)

**A production-grade, cryptographically anonymous electronic voting platform** with double-barrier anonymity, two-factor authentication, and complete audit trails.

📍 Built as a *Fil Rouge* (capstone) project at **YouCode — UM6P**

---

## ✨ Key Features

| Feature | Description |
|---------|-------------|
| 🔐 **Double-Barrier Anonymity** | Voter registry (`Emargement`) completely separated from ballot (`Vote`) → impossible to link voter to vote |
| 🔑 **Two-Factor Authentication** | Email-based OTP (6-digit, 5-min expiry) before voter ballot access |
| 🎫 **Cryptographic Receipts** | Every voter receives a UUID proof-of-participation (verified freely on public endpoint) |
| 📊 **Vote Integrity** | SHA-256 checksums per vote record + full table integrity verification |
| 🛡️ **RBAC & Rate Limiting** | Three roles (ADMIN, ELECTEUR, OBSERVATEUR) with Bucket4j endpoint protection |
| ⏰ **Election State Machine** | Automated lifecycle: BROUILLON → PLANIFIEE → OUVERTE → CLOTUREE → PUBLIEE |
| 📝 **Immutable Audit Trail** | All mutations logged via AOP `@Auditable` aspect (compliance-ready) |
| 🚀 **Stateless JWT Auth** | 1-hour access token + 7-day refresh token, no server session store |
| 📦 **Docker Ready** | Docker Compose setup for local dev, PostgreSQL included |
| 📡 **Real-time Metrics** | Platform dashboard: total votes, users, elections, participation |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     Angular SPA (port 4200)                             │
└──────────────────────────┬──────────────────────────────────────,────────┘
                           │ HTTPS / JWT Bearer
                           ▼
    ┌──────────────────────────────────────────────────────────┐
    │          Spring Boot API (port 8080)                     │
    ├──────────────────────────────────────────────────────────┤
    │ Controller Layer (11 REST endpoints)                    │
    │ ├─ AuthController                                       │
    │ ├─ VoteController                                       │
    │ ├─ AdminElectionController                              │
    │ ├─ ObserverController                                   │
    │ └─ ... (PublicElection, AdminCandidat, etc)            │
    ├──────────────────────────────────────────────────────────┤
    │ Security Layer                                           │
    │ ├─ JwtAuthenticationFilter (Bearer token validation)    │
    │ ├─ Spring Security 7.x filter chain                     │
    │ ├─ @PreAuthorize role checks                            │
    │ └─ Bucket4j rate limiting (sensitive endpoints)         │
    ├──────────────────────────────────────────────────────────┤
    │ Service Layer (15 business logic services)              │
    │ ├─ VoteService (SERIALIZABLE transaction)              │
    │ ├─ AuthService (2FA, token refresh)                    │
    │ ├─ ElectionSchedulerService (@Scheduled)              │
    │ ├─ ElectionService (state machine validation)          │
    │ └─ ... (OTP, Password Reset, Audit, Results, etc)      │
    ├──────────────────────────────────────────────────────────┤
    │ AOP / Cross-Cutting Concerns                            │
    │ ├─ @Auditable aspect (immutable logging)               │
    │ ├─ AspectJ-based audit trail capture                   │
    │ └─ Exception handlers (@RestControllerAdvice)          │
    ├──────────────────────────────────────────────────────────┤
    │ Domain Layer (12 JPA entities)                          │
    │ ├─ Utilisateur (SINGLE_TABLE inheritance)              │
    │ ├─ Vote (NO user FK - security)                        │
    │ ├─ Emargement (voter registry - separated)             │
    │ ├─ Election (state machine)                            │
    │ └─ ... (Candidat, CollegeElectoral, LogAudit, etc)     │
    ├──────────────────────────────────────────────────────────┤
    │ Data Access Layer (9 Spring Data repositories)          │
    │ └─ Optimized queries with @Query annotations            │
    └──────────────────────────┬───────────────────────────────┘
                               │
                               ▼
                ┌────────────────────────────┐
                │   PostgreSQL 16            │
                ├────────────────────────────┤
                │ • Emargement (who voted)   │
                │ • Vote (what voted)        │
                │ • Election (lifecycle)     │
                │ • Utilisateur (users)      │
                │ • LogAudit (immutable)     │
                │ • ... (9 tables total)     │
                └────────────────────────────┘
```

---

## 🛠️ Technology Stack

### Backend Framework
- **Spring Boot** 4.0.3 — REST API framework
- **Spring Security** 7.x — Authentication & authorization
- **Spring Data JPA** — Hibernate ORM
- **Spring Cache** — Caching abstraction
- **Spring Retry** — Automatic retry on lock timeouts

### Database & Persistence
- **PostgreSQL** 16 — Production database
- **Liquibase** — Database schema versioning
- **Hibernate** 7.x — JPA ORM with SERIALIZABLE isolation
- **H2** — In-memory testing database

### Security & Authentication
- **JJWT** 0.12.6 — JWT token generation/validation (HS256)
- **Spring Security** BCrypt — Password hashing (12 rounds)
- **Custom 2FA OTP** — Email-based TOTP flow

### API & Documentation
- **springdoc-openapi** 2.8.3 — Swagger UI + OpenAPI 3.0
- **Springdoc** — Interactive API documentation

### Performance & Resilience
- **Bucket4j** 7.6.0 — Rate limiting
- **AspectJ** — AOP for audit logging
- **Caching** — Spring Cache abstraction

### Code Generation & Quality
- **MapStruct** 1.6.3 — Compile-time DTO mapping
- **Lombok** — Boilerplate reduction
- **JaCoCo** 0.8.12 — Code coverage (70% minimum enforced)

### Testing
- **JUnit 5** — Latest test framework
- **Spring Test** — MockMvc for integration tests
- **Spring Security Test** — Authentication mocking
- **Mockito** — Unit test mocking

### Build & DevOps
- **Maven** 3.x — Build automation
- **Docker** — Containerization
- **GitHub Actions** — CI/CD pipeline

---

## ⚡ Quick Start (Local Development)

### Prerequisites
- **Java 17+** (Eclipse Temurin or OpenJDK)
- **Maven 3.8+**
- **PostgreSQL 12+**
- **Git**

### 1. Clone & Setup
```bash
git clone https://github.com/HamzaBraik01/surevote-dlt-api.git
cd surevote-dlt-api
```

### 2. Configure Environment Variables
Create a `.env` file (Git-ignored) in the root directory:

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/surevote_db
DB_USER=surevote_user
DB_PASSWORD=secure_password_here

# JWT
JWT_SECRET=your-32-character-hex-string-here-12345678
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000

# Email (Gmail SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-gmail@gmail.com
MAIL_PASSWORD=your-app-specific-password

# Security
SECURITY_USER_NAME=admin
SECURITY_USER_PASSWORD=admin123

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:4200

# File Upload
FILE_UPLOAD_DIR=./uploads
```

> ⚠️ **For Gmail**: Use an [App-Specific Password](https://support.google.com/accounts/answer/185833), NOT your main Gmail password.

### 3. Create PostgreSQL Database
```bash
# On your PostgreSQL server:
CREATE DATABASE surevote_db;
CREATE USER surevote_user WITH ENCRYPTED PASSWORD 'secure_password_here';
GRANT ALL PRIVILEGES ON DATABASE surevote_db TO surevote_user;
```

### 4. Build & Run
```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/surevote-0.0.1-SNAPSHOT.jar

# Or run directly from Maven:
mvn spring-boot:run
```

### 5. Verify API is Running
```bash
curl http://localhost:8080/swagger-ui.html
```

✅ Open browser → `http://localhost:8080/swagger-ui.html`

---

## 🐳 Docker Compose Setup

### Quick Start with Docker
```bash
# Install Docker & Docker Compose (if not already installed)
# https://docs.docker.com/get-docker/
# https://docs.docker.com/compose/install/

# Start everything
docker-compose up -d

# Check logs
docker-compose logs -f surevote-api

# Stop everything
docker-compose down
```

### Docker Compose Services
- **surevote-api** (port 8080) — Spring Boot application
- **postgres** (port 5432) — PostgreSQL database
- **pgadmin** (port 5050) — Database UI (optional)

### Default Credentials (Development Only)
| Service | Username | Password |
|---------|----------|----------|
| **API** | admin | admin |
| **PGAdmin** | pgadmin4@pgadmin.org | admin |

---

## 📡 API Reference

### Base URL
```
http://localhost:8080/api
```

### Authentication Endpoints

#### 1. Register a New Voter
```http
POST /auth/register
Content-Type: application/json

{
  "email": "voter@example.com",
  "password": "SecurePassword123!",
  "firstName": "John",
  "lastName": "Doe"
}

Response: 201 Created
{
  "id": "uuid-here",
  "email": "voter@example.com",
  "roles": ["ELECTEUR"],
  "emailVerified": false,
  "otpRequired": true
}
```

#### 2. Login
```http
POST /auth/login
Content-Type: application/json

{
  "email": "voter@example.com",
  "password": "SecurePassword123!"
}

Response: 200 OK
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 3600000,
  "requiresOtp": true
}
```

#### 3. Verify 2FA OTP
```http
POST /auth/verify-otp
Content-Type: application/json
Authorization: Bearer <accessToken>

{
  "otp": "123456"
}

Response: 200 OK
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 3600000,
  "message": "OTP verified successfully"
}
```

### Public Election Endpoints (No Auth)

#### 4. List Published Elections
```http
GET /public/elections?status=PUBLIEE&page=0&size=20
Response: 200 OK
[
  {
    "id": "election-id",
    "title": "2024 Presidential Election",
    "status": "PUBLIEE",
    "startDate": "2024-01-01T08:00:00Z",
    "endDate": "2024-01-01T18:00:00Z",
    "candidates": 5,
    "totalVotes": 1250
  }
]
```

#### 5. View Election Candidates
```http
GET /public/elections/{electionId}/candidates
Response: 200 OK
[
  {
    "id": "candidate-id",
    "firstName": "John",
    "lastName": "Smith",
    "party": "Democratic Party",
    "photoUrl": "/uploads/candidates/john-smith.jpg",
    "biography": "..."
  }
]
```

#### 6. Verify Vote Receipt (Anyone Can Use)
```http
GET /public/receipts/{receiptUuid}/verify
Response: 200 OK
{
  "receiptUuid": "550e8400-e29b-41d4-a716-446655440000",
  "electionId": "election-id",
  "electionTitle": "2024 Presidential Election",
  "verifiedAt": "2024-01-01T15:30:00Z",
  "message": "✓ Your vote was successfully counted!"
}
```

### Voter Voting Endpoints (Auth Required)

#### 7. Cast a Vote
```http
POST /vote/cast
Content-Type: application/json
Authorization: Bearer <accessToken>

{
  "electionId": "election-id",
  "selectedCandidateId": "candidate-id"
}

Response: 201 Created
{
  "receiptUuid": "550e8400-e29b-41d4-a716-446655440000",
  "electionId": "election-id",
  "message": "Vote submitted successfully!",
  "toVerify": "http://api/public/receipts/550e8400.../verify"
}
```

#### 8. Get Voted Elections
```http
GET /vote/my-elections
Authorization: Bearer <accessToken>

Response: 200 OK
[
  {
    "electionId": "election-id",
    "electionTitle": "2024 Presidential Election",
    "votedAt": "2024-01-01T15:30:00Z",
    "receiptUuid": "550e8400-e29b-41d4-a716-446655440000"
  }
]
```

### Admin Election Management (ADMIN Role)

#### 9. Create Election
```http
POST /admin/elections
Content-Type: application/json
Authorization: Bearer <adminAccessToken>

{
  "title": "2024 Presidential Election",
  "description": "Choose the next president",
  "startDate": "2024-01-01T08:00:00Z",
  "endDate": "2024-01-01T18:00:00Z",
  "collegeElectoralId": null
}

Response: 201 Created
{
  "id": "election-id",
  "title": "2024 Presidential Election",
  "status": "BROUILLON"
}
```

#### 10. Publish Election Results
```http
POST /admin/elections/{electionId}/publish
Authorization: Bearer <adminAccessToken>

Response: 200 OK
{
  "id": "election-id",
  "status": "PUBLIEE",
  "results": {
    "totalVotes": 1250,
    "candidates": [
      {
        "candidateId": "candidate-1",
        "name": "John Smith",
        "voteCount": 650
      }
    ]
  }
}
```

### Admin Candidate Management

#### 11. Add Candidate to Election
```http
POST /admin/candidates
Content-Type: application/json
Authorization: Bearer <adminAccessToken>

{
  "electionId": "election-id",
  "firstName": "John",
  "lastName": "Smith",
  "party": "Democratic Party",
  "biography": "Long biography text..."
}

Response: 201 Created
{
  "id": "candidate-id",
  "firstName": "John",
  "lastName": "Smith"
}
```

#### 12. Upload Candidate Photo
```http
POST /file-upload/{candidateId}
Content-Type: multipart/form-data
Authorization: Bearer <adminAccessToken>

[Binary image data]

Response: 200 OK
{
  "candidateId": "candidate-id",
  "photoUrl": "/uploads/candidates/john-smith-12345.jpg"
}
```

### Observer Endpoints (OBSERVATEUR Role)

#### 13. Platform Metrics
```http
GET /observer/metrics
Authorization: Bearer <observerAccessToken>

Response: 200 OK
{
  "totalUsers": 5420,
  "totalVotes": 3210,
  "totalElections": 12,
  "ongoingElections": 2,
  "averageParticipationRate": 59.3
}
```

#### 14. Audit Trail
```http
GET /observer/audit-logs?page=0&size=20
Authorization: Bearer <observerAccessToken>

Response: 200 OK
[
  {
    "id": "log-id",
    "operation": "VOTE_SUBMITTED",
    "timestamp": "2024-01-01T15:30:00Z",
    "userId": "user-id",
    "details": "Vote submitted in election: 2024 Election"
  }
]
```

---

## 🔐 Security Design — Double Barrier Pattern

### The Problem: Voter Anonymity

Traditional electronic voting systems face a critical paradox:
- ❌ If a system stores `voter → ballot`, DBA (or attacker) can link voter to their vote
- ❌ If a system stores only `ballot` (no voter info), how do you prevent double voting?

### The Solution: Double-Barrier Architecture

SureVote **completely separates** voter registry from actual ballots:

#### Database Schema
```sql
-- Table 1: WHO VOTED (linked to voter)
CREATE TABLE emargement (
  id UUID PRIMARY KEY,
  voter_id UUID NOT NULL REFERENCES utilisateur(id),
  election_id UUID NOT NULL REFERENCES election(id),
  voted_timestamp TIMESTAMP NOT NULL,
  receipt_uuid UUID NOT NULL UNIQUE,
  ip_address INET,
  UNIQUE(voter_id, election_id)  -- Prevent double voting
);

-- Table 2: WHAT WAS VOTED (NO voter link)
CREATE TABLE vote (
  id UUID PRIMARY KEY,
  election_id UUID NOT NULL REFERENCES election(id),
  candidate_id UUID NOT NULL REFERENCES candidat(id),
  voted_timestamp TIMESTAMP NOT NULL,
  integrity_checksum VARCHAR(64) NOT NULL,
  -- NO voter_id field — cryptographically impossible to link
);

-- Table 3: VOTER REGISTRY (for verification)
CREATE TABLE utilisateur (
  id UUID PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  first_name VARCHAR(100),
  last_name VARCHAR(100),
  role ENUM ('ADMIN', 'ELECTEUR', 'OBSERVATEUR'),
  status ENUM ('ACTIF', 'INACTIF', 'SUSPENDU')
);
```

#### Transaction Flow (Atomic)

```
1. Voter submits ballot in transaction:

   BEGIN TRANSACTION (SERIALIZABLE isolation level)

   A) INSERT INTO emargement(voter_id, election_id, receipt_uuid, ...)
      └─ Records: "Voter #12345 voted in Election XYZ"
      └─ Generates and returns UUID receipt to voter

   B) INSERT INTO vote(election_id, candidate_id, integrity_checksum, ...)
      └─ Records: "Someone voted for Candidate ABC in Election XYZ"
      └─ No voter_id field — completely anonymous

   C) Both inserts succeed or both rollback together

   COMMIT TRANSACTION

2. Result:
   ✓ Voter gets receipt UUID (can verify vote was counted)
   ✓ Emargement table proves they voted (only they see their UUID)
   ✓ Vote table proves ballot was counted (cannot be linked back)
   ✓ Even with database access, NO query can join voter → ballot
```

#### Security Guarantees

| Attacker | Can Do | Cannot Do |
|----------|--------|-----------|
| **Public (no access)** | Verify participation (UUID) | See any votes linked to voters |
| **Logged-in voter** | Verify their own vote (UUID) | See other voters' choices |
| **Database admin (DBA)** | See all votes & all voters | Link voter to their vote (by design) |
| **DBAnalytics tools** | Query vote statistics | Perform JOIN emargement ↔ vote |
| **Hacker (DB breach)** | See tables separately | Link voter to ballot (no FK exists) |

---

## 📅 Election Lifecycle (State Machine)

### States

```
┌──────────────────────────────────────────────────────────────────┐
│                    ELECTION LIFECYCLE                            │
├──────────────────────────────────────────────────────────────────┤

START
  │
  ▼
[1] BROUILLON (Draft)
  │ Admin creates & configures election
  │ Voters cannot access
  │ Candidates can be added
  │ [Admin action: planifier()] OR [Scheduler @ dateDebut]
  │
  ▼
[2] PLANIFIEE (Scheduled)
  │ Election date is set
  │ Voters can see election info
  │ Voting portal not yet open
  │ [Scheduler @ dateDebut OR Admin: ouvrir()]
  │
  ▼
[3] OUVERTE (Open for Voting)
  │ Voting is LIVE
  │ Voters can submit ballots
  │ Receipts are generated
  │ Emargement & Vote records created
  │ [Scheduler @ dateFin OR Admin: cloturer()]
  │
  ▼
[4] CLOTUREE (Closed)
  │ Voting stopped
  │ Vote counting in progress
  │ Results not yet public
  │ [Admin: publier()]
  │
  ▼
[5] PUBLIEE (Published)
  │ Results publicly visible
  │ Vote counts by candidate shown
  │ Electoral archive state
  │ Read-only (no further changes)
  │
  END

Transitions:
  • Automatic: Scheduler checks every 60 seconds
  • Manual: Admin can force transition
  • Validation: State guards prevent invalid operations
```

### Example Flow

```python
# Day 1, 8 AM: Create election draft
POST /admin/elections
  title: "2024 Presidential Election"
  startDate: 2024-01-15 08:00:00
  endDate: 2024-01-15 18:00:00
  status: BROUILLON

# Day 2: Schedule it (move to PLANIFIEE)
PUT /admin/elections/{id}/planifier
  # System sets status to PLANIFIEE

# Day 15, 8 AM: Scheduler fires, opens voting
# (status automatically → OUVERTE)

# Day 15, 6 PM: Scheduler fires, closes voting
# (status automatically → CLOTUREE)

# Day 16: Admin publishes results
PUT /admin/elections/{id}/publier
  # System calculates vote counts
  # status → PUBLIEE
  # Results visible to public

# Anytime: Observers can request audit trail
GET /observer/audit-logs?election={id}
```

---

## 🔑 Authentication Flow

### Registration → Login → Voting

```
┌─────────────────────────────────────────────────────────────────┐
│                    AUTHENTICATION FLOW                           │
├─────────────────────────────────────────────────────────────────┤

1. REGISTRATION
   ┌──────────────────────────────────────────┐
   │ POST /auth/register                      │
   │ {                                        │
   │   "email": "voter@example.com",          │
   │   "password": "SecurePassword123!",      │
   │   "firstName": "John",                   │
   │   "lastName": "Doe"                      │
   │ }                                        │
   └────────────┬─────────────────────────────┘
                │
                ▼
   ✓ Email validation
   ✓ Password strength check
   ✓ BCrypt-12 hash & store
   ✓ Account created as ELECTEUR role
   ✓ Status: INACTIF (pending email verification)

   Response: 201 Created
   {
     "id": "user-id",
     "email": "voter@example.com",
     "role": "ELECTEUR",
     "emailVerified": false,
     "message": "Check your email to verify account"
   }

─────────────────────────────────────────────────────────────────

2. LOGIN ATTEMPT
   ┌──────────────────────────────────────────┐
   │ POST /auth/login                         │
   │ {                                        │
   │   "email": "voter@example.com",          │
   │   "password": "SecurePassword123!"       │
   │ }                                        │
   └────────────┬─────────────────────────────┘
                │
                ▼
   ✓ Email exists?
   ✓ Password matches BCrypt hash?
   ✓ Email verified?
   ✓ Account not suspended?
   ✓ Generate JWT access token (1h)
   ✓ Generate JWT refresh token (7d)

   Response: 200 OK
   {
     "accessToken": "eyJhbGciOiJIUzI1NiIsInR5...",
     "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5...",
     "expiresIn": 3600000,
     "requiresOtp": true,
     "message": "OTP sent to voter@example.com"
   }

─────────────────────────────────────────────────────────────────

3. 2FA OTP VERIFICATION
   ┌──────────────────────────────────────────┐
   │ POST /auth/verify-otp                    │
   │ Authorization: Bearer <accessToken>      │
   │ {                                        │
   │   "otp": "123456"                        │
   │ }                                        │
   └────────────┬─────────────────────────────┘
                │
                ▼
   ✓ OTP valid (6 digits)?
   ✓ OTP not expired (5 min)?
   ✓ OTP matches database?
   ✓ Mark user as OTP_VERIFIED
   ✓ Issue AUTHENTICATED access token

   Response: 200 OK
   {
     "accessToken": "eyJhbGciOiJIUzI1NiIsInR5...",
     "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5...",
     "expiresIn": 3600000,
     "otpVerified": true,
     "message": "Authentication complete!"
   }

─────────────────────────────────────────────────────────────────

4. AUTHORIZED API CALLS (with valid JWT)
   ┌──────────────────────────────────────────┐
   │ GET /vote/my-elections                   │
   │ Authorization: Bearer <accessToken>      │
   └────────────┬─────────────────────────────┘
                │
                ▼
   ✓ Extract JWT from Authorization header
   ✓ Verify signature (HS256 + secret key)
   ✓ Check expiration (not past 1h?)
   ✓ Load user claims from token
   ✓ Check role-based access (@PreAuthorize)

   Response: 200 OK
   [...]

─────────────────────────────────────────────────────────────────

5. TOKEN REFRESH (when access token expires)
   ┌──────────────────────────────────────────┐
   │ POST /auth/refresh                       │
   │ {                                        │
   │   "refreshToken": "eyJhbGciOiJIUzI1..."  │
   │ }                                        │
   └────────────┬─────────────────────────────┘
                │
                ▼
   ✓ Verify refresh token valid (not in blacklist)
   ✓ Verify refresh token not expired (7 days)
   ✓ Issue new access token (1h)

   Response: 200 OK
   {
     "accessToken": "eyJhbGciOiJIUzI1NiIsInR5...",
     "expiresIn": 3600000
   }

─────────────────────────────────────────────────────────────────

6. LOGOUT
   ┌──────────────────────────────────────────┐
   │ POST /auth/logout                        │
   │ Authorization: Bearer <accessToken>      │
   └────────────┬─────────────────────────────┘
                │
                ▼
   ✓ Add current token to RevokedToken blacklist
   ✓ Mark tokens as revoked

   Response: 200 OK
   {
     "message": "Logged out successfully"
   }

   Future requests with this token → 401 Unauthorized

```

---

## 🧪 Running Tests

### Run All Tests
```bash
mvn clean test
```

### Coverage Report (70% minimum enforced)
```bash
mvn clean test jacoco:report
# Open: target/site/jacoco/index.html
```

### Run Specific Test Class
```bash
mvn test -Dtest=VoteServiceTests
```

### Test Categories

| Type | Count | Scope |
|------|-------|-------|
| **Unit Tests** | 22 | Service logic, mapping, security |
| **Integration Tests** | 15 | Controller endpoints, DB interaction |
| **Security Tests** | 3 | JWT, authentication, authorization |
| **Domain Tests** | 4 | Entity validation, enums |

---

## 🚀 Deployment

### Production Checklist

- [ ] Set strong `JWT_SECRET` (32+ random hex)
- [ ] Use production PostgreSQL (not localhost)
- [ ] Enable HTTPS (TLS certificate)
- [ ] Set `CORS_ALLOWED_ORIGINS` to frontend domain
- [ ] Use Gmail App-Specific Password (not main password)
- [ ] Set `spring.jpa.hibernate.ddl-auto=validate` (DO NOT use `update` in prod)
- [ ] Run Liquibase migrations explicitly: `mvn liquibase:update`
- [ ] Enable Spring Security CSRF (if not REST API only)
- [ ] Set up log aggregation (CloudWatch, ELK, etc.)
- [ ] Monitor rate limiting metrics
- [ ] Regular backup schedule for PostgreSQL

### Docker Production Build
```bash
docker build -f Dockerfile -t surevote:latest .
docker run -e SPRING_DATASOURCE_URL=... -e JWT_SECRET=... surevote:latest
```

---

## 📚 Project Structure

```
surevote-dlt-api/
├── src/
│   ├── main/
│   │   ├── java/ma/youcode/surevote/
│   │   │   ├── annotation/              # @Auditable
│   │   │   ├── aspect/                  # AOP audit logging
│   │   │   ├── config/                  # Security, OpenAPI, Scheduling
│   │   │   ├── controller/              # 11 REST endpoints
│   │   │   ├── domain/
│   │   │   │   ├── entity/              # 12 JPA entities
│   │   │   │   └── enums/               # Roles, election status
│   │   │   ├── dto/
│   │   │   │   ├── request/             # Incoming payloads
│   │   │   │   └── response/            # Outgoing payloads
│   │   │   ├── exception/               # Custom exceptions
│   │   │   ├── mapper/                  # MapStruct DTOs
│   │   │   ├── repository/              # 9 Spring Data repos
│   │   │   ├── security/                # JWT, authentication
│   │   │   └── service/                 # 15 business services
│   │   └── resources/
│   │       ├── application.properties   # Config (env vars)
│   │       ├── db/changelog/            # Liquibase migrations
│   │       └── templates/               # Email templates
│   └── test/java/                       # 44 test classes
├── pom.xml                              # Maven build config
├── docker-compose.yml                   # Dev environment
├── Dockerfile                           # Container image
├── README.md                            # This file
└── DEVELOPMENT_PLAN.md                  # Commit history
```

---

## ⚙️ Environment Variables Reference

```bash
# Database Connection
SPRING_DATASOURCE_URL=jdbc:postgresql://hostname:5432/surevote_db
DB_USER=surevote_user
DB_PASSWORD=your_strong_password

# JWT Configuration
JWT_SECRET=your-32-character-hex-string-minimum-32-chars
JWT_EXPIRATION=3600000                              # 1 hour (ms)
JWT_REFRESH_EXPIRATION=604800000                    # 7 days (ms)

# Email Configuration (Gmail SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-specific-password            # NOT main password

# Spring Security
SECURITY_USER_NAME=admin
SECURITY_USER_PASSWORD=your_secure_password

# CORS Policy
CORS_ALLOWED_ORIGINS=http://localhost:4200,https://yourdomain.com

# File Upload
FILE_UPLOAD_DIR=/var/uploads                        # Persistent volume

# Logging
LOGGING_LEVEL=INFO
```

---

## 🔗 Useful Links

- 📖 [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- 🔐 [Spring Security Guide](https://spring.io/guides/gs/securing-web/)
- 🗄️ [PostgreSQL Docs](https://www.postgresql.org/docs/)
- 🚀 [Docker Guide](https://docs.docker.com/)
- 📡 [OAuth 2.0 / JWT Best Practices](https://tools.ietf.org/html/rfc7519)
- 🧪 [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)

---

## 📝 License

This project is licensed under the **MIT License** — see the LICENSE file for details.

---

## 👥 Contributors

**Team YouCode — UM6P**
- **Project Name**: SureVote (Secure Electronic Voting)
- **Type**: Fil Rouge (Capstone) Project
- **Developed**: August 2025 - March 2026

---

## ❓ FAQ

**Q: How is voter privacy protected?**
A: The database uses a double-barrier design: the `Emargement` table (who voted) is completely separated from the `Vote` table (what was voted). No SQL JOIN exists between them, making it cryptographically impossible to link a voter to their ballot.

**Q: Can an admin see who voted for whom?**
A: No. Even a database admin cannot link voter to ballot because there is no foreign key between tables. The architecture prevents this by design.

**Q: What happens if a voter loses their receipt UUID?**
A: The receipt is cryptographic proof. If lost, the voter can request a new one by contacting an admin (with authentication), but the original receipt cannot be recreated.

**Q: Is this GDPR compliant?**
A: The system is designed for election privacy (audit trail needed). Consult your legal team on GDPR compliance specific to your jurisdiction.

**Q: How do I integrate with an Angular frontend?**
A: Use the Swagger UI (`/swagger-ui.html`) or review API Reference section above. CORS is configured for `localhost:4200` by default.

---

## 📞 Support

For issues, bug reports, or feature requests → **GitHub Issues**

---

**Made with ❤️ at YouCode — UM6P**

> 🔐 Security First | 📊 Audit Always | 🗳️ Democracy Secured

