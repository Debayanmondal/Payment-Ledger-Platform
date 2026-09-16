#!/usr/bin/env bash
set -e

PAYMENT_SERVICE="http://localhost:8081"
LEDGER_SERVICE="http://localhost:8082"

echo -e "\n=== [1] Seeding Test Accounts in Ledger Service ==="
curl -s -X POST "$LEDGER_SERVICE/api/v1/ledger/seed" | jq .

echo -e "\n=== [2] Checking Initial Balance for ACC-1001 & ACC-2002 ==="
curl -s "$LEDGER_SERVICE/api/v1/ledger/accounts/ACC-1001" | jq .
curl -s "$LEDGER_SERVICE/api/v1/ledger/accounts/ACC-2002" | jq .

IDEMPOTENCY_KEY="TXN-$(date +%s)"
echo -e "\n=== [3] Submitting First Payment Request ($250.00) with Idempotency-Key: $IDEMPOTENCY_KEY ==="
PAYMENT_RESPONSE=$(curl -s -X POST "$PAYMENT_SERVICE/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "sourceAccountId": "ACC-1001",
    "destinationAccountId": "ACC-2002",
    "amount": 250.00,
    "currency": "USD",
    "description": "Invoice settlement #4892"
  }')

echo "$PAYMENT_RESPONSE" | jq .
PAYMENT_ID=$(echo "$PAYMENT_RESPONSE" | jq -r '.data.paymentId')

echo -e "\n=== [4] Submitting DUPLICATE Payment Request with identical Idempotency-Key ==="
curl -s -X POST "$PAYMENT_SERVICE/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "sourceAccountId": "ACC-1001",
    "destinationAccountId": "ACC-2002",
    "amount": 250.00,
    "currency": "USD",
    "description": "Invoice settlement #4892"
  }' | jq .

sleep 2

echo -e "\n=== [5] Checking Double-Entry General Ledger Postings for Payment $PAYMENT_ID ==="
curl -s "$LEDGER_SERVICE/api/v1/ledger/entries/$PAYMENT_ID" | jq .

echo -e "\n=== [6] Checking Updated Balances ==="
curl -s "$LEDGER_SERVICE/api/v1/ledger/accounts/ACC-1001" | jq .
curl -s "$LEDGER_SERVICE/api/v1/ledger/accounts/ACC-2002" | jq .

echo -e "\n=== [7] Testing Resilience4j Circuit Breaker Endpoint ==="
curl -s "$PAYMENT_SERVICE/api/v1/payments/test-circuit-breaker?simulateFailure=false" | jq .
echo -e "\nTriggering Fallback:"
curl -s "$PAYMENT_SERVICE/api/v1/payments/test-circuit-breaker?simulateFailure=true" | jq .
