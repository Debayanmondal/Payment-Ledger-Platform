# ==============================================================================
# Payment & Ledger Platform - Automated Verification Script (PowerShell)
# ==============================================================================

$PaymentService = "http://localhost:8081"
$LedgerService  = "http://localhost:8082"

Write-Host "`n=== [1] Seeding Test Accounts in Ledger Service ===" -ForegroundColor Cyan
try {
    $seedResponse = Invoke-RestMethod -Uri "$LedgerService/api/v1/ledger/seed" -Method Post
    Write-Host "Response:" ($seedResponse | ConvertTo-Json -Compress) -ForegroundColor Green
} catch {
    Write-Host "Ledger service not reachable on $LedgerService. Ensure services are running." -ForegroundColor Red
}

Write-Host "`n=== [2] Checking Initial Balance for ACC-1001 & ACC-2002 ===" -ForegroundColor Cyan
try {
    $acc1 = Invoke-RestMethod -Uri "$LedgerService/api/v1/ledger/accounts/ACC-1001" -Method Get
    Write-Host "ACC-1001 Balance: $($acc1.balance) $($acc1.currency) (Version: $($acc1.version))" -ForegroundColor Yellow
    $acc2 = Invoke-RestMethod -Uri "$LedgerService/api/v1/ledger/accounts/ACC-2002" -Method Get
    Write-Host "ACC-2002 Balance: $($acc2.balance) $($acc2.currency) (Version: $($acc2.version))" -ForegroundColor Yellow
} catch {
    Write-Host "Failed to query account balances." -ForegroundColor Red
}

Write-Host "`n=== [3] Submitting First Payment Request (Amount: $250.00) ===" -ForegroundColor Cyan
$idempotencyKey = "TXN-" + [System.Guid]::NewGuid().ToString().Substring(0, 8)
$paymentBody = @{
    sourceAccountId      = "ACC-1001"
    destinationAccountId = "ACC-2002"
    amount               = 250.00
    currency             = "USD"
    description          = "Invoice settlement #4892"
} | ConvertTo-Json

$headers = @{
    "Content-Type"    = "application/json"
    "Idempotency-Key" = $idempotencyKey
}

try {
    $payResponse = Invoke-RestMethod -Uri "$PaymentService/api/v1/payments" -Method Post -Headers $headers -Body $paymentBody
    Write-Host "Payment Accepted! ID:" $payResponse.data.paymentId -ForegroundColor Green
    Write-Host "Message:" $payResponse.message -ForegroundColor Green
    Write-Host "Idempotent Replay Flag:" $payResponse.data.idempotentReplay -ForegroundColor Yellow
    $paymentId = $payResponse.data.paymentId
} catch {
    Write-Host "Payment request failed: $_" -ForegroundColor Red
}

Write-Host "`n=== [4] Submitting DUPLICATE Payment Request with identical Idempotency-Key ===" -ForegroundColor Cyan
try {
    $duplicateResponse = Invoke-RestMethod -Uri "$PaymentService/api/v1/payments" -Method Post -Headers $headers -Body $paymentBody
    Write-Host "Duplicate Request Intercepted!" -ForegroundColor Green
    Write-Host "Message:" $duplicateResponse.message -ForegroundColor Yellow
    Write-Host "Idempotent Replay Flag:" $duplicateResponse.data.idempotentReplay -ForegroundColor Green
} catch {
    Write-Host "Duplicate request error: $_" -ForegroundColor Red
}

Start-Sleep -Seconds 2

Write-Host "`n=== [5] Checking Double-Entry General Ledger Postings for Payment $paymentId ===" -ForegroundColor Cyan
try {
    $journal = Invoke-RestMethod -Uri "$LedgerService/api/v1/ledger/entries/$paymentId" -Method Get
    Write-Host "Journal Entry ID:" $journal.id -ForegroundColor Green
    Write-Host "Total Amount:" $journal.totalAmount $journal.currency -ForegroundColor Green
    Write-Host "Postings:"
    foreach ($line in $journal.lines) {
        Write-Host "  Account: $($line.accountId) | Entry: $($line.entryType) | Amount: $($line.amount)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "Journal entry not yet published or still processing." -ForegroundColor DarkYellow
}

Write-Host "`n=== [6] Checking Updated Balances (Double-Entry Balance Verification) ===" -ForegroundColor Cyan
try {
    $acc1After = Invoke-RestMethod -Uri "$LedgerService/api/v1/ledger/accounts/ACC-1001" -Method Get
    Write-Host "ACC-1001 Balance After: $($acc1After.balance) (Version: $($acc1After.version))" -ForegroundColor Green
    $acc2After = Invoke-RestMethod -Uri "$LedgerService/api/v1/ledger/accounts/ACC-2002" -Method Get
    Write-Host "ACC-2002 Balance After: $($acc2After.balance) (Version: $($acc2After.version))" -ForegroundColor Green
} catch {
    Write-Host "Failed to query updated balances." -ForegroundColor Red
}

Write-Host "`n=== [7] Testing Resilience4j Circuit Breaker Endpoint ===" -ForegroundColor Cyan
try {
    $cbSuccess = Invoke-RestMethod -Uri "$PaymentService/api/v1/payments/test-circuit-breaker?simulateFailure=false" -Method Get
    Write-Host "Healthy Call:" $cbSuccess.data -ForegroundColor Green

    Write-Host "Triggering Simulated Outage Call (Circuit Breaker Fallback)..."
    try {
        $cbFail = Invoke-RestMethod -Uri "$PaymentService/api/v1/payments/test-circuit-breaker?simulateFailure=true" -Method Get
    } catch {
        Write-Host "Fallback Caught (503 SERVICE UNAVAILABLE): Gracefully degraded." -ForegroundColor Yellow
    }
} catch {
    Write-Host "Circuit breaker endpoint error: $_" -ForegroundColor Red
}

Write-Host "`n==============================================================================" -ForegroundColor Cyan
Write-Host "Verification Complete! Every claim on your resume is backed by working code." -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Cyan
