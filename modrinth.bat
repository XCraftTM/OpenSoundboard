<# : batch launcher - the PowerShell script below does the work
@echo off
setlocal
set "OSB_ROOT=%~dp0"
set "OSB_ARGS=%*"
powershell -NoProfile -ExecutionPolicy Bypass -Command "iex ([IO.File]::ReadAllText('%~f0'))"
set "OSB_EXIT=%ERRORLEVEL%"
if not defined OSB_NO_PAUSE pause
exit /b %OSB_EXIT%
: end of batch launcher #>

# ------------------------------------------------------------------------------------------
# modrinth.bat - builds every supported Minecraft version and uploads the jars to Modrinth.
#
#   modrinth.bat                 build, show a summary, ask, then upload
#   modrinth.bat --dry-run       build and show what would be uploaded, upload nothing
#   modrinth.bat --skip-build    use the jars already in build\jars
#   modrinth.bat --type=beta     upload as beta (release, beta or alpha; default release)
#   modrinth.bat --yes           do not ask before uploading
#
# The changelog is the "## OpenSoundboard <mod_version>" section of CHANGELOG.md. The
# Modrinth token comes from the MODRINTH_TOKEN environment variable, a modrinth_token entry in
# %USERPROFILE%\.gradle\gradle.properties, or is asked for. It needs the "Create versions"
# scope. Versions that already exist on Modrinth are skipped, so a failed run can be repeated.
# Set OSB_NO_PAUSE=1 to skip the final "press any key".
# ------------------------------------------------------------------------------------------

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.Net.Http

$api = 'https://api.modrinth.com/v2'
$userAgent = 'XCraftTM/OpenSoundboard (modrinth.bat)'
# Fabric API is required; either voice chat is needed for others to hear sounds, and Modrinth
# has no "one of", so both are optional. Mod Menu is optional for the settings button.
$dependencies = [ordered]@{
    'fabric-api'        = 'required'
    'simple-voice-chat' = 'optional'
    'plasmo-voice'      = 'optional'
    'modmenu'           = 'optional'
}

function Fail([string] $message) {
    Write-Host ''
    Write-Host "ERROR: $message" -ForegroundColor Red
    exit 1
}

function Read-Props([string] $path) {
    $props = @{}
    foreach ($line in [IO.File]::ReadAllLines($path)) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $i = $t.IndexOf('=')
        if ($i -gt 0) { $props[$t.Substring(0, $i).Trim()] = $t.Substring($i + 1).Trim() }
    }
    return $props
}

function Api-Get([string] $path) {
    return Invoke-RestMethod -Uri "$api$path" -UserAgent $userAgent
}

function Java-Major([string] $javaHome) {
    if (-not $javaHome) { return 0 }
    $release = Join-Path $javaHome 'release'
    if (-not (Test-Path $release)) { return 0 }
    $m = [regex]::Match([IO.File]::ReadAllText($release), 'JAVA_VERSION="(\d+)')
    if ($m.Success) { return [int] $m.Groups[1].Value }
    return 0
}

# ---- options -----------------------------------------------------------------------------

$skipBuild = $false
$dryRun = $false
$assumeYes = $false
$versionType = 'release'
$flags = @()
if ($env:OSB_ARGS) { $flags = $env:OSB_ARGS.Split(' ', [StringSplitOptions]::RemoveEmptyEntries) }
foreach ($flag in $flags) {
    switch -Regex ($flag.ToLowerInvariant()) {
        '^--skip-build$' { $skipBuild = $true }
        '^--dry-run$'    { $dryRun = $true }
        '^--yes$'        { $assumeYes = $true }
        '^--type=(release|beta|alpha)$' { $versionType = $Matches[1] }
        default { Fail "Unknown option '$flag'. Options: --dry-run, --skip-build, --type=release|beta|alpha, --yes" }
    }
}

# ---- project data ------------------------------------------------------------------------

