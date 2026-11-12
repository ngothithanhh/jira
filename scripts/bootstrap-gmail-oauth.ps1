#requires -Version 7.2
param(
    [Parameter(Mandatory)][string]$DesktopClientJson,
    [Parameter(Mandatory)][string]$SenderAddress
)
$ErrorActionPreference = 'Stop'
$scope = 'https://www.googleapis.com/auth/gmail.send'

function To-Base64Url([byte[]]$bytes) {
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

# Một callback chỉ có một dòng yêu cầu tối đa 8192 byte và một hạn đọc chung.
function Read-CallbackLine($Connection, $Stream, [DateTimeOffset]$Deadline) {
    $buffer = [byte[]]::new(8192)
    for ($index = 0; $index -lt $buffer.Length; $index++) {
        $remaining = ($Deadline - [DateTimeOffset]::UtcNow).TotalMilliseconds
        if ($remaining -le 0) { return }
        $Connection.ReceiveTimeout = [int][Math]::Ceiling($remaining)
        $value = $Stream.ReadByte()
        if ($value -eq -1) { return }
        if ($value -eq 10) { return [Text.Encoding]::ASCII.GetString($buffer, 0, $index).TrimEnd("`r") }
        $buffer[$index] = [byte]$value
    }
}

try {
    $client = (Get-Content -LiteralPath $DesktopClientJson -Raw | ConvertFrom-Json).installed
    $sender = [System.Net.Mail.MailAddress]::new($SenderAddress)
} catch { throw 'Tệp OAuth Desktop hoặc địa chỉ sender không hợp lệ.' }
if (!$client.client_id -or !$client.client_secret -or $sender.Address -cne $SenderAddress -or $SenderAddress -match '[\r\n]') {
    throw 'Cần JSON của OAuth Desktop client riêng và địa chỉ Gmail sender đã xác nhận.'
}

$verifier = To-Base64Url ([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$challenge = To-Base64Url ([Security.Cryptography.SHA256]::HashData([Text.Encoding]::ASCII.GetBytes($verifier)))
$state = To-Base64Url ([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)

try {
    # Chỉ lắng nghe trên 127.0.0.1, giữ cùng cổng từ lúc mở consent đến callback.
    $listener.Start()
    $redirect = "http://127.0.0.1:$($listener.LocalEndpoint.Port)/oauth2/callback"
    $parameters = [ordered]@{
        client_id = $client.client_id; redirect_uri = $redirect; response_type = 'code'
        scope = $scope; state = $state; code_challenge = $challenge; code_challenge_method = 'S256'
        access_type = 'offline'; prompt = 'consent'; login_hint = $SenderAddress
    }
    $query = ($parameters.GetEnumerator() | ForEach-Object { "$($_.Key)=$([Uri]::EscapeDataString($_.Value))" }) -join '&'
    Start-Process "https://accounts.google.com/o/oauth2/v2/auth?$query"
    $deadline = [DateTimeOffset]::UtcNow.AddMinutes(4)
    $authorizationCode = $null
    do {
        $remaining = $deadline - [DateTimeOffset]::UtcNow
        if ($remaining.TotalMilliseconds -le 0) { throw 'Hết thời gian chờ đồng ý OAuth.' }
        try { $connection = $listener.AcceptTcpClientAsync().WaitAsync($remaining).GetAwaiter().GetResult() }
        catch { throw 'Hết thời gian chờ đồng ý OAuth.' }
        $valid = $false
        $denied = $false
        try {
            $connection.SendTimeout = 2000
            $stream = $connection.GetStream()
            $readDeadline = [DateTimeOffset]::UtcNow.AddSeconds(2)
            if ($deadline -lt $readDeadline) { $readDeadline = $deadline }
            $line = Read-CallbackLine $connection $stream $readDeadline
            if ($line -and $line -match '^GET (/oauth2/callback\?[^ ]+) HTTP/1\.[01]$') {
                $returned = [System.Web.HttpUtility]::ParseQueryString(([Uri]::new("http://127.0.0.1$($Matches[1])")).Query)
                if ($returned.GetValues('state').Count -eq 1 -and $returned['state'] -ceq $state) {
                    $denied = [bool]$returned['error']
                    $valid = !$denied -and $returned.GetValues('code').Count -eq 1 -and [bool]$returned['code']
                    if ($valid) { $authorizationCode = $returned['code'] }
                }
            }
            $status = if ($valid) { '200 OK' } else { '400 Bad Request' }
            $message = if ($valid) { 'Callback received. Return to the terminal for the OAuth result.' } else { 'OAuth callback rejected.' }
            $response = [Text.Encoding]::ASCII.GetBytes("HTTP/1.1 $status`r`nContent-Type: text/plain; charset=us-ascii`r`nCache-Control: no-store`r`nConnection: close`r`nContent-Length: $($message.Length)`r`n`r`n$message")
            $stream.Write($response, 0, $response.Length)
        } catch {
            # Callback hỏng không được in URL, query hoặc code.
        } finally { $connection.Dispose() }
        if ($denied) { throw 'Google chưa cấp quyền gửi Gmail.' }
    } until ($authorizationCode)
} finally { $listener.Stop() }

try {
    $tokens = Invoke-RestMethod -Method Post -Uri 'https://oauth2.googleapis.com/token' -ContentType 'application/x-www-form-urlencoded' `
        -MaximumRedirection 0 -TimeoutSec 12 -Body @{
            code = $authorizationCode; client_id = $client.client_id; client_secret = $client.client_secret
            redirect_uri = $redirect; grant_type = 'authorization_code'; code_verifier = $verifier
        }
} catch { throw 'Google từ chối trao đổi OAuth hoặc kết nối hết hạn.' }
if ($tokens.scope -cne $scope -or !$tokens.refresh_token) { throw 'Google chưa cấp đúng scope gmail.send hoặc refresh token.' }

# Đặt ACL trên thư mục mới trước khi ghi credential. File chỉ được tạo ngoài Git.
$privateRoot = [IO.Path]::GetFullPath((Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) ('JiraGmail-' + [Guid]::NewGuid().ToString('N'))))
for ($parent = [IO.DirectoryInfo]::new($privateRoot); $parent; $parent = $parent.Parent) {
    if (Test-Path -LiteralPath (Join-Path $parent.FullName '.git')) { throw 'Chọn tài khoản Windows có LocalAppData ngoài Git.' }
}
$null = New-Item -ItemType Directory -Path $privateRoot
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl = Get-Acl -LiteralPath $privateRoot
$acl.SetAccessRuleProtection($true, $false)
foreach ($rule in @($acl.Access)) { $acl.RemoveAccessRuleAll($rule) }
$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
Set-Acl -LiteralPath $privateRoot -AclObject $acl
$tokenFile = Join-Path $privateRoot 'gmail-token.json'
@{
    senderAddress = $SenderAddress; scope = $scope; clientId = $client.client_id
    clientSecret = $client.client_secret; refreshToken = $tokens.refresh_token
} | ConvertTo-Json -Compress | Set-Content -LiteralPath $tokenFile -Encoding utf8NoBOM
Write-Output "OAuth đã cấp gmail.send. Đặt GMAIL_TOKEN_FILE trỏ tới $tokenFile"
