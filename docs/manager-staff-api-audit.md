# Manager/staff API contract audit

Scope: all existing `/api/manager/**` and `/api/staff/**` routes: ManagerAssessmentController (8 operations) and AssignedCaseController (2 operations). Shared revision/snapshot/horse/service schemas retain the normalized contracts from PR24; no new business endpoints or database migration.

| Location | Before | After |
|---|---|---|
| CaseStaffResponse.role, CaseAssignmentResponse.role | Java String with duplicated allowableValues | Existing CaseRole enum, SPECIALIST/COORDINATOR |
| CaseDecisionResponse.decision | Java String | Existing DecisionCode enum, shared with AssessmentRequest |
| CaseResponse.orderStatus, CaseSummaryResponse.orderStatus, AssessmentReceipt.orderStatus | Java String with repeated vocabulary | OrderStatus enum preserving the previously documented JSON codes, including legacy CANCELED |
| AssessmentRequest.decision | Whole decision object as a scalar example; nullable metadata despite NotNull | CHANGES_REQUESTED scalar example, nonnullable enum |
| AssessmentRequest.revisionId, CaseAssignmentRequest.staffId, CaseDecisionResponse.revisionId | Primitive positive IDs marked nullable | Positive nonnullable IDs |
| Assignment/end reason | Required reason marked nullable | Required nonnullable reason |
| Every manager/staff page/size parameter | Defaults present, explicit examples absent | page=0, size=20 examples, defaults and bounds 0+/1..100 |
| Controller role/state/id parameters | Example absent | SPECIALIST/SUBMITTED/101 examples; enum types and positive IDs preserved |
| Assessment reason/assignmentNote, assignmentNote | Optional text could be empty or whitespace | null allowed, supplied value nonblank via existing OptionalText |
| CasePageResponse, CaseStaffPageResponse | Correct page=0,size=20,count=1 examples | Verified and retained |
| Both controllers | application/json already declared | Retained and checked in generated OpenAPI |

Enum conversion occurs in the service mapper; SQL projections/persistence strings and JSON property names remain unchanged. Stored retry receipts remain JSON strings and deserialize into enum DTOs. Customer assessment maps the shared enum to its existing public string contract and still excludes internal approval notes.

OrderStatus is the existing documented vocabulary, not an authorization to implement future transitions. The real database has no OrderStatus CHECK and currently no order rows; unknown manually inserted status strings are outside this declared contract and require an explicit domain decision, not silently coerced to another state. Role/decision reuse existing domain enums; no security role expansion.

Compatibility: valid JSON enum names are unchanged; optional blank reason/note clients should send null instead. No real database writes. Tests check Java enum types, all status-code JSON round trips, real Swagger parameter examples/media types and SQL assessment security/idempotency regressions on the isolated test database. Exact final evidence is recorded in the PR.

This change inherits PR24 (fix/api-dto-contracts); merge PR24 first if it remains open.
