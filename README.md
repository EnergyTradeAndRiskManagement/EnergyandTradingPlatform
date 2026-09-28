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



~ -----------------------------------------------outbox pattern``````````````````````````````````````````````````````````````````~

We have implemented Kafka, the Transactional Outbox Pattern, and Idempotent Consumers in energyandtradingplatform.

Architectural Flow
1. TRADE CAPTURE (Atomic Transaction)
   Trader/API
       │
       ▼
   TradeService (ACID Transaction)
       ├─▶ 1. Saves Trade to `Trade` table
       ├─▶ 2. Saves IdempotencyKey to `idempotency_keys` table
       ├─▶ 3. Saves Audit to `trade_audit` table
       └─▶ 4. Saves Event to `outbox_events` table (Status: PENDING)
                               │
               DATABASE TRANSACTION COMMITS
                               │
2. OUTBOX PUBLISHING (Zero Lost Events)
                               ▼
                    OutboxPublisherService
                    (Immediate trigger + Scheduled Poller every 2s)
                               │
                               ▼ KafkaTemplate.send("trade-created", ...)
                         Apache Kafka
                         ├─▶ "trade-created"      ──▶ position-service
                         └─▶ "trade-confirmation" ──▶ energyandtradingplatform (Consumer)
                                                            │
3. IDEMPOTENT CONSUMPTION                                   │
                                                            ▼
                                                TradeConfirmationConsumer
                                                ├─ Checks `processed_events` table
                                                ├─ If duplicate: skips side effects
                                                └─ If new: updates status & saves eventId
1. Kafka Configuration & Resilience
Updated 

application.properties
Fixed invalid formatting and configured reliable producer settings:
acks=all: Guarantees message persistence across all in-sync replicas before acknowledgement.
enable.idempotence=true: Prevents duplicate messages produced by network retries.
Configured consumer group energy-trading-group with JSON deserialization and trusted packages.
Added outbox configuration:
outbox.publisher.interval-ms=2000
outbox.publisher.max-retries=5
Created 

KafkaConfig.java
Defines auto-created topic beans:

trade-created (3 partitions, replication factor 1)
trade-confirmation (3 partitions, replication factor 1)
Enabled in 

TradeserviceApplication.java
Added @EnableScheduling (for the outbox poller) and @EnableKafka.

2. Transactional Outbox Pattern
Why It Is Essential
Without the Outbox pattern, calling kafkaTemplate.send() directly inside a database transaction suffers from the Dual-Write Problem:

If Kafka fails or times out, the trade event is lost forever.
If the database transaction rolls back after Kafka sends, downstream services receive "ghost" trades that do not exist.
With the Outbox pattern, events are written to the database in the same ACID transaction as the trade. If the trade commits, the event is guaranteed to exist.

Components Implemented:


OutboxStatus.java
: Enum with states PENDING, SENT, FAILED.


OutboxEvent.java
: PostgreSQL entity mapping to outbox_events storing aggregateId, eventType, topic, payload (JSON), retryCount, status, and timestamps.


OutboxRepository.java
: Queries top 50 pending events ordered by creation timestamp.


OutboxService.java
: Helper service invoked during trade create, update, and cancel operations to append outbox entries atomically.


OutboxPublisherService.java
:
Immediate dispatch: Sends events to Kafka asynchronously right after transaction save.
Scheduled poller (@Scheduled every 2s): Sweeps any pending/failed events (e.g. if Kafka broker was temporarily down).
Status management: On Kafka broker confirmation, updates status to SENT. If it fails, increments retryCount up to maxRetries before marking FAILED.
Integrated into 

TradeService.java
: All operations (captureTrade, updateTrade, cancelTrade) now write to the Outbox.
3. Idempotent Consumer Pattern
Why It Is Essential
In distributed systems, Kafka guarantees At-Least-Once Delivery. Network partitions, consumer group rebalances, or producer retries can deliver the exact same message twice. Without consumer idempotency, duplicate side effects (e.g. double settling, double invoicing) will occur.

Components Implemented:


ProcessedEvent.java
: Entity mapping to processed_events table with a unique constraint on (eventId, consumerGroup).


ProcessedEventRepository.java
: Checks existsByEventIdAndConsumerGroup(eventId, consumerGroup).


IdempotentConsumerService.java
: Provides isAlreadyProcessed() and markAsProcessed() methods executed inside the consumer's transaction.


TradeConfirmationEvent.java
: DTO carrying eventId, tradeId, confirmationStatus (CONFIRMED, SETTLED, REJECTED), and clearingReference.


TradeConfirmationConsumer.java
:
@KafkaListener subscribed to "trade-confirmation" topic.
Deduplication step: If eventId is already present in processed_events, it logs a warning and skips processing immediately.
Processing step: If new, advances trade lifecycle status, logs an audit entry, and records the eventId in processed_events within the same atomic transaction.
Real-Time Outbox Monitoring Endpoint
Added a monitoring endpoint in 

tradeControllers.java
:

Endpoint: GET /api/v1/trades/outbox
Returns all outbox events, their current delivery status (PENDING, SENT, FAILED), retry count, and timestamps:
json
[
  {
    "id": 1,
    "aggregateType": "TRADE",
    "aggregateId": 101,
    "eventType": "TRADE_CREATED",
    "topic": "trade-created",
    "status": "SENT",
    "retryCount": 0,
    "errorMessage": null,
    "createdAt": "2026-09-28T16:15:00",
    "sentAt": "2026-09-28T16:15:00.045"
  }
]
