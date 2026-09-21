# Database before/after comparison

Before: 2026-09-21T02:11:50.438566+05:30

After: 2026-09-21T02:37:17.166955+05:30

1474 changed field values across 107 rows. All 19 canonical table counts and primary keys were checked. RISK_REVIEW uses retained APPROVAL_ID.

These are observed differences, not automatic attribution to frontend tests. Schedulers, existing sessions and other users may also change rows. Secret/hash/token/key/LOB fields were excluded by the SQL and cannot be compared. This is not a rollback script.

| Table | Row key | Change | Field | Before | After | Attribution |
|---|---|---|---|---|---|---|
| APP_USER | 2468 | UPDATE | LAST_SUCCESSFUL_LOGIN_AT | 2026-09-20T14:20:01.758834+00:00 | 2026-09-20T20:47:28.716799+00:00 | Approved sign-ins for user 2468; login timestamp/version only |
| APP_USER | 2468 | UPDATE | UPDATED_AT | 2026-09-20T14:20:01.758834+00:00 | 2026-09-20T20:47:28.716799+00:00 | Approved sign-ins for user 2468; login timestamp/version only |
| APP_USER | 2468 | UPDATE | VERSION_NO | 10 | 12 | Approved sign-ins for user 2468; login timestamp/version only |
| APP_USER | 2470 | UPDATE | LAST_SUCCESSFUL_LOGIN_AT | NULL | 2026-09-20T20:50:41.016948+00:00 | Approved sign-ins for user 2470; login timestamp/version only |
| APP_USER | 2470 | UPDATE | UPDATED_AT | 2026-03-02T16:26:29.301533+05:30 | 2026-09-20T20:50:41.016948+00:00 | Approved sign-ins for user 2470; login timestamp/version only |
| APP_USER | 2470 | UPDATE | VERSION_NO | 0 | 2 | Approved sign-ins for user 2470; login timestamp/version only |
| APP_USER | 2498 | UPDATE | LAST_SUCCESSFUL_LOGIN_AT | NULL | 2026-09-20T21:05:47.535078+00:00 | Approved sign-ins for user 2498; login timestamp/version only |
| APP_USER | 2498 | UPDATE | UPDATED_AT | 2026-03-02T16:26:29.305581+05:30 | 2026-09-20T21:05:47.535078+00:00 | Approved sign-ins for user 2498; login timestamp/version only |
| APP_USER | 2498 | UPDATE | VERSION_NO | 0 | 2 | Approved sign-ins for user 2498; login timestamp/version only |
| APP_USER | 2499 | UPDATE | LAST_SUCCESSFUL_LOGIN_AT | NULL | 2026-09-20T21:01:05.568550+00:00 | Approved sign-ins for user 2499; login timestamp/version only |
| APP_USER | 2499 | UPDATE | UPDATED_AT | 2026-03-02T16:26:29.309577+05:30 | 2026-09-20T21:01:05.568550+00:00 | Approved sign-ins for user 2499; login timestamp/version only |
| APP_USER | 2499 | UPDATE | VERSION_NO | 0 | 2 | Approved sign-ins for user 2499; login timestamp/version only |
| APP_USER | 2500 | UPDATE | LAST_SUCCESSFUL_LOGIN_AT | NULL | 2026-09-20T21:06:13.954262+00:00 | Approved sign-ins for user 2500; login timestamp/version only |
| APP_USER | 2500 | UPDATE | UPDATED_AT | 2026-03-02T16:26:29.309577+05:30 | 2026-09-20T21:06:13.954262+00:00 | Approved sign-ins for user 2500; login timestamp/version only |
| APP_USER | 2500 | UPDATE | VERSION_NO | 0 | 2 | Approved sign-ins for user 2500; login timestamp/version only |
| AUTH_SESSION | 601 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:45:35.391755+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 601 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:45:35.391755+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 601 | INSERT | LAST_USED_AT | [row absent] | 2026-09-20T20:45:40.757695+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 601 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | 606 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 601 | INSERT | REVOCATION_REASON | [row absent] | ROTATED | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 601 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:45:40.757695+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 601 | INSERT | SESSION_ID | [row absent] | 601 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 601 | INSERT | USER_ID | [row absent] | 2468 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 601 | INSERT | VERSION_NO | [row absent] | 2 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 602 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:45:38.019883+00:00 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 602 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:45:38.019883+00:00 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 602 | INSERT | LAST_USED_AT | [row absent] | NULL | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 602 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | NULL | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 602 | INSERT | REVOCATION_REASON | [row absent] | LOGOUT | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 602 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:45:43.862415+00:00 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 602 | INSERT | SESSION_ID | [row absent] | 602 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 602 | INSERT | USER_ID | [row absent] | 2470 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 602 | INSERT | VERSION_NO | [row absent] | 1 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 603 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:45:39.276733+00:00 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 603 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:45:39.276733+00:00 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 603 | INSERT | LAST_USED_AT | [row absent] | NULL | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 603 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | NULL | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 603 | INSERT | REVOCATION_REASON | [row absent] | LOGOUT | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 603 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:45:43.916956+00:00 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 603 | INSERT | SESSION_ID | [row absent] | 603 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 603 | INSERT | USER_ID | [row absent] | 2498 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 603 | INSERT | VERSION_NO | [row absent] | 1 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 604 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:45:39.918824+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 604 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:45:39.918824+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 604 | INSERT | LAST_USED_AT | [row absent] | NULL | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 604 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | NULL | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 604 | INSERT | REVOCATION_REASON | [row absent] | LOGOUT | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 604 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:45:43.990072+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 604 | INSERT | SESSION_ID | [row absent] | 604 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 604 | INSERT | USER_ID | [row absent] | 2499 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 604 | INSERT | VERSION_NO | [row absent] | 1 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 605 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:45:40.576666+00:00 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 605 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:45:40.576666+00:00 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 605 | INSERT | LAST_USED_AT | [row absent] | NULL | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 605 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | NULL | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 605 | INSERT | REVOCATION_REASON | [row absent] | LOGOUT | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 605 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:45:44.058729+00:00 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 605 | INSERT | SESSION_ID | [row absent] | 605 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 605 | INSERT | USER_ID | [row absent] | 2500 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 605 | INSERT | VERSION_NO | [row absent] | 1 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 606 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:45:40.757695+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 606 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:45:40.757695+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 606 | INSERT | LAST_USED_AT | [row absent] | NULL | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 606 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | NULL | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 606 | INSERT | REVOCATION_REASON | [row absent] | LOGOUT | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 606 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:45:43.757101+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 606 | INSERT | SESSION_ID | [row absent] | 606 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 606 | INSERT | USER_ID | [row absent] | 2468 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 606 | INSERT | VERSION_NO | [row absent] | 1 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 607 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:47:28.716799+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 607 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:47:28.716799+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 607 | INSERT | LAST_USED_AT | [row absent] | 2026-09-20T20:47:29.638212+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 607 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | 608 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 607 | INSERT | REVOCATION_REASON | [row absent] | ROTATED | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 607 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:47:29.638212+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 607 | INSERT | SESSION_ID | [row absent] | 607 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 607 | INSERT | USER_ID | [row absent] | 2468 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 607 | INSERT | VERSION_NO | [row absent] | 2 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 608 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:47:29.638212+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 608 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:47:29.638212+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 608 | INSERT | LAST_USED_AT | [row absent] | 2026-09-20T20:48:49.101078+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 608 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | 609 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 608 | INSERT | REVOCATION_REASON | [row absent] | ROTATED | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 608 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:48:49.101078+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 608 | INSERT | SESSION_ID | [row absent] | 608 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 608 | INSERT | USER_ID | [row absent] | 2468 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 608 | INSERT | VERSION_NO | [row absent] | 2 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 609 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:48:49.101078+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 609 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:48:49.101078+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 609 | INSERT | LAST_USED_AT | [row absent] | 2026-09-20T20:53:26.443050+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 609 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | 611 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 609 | INSERT | REVOCATION_REASON | [row absent] | ROTATED | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 609 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:53:26.443050+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 609 | INSERT | SESSION_ID | [row absent] | 609 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 609 | INSERT | USER_ID | [row absent] | 2468 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 609 | INSERT | VERSION_NO | [row absent] | 2 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 610 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:41.016948+00:00 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 610 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:50:41.016948+00:00 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 610 | INSERT | LAST_USED_AT | [row absent] | NULL | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 610 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | NULL | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 610 | INSERT | REVOCATION_REASON | [row absent] | LOGOUT | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 610 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:50:43.108240+00:00 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 610 | INSERT | SESSION_ID | [row absent] | 610 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 610 | INSERT | USER_ID | [row absent] | 2470 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 610 | INSERT | VERSION_NO | [row absent] | 1 | Sign-in/refresh for approved user 2470; revoked by rotation/logout |
| AUTH_SESSION | 611 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:53:26.443050+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 611 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:53:26.443050+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 611 | INSERT | LAST_USED_AT | [row absent] | 2026-09-20T20:54:42.058134+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 611 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | 612 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 611 | INSERT | REVOCATION_REASON | [row absent] | ROTATED | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 611 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:54:42.058134+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 611 | INSERT | SESSION_ID | [row absent] | 611 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 611 | INSERT | USER_ID | [row absent] | 2468 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 611 | INSERT | VERSION_NO | [row absent] | 2 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 612 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:54:42.058134+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 612 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:54:42.058134+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 612 | INSERT | LAST_USED_AT | [row absent] | 2026-09-20T20:54:48.656475+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 612 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | 613 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 612 | INSERT | REVOCATION_REASON | [row absent] | ROTATED | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 612 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:54:48.656475+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 612 | INSERT | SESSION_ID | [row absent] | 612 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 612 | INSERT | USER_ID | [row absent] | 2468 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 612 | INSERT | VERSION_NO | [row absent] | 2 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 613 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:54:48.656475+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 613 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T20:54:48.656475+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 613 | INSERT | LAST_USED_AT | [row absent] | NULL | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 613 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | NULL | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 613 | INSERT | REVOCATION_REASON | [row absent] | LOGOUT | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 613 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T20:59:58.610745+00:00 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 613 | INSERT | SESSION_ID | [row absent] | 613 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 613 | INSERT | USER_ID | [row absent] | 2468 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 613 | INSERT | VERSION_NO | [row absent] | 1 | Sign-in/refresh for approved user 2468; revoked by rotation/logout |
| AUTH_SESSION | 614 | INSERT | CREATED_AT | [row absent] | 2026-09-20T21:01:05.568550+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 614 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T21:01:05.568550+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 614 | INSERT | LAST_USED_AT | [row absent] | 2026-09-20T21:01:06.225173+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 614 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | 615 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 614 | INSERT | REVOCATION_REASON | [row absent] | ROTATED | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 614 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T21:01:06.225173+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 614 | INSERT | SESSION_ID | [row absent] | 614 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 614 | INSERT | USER_ID | [row absent] | 2499 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 614 | INSERT | VERSION_NO | [row absent] | 2 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 615 | INSERT | CREATED_AT | [row absent] | 2026-09-20T21:01:06.225173+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 615 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T21:01:06.225173+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 615 | INSERT | LAST_USED_AT | [row absent] | 2026-09-20T21:05:02.608832+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 615 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | 616 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 615 | INSERT | REVOCATION_REASON | [row absent] | ROTATED | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 615 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T21:05:02.608832+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 615 | INSERT | SESSION_ID | [row absent] | 615 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 615 | INSERT | USER_ID | [row absent] | 2499 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 615 | INSERT | VERSION_NO | [row absent] | 2 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 616 | INSERT | CREATED_AT | [row absent] | 2026-09-20T21:05:02.608832+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 616 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T21:05:02.608832+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 616 | INSERT | LAST_USED_AT | [row absent] | NULL | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 616 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | NULL | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 616 | INSERT | REVOCATION_REASON | [row absent] | LOGOUT | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 616 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T21:05:44.670561+00:00 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 616 | INSERT | SESSION_ID | [row absent] | 616 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 616 | INSERT | USER_ID | [row absent] | 2499 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 616 | INSERT | VERSION_NO | [row absent] | 1 | Sign-in/refresh for approved user 2499; revoked by rotation/logout |
| AUTH_SESSION | 617 | INSERT | CREATED_AT | [row absent] | 2026-09-20T21:05:47.535078+00:00 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 617 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T21:05:47.535078+00:00 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 617 | INSERT | LAST_USED_AT | [row absent] | 2026-09-20T21:05:48.023017+00:00 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 617 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | 618 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 617 | INSERT | REVOCATION_REASON | [row absent] | ROTATED | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 617 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T21:05:48.023017+00:00 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 617 | INSERT | SESSION_ID | [row absent] | 617 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 617 | INSERT | USER_ID | [row absent] | 2498 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 617 | INSERT | VERSION_NO | [row absent] | 2 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 618 | INSERT | CREATED_AT | [row absent] | 2026-09-20T21:05:48.023017+00:00 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 618 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T21:05:48.023017+00:00 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 618 | INSERT | LAST_USED_AT | [row absent] | NULL | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 618 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | NULL | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 618 | INSERT | REVOCATION_REASON | [row absent] | LOGOUT | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 618 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T21:06:12.441061+00:00 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 618 | INSERT | SESSION_ID | [row absent] | 618 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 618 | INSERT | USER_ID | [row absent] | 2498 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 618 | INSERT | VERSION_NO | [row absent] | 1 | Sign-in/refresh for approved user 2498; revoked by rotation/logout |
| AUTH_SESSION | 619 | INSERT | CREATED_AT | [row absent] | 2026-09-20T21:06:13.954262+00:00 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 619 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T21:06:13.954262+00:00 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 619 | INSERT | LAST_USED_AT | [row absent] | 2026-09-20T21:06:14.412425+00:00 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 619 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | 620 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 619 | INSERT | REVOCATION_REASON | [row absent] | ROTATED | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 619 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T21:06:14.412425+00:00 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 619 | INSERT | SESSION_ID | [row absent] | 619 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 619 | INSERT | USER_ID | [row absent] | 2500 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 619 | INSERT | VERSION_NO | [row absent] | 2 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 620 | INSERT | CREATED_AT | [row absent] | 2026-09-20T21:06:14.412425+00:00 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 620 | INSERT | EXPIRES_AT | [row absent] | 2026-09-27T21:06:14.412425+00:00 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 620 | INSERT | LAST_USED_AT | [row absent] | NULL | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 620 | INSERT | REPLACED_BY_SESSION_ID | [row absent] | NULL | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 620 | INSERT | REVOCATION_REASON | [row absent] | LOGOUT | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 620 | INSERT | REVOKED_AT | [row absent] | 2026-09-20T21:06:51.094886+00:00 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 620 | INSERT | SESSION_ID | [row absent] | 620 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 620 | INSERT | USER_ID | [row absent] | 2500 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| AUTH_SESSION | 620 | INSERT | VERSION_NO | [row absent] | 1 | Sign-in/refresh for approved user 2500; revoked by rotation/logout |
| ACCOUNT | 1626 | UPDATE | AVAILABLE_BALANCE | 170000 | 169999 | Browser payment 1558: 1.00 reserved, RELEASED; current balance unchanged |
| ACCOUNT | 1626 | UPDATE | RESERVED_AMOUNT | 180000 | 180001 | Browser payment 1558: 1.00 reserved, RELEASED; current balance unchanged |
| ACCOUNT | 1626 | UPDATE | UPDATED_AT | 2026-09-20T14:16:46.575117+00:00 | 2026-09-20T20:52:39.459157+00:00 | Browser payment 1558: 1.00 reserved, RELEASED; current balance unchanged |
| ACCOUNT | 1626 | UPDATE | VERSION_NO | 1 | 2 | Browser payment 1558: 1.00 reserved, RELEASED; current balance unchanged |
| ACCOUNT | 1628 | UPDATE | UPDATED_AT | 2026-09-17T16:26:29.361590+05:30 | 2026-09-20T20:50:42.697188+00:00 | API protected/cancel tests 1554/1555: version/timestamp changed; final funds unchanged |
| ACCOUNT | 1628 | UPDATE | VERSION_NO | 0 | 4 | API protected/cancel tests 1554/1555: version/timestamp changed; final funds unchanged |
| BENEFICIARY | 1751 | INSERT | BANK_ACCOUNT_NUMBER | [row absent] | NULL | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | BANK_NAME | [row absent] | NULL | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | BENEFICIARY_ID | [row absent] | 1751 | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | BENEFICIARY_NAME | [row absent] | SafePay Integration Demo | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:55:02.979949+00:00 | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | IFSC_CODE | [row absent] | NULL | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | NICKNAME | [row absent] | Integration check | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | OWNER_USER_ID | [row absent] | 2468 | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | PAYMENT_METHOD | [row absent] | UPI | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | PURPOSE_NOTE | [row absent] | Approved UI verification; do not use for payments | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | RELATIONSHIP_LABEL | [row absent] | Demo fixture | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | STATUS | [row absent] | DISABLED | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:59:38.856285+00:00 | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | UPI_ID | [row absent] | safepay.integration@demo | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| BENEFICIARY | 1751 | INSERT | VERSION_NO | [row absent] | 3 | Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | AMOUNT | [row absent] | 5000.01 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | AUTHORIZED_AT | [row absent] | 2026-09-20T20:50:41.975799+00:00 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | BENEFICIARY_ID | [row absent] | 1160 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | CANCELLED_AT | [row absent] | 2026-09-20T20:50:42.432263+00:00 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:41.419878+00:00 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | CURRENCY_CODE | [row absent] | INR | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | CUSTOMER_REFERENCE | [row absent] | SP-INTEGRATION-20260921 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | CUSTOMER_USER_ID | [row absent] | 2470 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | FAILED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | MATCHED_BAND_CODE | [row absent] | AMOUNT_MEDIUM_V1 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | PAYMENT_CATEGORY | [row absent] | NULL | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | POLICY_VERSION | [row absent] | AMOUNT_ONLY_V1 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | PROTECTED_UNTIL | [row absent] | 2026-09-20T20:50:51.980316+00:00 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | PROTECTION_POLICY_ID | [row absent] | 2 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | PROTECTION_SECONDS | [row absent] | 10 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | PURPOSE | [row absent] | Approved demo integration verification | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | RELEASED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | RESERVATION_ENDED_AT | [row absent] | 2026-09-20T20:50:42.432263+00:00 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | RESERVED_AMOUNT | [row absent] | 0 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | RESERVED_AT | [row absent] | 2026-09-20T20:50:41.980316+00:00 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | RISK_ASSESSED_AT | [row absent] | 2026-09-20T20:50:41.980316+00:00 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | RISK_EXPLANATION | [row absent] | The payment amount matched the SafePay V1 MEDIUM band. | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | RISK_POLICY_BAND_ID | [row absent] | 2 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | RISK_POLICY_ID | [row absent] | 1 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | RISK_SCORE | [row absent] | NULL | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | RISK_TIER | [row absent] | MEDIUM | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | SETTLED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | SOURCE_ACCOUNT_ID | [row absent] | 1628 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | STATE | [row absent] | CANCELLED | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | TERMINAL_REASON_CODE | [row absent] | CUSTOMER_CANCELLED | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | TRANSACTION_ID | [row absent] | 1554 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | TRANSACTION_REFERENCE | [row absent] | SP-3835163624A74E13B1D35C929E68A32B | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:50:42.432263+00:00 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | VERIFICATION_COMPLETED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1554 | INSERT | VERSION_NO | [row absent] | 2 | LIVE_API_TESTS.md: payment 1554 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | AMOUNT | [row absent] | 25000.01 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | AUTHORIZED_AT | [row absent] | 2026-09-20T20:50:42.630015+00:00 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | BENEFICIARY_ID | [row absent] | 1160 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | CANCELLED_AT | [row absent] | 2026-09-20T20:50:42.697188+00:00 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.567163+00:00 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | CURRENCY_CODE | [row absent] | INR | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | CUSTOMER_REFERENCE | [row absent] | SP-INTEGRATION-20260921 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | CUSTOMER_USER_ID | [row absent] | 2470 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | FAILED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | MATCHED_BAND_CODE | [row absent] | AMOUNT_HIGH_V1 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | PAYMENT_CATEGORY | [row absent] | NULL | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | POLICY_VERSION | [row absent] | AMOUNT_ONLY_V1 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | PROTECTED_UNTIL | [row absent] | 2026-09-20T20:51:42.630015+00:00 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | PROTECTION_POLICY_ID | [row absent] | 3 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | PROTECTION_SECONDS | [row absent] | 60 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | PURPOSE | [row absent] | Approved demo integration verification | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | RELEASED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | RESERVATION_ENDED_AT | [row absent] | 2026-09-20T20:50:42.697188+00:00 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | RESERVED_AMOUNT | [row absent] | 0 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | RESERVED_AT | [row absent] | 2026-09-20T20:50:42.630015+00:00 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | RISK_ASSESSED_AT | [row absent] | 2026-09-20T20:50:42.630015+00:00 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | RISK_EXPLANATION | [row absent] | The payment amount matched the SafePay V1 HIGH band. | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | RISK_POLICY_BAND_ID | [row absent] | 3 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | RISK_POLICY_ID | [row absent] | 1 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | RISK_SCORE | [row absent] | NULL | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | RISK_TIER | [row absent] | HIGH | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | SETTLED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | SOURCE_ACCOUNT_ID | [row absent] | 1628 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | STATE | [row absent] | CANCELLED | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | TERMINAL_REASON_CODE | [row absent] | CUSTOMER_CANCELLED | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | TRANSACTION_ID | [row absent] | 1555 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | TRANSACTION_REFERENCE | [row absent] | SP-0553AAD60C12448A935C9FBD9F9F22AD | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:50:42.697188+00:00 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | VERIFICATION_COMPLETED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1555 | INSERT | VERSION_NO | [row absent] | 2 | LIVE_API_TESTS.md: payment 1555 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | AMOUNT | [row absent] | 100000.01 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | AUTHORIZED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | BENEFICIARY_ID | [row absent] | 1160 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | CANCELLED_AT | [row absent] | 2026-09-20T20:50:42.835563+00:00 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.782254+00:00 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | CURRENCY_CODE | [row absent] | INR | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | CUSTOMER_REFERENCE | [row absent] | SP-INTEGRATION-20260921 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | CUSTOMER_USER_ID | [row absent] | 2470 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | FAILED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | MATCHED_BAND_CODE | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | PAYMENT_CATEGORY | [row absent] | MEDICAL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | POLICY_VERSION | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | PROTECTED_UNTIL | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | PROTECTION_POLICY_ID | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | PROTECTION_SECONDS | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | PURPOSE | [row absent] | Approved demo integration verification | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | RELEASED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | RESERVATION_ENDED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | RESERVED_AMOUNT | [row absent] | 0 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | RESERVED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | RISK_ASSESSED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | RISK_EXPLANATION | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | RISK_POLICY_BAND_ID | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | RISK_POLICY_ID | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | RISK_SCORE | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | RISK_TIER | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | SETTLED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | SOURCE_ACCOUNT_ID | [row absent] | 1628 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | STATE | [row absent] | CANCELLED | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | TERMINAL_REASON_CODE | [row absent] | CUSTOMER_CANCELLED | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | TRANSACTION_ID | [row absent] | 1556 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | TRANSACTION_REFERENCE | [row absent] | SP-CDC00BE7CA074F66B2A5B85810623759 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:50:42.835563+00:00 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | VERIFICATION_COMPLETED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1556 | INSERT | VERSION_NO | [row absent] | 1 | LIVE_API_TESTS.md: payment 1556 / CANCELLED |
| PAYMENT_TRANSACTION | 1557 | INSERT | AMOUNT | [row absent] | 200000.01 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | AUTHORIZED_AT | [row absent] | 2026-09-20T20:50:42.951370+00:00 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | BENEFICIARY_ID | [row absent] | 1160 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | CANCELLED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.897122+00:00 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | CURRENCY_CODE | [row absent] | INR | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | CUSTOMER_REFERENCE | [row absent] | SP-INTEGRATION-20260921 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | CUSTOMER_USER_ID | [row absent] | 2470 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | FAILED_AT | [row absent] | 2026-09-20T20:50:42.957371+00:00 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | MATCHED_BAND_CODE | [row absent] | AMOUNT_VERY_HIGH_V1 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | PAYMENT_CATEGORY | [row absent] | MEDICAL | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | POLICY_VERSION | [row absent] | AMOUNT_ONLY_V1 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | PROTECTED_UNTIL | [row absent] | NULL | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | PROTECTION_POLICY_ID | [row absent] | 4 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | PROTECTION_SECONDS | [row absent] | NULL | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | PURPOSE | [row absent] | Approved demo integration verification | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | RELEASED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | RESERVATION_ENDED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | RESERVED_AMOUNT | [row absent] | 0 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | RESERVED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | RISK_ASSESSED_AT | [row absent] | 2026-09-20T20:50:42.952366+00:00 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | RISK_EXPLANATION | [row absent] | The payment amount matched the SafePay V1 VERY_HIGH band. | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | RISK_POLICY_BAND_ID | [row absent] | 4 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | RISK_POLICY_ID | [row absent] | 1 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | RISK_SCORE | [row absent] | NULL | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | RISK_TIER | [row absent] | VERY_HIGH | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | SETTLED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | SOURCE_ACCOUNT_ID | [row absent] | 1628 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | STATE | [row absent] | FAILED | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | TERMINAL_REASON_CODE | [row absent] | INSUFFICIENT_AVAILABLE_BALANCE | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | TRANSACTION_ID | [row absent] | 1557 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | TRANSACTION_REFERENCE | [row absent] | SP-191D25B897804965A100C0CBCB17E73B | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:50:42.957371+00:00 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | VERIFICATION_COMPLETED_AT | [row absent] | NULL | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1557 | INSERT | VERSION_NO | [row absent] | 1 | LIVE_API_TESTS.md: payment 1557 / FAILED |
| PAYMENT_TRANSACTION | 1558 | INSERT | AMOUNT | [row absent] | 1 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | AUTHORIZED_AT | [row absent] | 2026-09-20T20:52:39.458160+00:00 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | BENEFICIARY_ID | [row absent] | 1158 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | CANCELLED_AT | [row absent] | NULL | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:51:40.416250+00:00 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | CURRENCY_CODE | [row absent] | INR | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | CUSTOMER_REFERENCE | [row absent] | SP-UI-20260921-LOW | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | CUSTOMER_USER_ID | [row absent] | 2468 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | FAILED_AT | [row absent] | NULL | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | MATCHED_BAND_CODE | [row absent] | AMOUNT_LOW_V1 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | PAYMENT_CATEGORY | [row absent] | NULL | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | POLICY_VERSION | [row absent] | AMOUNT_ONLY_V1 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | PROTECTED_UNTIL | [row absent] | NULL | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | PROTECTION_POLICY_ID | [row absent] | 1 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | PROTECTION_SECONDS | [row absent] | 0 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | PURPOSE | [row absent] | Approved demo frontend integration check | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | RELEASED_AT | [row absent] | 2026-09-20T20:52:39.459157+00:00 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | RESERVATION_ENDED_AT | [row absent] | NULL | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | RESERVED_AMOUNT | [row absent] | 1 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | RESERVED_AT | [row absent] | 2026-09-20T20:52:39.459157+00:00 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | RISK_ASSESSED_AT | [row absent] | 2026-09-20T20:52:39.459157+00:00 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | RISK_EXPLANATION | [row absent] | The payment amount matched the SafePay V1 LOW band. | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | RISK_POLICY_BAND_ID | [row absent] | 1 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | RISK_POLICY_ID | [row absent] | 1 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | RISK_SCORE | [row absent] | NULL | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | RISK_TIER | [row absent] | LOW | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | SETTLED_AT | [row absent] | NULL | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | SOURCE_ACCOUNT_ID | [row absent] | 1626 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | STATE | [row absent] | RELEASED | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | TERMINAL_REASON_CODE | [row absent] | NULL | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | TRANSACTION_ID | [row absent] | 1558 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | TRANSACTION_REFERENCE | [row absent] | SP-F4765F0E10C54E2AAFD4E812D1263256 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:52:39.459157+00:00 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | VERIFICATION_COMPLETED_AT | [row absent] | NULL | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| PAYMENT_TRANSACTION | 1558 | INSERT | VERSION_NO | [row absent] | 1 | LIVE_BROWSER_TESTS.md: payment 1558 / RELEASED |
| TRANSACTION_RISK_FACTOR | 936 | INSERT | EVALUATED_AT | [row absent] | 2026-09-20T20:50:41.980316+00:00 | Authorization of test payment 1554; MEDIUM amount band |
| TRANSACTION_RISK_FACTOR | 936 | INSERT | EXPLANATION | [row absent] | The payment amount matched the SafePay V1 MEDIUM band. | Authorization of test payment 1554; MEDIUM amount band |
| TRANSACTION_RISK_FACTOR | 936 | INSERT | FACTOR_CODE | [row absent] | PAYMENT_AMOUNT | Authorization of test payment 1554; MEDIUM amount band |
| TRANSACTION_RISK_FACTOR | 936 | INSERT | RAW_VALUE | [row absent] | 5000.01 | Authorization of test payment 1554; MEDIUM amount band |
| TRANSACTION_RISK_FACTOR | 936 | INSERT | RESULTING_TIER | [row absent] | MEDIUM | Authorization of test payment 1554; MEDIUM amount band |
| TRANSACTION_RISK_FACTOR | 936 | INSERT | RISK_POLICY_BAND_ID | [row absent] | 2 | Authorization of test payment 1554; MEDIUM amount band |
| TRANSACTION_RISK_FACTOR | 936 | INSERT | TRANSACTION_ID | [row absent] | 1554 | Authorization of test payment 1554; MEDIUM amount band |
| TRANSACTION_RISK_FACTOR | 936 | INSERT | TRANSACTION_RISK_FACTOR_ID | [row absent] | 936 | Authorization of test payment 1554; MEDIUM amount band |
| TRANSACTION_RISK_FACTOR | 937 | INSERT | EVALUATED_AT | [row absent] | 2026-09-20T20:50:42.630015+00:00 | Authorization of test payment 1555; HIGH amount band |
| TRANSACTION_RISK_FACTOR | 937 | INSERT | EXPLANATION | [row absent] | The payment amount matched the SafePay V1 HIGH band. | Authorization of test payment 1555; HIGH amount band |
| TRANSACTION_RISK_FACTOR | 937 | INSERT | FACTOR_CODE | [row absent] | PAYMENT_AMOUNT | Authorization of test payment 1555; HIGH amount band |
| TRANSACTION_RISK_FACTOR | 937 | INSERT | RAW_VALUE | [row absent] | 25000.01 | Authorization of test payment 1555; HIGH amount band |
| TRANSACTION_RISK_FACTOR | 937 | INSERT | RESULTING_TIER | [row absent] | HIGH | Authorization of test payment 1555; HIGH amount band |
| TRANSACTION_RISK_FACTOR | 937 | INSERT | RISK_POLICY_BAND_ID | [row absent] | 3 | Authorization of test payment 1555; HIGH amount band |
| TRANSACTION_RISK_FACTOR | 937 | INSERT | TRANSACTION_ID | [row absent] | 1555 | Authorization of test payment 1555; HIGH amount band |
| TRANSACTION_RISK_FACTOR | 937 | INSERT | TRANSACTION_RISK_FACTOR_ID | [row absent] | 937 | Authorization of test payment 1555; HIGH amount band |
| TRANSACTION_RISK_FACTOR | 938 | INSERT | EVALUATED_AT | [row absent] | 2026-09-20T20:50:42.952366+00:00 | Authorization of test payment 1557; VERY_HIGH amount band |
| TRANSACTION_RISK_FACTOR | 938 | INSERT | EXPLANATION | [row absent] | The payment amount matched the SafePay V1 VERY_HIGH band. | Authorization of test payment 1557; VERY_HIGH amount band |
| TRANSACTION_RISK_FACTOR | 938 | INSERT | FACTOR_CODE | [row absent] | PAYMENT_AMOUNT | Authorization of test payment 1557; VERY_HIGH amount band |
| TRANSACTION_RISK_FACTOR | 938 | INSERT | RAW_VALUE | [row absent] | 200000.01 | Authorization of test payment 1557; VERY_HIGH amount band |
| TRANSACTION_RISK_FACTOR | 938 | INSERT | RESULTING_TIER | [row absent] | VERY_HIGH | Authorization of test payment 1557; VERY_HIGH amount band |
| TRANSACTION_RISK_FACTOR | 938 | INSERT | RISK_POLICY_BAND_ID | [row absent] | 4 | Authorization of test payment 1557; VERY_HIGH amount band |
| TRANSACTION_RISK_FACTOR | 938 | INSERT | TRANSACTION_ID | [row absent] | 1557 | Authorization of test payment 1557; VERY_HIGH amount band |
| TRANSACTION_RISK_FACTOR | 938 | INSERT | TRANSACTION_RISK_FACTOR_ID | [row absent] | 938 | Authorization of test payment 1557; VERY_HIGH amount band |
| TRANSACTION_RISK_FACTOR | 939 | INSERT | EVALUATED_AT | [row absent] | 2026-09-20T20:52:39.459157+00:00 | Authorization of test payment 1558; LOW amount band |
| TRANSACTION_RISK_FACTOR | 939 | INSERT | EXPLANATION | [row absent] | The payment amount matched the SafePay V1 LOW band. | Authorization of test payment 1558; LOW amount band |
| TRANSACTION_RISK_FACTOR | 939 | INSERT | FACTOR_CODE | [row absent] | PAYMENT_AMOUNT | Authorization of test payment 1558; LOW amount band |
| TRANSACTION_RISK_FACTOR | 939 | INSERT | RAW_VALUE | [row absent] | 1.00 | Authorization of test payment 1558; LOW amount band |
| TRANSACTION_RISK_FACTOR | 939 | INSERT | RESULTING_TIER | [row absent] | LOW | Authorization of test payment 1558; LOW amount band |
| TRANSACTION_RISK_FACTOR | 939 | INSERT | RISK_POLICY_BAND_ID | [row absent] | 1 | Authorization of test payment 1558; LOW amount band |
| TRANSACTION_RISK_FACTOR | 939 | INSERT | TRANSACTION_ID | [row absent] | 1558 | Authorization of test payment 1558; LOW amount band |
| TRANSACTION_RISK_FACTOR | 939 | INSERT | TRANSACTION_RISK_FACTOR_ID | [row absent] | 939 | Authorization of test payment 1558; LOW amount band |
| IDEMPOTENCY_RECORD | 365 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:50:41.509809+00:00 | TRANSACTION_CREATE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 365 | INSERT | CORRELATION_ID | [row absent] | 740d9a52-2aa8-4d7e-9c78-cdc8438864e9 | TRANSACTION_CREATE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 365 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:41.332155+00:00 | TRANSACTION_CREATE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 365 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:50:41.332155+00:00 | TRANSACTION_CREATE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 365 | INSERT | HTTP_STATUS | [row absent] | 201 | TRANSACTION_CREATE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 365 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 365 | TRANSACTION_CREATE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 365 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_CREATE | TRANSACTION_CREATE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 365 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_CREATE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 365 | INSERT | TRANSACTION_ID | [row absent] | 1554 | TRANSACTION_CREATE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 365 | INSERT | USER_ID | [row absent] | 2470 | TRANSACTION_CREATE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 365 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_CREATE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 366 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:50:42.335834+00:00 | TRANSACTION_AUTHORIZE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 366 | INSERT | CORRELATION_ID | [row absent] | 9d8738d6-2707-4f2f-b246-27c29ec606f7 | TRANSACTION_AUTHORIZE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 366 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:41.723026+00:00 | TRANSACTION_AUTHORIZE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 366 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:50:41.723026+00:00 | TRANSACTION_AUTHORIZE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 366 | INSERT | HTTP_STATUS | [row absent] | 200 | TRANSACTION_AUTHORIZE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 366 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 366 | TRANSACTION_AUTHORIZE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 366 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_AUTHORIZE | TRANSACTION_AUTHORIZE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 366 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_AUTHORIZE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 366 | INSERT | TRANSACTION_ID | [row absent] | 1554 | TRANSACTION_AUTHORIZE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 366 | INSERT | USER_ID | [row absent] | 2470 | TRANSACTION_AUTHORIZE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 366 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_AUTHORIZE payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 367 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:50:42.460264+00:00 | TRANSACTION_CANCEL payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 367 | INSERT | CORRELATION_ID | [row absent] | 3d610f3e-65ef-4a4a-bdda-720df5df1259 | TRANSACTION_CANCEL payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 367 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.423879+00:00 | TRANSACTION_CANCEL payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 367 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:50:42.423879+00:00 | TRANSACTION_CANCEL payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 367 | INSERT | HTTP_STATUS | [row absent] | 200 | TRANSACTION_CANCEL payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 367 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 367 | TRANSACTION_CANCEL payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 367 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_CANCEL | TRANSACTION_CANCEL payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 367 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_CANCEL payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 367 | INSERT | TRANSACTION_ID | [row absent] | 1554 | TRANSACTION_CANCEL payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 367 | INSERT | USER_ID | [row absent] | 2470 | TRANSACTION_CANCEL payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 367 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_CANCEL payment 1554; completed original-key evidence |
| IDEMPOTENCY_RECORD | 368 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:50:42.576164+00:00 | TRANSACTION_CREATE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 368 | INSERT | CORRELATION_ID | [row absent] | adbf19cf-fba6-4e23-9e66-f1fee38b9943 | TRANSACTION_CREATE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 368 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.555405+00:00 | TRANSACTION_CREATE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 368 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:50:42.555405+00:00 | TRANSACTION_CREATE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 368 | INSERT | HTTP_STATUS | [row absent] | 201 | TRANSACTION_CREATE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 368 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 368 | TRANSACTION_CREATE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 368 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_CREATE | TRANSACTION_CREATE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 368 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_CREATE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 368 | INSERT | TRANSACTION_ID | [row absent] | 1555 | TRANSACTION_CREATE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 368 | INSERT | USER_ID | [row absent] | 2470 | TRANSACTION_CREATE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 368 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_CREATE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 369 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:50:42.651948+00:00 | TRANSACTION_AUTHORIZE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 369 | INSERT | CORRELATION_ID | [row absent] | 73e3e0e4-2f2d-4319-93e3-b545771266c9 | TRANSACTION_AUTHORIZE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 369 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.614155+00:00 | TRANSACTION_AUTHORIZE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 369 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:50:42.614155+00:00 | TRANSACTION_AUTHORIZE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 369 | INSERT | HTTP_STATUS | [row absent] | 200 | TRANSACTION_AUTHORIZE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 369 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 369 | TRANSACTION_AUTHORIZE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 369 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_AUTHORIZE | TRANSACTION_AUTHORIZE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 369 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_AUTHORIZE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 369 | INSERT | TRANSACTION_ID | [row absent] | 1555 | TRANSACTION_AUTHORIZE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 369 | INSERT | USER_ID | [row absent] | 2470 | TRANSACTION_AUTHORIZE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 369 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_AUTHORIZE payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 370 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:50:42.714185+00:00 | TRANSACTION_CANCEL payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 370 | INSERT | CORRELATION_ID | [row absent] | 53fa44ee-97d2-4f2d-b598-420f7366a239 | TRANSACTION_CANCEL payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 370 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.688233+00:00 | TRANSACTION_CANCEL payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 370 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:50:42.688233+00:00 | TRANSACTION_CANCEL payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 370 | INSERT | HTTP_STATUS | [row absent] | 200 | TRANSACTION_CANCEL payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 370 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 370 | TRANSACTION_CANCEL payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 370 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_CANCEL | TRANSACTION_CANCEL payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 370 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_CANCEL payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 370 | INSERT | TRANSACTION_ID | [row absent] | 1555 | TRANSACTION_CANCEL payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 370 | INSERT | USER_ID | [row absent] | 2470 | TRANSACTION_CANCEL payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 370 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_CANCEL payment 1555; completed original-key evidence |
| IDEMPOTENCY_RECORD | 371 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:50:42.789253+00:00 | TRANSACTION_CREATE payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 371 | INSERT | CORRELATION_ID | [row absent] | dbda9a09-f16f-4e79-938d-7a88100c3d22 | TRANSACTION_CREATE payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 371 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.769943+00:00 | TRANSACTION_CREATE payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 371 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:50:42.769943+00:00 | TRANSACTION_CREATE payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 371 | INSERT | HTTP_STATUS | [row absent] | 201 | TRANSACTION_CREATE payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 371 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 371 | TRANSACTION_CREATE payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 371 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_CREATE | TRANSACTION_CREATE payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 371 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_CREATE payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 371 | INSERT | TRANSACTION_ID | [row absent] | 1556 | TRANSACTION_CREATE payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 371 | INSERT | USER_ID | [row absent] | 2470 | TRANSACTION_CREATE payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 371 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_CREATE payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 372 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:50:42.851742+00:00 | TRANSACTION_CANCEL payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 372 | INSERT | CORRELATION_ID | [row absent] | e4b83e5e-954b-49b4-ba61-7950b427beaf | TRANSACTION_CANCEL payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 372 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.824260+00:00 | TRANSACTION_CANCEL payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 372 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:50:42.824260+00:00 | TRANSACTION_CANCEL payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 372 | INSERT | HTTP_STATUS | [row absent] | 200 | TRANSACTION_CANCEL payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 372 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 372 | TRANSACTION_CANCEL payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 372 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_CANCEL | TRANSACTION_CANCEL payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 372 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_CANCEL payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 372 | INSERT | TRANSACTION_ID | [row absent] | 1556 | TRANSACTION_CANCEL payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 372 | INSERT | USER_ID | [row absent] | 2470 | TRANSACTION_CANCEL payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 372 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_CANCEL payment 1556; completed original-key evidence |
| IDEMPOTENCY_RECORD | 373 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:50:42.903125+00:00 | TRANSACTION_CREATE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 373 | INSERT | CORRELATION_ID | [row absent] | 506111bd-da98-469b-9e9d-f2c94cdac553 | TRANSACTION_CREATE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 373 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.883126+00:00 | TRANSACTION_CREATE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 373 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:50:42.883126+00:00 | TRANSACTION_CREATE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 373 | INSERT | HTTP_STATUS | [row absent] | 201 | TRANSACTION_CREATE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 373 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 373 | TRANSACTION_CREATE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 373 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_CREATE | TRANSACTION_CREATE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 373 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_CREATE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 373 | INSERT | TRANSACTION_ID | [row absent] | 1557 | TRANSACTION_CREATE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 373 | INSERT | USER_ID | [row absent] | 2470 | TRANSACTION_CREATE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 373 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_CREATE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 374 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:50:42.975370+00:00 | TRANSACTION_AUTHORIZE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 374 | INSERT | CORRELATION_ID | [row absent] | d654aab5-4f7a-4e65-bd05-acb44c91cf45 | TRANSACTION_AUTHORIZE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 374 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.934365+00:00 | TRANSACTION_AUTHORIZE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 374 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:50:42.934365+00:00 | TRANSACTION_AUTHORIZE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 374 | INSERT | HTTP_STATUS | [row absent] | 200 | TRANSACTION_AUTHORIZE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 374 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 374 | TRANSACTION_AUTHORIZE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 374 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_AUTHORIZE | TRANSACTION_AUTHORIZE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 374 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_AUTHORIZE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 374 | INSERT | TRANSACTION_ID | [row absent] | 1557 | TRANSACTION_AUTHORIZE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 374 | INSERT | USER_ID | [row absent] | 2470 | TRANSACTION_AUTHORIZE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 374 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_AUTHORIZE payment 1557; completed original-key evidence |
| IDEMPOTENCY_RECORD | 376 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:51:40.455234+00:00 | TRANSACTION_CREATE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 376 | INSERT | CORRELATION_ID | [row absent] | d142f31e-72cb-4aed-b62b-d6a18bfa5ffa | TRANSACTION_CREATE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 376 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:51:40.379124+00:00 | TRANSACTION_CREATE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 376 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:51:40.379124+00:00 | TRANSACTION_CREATE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 376 | INSERT | HTTP_STATUS | [row absent] | 201 | TRANSACTION_CREATE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 376 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 376 | TRANSACTION_CREATE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 376 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_CREATE | TRANSACTION_CREATE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 376 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_CREATE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 376 | INSERT | TRANSACTION_ID | [row absent] | 1558 | TRANSACTION_CREATE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 376 | INSERT | USER_ID | [row absent] | 2468 | TRANSACTION_CREATE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 376 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_CREATE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 377 | INSERT | COMPLETED_AT | [row absent] | 2026-09-20T20:52:39.546161+00:00 | TRANSACTION_AUTHORIZE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 377 | INSERT | CORRELATION_ID | [row absent] | 5bc00c72-cb69-4906-8cb4-2449964219a1 | TRANSACTION_AUTHORIZE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 377 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:52:39.371959+00:00 | TRANSACTION_AUTHORIZE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 377 | INSERT | EXPIRES_AT | [row absent] | 2026-09-21T08:52:39.371959+00:00 | TRANSACTION_AUTHORIZE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 377 | INSERT | HTTP_STATUS | [row absent] | 200 | TRANSACTION_AUTHORIZE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 377 | INSERT | IDEMPOTENCY_RECORD_ID | [row absent] | 377 | TRANSACTION_AUTHORIZE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 377 | INSERT | OPERATION_CODE | [row absent] | TRANSACTION_AUTHORIZE | TRANSACTION_AUTHORIZE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 377 | INSERT | STATUS | [row absent] | COMPLETED | TRANSACTION_AUTHORIZE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 377 | INSERT | TRANSACTION_ID | [row absent] | 1558 | TRANSACTION_AUTHORIZE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 377 | INSERT | USER_ID | [row absent] | 2468 | TRANSACTION_AUTHORIZE payment 1558; completed original-key evidence |
| IDEMPOTENCY_RECORD | 377 | INSERT | VERSION_NO | [row absent] | 1 | TRANSACTION_AUTHORIZE payment 1558; completed original-key evidence |
| AUDIT_LOG | 2138 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGIN_SUCCEEDED | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | AUDIT_LOG_ID | [row absent] | 2138 | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | CORRELATION_ID | [row absent] | 62c2d441-4179-4d1f-a3f7-d59159176b54 | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | ENTITY_ID | [row absent] | 2468 | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | EVENT_REFERENCE | [row absent] | b1954204-ca6f-42d2-8cd4-86583b452d47 | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:35.391755+00:00 | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2138 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGIN_SUCCEEDED | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | AUDIT_LOG_ID | [row absent] | 2139 | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | CORRELATION_ID | [row absent] | cccc3634-f3e4-4022-9b1b-16a56366d13d | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | ENTITY_ID | [row absent] | 2470 | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | EVENT_REFERENCE | [row absent] | b7d53784-7703-4f5d-9ab0-ba0c413d0e21 | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:38.019883+00:00 | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2139 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGIN_SUCCEEDED | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | ACTOR_USER_ID | [row absent] | 2498 | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | AUDIT_LOG_ID | [row absent] | 2140 | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | CORRELATION_ID | [row absent] | 3b823285-1d8f-4878-aa4b-b3502aeca971 | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | ENTITY_ID | [row absent] | 2498 | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | EVENT_REFERENCE | [row absent] | 74919d95-be07-40b1-9364-46c421d47e98 | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:39.276733+00:00 | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2140 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGIN_SUCCEEDED | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | ACTOR_USER_ID | [row absent] | 2499 | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | AUDIT_LOG_ID | [row absent] | 2141 | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | CORRELATION_ID | [row absent] | 68e52995-7b18-456a-bec1-8bcb7d95a3e4 | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | ENTITY_ID | [row absent] | 2499 | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | EVENT_REFERENCE | [row absent] | f8c910d2-2519-45ad-98b4-139504e59e03 | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:39.918824+00:00 | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2141 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGIN_SUCCEEDED | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | ACTOR_USER_ID | [row absent] | 2500 | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | AUDIT_LOG_ID | [row absent] | 2142 | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | CORRELATION_ID | [row absent] | 571893ca-3cb4-4a83-8121-a58392345d1e | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | ENTITY_ID | [row absent] | 2500 | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | EVENT_REFERENCE | [row absent] | 7c6dc15c-0247-4be0-a161-2e262a744918 | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:40.576666+00:00 | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2142 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_ROTATED | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | AUDIT_LOG_ID | [row absent] | 2143 | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | CORRELATION_ID | [row absent] | 1b57f88c-5a4b-42eb-af22-e4437b4902ba | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | ENTITY_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | EVENT_REFERENCE | [row absent] | 88ce35f6-fee7-496a-97c5-c714d8640b81 | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:40.757695+00:00 | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2143 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | ACTION_CODE | [row absent] | AUTH_FORBIDDEN_ACCESS | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | AUDIT_LOG_ID | [row absent] | 2144 | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | CORRELATION_ID | [row absent] | 066b983c-6b1a-46c7-b853-574db41f07f6 | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | ENTITY_ID | [row absent] | 2468 | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | EVENT_REFERENCE | [row absent] | 9cd3094d-33f1-4a89-b5d3-bdd6e9452390 | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:40.856051+00:00 | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | OUTCOME | [row absent] | DENIED | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | REASON_CODE | [row absent] | HTTP_ACCESS_DENIED | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2144 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | ACTION_CODE | [row absent] | AUTH_FORBIDDEN_ACCESS | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | ACTOR_USER_ID | [row absent] | 2499 | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | AUDIT_LOG_ID | [row absent] | 2145 | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | CORRELATION_ID | [row absent] | 1ae3d279-5839-4b5f-b3e8-4a22386a4d7c | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | ENTITY_ID | [row absent] | 2499 | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | EVENT_REFERENCE | [row absent] | c677bc1f-bafd-4da3-b53b-608810a15ae1 | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:41.785784+00:00 | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | OUTCOME | [row absent] | DENIED | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | REASON_CODE | [row absent] | HTTP_ACCESS_DENIED | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2145 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | ACTION_CODE | [row absent] | AUTH_FORBIDDEN_ACCESS | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | ACTOR_USER_ID | [row absent] | 2498 | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | AUDIT_LOG_ID | [row absent] | 2146 | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | CORRELATION_ID | [row absent] | 00046a6b-5bdb-42fc-b1c1-a1e541a134e3 | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | ENTITY_ID | [row absent] | 2498 | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | EVENT_REFERENCE | [row absent] | 9be16ae1-304d-423e-b306-5ec881267d05 | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:42.115286+00:00 | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | OUTCOME | [row absent] | DENIED | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | REASON_CODE | [row absent] | HTTP_ACCESS_DENIED | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2146 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | ACTION_CODE | [row absent] | AUTH_FORBIDDEN_ACCESS | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | ACTOR_USER_ID | [row absent] | 2500 | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | AUDIT_LOG_ID | [row absent] | 2147 | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | CORRELATION_ID | [row absent] | 2cc0ab7f-2e31-4ce1-bab7-6376c19c0efa | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | ENTITY_ID | [row absent] | 2500 | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | EVENT_REFERENCE | [row absent] | 72e43d78-c9c2-4a2e-966c-83351c2d2488 | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:43.678569+00:00 | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | OUTCOME | [row absent] | DENIED | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | REASON_CODE | [row absent] | HTTP_ACCESS_DENIED | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2147 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_FORBIDDEN_ACCESS; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGOUT | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | AUDIT_LOG_ID | [row absent] | 2148 | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | CORRELATION_ID | [row absent] | 5f6fee8d-4591-4109-91ee-13885fc04319 | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | ENTITY_ID | [row absent] | 2468 | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | EVENT_REFERENCE | [row absent] | d0af4aff-e5ca-44ad-bcd9-5565035ba7c4 | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:43.757101+00:00 | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2148 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGOUT; approved actor 2468; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGOUT | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | AUDIT_LOG_ID | [row absent] | 2149 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | CORRELATION_ID | [row absent] | 63053978-22ea-4316-b9c7-b2c69e6a9a09 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | ENTITY_ID | [row absent] | 2470 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | EVENT_REFERENCE | [row absent] | 7dea1122-4d73-46b1-8674-225f70391710 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:43.862415+00:00 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2149 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGOUT | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | ACTOR_USER_ID | [row absent] | 2498 | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | AUDIT_LOG_ID | [row absent] | 2150 | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | CORRELATION_ID | [row absent] | faa726e7-1594-4e89-b6de-487a694b580a | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | ENTITY_ID | [row absent] | 2498 | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | EVENT_REFERENCE | [row absent] | 2c0b42e9-5a00-40a2-b50e-0f3b51ba1061 | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:43.916956+00:00 | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2150 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGOUT; approved actor 2498; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGOUT | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | ACTOR_USER_ID | [row absent] | 2499 | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | AUDIT_LOG_ID | [row absent] | 2151 | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | CORRELATION_ID | [row absent] | 9a93792c-f1a5-4435-8f63-f4de920d1140 | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | ENTITY_ID | [row absent] | 2499 | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | EVENT_REFERENCE | [row absent] | 14b36c08-9f39-4888-9496-0c993500096c | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:43.990072+00:00 | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2151 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGOUT; approved actor 2499; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGOUT | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | ACTOR_USER_ID | [row absent] | 2500 | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | AUDIT_LOG_ID | [row absent] | 2152 | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | CORRELATION_ID | [row absent] | 4e8fc1b4-c22e-4ec8-8e83-1d295427c296 | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | ENTITY_ID | [row absent] | 2500 | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | EVENT_REFERENCE | [row absent] | 1dbd9931-fd90-4504-94de-242a38cc7e92 | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:45:44.058729+00:00 | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2152 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGOUT; approved actor 2500; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2153 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_FAILED | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | ACTOR_TYPE | [row absent] | SYSTEM | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | ACTOR_USER_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | AUDIT_LOG_ID | [row absent] | 2153 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | CORRELATION_ID | [row absent] | 0e8e7c44-6d28-4f9a-8fbd-3af2342af1c7 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | ENTITY_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | ENTITY_TYPE | [row absent] | AUTHENTICATION | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | EVENT_REFERENCE | [row absent] | bbdc7d7f-121b-498a-8272-e7f9c9c43ca7 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | NEW_STATE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:46:53.564550+00:00 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | OUTCOME | [row absent] | DENIED | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | PREVIOUS_STATE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | REASON_CODE | [row absent] | INVALID_REFRESH_TOKEN | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2153 | INSERT | TRANSACTION_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2154 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGIN_SUCCEEDED | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | AUDIT_LOG_ID | [row absent] | 2154 | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | CORRELATION_ID | [row absent] | b5740a6b-02f8-48d3-942b-e04e115c1c58 | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | ENTITY_ID | [row absent] | 2468 | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | EVENT_REFERENCE | [row absent] | 9746196f-ca0a-4164-84df-f3572d28ec5f | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:47:28.716799+00:00 | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2154 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_ROTATED | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | AUDIT_LOG_ID | [row absent] | 2155 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | CORRELATION_ID | [row absent] | c6495be5-2c82-4561-94de-f7cc7a83231d | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | ENTITY_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | EVENT_REFERENCE | [row absent] | 69da350c-e25b-4ccc-a0ba-67ceb8603152 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:47:29.638212+00:00 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2155 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_ROTATED | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | AUDIT_LOG_ID | [row absent] | 2156 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | CORRELATION_ID | [row absent] | 0b503804-eb56-4ffa-a9b2-7649aa8052e5 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | ENTITY_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | EVENT_REFERENCE | [row absent] | 8298e514-7aff-4b06-a083-9f5a356790c7 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:48:49.101078+00:00 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2156 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2157 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGIN_SUCCEEDED | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | AUDIT_LOG_ID | [row absent] | 2157 | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | CORRELATION_ID | [row absent] | 4cc7343b-eb42-4d94-a250-13156a2bec29 | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | ENTITY_ID | [row absent] | 2470 | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | EVENT_REFERENCE | [row absent] | fc58b4e9-4f27-4ee4-8994-1b6fcaa62dd0 | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:41.016948+00:00 | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2157 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | ACTION_CODE | [row absent] | PAYMENT_CREATED | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | AUDIT_LOG_ID | [row absent] | 2158 | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | CORRELATION_ID | [row absent] | 740d9a52-2aa8-4d7e-9c78-cdc8438864e9 | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | ENTITY_ID | [row absent] | 1554 | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | EVENT_REFERENCE | [row absent] | AUD-5d734e6cee2014caae34a703112442d3e9d0eb6aec6658b3 | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | NEW_STATE | [row absent] | CREATED | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:41.419878+00:00 | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | OUTCOME | [row absent] | SUCCESS | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | PREVIOUS_STATE | [row absent] | NULL | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | REASON_CODE | [row absent] | NULL | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2158 | INSERT | TRANSACTION_ID | [row absent] | 1554 | PAYMENT_CREATED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | ACTION_CODE | [row absent] | PAYMENT_PROTECTED | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | AUDIT_LOG_ID | [row absent] | 2159 | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | CORRELATION_ID | [row absent] | 9d8738d6-2707-4f2f-b246-27c29ec606f7 | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | ENTITY_ID | [row absent] | 1554 | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | EVENT_REFERENCE | [row absent] | AUD-512be0d613fbe129959c61cf9eb0939378c9ce09e3bf882e | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | NEW_STATE | [row absent] | PROTECTED | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:41.980316+00:00 | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | OUTCOME | [row absent] | SUCCESS | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | PREVIOUS_STATE | [row absent] | CREATED | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | REASON_CODE | [row absent] | NULL | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2159 | INSERT | TRANSACTION_ID | [row absent] | 1554 | PAYMENT_PROTECTED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | ACTION_CODE | [row absent] | PAYMENT_CANCELLED | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | AUDIT_LOG_ID | [row absent] | 2160 | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | CORRELATION_ID | [row absent] | 3d610f3e-65ef-4a4a-bdda-720df5df1259 | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | ENTITY_ID | [row absent] | 1554 | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | EVENT_REFERENCE | [row absent] | AUD-0100b3df7d9496e35de017101ce93a52204bd62b4aef4a27 | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | NEW_STATE | [row absent] | CANCELLED | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:42.432263+00:00 | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | OUTCOME | [row absent] | SUCCESS | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | PREVIOUS_STATE | [row absent] | PROTECTED | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | REASON_CODE | [row absent] | CUSTOMER_CANCELLED | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2160 | INSERT | TRANSACTION_ID | [row absent] | 1554 | PAYMENT_CANCELLED; test payment 1554; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | ACTION_CODE | [row absent] | PAYMENT_CREATED | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | AUDIT_LOG_ID | [row absent] | 2161 | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | CORRELATION_ID | [row absent] | adbf19cf-fba6-4e23-9e66-f1fee38b9943 | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | ENTITY_ID | [row absent] | 1555 | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | EVENT_REFERENCE | [row absent] | AUD-ca701809bdf681ccc8897025ceca204b28e3cfb5481e3cb6 | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | NEW_STATE | [row absent] | CREATED | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:42.567163+00:00 | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | OUTCOME | [row absent] | SUCCESS | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | PREVIOUS_STATE | [row absent] | NULL | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | REASON_CODE | [row absent] | NULL | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2161 | INSERT | TRANSACTION_ID | [row absent] | 1555 | PAYMENT_CREATED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | ACTION_CODE | [row absent] | PAYMENT_PROTECTED | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | AUDIT_LOG_ID | [row absent] | 2162 | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | CORRELATION_ID | [row absent] | 73e3e0e4-2f2d-4319-93e3-b545771266c9 | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | ENTITY_ID | [row absent] | 1555 | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | EVENT_REFERENCE | [row absent] | AUD-aa865a84353cf81a2844356367321619c7818c18a57f3ee8 | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | NEW_STATE | [row absent] | PROTECTED | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:42.630015+00:00 | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | OUTCOME | [row absent] | SUCCESS | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | PREVIOUS_STATE | [row absent] | CREATED | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | REASON_CODE | [row absent] | NULL | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2162 | INSERT | TRANSACTION_ID | [row absent] | 1555 | PAYMENT_PROTECTED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | ACTION_CODE | [row absent] | PAYMENT_CANCELLED | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | AUDIT_LOG_ID | [row absent] | 2163 | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | CORRELATION_ID | [row absent] | 53fa44ee-97d2-4f2d-b598-420f7366a239 | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | ENTITY_ID | [row absent] | 1555 | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | EVENT_REFERENCE | [row absent] | AUD-46a52188f8dd6b19f3fdd72866fbbf42ae9d0f723edbd125 | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | NEW_STATE | [row absent] | CANCELLED | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:42.697188+00:00 | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | OUTCOME | [row absent] | SUCCESS | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | PREVIOUS_STATE | [row absent] | PROTECTED | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | REASON_CODE | [row absent] | CUSTOMER_CANCELLED | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2163 | INSERT | TRANSACTION_ID | [row absent] | 1555 | PAYMENT_CANCELLED; test payment 1555; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | ACTION_CODE | [row absent] | PAYMENT_CREATED | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | AUDIT_LOG_ID | [row absent] | 2164 | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | CORRELATION_ID | [row absent] | dbda9a09-f16f-4e79-938d-7a88100c3d22 | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | ENTITY_ID | [row absent] | 1556 | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | EVENT_REFERENCE | [row absent] | AUD-a4be4637a5e2ef8604f93445f36a8fe9e013c9a6ed4678a0 | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | NEW_STATE | [row absent] | CREATED | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:42.782254+00:00 | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | OUTCOME | [row absent] | SUCCESS | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | PREVIOUS_STATE | [row absent] | NULL | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | REASON_CODE | [row absent] | NULL | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2164 | INSERT | TRANSACTION_ID | [row absent] | 1556 | PAYMENT_CREATED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | ACTION_CODE | [row absent] | PAYMENT_CANCELLED | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | AUDIT_LOG_ID | [row absent] | 2165 | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | CORRELATION_ID | [row absent] | e4b83e5e-954b-49b4-ba61-7950b427beaf | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | ENTITY_ID | [row absent] | 1556 | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | EVENT_REFERENCE | [row absent] | AUD-b175ac9e2cca52cc6a90ceb613d34b9971bf1611cc1d4d46 | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | NEW_STATE | [row absent] | CANCELLED | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:42.835563+00:00 | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | OUTCOME | [row absent] | SUCCESS | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | PREVIOUS_STATE | [row absent] | CREATED | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | REASON_CODE | [row absent] | CUSTOMER_CANCELLED | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2165 | INSERT | TRANSACTION_ID | [row absent] | 1556 | PAYMENT_CANCELLED; test payment 1556; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | ACTION_CODE | [row absent] | PAYMENT_CREATED | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | AUDIT_LOG_ID | [row absent] | 2166 | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | CORRELATION_ID | [row absent] | 506111bd-da98-469b-9e9d-f2c94cdac553 | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | ENTITY_ID | [row absent] | 1557 | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | EVENT_REFERENCE | [row absent] | AUD-e011fe5797c7c64f58be5a4fbacdfd8969bff881000e9ce2 | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | NEW_STATE | [row absent] | CREATED | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:42.897122+00:00 | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | OUTCOME | [row absent] | SUCCESS | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | PREVIOUS_STATE | [row absent] | NULL | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | REASON_CODE | [row absent] | NULL | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2166 | INSERT | TRANSACTION_ID | [row absent] | 1557 | PAYMENT_CREATED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | ACTION_CODE | [row absent] | PAYMENT_FAILED | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | AUDIT_LOG_ID | [row absent] | 2167 | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | CORRELATION_ID | [row absent] | d654aab5-4f7a-4e65-bd05-acb44c91cf45 | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | ENTITY_ID | [row absent] | 1557 | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | EVENT_REFERENCE | [row absent] | AUD-a72ee3aeb35c2b54cf93c39ad58c31062526b79857988fae | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | NEW_STATE | [row absent] | FAILED | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:42.957371+00:00 | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | OUTCOME | [row absent] | FAILED | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | PREVIOUS_STATE | [row absent] | CREATED | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | REASON_CODE | [row absent] | INSUFFICIENT_AVAILABLE_BALANCE | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2167 | INSERT | TRANSACTION_ID | [row absent] | 1557 | PAYMENT_FAILED; test payment 1557; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGOUT | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | ACTOR_USER_ID | [row absent] | 2470 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | AUDIT_LOG_ID | [row absent] | 2168 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | CORRELATION_ID | [row absent] | e87e2d9d-b379-464a-81e0-c4a459ff2d12 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | ENTITY_ID | [row absent] | 2470 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | EVENT_REFERENCE | [row absent] | 302c4ec6-8dde-46bf-af27-80a8a46a7113 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:50:43.108240+00:00 | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2168 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGOUT; approved actor 2470; correlation matched LIVE_API_TESTS.md |
| AUDIT_LOG | 2169 | INSERT | ACTION_CODE | [row absent] | PAYMENT_CREATED | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | AUDIT_LOG_ID | [row absent] | 2169 | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | CORRELATION_ID | [row absent] | d142f31e-72cb-4aed-b62b-d6a18bfa5ffa | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | ENTITY_ID | [row absent] | 1558 | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | EVENT_REFERENCE | [row absent] | AUD-e7b5004fab3b6f2501210f4f2c217ba2dbd60eb16d97ef59 | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | NEW_STATE | [row absent] | CREATED | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:51:40.416250+00:00 | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | OUTCOME | [row absent] | SUCCESS | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | PREVIOUS_STATE | [row absent] | NULL | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | REASON_CODE | [row absent] | NULL | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2169 | INSERT | TRANSACTION_ID | [row absent] | 1558 | PAYMENT_CREATED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | ACTION_CODE | [row absent] | PAYMENT_RELEASED | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | ACTOR_ROLE_CODE | [row absent] | CUSTOMER | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | ACTOR_TYPE | [row absent] | USER | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | AUDIT_LOG_ID | [row absent] | 2170 | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | CORRELATION_ID | [row absent] | 5bc00c72-cb69-4906-8cb4-2449964219a1 | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | ENTITY_ID | [row absent] | 1558 | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | ENTITY_TYPE | [row absent] | PAYMENT_TRANSACTION | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | EVENT_REFERENCE | [row absent] | AUD-227842c03a87593c4c399d1f4d16460bd0e11437f09dbf64 | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | NEW_STATE | [row absent] | RELEASED | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:52:39.459157+00:00 | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | OUTCOME | [row absent] | SUCCESS | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | PREVIOUS_STATE | [row absent] | CREATED | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | REASON_CODE | [row absent] | NULL | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2170 | INSERT | TRANSACTION_ID | [row absent] | 1558 | PAYMENT_RELEASED; test payment 1558; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_ROTATED | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | AUDIT_LOG_ID | [row absent] | 2171 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | CORRELATION_ID | [row absent] | 627eceef-bd44-403e-8953-7604b8fb8e32 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | ENTITY_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | EVENT_REFERENCE | [row absent] | a85295d5-d75e-4843-9ab9-ab60bbf7e1f4 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:53:26.443050+00:00 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2171 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_ROTATED | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | AUDIT_LOG_ID | [row absent] | 2172 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | CORRELATION_ID | [row absent] | 816726c7-3383-40f7-873d-9eb6a381ecec | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | ENTITY_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | EVENT_REFERENCE | [row absent] | 06cebd91-3bad-4c11-b53b-a37c256b05d4 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:54:42.058134+00:00 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2172 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_ROTATED | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | AUDIT_LOG_ID | [row absent] | 2173 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | CORRELATION_ID | [row absent] | cdd39ce5-ddee-4833-943a-71120314f13d | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | ENTITY_ID | [row absent] | 2468 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | EVENT_REFERENCE | [row absent] | 34e56d75-dabe-4316-b4b7-bad139eeacf2 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:54:48.656475+00:00 | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2173 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGOUT | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | ACTOR_USER_ID | [row absent] | 2468 | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | AUDIT_LOG_ID | [row absent] | 2174 | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | CORRELATION_ID | [row absent] | ddbc973e-cb2e-453f-8cef-91495a56c539 | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | ENTITY_ID | [row absent] | 2468 | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | EVENT_REFERENCE | [row absent] | 4f872958-a915-4d21-a06c-5242f14f6361 | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:59:58.610745+00:00 | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2174 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGOUT; approved actor 2468; browser/action window evidence |
| AUDIT_LOG | 2175 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_FAILED | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | ACTOR_TYPE | [row absent] | SYSTEM | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | ACTOR_USER_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | AUDIT_LOG_ID | [row absent] | 2175 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | CORRELATION_ID | [row absent] | 665e8078-df1a-4b4e-8841-e1bb963f0adf | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | ENTITY_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | ENTITY_TYPE | [row absent] | AUTHENTICATION | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | EVENT_REFERENCE | [row absent] | 68190ea1-82d3-433b-8220-bf0bd2a88b91 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | NEW_STATE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T20:59:59.202097+00:00 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | OUTCOME | [row absent] | DENIED | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | PREVIOUS_STATE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | REASON_CODE | [row absent] | INVALID_REFRESH_TOKEN | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2175 | INSERT | TRANSACTION_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2176 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGIN_SUCCEEDED | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | ACTOR_USER_ID | [row absent] | 2499 | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | AUDIT_LOG_ID | [row absent] | 2176 | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | CORRELATION_ID | [row absent] | d64944d3-1f16-4cb1-bc9e-0827ea81f15f | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | ENTITY_ID | [row absent] | 2499 | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | EVENT_REFERENCE | [row absent] | 9bc5ebaf-845b-498c-bb2f-9c28d3db8a88 | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:01:05.568550+00:00 | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2176 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_ROTATED | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | ACTOR_USER_ID | [row absent] | 2499 | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | AUDIT_LOG_ID | [row absent] | 2177 | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | CORRELATION_ID | [row absent] | 68c7685a-8958-4546-845c-272e8d082461 | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | ENTITY_ID | [row absent] | 2499 | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | EVENT_REFERENCE | [row absent] | 57c1c12b-41a6-4b38-99e0-0598ad31808e | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:01:06.225173+00:00 | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2177 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_ROTATED | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | ACTOR_USER_ID | [row absent] | 2499 | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | AUDIT_LOG_ID | [row absent] | 2178 | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | CORRELATION_ID | [row absent] | 3c4be970-b527-4508-9c55-53423578646c | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | ENTITY_ID | [row absent] | 2499 | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | EVENT_REFERENCE | [row absent] | 01c6fed7-642b-4566-93d2-3b7391a7a4f9 | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:05:02.608832+00:00 | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2178 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGOUT | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | ACTOR_USER_ID | [row absent] | 2499 | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | AUDIT_LOG_ID | [row absent] | 2179 | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | CORRELATION_ID | [row absent] | b1902dab-9795-4362-8e8d-258b3aa480a6 | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | ENTITY_ID | [row absent] | 2499 | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | EVENT_REFERENCE | [row absent] | e32f1095-2829-4a1e-b0e1-982ba6db29b4 | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:05:44.670561+00:00 | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2179 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGOUT; approved actor 2499; browser/action window evidence |
| AUDIT_LOG | 2180 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_FAILED | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | ACTOR_TYPE | [row absent] | SYSTEM | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | ACTOR_USER_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | AUDIT_LOG_ID | [row absent] | 2180 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | CORRELATION_ID | [row absent] | 837e478d-4000-4799-a3ec-befa1ae5cb08 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | ENTITY_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | ENTITY_TYPE | [row absent] | AUTHENTICATION | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | EVENT_REFERENCE | [row absent] | 80f31e28-7a81-4402-8274-8b699006942a | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | NEW_STATE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:05:45.137644+00:00 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | OUTCOME | [row absent] | DENIED | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | PREVIOUS_STATE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | REASON_CODE | [row absent] | INVALID_REFRESH_TOKEN | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2180 | INSERT | TRANSACTION_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2181 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGIN_SUCCEEDED | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | ACTOR_USER_ID | [row absent] | 2498 | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | AUDIT_LOG_ID | [row absent] | 2181 | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | CORRELATION_ID | [row absent] | 0f8d17a2-c900-475e-b3a8-b9797fee96b8 | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | ENTITY_ID | [row absent] | 2498 | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | EVENT_REFERENCE | [row absent] | cca29af9-babb-4250-ad96-2ca04bfac76e | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:05:47.535078+00:00 | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2181 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_ROTATED | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | ACTOR_USER_ID | [row absent] | 2498 | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | AUDIT_LOG_ID | [row absent] | 2182 | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | CORRELATION_ID | [row absent] | c2ad2b4a-625e-40e9-87da-c5c91eeb57b5 | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | ENTITY_ID | [row absent] | 2498 | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | EVENT_REFERENCE | [row absent] | f2b0f6d1-62f6-418c-9726-a87f75e84084 | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:05:48.023017+00:00 | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2182 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGOUT | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | ACTOR_USER_ID | [row absent] | 2498 | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | AUDIT_LOG_ID | [row absent] | 2183 | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | CORRELATION_ID | [row absent] | c77b7cc2-cd46-44da-bd05-8d1c5e9556ae | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | ENTITY_ID | [row absent] | 2498 | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | EVENT_REFERENCE | [row absent] | fe69d211-6339-4207-9e5f-7fdf643597d6 | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:06:12.441061+00:00 | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2183 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGOUT; approved actor 2498; browser/action window evidence |
| AUDIT_LOG | 2184 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_FAILED | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | ACTOR_TYPE | [row absent] | SYSTEM | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | ACTOR_USER_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | AUDIT_LOG_ID | [row absent] | 2184 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | CORRELATION_ID | [row absent] | 6af1490b-8194-4acf-a699-c013245dae89 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | ENTITY_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | ENTITY_TYPE | [row absent] | AUTHENTICATION | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | EVENT_REFERENCE | [row absent] | ff1399dd-1806-4cf1-81da-62ee733eb27a | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | NEW_STATE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:06:12.893054+00:00 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | OUTCOME | [row absent] | DENIED | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | PREVIOUS_STATE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | REASON_CODE | [row absent] | INVALID_REFRESH_TOKEN | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2184 | INSERT | TRANSACTION_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2185 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGIN_SUCCEEDED | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | ACTOR_USER_ID | [row absent] | 2500 | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | AUDIT_LOG_ID | [row absent] | 2185 | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | CORRELATION_ID | [row absent] | 632f6bd5-45cd-4f8e-96b7-6f945c641b52 | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | ENTITY_ID | [row absent] | 2500 | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | EVENT_REFERENCE | [row absent] | 253cdba7-8715-4d8c-88ef-6e1134815252 | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:06:13.954262+00:00 | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2185 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGIN_SUCCEEDED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_ROTATED | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | ACTOR_USER_ID | [row absent] | 2500 | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | AUDIT_LOG_ID | [row absent] | 2186 | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | CORRELATION_ID | [row absent] | bcf301e8-f1f2-4a23-8c5d-984aa3144fa5 | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | ENTITY_ID | [row absent] | 2500 | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | EVENT_REFERENCE | [row absent] | fb7d1275-4eca-4182-81b4-546ef292840c | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:06:14.412425+00:00 | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2186 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_REFRESH_ROTATED; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | ACTION_CODE | [row absent] | AUTH_LOGOUT | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | ACTOR_TYPE | [row absent] | USER | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | ACTOR_USER_ID | [row absent] | 2500 | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | AUDIT_LOG_ID | [row absent] | 2187 | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | CORRELATION_ID | [row absent] | e06cff1b-d47e-4748-8359-ccde0647b5c8 | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | ENTITY_ID | [row absent] | 2500 | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | ENTITY_TYPE | [row absent] | APP_USER | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | EVENT_REFERENCE | [row absent] | 8f76936b-d85f-441f-85f4-4b6103153847 | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | NEW_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:06:51.094886+00:00 | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | OUTCOME | [row absent] | SUCCESS | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | PREVIOUS_STATE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | REASON_CODE | [row absent] | NULL | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2187 | INSERT | TRANSACTION_ID | [row absent] | NULL | AUTH_LOGOUT; approved actor 2500; browser/action window evidence |
| AUDIT_LOG | 2188 | INSERT | ACTION_CODE | [row absent] | AUTH_REFRESH_FAILED | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | ACTOR_ROLE_CODE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | ACTOR_TYPE | [row absent] | SYSTEM | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | ACTOR_USER_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | AUDIT_LOG_ID | [row absent] | 2188 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | CORRELATION_ID | [row absent] | ff2116cc-d6af-406f-86e4-5941823cd746 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | ENTITY_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | ENTITY_TYPE | [row absent] | AUTHENTICATION | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | EVENT_REFERENCE | [row absent] | 44b5d17f-323d-46c8-8546-7a188afccf99 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | NEW_STATE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | OCCURRED_AT | [row absent] | 2026-09-20T21:06:51.543776+00:00 | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | OUTCOME | [row absent] | DENIED | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | PREVIOUS_STATE | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | REASON_CODE | [row absent] | INVALID_REFRESH_TOKEN | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| AUDIT_LOG | 2188 | INSERT | TRANSACTION_ID | [row absent] | NULL | Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure |
| APP_NOTIFICATION | 1183 | INSERT | ATTEMPT_COUNT | [row absent] | 0 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | CORRELATION_ID | [row absent] | 9d8738d6-2707-4f2f-b246-27c29ec606f7 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:41.980316+00:00 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | DEDUPLICATION_KEY | [row absent] | PAYMENT_PROTECTED:512be0d613fbe129959c61cf9eb0939378c9ce09e3bf882e53c3718bfb28b1ee | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | DELIVERED_AT | [row absent] | NULL | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | DELIVERY_CHANNEL | [row absent] | IN_APP | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | DELIVERY_STATUS | [row absent] | PENDING | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | FAILED_AT | [row absent] | NULL | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | LAST_ERROR_CODE | [row absent] | NULL | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | MAX_ATTEMPTS | [row absent] | 5 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | MESSAGE | [row absent] | Your payment is inside its SafePay protection window. | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | NEXT_ATTEMPT_AT | [row absent] | NULL | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | NOTIFICATION_ID | [row absent] | 1183 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | NOTIFICATION_REFERENCE | [row absent] | NOTIFY-512be0d613fbe129959c61cf9eb0939378c9ce09e3bf882e | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | NOTIFICATION_TYPE | [row absent] | PAYMENT_PROTECTED | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | READ_AT | [row absent] | NULL | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | RECIPIENT_USER_ID | [row absent] | 2470 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | SEVERITY | [row absent] | WARNING | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | TITLE | [row absent] | Payment protected | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | TRANSACTION_ID | [row absent] | 1554 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:50:41.980316+00:00 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1183 | INSERT | VERSION_NO | [row absent] | 0 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | ATTEMPT_COUNT | [row absent] | 0 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | CORRELATION_ID | [row absent] | 3d610f3e-65ef-4a4a-bdda-720df5df1259 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.432263+00:00 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | DEDUPLICATION_KEY | [row absent] | PAYMENT_CANCELLED:0100b3df7d9496e35de017101ce93a52204bd62b4aef4a273ede0ed9b5eec225 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | DELIVERED_AT | [row absent] | NULL | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | DELIVERY_CHANNEL | [row absent] | IN_APP | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | DELIVERY_STATUS | [row absent] | PENDING | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | FAILED_AT | [row absent] | NULL | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | LAST_ERROR_CODE | [row absent] | NULL | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | MAX_ATTEMPTS | [row absent] | 5 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | MESSAGE | [row absent] | Your payment was cancelled before simulated settlement. | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | NEXT_ATTEMPT_AT | [row absent] | NULL | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | NOTIFICATION_ID | [row absent] | 1184 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | NOTIFICATION_REFERENCE | [row absent] | NOTIFY-0100b3df7d9496e35de017101ce93a52204bd62b4aef4a27 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | NOTIFICATION_TYPE | [row absent] | PAYMENT_CANCELLED | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | READ_AT | [row absent] | NULL | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | RECIPIENT_USER_ID | [row absent] | 2470 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | SEVERITY | [row absent] | WARNING | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | TITLE | [row absent] | Payment cancelled | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | TRANSACTION_ID | [row absent] | 1554 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:50:42.432263+00:00 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1184 | INSERT | VERSION_NO | [row absent] | 0 | Notification from test payment 1554 |
| APP_NOTIFICATION | 1185 | INSERT | ATTEMPT_COUNT | [row absent] | 0 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | CORRELATION_ID | [row absent] | 73e3e0e4-2f2d-4319-93e3-b545771266c9 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.630015+00:00 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | DEDUPLICATION_KEY | [row absent] | PAYMENT_PROTECTED:aa865a84353cf81a2844356367321619c7818c18a57f3ee88e675402ab8a4548 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | DELIVERED_AT | [row absent] | NULL | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | DELIVERY_CHANNEL | [row absent] | IN_APP | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | DELIVERY_STATUS | [row absent] | PENDING | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | FAILED_AT | [row absent] | NULL | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | LAST_ERROR_CODE | [row absent] | NULL | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | MAX_ATTEMPTS | [row absent] | 5 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | MESSAGE | [row absent] | Your payment is inside its SafePay protection window. | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | NEXT_ATTEMPT_AT | [row absent] | NULL | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | NOTIFICATION_ID | [row absent] | 1185 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | NOTIFICATION_REFERENCE | [row absent] | NOTIFY-aa865a84353cf81a2844356367321619c7818c18a57f3ee8 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | NOTIFICATION_TYPE | [row absent] | PAYMENT_PROTECTED | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | READ_AT | [row absent] | NULL | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | RECIPIENT_USER_ID | [row absent] | 2470 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | SEVERITY | [row absent] | WARNING | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | TITLE | [row absent] | Payment protected | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | TRANSACTION_ID | [row absent] | 1555 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:50:42.630015+00:00 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1185 | INSERT | VERSION_NO | [row absent] | 0 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | ATTEMPT_COUNT | [row absent] | 0 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | CORRELATION_ID | [row absent] | 53fa44ee-97d2-4f2d-b598-420f7366a239 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.697188+00:00 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | DEDUPLICATION_KEY | [row absent] | PAYMENT_CANCELLED:46a52188f8dd6b19f3fdd72866fbbf42ae9d0f723edbd125ef8c16377f8e9be5 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | DELIVERED_AT | [row absent] | NULL | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | DELIVERY_CHANNEL | [row absent] | IN_APP | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | DELIVERY_STATUS | [row absent] | PENDING | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | FAILED_AT | [row absent] | NULL | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | LAST_ERROR_CODE | [row absent] | NULL | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | MAX_ATTEMPTS | [row absent] | 5 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | MESSAGE | [row absent] | Your payment was cancelled before simulated settlement. | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | NEXT_ATTEMPT_AT | [row absent] | NULL | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | NOTIFICATION_ID | [row absent] | 1186 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | NOTIFICATION_REFERENCE | [row absent] | NOTIFY-46a52188f8dd6b19f3fdd72866fbbf42ae9d0f723edbd125 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | NOTIFICATION_TYPE | [row absent] | PAYMENT_CANCELLED | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | READ_AT | [row absent] | NULL | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | RECIPIENT_USER_ID | [row absent] | 2470 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | SEVERITY | [row absent] | WARNING | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | TITLE | [row absent] | Payment cancelled | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | TRANSACTION_ID | [row absent] | 1555 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:50:42.697188+00:00 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1186 | INSERT | VERSION_NO | [row absent] | 0 | Notification from test payment 1555 |
| APP_NOTIFICATION | 1187 | INSERT | ATTEMPT_COUNT | [row absent] | 0 | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | CORRELATION_ID | [row absent] | e4b83e5e-954b-49b4-ba61-7950b427beaf | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.835563+00:00 | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | DEDUPLICATION_KEY | [row absent] | PAYMENT_CANCELLED:b175ac9e2cca52cc6a90ceb613d34b9971bf1611cc1d4d4657d8bf12e2dbf3fd | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | DELIVERED_AT | [row absent] | NULL | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | DELIVERY_CHANNEL | [row absent] | IN_APP | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | DELIVERY_STATUS | [row absent] | PENDING | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | FAILED_AT | [row absent] | NULL | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | LAST_ERROR_CODE | [row absent] | NULL | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | MAX_ATTEMPTS | [row absent] | 5 | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | MESSAGE | [row absent] | Your payment was cancelled before simulated settlement. | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | NEXT_ATTEMPT_AT | [row absent] | NULL | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | NOTIFICATION_ID | [row absent] | 1187 | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | NOTIFICATION_REFERENCE | [row absent] | NOTIFY-b175ac9e2cca52cc6a90ceb613d34b9971bf1611cc1d4d46 | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | NOTIFICATION_TYPE | [row absent] | PAYMENT_CANCELLED | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | READ_AT | [row absent] | NULL | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | RECIPIENT_USER_ID | [row absent] | 2470 | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | SEVERITY | [row absent] | WARNING | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | TITLE | [row absent] | Payment cancelled | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | TRANSACTION_ID | [row absent] | 1556 | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:50:42.835563+00:00 | Notification from test payment 1556 |
| APP_NOTIFICATION | 1187 | INSERT | VERSION_NO | [row absent] | 0 | Notification from test payment 1556 |
| APP_NOTIFICATION | 1188 | INSERT | ATTEMPT_COUNT | [row absent] | 0 | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | CORRELATION_ID | [row absent] | d654aab5-4f7a-4e65-bd05-acb44c91cf45 | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:50:42.957371+00:00 | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | DEDUPLICATION_KEY | [row absent] | PAYMENT_FAILED:a72ee3aeb35c2b54cf93c39ad58c31062526b79857988fae0506bc17a7d3e9cc | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | DELIVERED_AT | [row absent] | NULL | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | DELIVERY_CHANNEL | [row absent] | IN_APP | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | DELIVERY_STATUS | [row absent] | PENDING | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | FAILED_AT | [row absent] | NULL | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | LAST_ERROR_CODE | [row absent] | NULL | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | MAX_ATTEMPTS | [row absent] | 5 | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | MESSAGE | [row absent] | Your payment could not complete SafePay's simulated processing. | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | NEXT_ATTEMPT_AT | [row absent] | NULL | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | NOTIFICATION_ID | [row absent] | 1188 | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | NOTIFICATION_REFERENCE | [row absent] | NOTIFY-a72ee3aeb35c2b54cf93c39ad58c31062526b79857988fae | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | NOTIFICATION_TYPE | [row absent] | PAYMENT_FAILED | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | READ_AT | [row absent] | NULL | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | RECIPIENT_USER_ID | [row absent] | 2470 | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | SEVERITY | [row absent] | CRITICAL | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | TITLE | [row absent] | Payment failed | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | TRANSACTION_ID | [row absent] | 1557 | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:50:42.957371+00:00 | Notification from test payment 1557 |
| APP_NOTIFICATION | 1188 | INSERT | VERSION_NO | [row absent] | 0 | Notification from test payment 1557 |
| APP_NOTIFICATION | 1189 | INSERT | ATTEMPT_COUNT | [row absent] | 0 | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | CORRELATION_ID | [row absent] | 5bc00c72-cb69-4906-8cb4-2449964219a1 | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | CREATED_AT | [row absent] | 2026-09-20T20:52:39.459157+00:00 | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | DEDUPLICATION_KEY | [row absent] | PAYMENT_RELEASED:227842c03a87593c4c399d1f4d16460bd0e11437f09dbf64497e618236695104 | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | DELIVERED_AT | [row absent] | NULL | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | DELIVERY_CHANNEL | [row absent] | IN_APP | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | DELIVERY_STATUS | [row absent] | PENDING | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | FAILED_AT | [row absent] | NULL | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | LAST_ERROR_CODE | [row absent] | NULL | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | MAX_ATTEMPTS | [row absent] | 5 | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | MESSAGE | [row absent] | Your payment was released for SafePay's simulated settlement process. | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | NEXT_ATTEMPT_AT | [row absent] | NULL | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | NOTIFICATION_ID | [row absent] | 1189 | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | NOTIFICATION_REFERENCE | [row absent] | NOTIFY-227842c03a87593c4c399d1f4d16460bd0e11437f09dbf64 | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | NOTIFICATION_TYPE | [row absent] | PAYMENT_RELEASED | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | READ_AT | [row absent] | 2026-09-20T20:54:19.882409+00:00 | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | RECIPIENT_USER_ID | [row absent] | 2468 | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | SEVERITY | [row absent] | INFO | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | TITLE | [row absent] | Payment released | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | TRANSACTION_ID | [row absent] | 1558 | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | UPDATED_AT | [row absent] | 2026-09-20T20:54:19.882409+00:00 | Notification from test payment 1558; marked read in browser |
| APP_NOTIFICATION | 1189 | INSERT | VERSION_NO | [row absent] | 1 | Notification from test payment 1558; marked read in browser |

Excluded columns:

- APP_USER|PASSWORD_HASH
- AUDIT_LOG|DETAILS_JSON
- AUDIT_LOG|IDEMPOTENCY_KEY
- AUTH_SESSION|REFRESH_TOKEN_HASH
- AUTH_SESSION|TOKEN_FAMILY_KEY
- IDEMPOTENCY_RECORD|IDEMPOTENCY_KEY
- IDEMPOTENCY_RECORD|REQUEST_HASH
- IDEMPOTENCY_RECORD|RESPONSE_BODY
- LEDGER_ENTRY|IDEMPOTENCY_KEY
- LEDGER_POSTING|IDEMPOTENCY_KEY
- PAYMENT_OTP_CHALLENGE|OTP_HASH
