# sb-ecom

REST API for a full-stack e-commerce platform — catalog, cart, checkout, JWT auth, and role-based admin/seller dashboards.

Pairs with the React storefront: **[react-ecom](https://github.com/Derek376/react-ecom)**

---

## Live demo

| | URL |
|---|-----|
| **Storefront** | [react-ecom (Vercel)](https://github.com/Derek376/react-ecom#live-demo) — see frontend README |
| **API (Swagger)** | https://sb-ecom-vbza.onrender.com/swagger-ui/index.html |
| **Sample public products** | https://sb-ecom-vbza.onrender.com/api/public/products?pageNumber=0&pageSize=10&sortBy=price&sortOrder=asc |

> **Note:** The free Render instance sleeps when idle. The first request after inactivity can take **30–60 seconds**.

---

## Highlights

- **Java 21** + **Spring Boot 4** REST API with layered architecture (controller → service → repository)
- **PostgreSQL** (Neon in production) with JPA / Hibernate
- **Spring Security + JWT** (HTTP-only cookie + `Authorization: Bearer` for cross-origin clients)
- **RBAC**: `USER`, `SELLER`, `ADMIN`
- **Stripe** PaymentIntent integration (test mode)
- **OpenAPI / Swagger** for interactive docs
- Deployed with **Docker** on **Render** + managed Postgres on **Neon**

---

## Features

| Area | What it does |
|------|----------------|
| Auth | Sign up / sign in / sign out; JWT in cookie and response body |
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
| Build | Maven Wrapper |
| Deploy | Docker → Render; DB on Neon |

---

## Architecture (high level)

```
Client (react-ecom)
        │  HTTPS + credentials / Bearer JWT
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

## API map

All routes are under `/api`.

| Group | Auth | Examples |
|-------|------|----------|
| Auth | Public | `POST /auth/signup`, `POST /auth/signin`, `POST /auth/signout` |
| Public catalog | Public | `GET /public/products`, `GET /public/categories` |
| Cart | JWT | `GET /carts/users/cart`, `POST /carts/products/{id}/quantity/{qty}` |
| Addresses | JWT | `GET /users/addresses`, `POST /addresses` |
| Orders | JWT | `POST /order/users/payments/{method}`, `GET /users/orders` |
| Stripe | JWT | `POST /order/stripe-client-secret` |
| Seller | SELLER/ADMIN | `POST /seller/categories/{id}/product`, product CRUD |
| Admin | ADMIN | categories, orders, analytics, sellers |

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
