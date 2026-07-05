# sb-ecom — Spring Boot E-commerce API

REST API backend for a full-stack e-commerce application. Built with Spring Boot, it provides product catalog, shopping cart, order placement, user authentication, and shipping address management for the companion [react-ecom](https://github.com/Derek376/react-ecom) frontend.

## Tech Stack

- **Java 21**
- **Spring Boot 4.0.3** — Web MVC, Data JPA, Validation, Security, Actuator
- **PostgreSQL** — primary database (H2 and MySQL configs available as commented alternatives)
- **Spring Security + JWT** — stateless authentication via HTTP-only cookies
- **ModelMapper** — DTO mapping
- **SpringDoc OpenAPI** — interactive API documentation
- **Lombok** — boilerplate reduction

## Features

- User registration, login, and logout with JWT stored in cookies
- Role-based access (admin and user roles seeded on startup)
- Public product and category endpoints with pagination, filtering, and keyword search
- Admin product management (create/update/delete, image upload)
- Shopping cart (create cart, add/update/remove items)
- Shipping address CRUD for authenticated users
- Order placement with payment metadata (Stripe / PayPal supported on the frontend)
- Global exception handling and structured API responses
- Static product image serving from `/images/`

## Prerequisites

- Java 21+
- Maven 3.9+ (or use the included Maven wrapper)
- PostgreSQL running locally with a database named `ecommerce` (or adjust the connection URL)

## Getting Started

### 1. Configure local secrets

Copy the example properties file and fill in your database credentials and JWT secret:

```bash
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties
```

Edit `application-local.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ecommerce
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.app.jwtSecret=REPLACE_WITH_YOUR_JWT_SECRET
```

> `application-local.properties` is gitignored and loaded automatically via `spring.config.import` in `application.properties`.

### 2. Run the application

```bash
./mvnw spring-boot:run
```

Or on Windows:

```bash
mvnw.cmd spring-boot:run
```

The API starts at **http://localhost:8080** by default.

### 3. Explore the API

| Resource | URL |
|----------|-----|
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Product images | http://localhost:8080/images/ |

## API Overview

All endpoints are prefixed with `/api`.

| Area | Example endpoints |
|------|-------------------|
| Auth | `POST /api/auth/signup`, `POST /api/auth/signin`, `POST /api/auth/signout` |
| Products (public) | `GET /api/public/products`, `GET /api/public/products/keyword/{keyword}` |
| Categories (public) | `GET /api/public/categories` |
| Cart (authenticated) | `GET /api/carts/users/cart`, `POST /api/carts/products/{productId}/quantity/{quantity}` |
| Addresses (authenticated) | `GET /api/users/addresses`, `POST /api/addresses` |
| Orders (authenticated) | `POST /api/order/users/payments/{paymentMethod}` |

Public routes under `/api/public/**` and auth routes under `/api/auth/**` do not require a token. All other routes require a valid JWT cookie.

## Configuration

Key settings in `application.properties`:

| Property | Description | Default |
|----------|-------------|---------|
| `frontend.url` | Allowed CORS origin | `http://localhost:5173/` |
| `image.base.url` | Base URL for product images | `http://localhost:8080/images/` |
| `spring.app.jwtExpirationMs` | JWT lifetime in milliseconds | `3000000` |
| `spring.ecom.app.jwtCookieName` | Cookie name for the JWT | `springBootEcom` |
| `project.image` | Local directory for uploaded images | `images/` |

## Project Structure

```
src/main/java/com/ecommerce/project/
├── config/          # App, Swagger, and MVC configuration
├── controller/      # REST controllers
├── model/           # JPA entities
├── payload/         # Request/response DTOs
├── repositories/    # Spring Data JPA repositories
├── security/        # JWT filter, auth config, user details
├── service/         # Business logic
├── exceptions/      # Custom exceptions and global handler
└── util/            # Helpers (auth, image URLs)
```

## Frontend Integration

This backend is designed to work with the [react-ecom](../react-ecom) frontend. Ensure:

1. The backend is running on port **8080**
2. `frontend.url` in `application.properties` matches the frontend dev server (`http://localhost:5173/`)
3. The frontend `.env` sets `VITE_BACK_END_URL=http://localhost:8080`

The frontend sends requests with `withCredentials: true` so JWT cookies are included automatically.

## License

MIT — see [LICENSE](LICENSE) for details.
