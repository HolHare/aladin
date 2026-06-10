# Aladin — Asset & Liability Investment Network

An open-source alternative to BlackRock's Aladdin, built with Kotlin Multiplatform.

## Stack

| Layer | Technology |
|-------|-----------|
| Backend API | Ktor 3 (Kotlin) |
| Database | PostgreSQL 16 via Exposed ORM |
| Web UI | Compose Multiplatform (wasmJs) |
| Android | Compose Multiplatform |
| Shared models | Kotlin Multiplatform |

## Features

- **Portfolio Dashboard** — AUM overview, P&L across all portfolios
- **Holdings** — Position-level view with unrealized P&L and weight
- **Risk Analytics** — VaR (95%/99%), Sharpe ratio, Beta, sector exposure
- **Trade Blotter** — Full trade history + new order entry
- **Performance** — 30-day cumulative return chart vs benchmark

## Running locally

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Start the API server

```bash
./gradlew :server:run
# Runs on http://localhost:8080
# API health: http://localhost:8080/health
```

### 3. Start the web frontend

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
# Opens http://localhost:8081
```

### 4. (Optional) Build Android APK

```bash
./gradlew :composeApp:assembleDebug
```

## Environment variables (server)

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5433/aladin` | JDBC connection URL |
| `DB_USER` | `aladin` | Database username |
| `DB_PASS` | `aladin_dev` | Database password |
| `PORT` | `8080` | Server port |

## API Endpoints

```
GET  /health
GET  /api/portfolios
GET  /api/portfolios/{id}
GET  /api/portfolios/{id}/holdings
GET  /api/portfolios/{id}/risk
GET  /api/portfolios/{id}/performance
GET  /api/portfolios/{id}/trades
GET  /api/assets
GET  /api/trades
POST /api/trades
```
