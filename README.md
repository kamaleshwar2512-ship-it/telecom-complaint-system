# Telecom Complaint & SLA Tracking System

A full-stack Spring Boot application for a telecom operator to manage customer complaints
(network, billing, SIM, data and other issues) against SLA (Service Level Agreement) deadlines,
with **automatic background monitoring and escalation** of at-risk and breached complaints.

## Project Overview

Telecom operators handle large volumes of complaints, and regulators typically require them to
be resolved within a fixed time window. In practice, complaints quietly miss their deadlines
because nobody is watching the clock. This system assigns every complaint an SLA deadline based
on its category and priority, and a scheduled background job continuously checks every open
complaint, flags anything approaching or past its deadline, and automatically escalates it -
raising its priority and marking it `ESCALATED` - without any manual action.

## Features

- **Customers** register, log in, raise complaints (category + priority + description), track
  status and a live SLA countdown, view the full activity timeline, and reopen a resolved
  complaint if the issue isn't actually fixed.
- **Agents** see their assigned complaint queue with color-coded urgency (green/amber/red), open
  a complaint's details, start progress, add notes, and resolve complaints.
- **Admins** see a dashboard of complaint statistics (Chart.js charts for status/category
  breakdowns and average resolution time by category), manage SLA rules, assign/reassign
  complaints to agents, and view a breach report.
- **Automatic SLA monitoring**: a `@Scheduled` job re-checks every open/in-progress/escalated
  complaint on a fixed interval, escalates anything within the configured "at risk" window, and
  flags anything that has actually breached its deadline - each exactly once (idempotent), so
  re-running the check never double-escalates or duplicates timeline entries.

## Technology Stack

- Java 25, Spring Boot 4.1.1
- Spring MVC (`spring-boot-starter-webmvc`), Spring Data JPA, Spring Security, Spring Validation
- Thymeleaf + Bootstrap 5 (server-rendered UI, no separate frontend app)
- MySQL 8.x (the only datastore the running application uses)
- Chart.js (via CDN) for the admin dashboard charts
- JUnit 5 / Spring Boot Test
- Maven

## Project Architecture

Simple layered architecture, as required:

```
Controller  ->  Service  ->  Repository  ->  Entity / Database
```

- **Controllers** (`controller` package) handle HTTP requests, validation, and navigation only.
- **Services** (`service` package) hold all business logic: SLA calculation, status-transition
  rules, assignment, and the automatic SLA monitor.
- **Repositories** (`repository` package) are plain Spring Data JPA interfaces.
- **Entities** (`entity` package, enums under `entity.enums`) map directly to the database tables.
- **DTOs/forms** (`dto` package) back the Thymeleaf forms and add Bean Validation constraints,
  keeping validation concerns out of the entities.
- **`config`** holds Spring Security configuration, the demo data seeder, and the scheduling
  enabler. **`exception`** holds the custom business exceptions and a small global handler that
  turns them into a friendly error page instead of a stack trace.

### Key design decisions worth knowing about

- **SLA deadline is calculated once**, at complaint-creation time (`ComplaintService.createComplaint`),
  from a `SlaRule` looked up by category + priority, and is never recalculated afterwards -
  including when a complaint is reopened.
- **Status transitions** are validated against an explicit allowed-transitions map in
  `ComplaintService`, so a complaint can never jump into an invalid state from any entry point
  (customer, agent, admin, or the scheduler).
- **SLA monitoring idempotency**: `Complaint` has two boolean flags, `approachingNotified` and
  `breached`, each set exactly once by `SlaMonitoringService`. Every scheduler run re-examines
  every open complaint, but a complaint already flagged for a condition is skipped for that
  condition on later runs - so priority is never bumped twice and no duplicate timeline entries
  are created. See `SlaMonitoringServiceTest` for tests proving this.
- **`SlaDisplayService`** is the single place that turns a deadline into a human countdown and an
  urgency color, and it's the same logic (and the same configurable `sla.at-risk-threshold-hours`)
  used both by the Thymeleaf templates (`${@slaDisplayService...}`) and by the scheduler, so the
  on-screen badge color and the automatic escalation always agree.

## Dependencies Added Beyond the Original `pom.xml`

Only one dependency was added beyond what `start.spring.io` already generated for this project:

- **`com.h2database:h2`, test scope only.** Used exclusively by the test suite
  (`src/test/resources/application.properties` points tests at an in-memory H2 database) so
  `mvn test` can run without a live MySQL server. It never appears on the runtime classpath and
  is not used by the application when it actually runs - MySQL remains the only production
  datastore, per the project requirements.

Everything else (Spring Web/MVC, Data JPA, Security, Validation, Thymeleaf, the MySQL driver,
Lombok, the per-technology test starters) was already present in the generated `pom.xml` and is
used as-is.

## Database Setup

1. Install and start MySQL 8.x.
2. The application will create the `telecom_complaint_system` schema automatically on first
   connection (`createDatabaseIfNotExist=true` in the JDBC URL) - you don't need to run any SQL
   by hand, and Hibernate (`spring.jpa.hibernate.ddl-auto=update`) creates/updates the tables.
3. Configure credentials **without editing `application.properties`**, via environment variables:

   ```bash
   export DB_URL="jdbc:mysql://localhost:3306/telecom_complaint_system?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC"
   export DB_USERNAME=root
   export DB_PASSWORD=your_mysql_password
   ```

   If you don't set these, the defaults in `application.properties` (`root` / `root` against
   `localhost:3306`) are used - convenient for a fresh local install, but change the password
   default for anything beyond your own machine.

