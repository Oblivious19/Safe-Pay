# SafePay

SafePay is a simulated, risk-adaptive payment-protection application. It demonstrates how eligible payments can be paused before completion, giving customers an opportunity to review and cancel them.

**No real money moves. SafePay is a demonstration project—not a bank, payment gateway, or guaranteed fraud-prevention system.**

## Key Features

- Customer registration, login, profile, and linked-account selection.
- Beneficiary management and simulated payments.
- Amount-based protection rules, timed cancellation windows, and administrator review for payments requiring additional approval.
- Transaction history and payment-status tracking.
- Admin dashboard, transaction browsing, and held-payment approval or decline.
- Admin user search by exact ID, first/full name, or email, with linked-account details.
- Static “Meet the Team” popup accessible from the footer.

## Technology Stack

- **Frontend:** Oracle JET, TypeScript, Knockout.js, HTML, and CSS.
- **Backend:** Java 17, Spring Boot, Spring Security, and Spring Data JPA/Hibernate.
- **Database:** Oracle Database.
- **Build tools:** Maven and npm.

The application follows a layered structure: frontend views and services communicate with backend controllers, which use service and repository layers to access data.

## Security and Scope

The application uses session-based authentication, role-based access controls, server-side validation, and CSRF protection. Payment protections are rule-based; they do not establish that a recipient is trustworthy or guarantee fraud detection. Completed payments cannot be cancelled through the protection-window feature.

## Main Folders

- `Backend/` — Backend APIs, business logic, and tests.
- `Frontend/SafePayJet/` — Oracle JET frontend and tests.
- `Database/` — Database SQL resources.
- `scripts/` — Setup and supporting scripts.
- `docs/` — Additional project documentation.

## Team

- **Aditya Rao:** Product Research & Management, Backend Development.
- **Shreya Ojha:** Project Lead, Backend Development.
- **Ruchi Shree:** UI/UX Visual Design, Frontend Development.
- **Gaurav Sahu:** Integrations and Deployment, Team Management.

## Setup and Run

Refer to **README-QUICKSTART.md** in the project root for prerequisites, Oracle schema setup, data seeding, backend startup, frontend startup, and troubleshooting.

Follow its prescribed setup order. Do not run every SQL file in the repository indiscriminately; the Quickstart identifies the required scripts.