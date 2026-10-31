
DataTrail
DataTrail – Database Provenance, Monitoring and Recovery Platform

DataTrail is a web application for organizing database-monitoring projects and reviewing database activity. It provides project and database-connection management, monitoring configuration, audit-log review, change analysis, provenance tracking, and recovery-related workflows.

Technology stack
Layer	Technology
Frontend	React, TypeScript, Vite
Backend	Java 21, Spring Boot 4.1.0, Maven
Persistence	Spring Data JPA, Hibernate
Database	PostgreSQL
Authentication and security	Spring Security, JWT / OAuth2 Resource Server
Database connectivity	PostgreSQL JDBC
Repository layout
DataTrail/
├── backend/
│   ├── src/
│   ├── .keys/                 # Local JWT key files; do not commit secrets
│   ├── .env                   # Local database settings; do not commit
│   ├── Dockerfile
│   ├── pom.xml
│   └── mvnw
└── frontend/
    ├── src/
    ├── public/
    ├── package.json
    └── vite.config.ts
Prerequisites
Java Development Kit (JDK) 21
Node.js and npm
PostgreSQL
Git
Configuration
1. Create the PostgreSQL database
Create a PostgreSQL database for the application. The default JDBC URL in the backend configuration points to localhost:5432/DataTrail; change it if your local database uses a different host, port, or database name.

2. Configure the backend environment
The backend loads local settings from backend/.env through Spring's optional file import. Create this file in the backend directory and provide the following variables:

DB_URL=jdbc:postgresql://localhost:5432/DataTrail
DB_USERNAME=your_postgres_username
DB_PASSWORD=your_postgres_password
Use your own local database credentials. Never put real passwords, private keys, tokens, or other secrets in this README or commit them to version control.

The backend also uses JWT key files under backend/.keys/. Keep the required key files in that local directory and follow the project's existing key configuration. Do not publish private keys.

3. Configure the frontend API URL
The frontend reads VITE_API_BASE_URL and falls back to http://localhost:8080 when it is not set. For a local backend, the default can be used. For another backend host, set VITE_API_BASE_URL to the backend base URL in the frontend environment.

Run locally
Open two terminal windows from the repository root.

Start the backend
cd backend
./mvnw spring-boot:run
On Windows Command Prompt or PowerShell, use:

cd backend
.\mvnw.cmd spring-boot:run
The backend's configured default server port is 8080 unless overridden by its configuration.

Start the frontend
cd frontend
npm install
npm run dev
Vite prints the local frontend URL in the terminal after the development server starts.

Build and quality checks
Backend package build:

cd backend
./mvnw clean package
Frontend production build:

cd frontend
npm run build
Frontend lint check:

cd frontend
npm run lint
Docker
A Dockerfile is included in the backend directory. From that directory, build the backend image with:

docker build -t datatrail-backend .
The Dockerfile exposes port 8080. Supply the required database configuration and JWT key material securely when running the container; do not bake credentials or private keys into a published image. A complete multi-container Docker Compose setup is not included in this repository snapshot.

Security notes
Keep .env and private JWT keys out of source control.
Use environment variables or a secure deployment configuration for credentials.
Do not publish database passwords, access tokens, or private keys in screenshots, logs, or documentation.
Current scope note
The application includes project, connection, monitoring, audit, provenance, and recovery-related components. Actual monitoring of a target PostgreSQL database depends on the backend environment being able to connect to that database. Access to databases on private local networks is not automatically provided by a remotely hosted backend.

License
No license has been specified in this repository. Add a license if the project is intended for public reuse.

⋮⋮⋮⋮