## Running the Application

```bash
# from the project root
./mvnw spring-boot:run
```

or build a jar and run it directly:

```bash
./mvnw clean package
java -jar target/telecom-complaint-system-0.0.1-SNAPSHOT.jar
```

The app starts on `http://localhost:8080`. On first startup (only if the `users` table is empty)
it seeds demo data automatically - see below.

## Demo Users

Seeded automatically on first run by `DemoDataLoader` (all passwords are the same demo password,
hashed with BCrypt before storage - never stored in plain text):

| Role     | Email                  | Password      |
|----------|-------------------------|---------------|
| Admin    | admin@telecom.com       | Password123   |
| Agent    | agent1@telecom.com      | Password123   |
| Agent    | agent2@telecom.com      | Password123   |
| Customer | customer1@telecom.com   | Password123   |
| Customer | customer2@telecom.com   | Password123   |

A full set of SLA rules (all 5 categories x 4 priorities) and a handful of sample complaints in
different states (on track, at risk, already breached, already escalated, resolved) are seeded
alongside them, so the SLA monitor and dashboard have something to show immediately. New
customers can also self-register from the login page (`/register`) - agent and admin accounts are
only created via this seed data, not through public registration.

## SLA Scheduler

`SlaMonitoringService.checkSlaDeadlines()` is annotated `@Scheduled(fixedRateString =
"${sla.monitoring.fixed-rate-ms:300000}")` - it runs every 5 minutes by default. Each run:

1. Loads every complaint that is not `RESOLVED`/`CLOSED`.
2. For each one, checks whether it has breached its deadline, or is within
   `sla.at-risk-threshold-hours` (4 hours by default) of it.
3. If breached and not already flagged: sets `breached = true`, raises priority to `CRITICAL`,
   moves `OPEN`/`IN_PROGRESS` complaints to `ESCALATED`, and adds a timeline entry explaining what
   happened.
4. If approaching and not already flagged: sets `approachingNotified = true`, bumps priority one
   level, escalates status the same way, and adds a timeline entry.

Both thresholds are configurable in `application.properties` without touching code:

```properties
sla.monitoring.fixed-rate-ms=300000
sla.at-risk-threshold-hours=4
```

## Testing

Run the full test suite with:

```bash
./mvnw test
```

Tests cover (see `src/test/java/.../service` and `.../security`):

- **SLA calculation**: deadline math for different category/priority combinations, and the
  graceful error when no matching rule exists (`ComplaintServiceTest`).
- **Status transitions**: valid and invalid transitions, including that a closed complaint
  cannot be resolved again (`ComplaintServiceTest`).
- **Complaint service**: creation, assignment, agent-ownership enforcement, resolution, reopening
  (`ComplaintServiceTest`).
- **SLA monitoring**: at-risk detection, breach detection, and - importantly - that running the
  scheduler repeatedly never re-escalates or duplicates timeline entries for an already-flagged
  complaint (`SlaMonitoringServiceTest`).
- **Validation**: invalid registration/complaint/SLA-rule input is rejected by Bean Validation
  (`ValidationTest`).
- **Security**: unauthenticated requests redirect to login, customers/agents cannot reach the
  admin area, a customer cannot access another customer's complaint, and passwords are hashed,
  never stored in plain text (`AccessControlTest`, `UserServiceTest`, plus the ownership test in
  `ComplaintServiceTest`).

Tests run against an in-memory H2 database (test-scope only, see `src/test/resources/application.properties`)
so they don't require a MySQL server to be running.

## Project Structure

```
src/main/java/com/kamaleshwar/telecom_complaint_system/
    entity/            User, Complaint, SlaRule, ComplaintUpdate + entity/enums/
    repository/        Spring Data JPA repositories
    service/           Business logic (ComplaintService, SlaMonitoringService, DashboardService, ...)
    controller/         AuthController, RootController, CustomerController, AgentController, AdminController
    dto/               Form-backing beans + DashboardStats view model
    exception/         Custom exceptions + GlobalExceptionHandler
    config/            SecurityConfig, CustomUserDetailsService, SchedulingConfig, DemoDataLoader
    util/              SlaCountdownFormatter (pure, unit-tested helper)

src/main/resources/
    templates/         login, register, error, customer/, agent/, admin/, fragments/
    static/css, static/js
    application.properties
```

## What Could Not Be Verified in This Session

This project was built and reviewed in an environment without network access to Maven Central
(and without a locally cached `~/.m2` repository), and without a working local shell on the
target machine for this particular session, so **`mvn compile` / `mvn test` could not actually be
run here**. Every file was written carefully and reviewed by hand for correctness (imports,
Spring/Thymeleaf/Lombok API usage, JPA mappings), and one real bug was caught and fixed during
that review (a `@Transactional` annotation on a self-invoked method that Spring would have
silently ignored - it's now on the public scheduled entry point instead). Still, please run:

```bash
./mvnw clean compile
./mvnw test
./mvnw spring-boot:run
```

as the first thing you do, and treat any compiler error as the next thing to fix - with Spring
Boot 4.1.1 / Spring Framework 7 being very new, there's a real chance of a small API mismatch
(e.g. an import path or method signature that changed from Spring Boot 3.x) that only a real
compiler run will catch. If something doesn't compile, the error message plus the relevant class
in `service/` or `config/` should make the fix straightforward.
