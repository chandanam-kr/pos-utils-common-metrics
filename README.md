# pos-utils-metrics

A Spring Boot application for collecting and exposing custom metrics, including support for annotations and aspect-oriented programming to capture and manage metrics in a POS (Point of Sale) environment.

## Features
- Custom metric annotations for easy integration
- Aspect-based metric capture
- Configurable metrics endpoints
- Health and metrics endpoints via Spring Boot Actuator

## Project Structure
- `src/main/java/com/kroger/metrics/annotation/` - Custom metric annotations
- `src/main/java/com/kroger/metrics/aspect/` - Aspects for capturing metrics
- `src/main/java/com/kroger/metrics/config/` - Metrics configuration classes
- `src/main/java/com/kroger/metrics/constants/` - Constants used in metrics
- `src/main/java/com/kroger/metrics/controller/` - Controller for metrics endpoints
- `src/main/resources/application.yml` - Application configuration

## Configuration
The application uses Spring Boot Actuator for metrics. The `application.yml` file configures the management endpoints, including the base path and exposure settings.

## Building and Running

1. **Build the project:**
   ```sh
   mvn clean package
   ```
2. **Run the application:**
   ```sh
   java -jar target/pos-utils-metrics-0.0.1.jar
   ```

## Endpoints
- `/metrics` - Exposes metrics (if enabled in configuration)
- `/health` - Health check endpoint

## Requirements
- Java 8 or higher
- Maven 3.6+