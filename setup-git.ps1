# ==============================================================================
# Helper Script to Initialize Git and Push to GitHub
# ==============================================================================

param(
    [string]$RepoUrl = "https://github.com/Debayanmondal/payment-ledger-platform.git"
)

Write-Host "Initializing Git Repository for Payment & Ledger Platform..." -ForegroundColor Cyan

git init
git branch -M main
git add .
git commit -m "feat: initial release of distributed event-driven payment and ledger platform"

Write-Host "`nLocal repository initialized and committed!" -ForegroundColor Green
Write-Host "To link and push to your GitHub account:" -ForegroundColor Cyan
Write-Host "  1. Create a repository named 'payment-ledger-platform' on https://github.com/new" -ForegroundColor Yellow
Write-Host "  2. Run the following commands:" -ForegroundColor Yellow
Write-Host "     git remote add origin $RepoUrl" -ForegroundColor White
Write-Host "     git push -u origin main" -ForegroundColor White