$root = $env:OSB_ROOT.TrimEnd('\')
Set-Location $root
$props = Read-Props "$root\gradle.properties"
$modVersion = $props['mod_version']
$projectSlug = $props['modrinth_project_id']
$baseName = $props['archives_base_name']
if (-not $modVersion -or -not $projectSlug -or -not $baseName) {
    Fail 'gradle.properties needs mod_version, modrinth_project_id and archives_base_name.'
}
$mcVersions = [IO.File]::ReadAllLines("$root\versions\supported.txt") | ForEach-Object { $_.Trim() } | Where-Object { $_ }

# ---- changelog ---------------------------------------------------------------------------

$lines = [IO.File]::ReadAllLines("$root\CHANGELOG.md", [Text.Encoding]::UTF8)
$heading = '^##\s+OpenSoundboard\s+' + [regex]::Escape($modVersion) + '(\s|$)'
$start = -1
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match $heading) { $start = $i; break }
}
if ($start -lt 0) { Fail "CHANGELOG.md has no section '## OpenSoundboard $modVersion'. Add it first." }
$body = New-Object System.Collections.Generic.List[string]
for ($i = $start + 1; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match '^##\s' -or $lines[$i].Trim() -eq '---') { break }
    $body.Add($lines[$i])
}
$changelog = ($body -join "`n").Trim()
if (-not $changelog) { Fail "The CHANGELOG.md section for $modVersion is empty." }

# ---- build -------------------------------------------------------------------------------

if (-not $skipBuild) {
    if ((Java-Major $env:JAVA_HOME) -lt 25) {
        $jdk = Get-ChildItem 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
            Where-Object { (Java-Major $_.FullName) -ge 25 } | Select-Object -First 1
        if (-not $jdk) { Fail 'The build needs Java 25 or newer. Set JAVA_HOME to a JDK 25.' }
        $env:JAVA_HOME = $jdk.FullName
    }
    Write-Host "Building OpenSoundboard $modVersion for all versions with $env:JAVA_HOME ..." -ForegroundColor Cyan
    & "$root\gradlew.bat" buildAllJars --console=plain
    if ($LASTEXITCODE -ne 0) { Fail 'The build failed, nothing was uploaded. See build\nested-gradle.log for details.' }
}

# ---- jars --------------------------------------------------------------------------------

