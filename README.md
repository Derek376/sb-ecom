# sb-ecom

[![Backend CI](https://github.com/Derek376/sb-ecom/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Derek376/sb-ecom/actions/workflows/backend-ci.yml)

REST API for a full-stack e-commerce platform — catalog, cart, checkout, JWT auth, and role-based admin/seller dashboards.

Pairs with the React storefront: **[react-ecom](https://github.com/Derek376/react-ecom)**

---

## Live demo

| | URL |
|---|-----|
| **Storefront** | [react-ecom (Vercel)](https://github.com/Derek376/react-ecom#live-demo) — see frontend README |
| **API (Swagger)** | https://sb-ecom-s41k.onrender.com/swagger-ui/index.html |
| **Sample public products** | https://sb-ecom-s41k.onrender.com/api/public/products?pageNumber=0&pageSize=10&sortBy=price&sortOrder=asc |

> **Note:** The free Render instance sleeps when idle. The first request after inactivity can take **30–60 seconds**.

---

## Highlights

- **Java 21** + **Spring Boot 4** REST API with layered architecture (controller → service → repository)
- **PostgreSQL** (Neon in production) with JPA / Hibernate
- **Spring Security + JWT** in an HTTP-only cookie, with SPA CSRF protection
- **RBAC**: `USER`, `SELLER`, `ADMIN`
- **Stripe** PaymentIntent integration (test mode)
- **OpenAPI / Swagger** for interactive docs
- Deployed with **Docker** on **Render** + managed Postgres on **Neon**

---

## Features

| Area | What it does |
|------|----------------|
| Auth | Sign up / sign in / sign out; JWT kept out of JavaScript in an HTTP-only cookie |
| Catalog | Paginated products & categories, keyword search, filters |
| Cart | Add / update / remove items; totals tracked server-side |
| Addresses | Per-user shipping addresses (`GET /api/users/addresses`) |
| Orders | Place order after payment metadata; user order history (`GET /api/users/orders`) |
| Seller | Create/update products & images under categories |
| Admin | Categories, products, sellers, orders, analytics |
| Media | Product image upload + static serving under `/images/**` |

---

## Tech stack

| Layer | Choice |
|-------|--------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.3 (Web MVC, Data JPA, Validation, Security, Actuator) |
| Database | PostgreSQL |
| Security | Spring Security, JJWT, BCrypt |
| Docs | SpringDoc OpenAPI 3 |
| Payments | Stripe Java SDK |
| Testing | JUnit 5, Mockito, MockMvc, Spring Security Test, H2 |
| Build | Maven Wrapper |
| Deploy | Docker → Render; DB on Neon |

---

## Architecture (high level)

```
Client (react-ecom)
        │  HTTPS + JWT cookie + CSRF header
        ▼
 Spring Security filter chain (JWT cookie or Authorization header)
        │
        ▼
 Controllers  →  Services  →  JPA Repositories  →  PostgreSQL
        │
        └── /images/**  (uploaded product files)
```

---

## Quick start (local)

### Prerequisites

- Java 21+
- Maven Wrapper (`./mvnw`) — included
- PostgreSQL (local or Neon)

### 1. Secrets

```bash
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties
```

Fill in:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ecommerce
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.app.jwtSecret=REPLACE_WITH_BASE64_SECRET
stripe.secret.key=sk_test_...
app.jwt.cookie.secure=false
app.jwt.cookie.same-site=Lax
```

Generate a JWT secret:

```bash
openssl rand -base64 64
```

### 2. Run

```bash
./mvnw spring-boot:run
```

API: **http://localhost:8080**  
Swagger: **http://localhost:8080/swagger-ui/index.html**

### 3. Frontend

Point [react-ecom](https://github.com/Derek376/react-ecom) at this API:

```env
VITE_BACK_END_URL=http://localhost:8080
VITE_FRONTEND_URL=http://localhost:5173
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_...
```

---

## Tests

Run the complete backend suite with the Maven Wrapper:

```bash
./mvnw test
```

The suite combines:

- fast Mockito unit tests for authentication, ownership, Stripe verification,
  stock handling, and order finalization;
- MockMvc integration tests for endpoint authorization, role boundaries, CSRF,
  request validation, and admin-controlled seller creation;
- a Spring context test using an isolated in-memory H2 database.

Tests do not call Stripe or require a local PostgreSQL server. External payment
responses are mocked, while persistence-backed security tests use H2 and roll
back their data after each test.

---

## Continuous integration

GitHub Actions runs the complete Maven verification lifecycle on every pull
request targeting `main`, every push to `main`, and on manual request from the
Actions tab:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

This compiles the application and tests, runs all backend tests, and packages the
executable Spring Boot JAR. The workflow uses Java 21, caches Maven dependencies,
and requires no database or Stripe repository secrets.

---

## API map

All routes are under `/api`.

| Group | Auth | Examples |
|-------|------|----------|
| Auth | Public | `POST /auth/signup`, `POST /auth/signin`, `POST /auth/signout` |
| Public catalog | Public | `GET /public/products`, `GET /public/categories` |
| Cart | JWT | `GET /carts/users/cart`, `POST /carts/products/{id}/quantity/{qty}` |
| Addresses | JWT | `GET /users/addresses`, `POST /addresses` |
| Orders | JWT | `POST /orders`, `GET /users/orders` |
| Stripe | JWT | `POST /order/stripe-client-secret` |
| Seller | SELLER/ADMIN | `POST /seller/categories/{id}/product`, product CRUD |
| Admin | ADMIN | categories, orders, analytics, `GET/POST /admin/sellers` |

### Security model

- The browser authenticates with a `Secure`, HTTP-only JWT cookie; the token is not returned in JSON or stored in `localStorage`.
- The SPA obtains a CSRF token from `GET /api/auth/csrf` and sends it in the returned header for every state-changing request.
- URL rules provide role checks, while service/repository queries scope addresses, products, carts, and seller orders to the authenticated owner.
- Public signup always creates `ROLE_USER`. Only an authenticated admin can create a seller through `POST /api/admin/sellers`.
- Stripe amounts are calculated from the server-side cart. Order creation retrieves the PaymentIntent from Stripe and verifies its status, amount, currency, user, and cart metadata.
- PaymentIntent IDs are unique in the payments table, making repeat order-confirmation requests idempotent.

---

## Production configuration (Render)

Typical environment variables:

| Variable | Purpose |
|----------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://...neon.tech/neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | Neon credentials |
| `SPRING_APP_JWTSECRET` | Base64 JWT signing key |
| `STRIPE_SECRET_KEY` | Stripe secret (`sk_test_...`) |
| `FRONTEND_URL` | Exact Vercel origin (no trailing slash) |
| `IMAGE_BASE_URL` | `https://<this-service>.onrender.com/images/` |
| `SERVER_PORT` | `$PORT` |
| `APP_JWT_COOKIE_SECURE` | `true` |
| `APP_JWT_COOKIE_SAME_SITE` | `None` |

Docker image (example):

```bash
docker build --platform linux/amd64 -t <dockerhub-user>/sb-ecom:latest .
docker push <dockerhub-user>/sb-ecom:latest
```

Then redeploy the existing Render Web Service (no need to recreate the service each time).

---

## Project structure

```
src/main/java/com/ecommerce/project/
├── config/        # Constants, Swagger, static resources
├── controller/    # REST endpoints
├── model/         # JPA entities
├── payload/       # DTOs
├── repositories/  # Spring Data JPA
├── security/      # JWT, filters, CORS, user details
├── service/       # Business logic (cart, order, Stripe, …)
├── exceptions/    # API exceptions + global handler
└── util/          # Auth helpers, image URL builder
```

---

## Related

- Frontend: [Derek376/react-ecom](https://github.com/Derek376/react-ecom)
- Author: [Derek376](https://github.com/Derek376)

---

## License

MIT — see [LICENSE](LICENSE).
