All trade management capabilities have been implemented in energyandtradingplatform.

Implementation Details
1. Create Trade (POST /api/v1/trades)
Validation: Enforces quantity $> 0$, price $\ge 0$, valid 3-letter currency (e.g. USD), valid trade type (BUY/SELL), valid commodity, location, and counterparty.
Idempotency: Validates IdempotencyKey header. Duplicate requests with the same hash return the existing trade without duplicate DB rows.
Event Streaming: Publishes trade-created Kafka event to update position-service in real time.
Audit: Automatically logs a CREATE audit record with user identity, timestamp, and version 0.
Response: Returns 201 Created with fully populated 

TradeResponse
 (including calculated notional and initial version: 0).
2. Get Trade (GET /api/v1/trades/{id} & GET /api/v1/trades)
Single Trade: Fetches trade by ID; throws TradeNotFoundException (returns 404) if missing.
All Trades: Returns all trades mapped to TradeResponse with current lifecycle status and version.
3. Update Trade (PUT /api/v1/trades/{tradeId})
Lifecycle Guard: Rejects updates on terminal states (SETTLED or CANCELLED).
Optimistic Locking: Compares request.getVersion() against existingTrade.getVersion(). If mismatched, throws TradeConflictException (409 Conflict).
Audit: Logs an UPDATE record recording old vs new values.
Sync: Emits an event to Kafka so position-service recalculates position exposures.
4. Cancel Trade (POST /api/v1/trades/{tradeId}/cancel & DELETE /api/v1/trades/{tradeId})
Soft Cancellation: In financial/energy trading, trades are never hard-deleted. Instead, status is transitioned to CANCELLED.
Settlement Guard: A SETTLED trade cannot be cancelled (requires an offsetting trade).
Audit: Logs a CANCEL audit record with the cancellation reason and operator username.
Sync: Publishes cancellation event to Kafka to reverse or adjust positions.
5. Trade Lifecycle Management (POST /api/v1/trades/{tradeId}/status?newStatus=...)
Strict state machine rules in 

TradeStatus.java
:

             ┌─────────────┐
             │    DRAFT    │
             └──────┬──────┘
                    │
         ┌──────────┴──────────┐
         ▼                     ▼
┌─────────────────┐   ┌─────────────────┐
│ PENDING_REVIEW  │   │    APPROVED     │
└────────┬────────┘   └────────┬────────┘
         │                     │
         ▼                     ▼
┌─────────────────┐   ┌───────────────────┐
│    REJECTED     │   │ SETTLEMENT_PENDING│
└─────────────────┘   └────────┬──────────┘
                               │
                               ▼
                      ┌─────────────────┐
                      │     SETTLED     │ (Terminal)
                      └─────────────────┘
* Note: DRAFT, PENDING_REVIEW, APPROVED, and SETTLEMENT_PENDING can transition to CANCELLED (Terminal).
Illegal transitions (e.g. SETTLED $\rightarrow$ DRAFT) throw InvalidTradeLifecycleException (422 Unprocessable Entity).

6. Optimistic Locking
@Version field: Managed in 

Trade.java
.
Concurrent write handling: If two users edit the trade concurrently, Hibernate throws ObjectOptimisticLockingFailureException.
Global Exception Handler: 

GlobalExceptions.java
 translates this into a clean JSON 409 Conflict:
json
{
  "errorCode": "OPTIMISTIC_LOCK_FAILURE",
  "message": "Trade was updated or cancelled by another user concurrently. Please reload."
}
7. Validation: 

TradeValidator.java
Requires non-blank IdempotencyKey on creation.
Requires valid trade_type (BUY or SELL).
Requires quantity > 0 and price >= 0.
Requires valid 3-letter currency code (e.g., USD, EUR, GBP).
Validates commodity, location, and counterparty existence.
Requires version on update requests for optimistic locking.
8. Audit Trail: 

TradeAudit.java
Endpoint: GET /api/v1/trades/{tradeId}/audit
Records:
action: CREATE, UPDATE, CANCEL, STATUS_CHANGE
oldStatus & newStatus
details: Snapshot of modified fields
modifiedBy: Authenticated user extracted from the Gateway's security headers
version: The version at the time of modification
timestamp: UTC timestamp of the change
REST API Reference
Action	HTTP Method	Path	Required Headers / Params
Create Trade	POST	/api/v1/trades	Header: IdempotencyKey: <uuid>, Body: TradeRequest
Get Trade	GET	/api/v1/trades/{id}	None
Get All Trades	GET	/api/v1/trades	None
Update Trade	PUT	/api/v1/trades/{tradeId}	Body: TradeRequest (must include version)
Cancel Trade	POST / DELETE	/api/v1/trades/{tradeId}/cancel	Param: version (optional), reason (optional)
Update Lifecycle	POST	/api/v1/trades/{tradeId}/status	Query: newStatus=SETTLED&version=1
View Audit History	GET	/api/v1/trades/{tradeId}/audit	None
Example Audit Response (GET /api/v1/trades/1/audit)
json
[
  {
    "id": 2,
    "tradeId": 1,
    "action": "CANCEL",
    "oldStatus": "APPROVED",
    "newStatus": "CANCELLED",
    "details": "Trade cancelled. Reason: Market price changed",
    "modifiedBy": "testuser",
    "version": 1,
    "timestamp": "2026-09-28T15:52:10"
  },
  {
    "id": 1,
    "tradeId": 1,
    "action": "CREATE",
    "oldStatus": null,
    "newStatus": "APPROVED",
    "details": "Trade created: BUY 1000.0 CRUDE_OIL @ 75.5 at CUSHING",
    "modifiedBy": "testuser",
    "version": 0,
    "timestamp": "2026-09-28T15:45:00"
  }
]