$uploads = @()
foreach ($mc in $mcVersions) {
    $jar = "$root\build\jars\$baseName-$modVersion+mc$mc.jar"
    if (-not (Test-Path $jar)) { Fail "Missing $jar. Run without --skip-build." }
    $versionProps = Read-Props "$root\versions\$mc\gradle.properties"
    $games = $mc
    if ($versionProps['modrinth.game.versions']) { $games = $versionProps['modrinth.game.versions'] }
    $uploads += [pscustomobject]@{
        Mc     = $mc
        Jar    = $jar
        Number = "$modVersion+mc$mc"
        Games  = [string[]] ($games.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    }
}

# ---- Modrinth lookups --------------------------------------------------------------------

Write-Host ''
Write-Host 'Checking Modrinth ...' -ForegroundColor Cyan
try {
    $project = Api-Get "/project/$projectSlug"
    $dependencyIds = [ordered]@{}
    foreach ($slug in $dependencies.Keys) { $dependencyIds[$slug] = (Api-Get "/project/$slug").id }
    $existing = @((Api-Get "/project/$($project.id)/version") | ForEach-Object { $_.version_number })
} catch {
    Fail "Could not reach Modrinth: $($_.Exception.Message)"
}

# ---- summary -----------------------------------------------------------------------------

Write-Host ''
Write-Host "Project:  $($project.title) ($projectSlug)"
Write-Host "Version:  $modVersion ($versionType)"
Write-Host ''
$pending = @()
foreach ($u in $uploads) {
    $size = '{0:N0} KB' -f ((Get-Item $u.Jar).Length / 1KB)
    $line = '  {0,-26} {1,-24} {2}' -f $u.Number, ($u.Games -join ', '), $size
    if ($existing -contains $u.Number) {
        Write-Host "$line  (already on Modrinth, skipped)" -ForegroundColor DarkGray
    } else {
        Write-Host $line
        $pending += $u
    }
}
Write-Host ''
Write-Host '---- changelog ----' -ForegroundColor Cyan
Write-Host $changelog
Write-Host '-------------------' -ForegroundColor Cyan
Write-Host ''

function Version-Data($u) {
    $deps = @()
    foreach ($slug in $dependencies.Keys) {
        $deps += [ordered]@{ project_id = $dependencyIds[$slug]; dependency_type = $dependencies[$slug] }
    }
    return [ordered]@{
        project_id     = $project.id
        name           = "$($project.title) $($u.Number)"
        version_number = $u.Number
        changelog      = $changelog
        dependencies   = $deps
        game_versions  = $u.Games
        version_type   = $versionType
        loaders        = @('fabric')
        featured       = $false
        status         = 'listed'
        file_parts     = @('file')
        primary_file   = 'file'
    }
}

if ($pending.Count -eq 0) {
    Write-Host 'Every version is already on Modrinth. Nothing to upload.' -ForegroundColor Green
    exit 0
}
if ($dryRun) {
    Write-Host "Dry run: would upload $($pending.Count) version(s). Request data for the first one:" -ForegroundColor Yellow
    Write-Host ((Version-Data $pending[0]) | ConvertTo-Json -Depth 5)
    exit 0
}
if (-not $assumeYes) {
    $answer = Read-Host "Upload $($pending.Count) version(s) to Modrinth? (y/N)"
    if ($answer -notmatch '^(y|yes|j|ja)$') {
        Write-Host 'Cancelled, nothing was uploaded.'
        exit 0
    }
}

# ---- token -------------------------------------------------------------------------------

$token = $env:MODRINTH_TOKEN
if (-not $token) {
    $gradleProps = Join-Path $env:USERPROFILE '.gradle\gradle.properties'
    if (Test-Path $gradleProps) { $token = (Read-Props $gradleProps)['modrinth_token'] }
}
if (-not $token) {
    $secure = Read-Host 'Modrinth token (needs the "Create versions" scope)' -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}
if (-not $token) { Fail 'No Modrinth token given.' }

# ---- upload ------------------------------------------------------------------------------

$client = [Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromMinutes(5)
[void] $client.DefaultRequestHeaders.TryAddWithoutValidation('Authorization', $token)
[void] $client.DefaultRequestHeaders.TryAddWithoutValidation('User-Agent', $userAgent)

$uploaded = 0
foreach ($u in $pending) {
    Write-Host "Uploading $($u.Number) ..." -NoNewline
    $json = (Version-Data $u) | ConvertTo-Json -Depth 5
    $form = [Net.Http.MultipartFormDataContent]::new()
    $form.Add([Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json'), 'data')
    $file = [Net.Http.ByteArrayContent]::new([IO.File]::ReadAllBytes($u.Jar))
    $file.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/java-archive')
    $form.Add($file, 'file', [IO.Path]::GetFileName($u.Jar))
    try {
        $response = $client.PostAsync("$api/version", $form).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    } catch {
        Write-Host ''
        Fail "Upload of $($u.Number) failed: $($_.Exception.Message). $uploaded version(s) were uploaded; run again to continue."
    } finally {
        $form.Dispose()
    }
    if (-not $response.IsSuccessStatusCode) {
        Write-Host ''
        Fail "Modrinth rejected $($u.Number) ($([int] $response.StatusCode)): $text. $uploaded version(s) were uploaded; run again to continue."
    }
    $created = $text | ConvertFrom-Json
    Write-Host " done: https://modrinth.com/mod/$projectSlug/version/$($created.id)" -ForegroundColor Green
    $uploaded++
}

Write-Host ''
Write-Host "Uploaded $uploaded version(s) of OpenSoundboard $modVersion to Modrinth." -ForegroundColor Green
exit 0
