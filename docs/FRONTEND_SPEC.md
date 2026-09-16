# SafePay Frontend — Phase 1

## Purpose

Oracle JET customer interface for SafePay, a simulated risk-adaptive pre-settlement transaction-control layer.

## Included now

- SafePay navigation and banking-style visual system
- Customer Dashboard
- Send Money route
- Beneficiaries route
- Transactions route

## Later work

- API wiring and CORS configuration
- Beneficiary management and payment initiation
- Protection countdown and cancellation controls
- Transaction history and responsive polish

## Excluded from Phase 1

- Admin area, Maker-Checker, disputes, live payment rails, AI/ML, and real payment verification

## Design principles

- Fast when safe. Careful when necessary.
- Deep navy navigation, calm light-blue information areas, and clear text status labels.
- The backend remains the source of truth for transaction state and risk decisions.
