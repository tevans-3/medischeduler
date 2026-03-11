# MediScheduler Quickstart Guide

Get MediScheduler running locally in under 10 minutes.

## Prerequisites

- [Docker](https://www.docker.com/get-started) and Docker Compose
- [Node.js](https://nodejs.org/) v18+ (for frontend development)
- A Google Cloud account with the following APIs enabled:
  - Routes API
  - Maps JavaScript API
  - OAuth 2.0 credentials

## 1. Clone the Repository

```bash
git clone https://github.com/tevans-3/MediScheduler.git
cd MediScheduler
```

## 2. Configure Environment Variables

Copy the example environment file and fill in your credentials:

```bash
cp .env.example .env
```

Edit `.env` with your values:

```env
# Redis (defaults work for local Docker setup)
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_USERNAME=default
REDIS_PASSWORD=

# Google Routes API (for computing travel distances)
ROUTES_API_KEY=your_routes_api_key_here

# Google Maps (frontend map display)
VITE_GOOGLE_MAPS_API_KEY=your_maps_api_key_here

# Google OAuth2 (authentication)
GOOGLE_CLIENT_ID=your_oauth_client_id_here
VITE_GOOGLE_CLIENT_ID=your_oauth_client_id_here
```

### Getting Google API Keys

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the **Routes API** and **Maps JavaScript API**
4. Go to **Credentials** and create:
   - An **API Key** for Routes and Maps
   - An **OAuth 2.0 Client ID** for authentication (Web application type)
5. For the OAuth Client ID, add `http://localhost:5173` to Authorized JavaScript origins

## 3. Start Backend Services

Launch all backend services with Docker Compose:

```bash
docker compose up -d --build
```

The `--build` flag ensures all containers are built from source before starting.

This starts:
- **Zookeeper** (port 2181)
- **Kafka** (port 9092)
- **Redis** (port 6379)
- **Route Service** (port 8081)
- **Scheduler Service** (port 8082)

Verify services are running:

```bash
docker compose ps
```

## 4. Start the Frontend

In a new terminal, navigate to the dashboard and install dependencies:

```bash
cd dashboard
npm install
npm run dev
```

The app will be available at **http://localhost:5173**

## 5. Test the Application

### Using Sample Data

Sample CSV files are provided in `dashboard/test-data/`:

1. Open http://localhost:5173 in your browser
2. Sign in with Google
3. Click **Upload Students** and select `test-data/students.csv`
4. Click **Upload Teachers** and select `test-data/teachers.csv`
5. Wait for validation to complete in the modal
6. Click **Run Scheduler** to generate assignments
7. View results on the interactive map

### CSV File Format

**Students CSV:**
```csv
id,firstName,lastName,email,address,travelMethod,specialtyInterests,weightedPreferences,sessionNum
S001,Emily,Chen,emily@example.com,"123 Main St, Edmonton, AB",TRANSIT,"{""familyMedicine"":""high""}","{""commute"":""0.4"",""specialty"":""0.6""}",1
```

**Teachers CSV:**
```csv
id,firstName,lastName,email,address,availability,specialtyInterests
T001,Dr. Robert,Smith,robert@example.com,"456 Oak Ave, Edmonton, AB","{""session1"":""available""}","{""familyMedicine"":""primary""}"
```

## Troubleshooting

### Services won't start
```bash
# Check logs
docker compose logs -f

# Restart everything
docker compose down
docker compose up -d
```

### Kafka connection issues
Wait 30 seconds after starting containers for Kafka to fully initialize.

### Frontend can't connect to backend
Ensure all Docker services are running and check CORS settings in the backend.

### Google OAuth errors
Verify your OAuth Client ID is correctly configured and `http://localhost:5173` is in the authorized origins.

## Stopping the Application

```bash
# Stop frontend
# Press Ctrl+C in the terminal running npm run dev

# Stop backend services
docker compose down

# Remove all data volumes (clean start)
docker compose down -v
```

## Next Steps

- Read the full [User Documentation](/docs)
- Review the [Privacy Statement](/privacy)
- Check out the [source code](https://github.com/tevans-3/MediScheduler)
