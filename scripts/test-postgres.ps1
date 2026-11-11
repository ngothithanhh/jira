param([string]$JavaHome = 'C:\Users\Admin\.jdks\ms-17.0.20.1', [switch]$WithUnit)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$cluster = Join-Path (Split-Path $repo -Parent) ('test-postgres-' + [guid]::NewGuid().ToString('N'))
$data = Join-Path $cluster 'data'
$started = $false
$testExit = 1

# Chỉ tạo cluster mới và chỉ dùng cổng riêng; cổng bận thì dừng, không tiếp quản PostgreSQL khác.
$probe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 55432)
try { $probe.Start() } finally { $probe.Stop() }
if (Test-Path -LiteralPath $cluster) { throw 'Cluster test phải là đường dẫn mới' }
New-Item -ItemType Directory -Path $cluster | Out-Null
try {
    & "$pgBin\initdb.exe" -D $data -U regression --auth=trust --encoding=UTF8 --locale=C *> (Join-Path $cluster 'init.log')
    if ($LASTEXITCODE -ne 0) { throw "initdb thất bại; xem $cluster\init.log" }
    # Bootstrap DB chỉ qua loopback; sau createdb giới hạn HBA về đúng DB test và đúng role.
    'host postgres regression 127.0.0.1/32 trust' | Set-Content -LiteralPath (Join-Path $data 'pg_hba.conf') -Encoding ascii
    # Chỉ chờ pg_ctl, không chờ cả cây postgres chạy nền trên Windows.
    $startProcess = Start-Process -FilePath "$pgBin\pg_ctl.exe" -ArgumentList @('-D', ('"' + $data + '"'), '-l', ('"' + (Join-Path $cluster 'server.log') + '"'), '-o', '"-h 127.0.0.1 -p 55432"', '-w', 'start') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $cluster 'start.log') -RedirectStandardError (Join-Path $cluster 'start-error.log')
    if (!$startProcess.WaitForExit(30000)) { throw 'pg_ctl không kết thúc sau 30 giây' }
    if ($startProcess.ExitCode -ne 0) { throw 'Không khởi động được cluster test' }
    $started = $true
    & "$pgBin\createdb.exe" -w --maintenance-db=postgres -h 127.0.0.1 -p 55432 -U regression jira_regression
    if ($LASTEXITCODE -ne 0) { throw 'Không tạo được DB test' }
    # Tệp cấu hình runtime được ghi cho cluster vừa initdb, không thay cấu hình PostgreSQL ứng dụng.
    'host jira_regression regression 127.0.0.1/32 trust' | Set-Content -LiteralPath (Join-Path $data 'pg_hba.conf') -Encoding ascii
    & "$pgBin\pg_ctl.exe" -D $data reload
    if ($LASTEXITCODE -ne 0) { throw 'Không reload được HBA test' }
    $env:JAVA_HOME = $JavaHome
    Push-Location $repo
    try {
        # WithUnit chạy cả unit và integration từ clean trong cùng lượt, vẫn chỉ dùng cluster test đã xác minh.
        $mavenGoals = if ($WithUnit) { @('clean', 'verify') } else { @('test-compile', 'failsafe:integration-test', 'failsafe:verify') }
        & .\mvnw.cmd -B -ntp -Pintegration @mavenGoals `
            '-Dtest.pg.url=jdbc:postgresql://127.0.0.1:55432/jira_regression' `
            '-Dtest.pg.user=regression' '-Dtest.pg.password=' "-Dtest.pg.data=$data"
        $testExit = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    # Maven đã kết thúc và đóng Spring context trước khi dừng đúng cluster do script này tạo.
    if ($started) { & "$pgBin\pg_ctl.exe" -D $data -m fast -w stop }
    Write-Output "Cluster test riêng và log được giữ tại $cluster"
}
exit $testExit
