# calorie-tracker-api — project notes

Spring Boot API for the Calorie Tracker. Frontend: `calorie-tracker-app` (sibling repo).

## Single user by design — do not raise multi-user concerns

This API serves exactly **one user: the owner**. It is a personal tool, not a multi-tenant product.

- Tables (`diary_entries`, `diary_items`, `foods`, `daily_goals`, and any future ones such as
  weigh-ins) have **no user column, on purpose**. Queries like `findRecentDistinctFoods` are
  intentionally global. Do not flag missing user isolation, and do not propose adding `user_id`
  columns, per-user filtering, or tenant separation.
- The single account is seeded at startup by `UserSeeder` from `APP_ADMIN_USERNAME` /
  `APP_ADMIN_PASSWORD`. JWT auth exists to keep strangers out, not to tell users apart.
- Public sign-up is not a feature this API needs. `/api/auth/register` predates this decision.
