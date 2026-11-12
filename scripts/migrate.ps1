param(
    [Parameter(Mandatory=$true)][string]$Database,
    [string]$HostName='127.0.0.1', [int]$Port=5432, [string]$UserName='postgres',
    [string]$PgBin='C:\Program Files\PostgreSQL\18\bin'
)
$ErrorActionPreference='Stop'

# Người chạy chỉ định rõ DB; password do PGPASSWORD/pgpass cung cấp, không đặt trong lệnh hay log.
if ($Database -notmatch '^[a-zA-Z0-9_]+$') { throw 'Tên database không hợp lệ' }
$repo=Split-Path $PSScriptRoot -Parent
$sql= [System.Text.StringBuilder]::new()
[void]$sql.AppendLine('\set ON_ERROR_STOP on')
[void]$sql.AppendLine("SELECT pg_advisory_lock(hashtext('jira_schema_migrations'));")
[void]$sql.AppendLine("SELECT to_regclass('public.schema_migrations') IS NULL AS unmanaged \gset")
[void]$sql.AppendLine('\if :unmanaged')
# DB cũ không có ledger phải được đối chiếu riêng; không tự nhận mọi schema có sẵn là đúng baseline.
[void]$sql.AppendLine(@'
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE') THEN
  RAISE EXCEPTION 'DB nonempty without migration history: verify legacy schema before adoption';
 END IF;
END $$;
CREATE TABLE public.schema_migrations (name varchar(100) PRIMARY KEY, sha256 varchar(64) NOT NULL, applied_at timestamp NOT NULL DEFAULT current_timestamp);
'@)
[void]$sql.AppendLine('\endif')

# Mỗi file và dòng ledger commit cùng transaction. Khóa advisory giữ xuyên suốt trên cùng kết nối psql.
foreach ($file in (Get-ChildItem (Join-Path $PSScriptRoot 'migrations') -Filter '*.sql' | Sort-Object Name)) {
    # Git có thể đổi LF/CRLF giữa Windows và Linux; checksum tính trên UTF-8/LF để cùng SQL có cùng hash.
    $content=Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $canonical=$content.Replace("`r`n","`n").Replace("`r","`n")
    $sha=[System.Security.Cryptography.SHA256]::Create()
    try { $hash=[BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($canonical))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
    $name=$file.Name
    [void]$sql.AppendLine("DO `$`$ BEGIN IF EXISTS (SELECT 1 FROM public.schema_migrations WHERE name='$name' AND sha256<>'$hash') THEN RAISE EXCEPTION 'Migration checksum changed: $name'; END IF; END `$`$;")
    [void]$sql.AppendLine("SELECT NOT EXISTS (SELECT 1 FROM public.schema_migrations WHERE name='$name') AS pending \gset")
    [void]$sql.AppendLine('\if :pending')
    [void]$sql.AppendLine('BEGIN;')
    # Baseline do pg_dump sinh; bỏ wrapper transaction của script cũ để ledger nằm trong cùng commit.
    $content=[regex]::Replace($content,'(?m)^\s*(BEGIN;|COMMIT;|\\restrict .*|\\unrestrict .*)\r?$','')
    [void]$sql.AppendLine($content)
    # pg_dump đặt search_path rỗng; khôi phục public trước các migration cũ dùng tên bảng ngắn.
    [void]$sql.AppendLine('SET search_path TO public;')
    [void]$sql.AppendLine("INSERT INTO public.schema_migrations(name,sha256) VALUES ('$name','$hash');")
    [void]$sql.AppendLine('COMMIT;')
    [void]$sql.AppendLine('\endif')
}
[void]$sql.AppendLine("SELECT pg_advisory_unlock(hashtext('jira_schema_migrations'));")
$batch=Join-Path ([System.IO.Path]::GetTempPath()) ('jira-migrate-'+[guid]::NewGuid().ToString('N')+'.sql')
try {
    [System.IO.File]::WriteAllText($batch,$sql.ToString(),[System.Text.UTF8Encoding]::new($false))
    & (Join-Path $PgBin 'psql.exe') -X -w -h $HostName -p $Port -U $UserName -d $Database -v ON_ERROR_STOP=1 -f $batch
    if ($LASTEXITCODE -ne 0) { throw 'Migration thất bại; không khởi động backend với schema chưa xác nhận' }
} finally {
    # Chỉ xóa tệp tạm do chính lượt này vừa tạo; không xóa dữ liệu DB hay thư mục người dùng.
    if (Test-Path -LiteralPath $batch) { Remove-Item -LiteralPath $batch }
}
