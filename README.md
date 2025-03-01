## System Design And Architecture

Below is the system design and architecture diagram for the Applix project:

![System Design And Architecture](https://github.com/suryapts007/Applix/blob/main/src/main/resources/Screenshot%202025-03-02%20at%202.47.48%E2%80%AFAM.png)



# Applix Project Setup Guide

## Prerequisites

Before setting up the Applix project, ensure you have the following installed:
- Java 17 or later (JDK 17+)
- Maven v3.8.6+
- Docker Compose v2.32.4+

## Backend Setup

### 1. Clone the Repository
```sh
git clone https://github.com/suryapts007/Applix.git
cd Applix
```

### 2. Start MySQL and Kafka Containers
Navigate to the Docker directory and run:
```sh
cd src/main/java/com/example/applix/docker
docker compose up -d
```
This will start the required containers in the background. You can verify their status using:
```sh
docker ps
```

### 3. Build the Backend
Run the following command to compile and package the project:
```sh
mvn clean install
```

### 4. Start the Backend Server
```sh
mvn spring-boot:run
```
Once started, the backend server will be accessible at: [http://localhost:8080](http://localhost:8080)
