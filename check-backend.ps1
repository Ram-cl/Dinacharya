# Backend Health Check Script

Write-Host "=== Backend Health Check ===" -ForegroundColor Cyan
Write-Host ""

# Check if port 8080 is listening
Write-Host "1. Checking if port 8080 is open..." -ForegroundColor Yellow
$portTest = Test-NetConnection -ComputerName localhost -Port 8080 -WarningAction SilentlyContinue
if ($portTest.TcpTestSucceeded) {
    Write-Host "   ✓ Port 8080 is OPEN" -ForegroundColor Green
} else {
    Write-Host "   ✗ Port 8080 is CLOSED - Backend not running!" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Check health endpoint
Write-Host "2. Checking health endpoint..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/actuator/health" -Method GET
    Write-Host "   ✓ Backend is HEALTHY" -ForegroundColor Green
    Write-Host "   Status: $($health.status)" -ForegroundColor Green
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "   ✗ Backend health check failed: $statusCode" -ForegroundColor Red
    
    if ($statusCode -eq 503) {
        Write-Host "   → Backend is starting... wait 30 seconds and try again" -ForegroundColor Yellow
    } elseif ($statusCode -eq 401) {
        Write-Host "   → Authentication issue" -ForegroundColor Yellow
    } else {
        Write-Host "   → Backend may not be fully started" -ForegroundColor Yellow
    }
}

Write-Host ""

# Check file import endpoint info
Write-Host "3. Checking file import endpoint..." -ForegroundColor Yellow
try {
    $info = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/import/template/info" -Method GET
    Write-Host "   ✓ Import endpoint is accessible" -ForegroundColor Green
    Write-Host "   Excel Format: $($info.excelFormat)" -ForegroundColor Cyan
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "   ✗ Import endpoint check failed: $statusCode" -ForegroundColor Red
    
    if ($statusCode -eq 401) {
        Write-Host "   → This endpoint requires authentication (normal)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Cyan
Write-Host "Backend is running on port 8080"
Write-Host "If health check shows 503, wait and try again"
Write-Host "Frontend: http://localhost:5173"
Write-Host "Backend API: http://localhost:8080/api/v1"
Write-Host ""
