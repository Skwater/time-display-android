param(
    [string]$ExpectedVersion
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$appGradle = Join-Path $project 'app\build.gradle'
$gradleText = Get-Content -LiteralPath $appGradle -Raw
$nameMatch = [regex]::Match($gradleText, '(?m)^\s*versionName\s+[\x27\x22]([^\x27\x22]+)')
$codeMatch = [regex]::Match($gradleText, '(?m)^\s*versionCode\s+(\d+)')
if (-not $nameMatch.Success -or -not $codeMatch.Success) {
    throw 'Cannot read versionName/versionCode from app/build.gradle.'
}
$version = $nameMatch.Groups[1].Value
$versionCode = $codeMatch.Groups[1].Value
if ($ExpectedVersion -and $ExpectedVersion -ne $version) {
    throw "Expected version $ExpectedVersion, found $version."
}

$properties = Join-Path $project 'keystore.properties'
$keystore = Join-Path $project 'app\signing\time-display-debug.keystore'
if (-not (Test-Path -LiteralPath $properties) -or -not (Test-Path -LiteralPath $keystore)) {
    throw 'Fixed debug signing files are missing. Refusing a build with a different key.'
}

$sdk = if ($env:ANDROID_HOME -and (Test-Path -LiteralPath $env:ANDROID_HOME)) {
    $env:ANDROID_HOME
} else {
    Join-Path $project '.tooling\sdk'
}
$buildToolsRoot = Join-Path $sdk 'build-tools'
$buildTools = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw "No Android build-tools found in $buildToolsRoot." }
$aapt = Join-Path $buildTools.FullName 'aapt.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$zipalign = Join-Path $buildTools.FullName 'zipalign.exe'
foreach ($tool in @($aapt, $apksigner, $zipalign)) {
    if (-not (Test-Path -LiteralPath $tool)) { throw "Missing tool: $tool" }
}

$bundledGradle = Join-Path $project '.tooling\gradle\gradle-8.13\bin\gradle.bat'
$gradle = if (Test-Path -LiteralPath $bundledGradle) {
    $bundledGradle
} else {
    Join-Path $project 'gradlew.bat'
}
if (-not (Test-Path -LiteralPath $gradle)) { throw "Missing Gradle launcher: $gradle" }

if (-not $env:JAVA_HOME) {
    $localJdk = 'C:\Program Files\Java\jdk-23'
    if (Test-Path -LiteralPath $localJdk) { $env:JAVA_HOME = $localJdk }
}
$env:ANDROID_HOME = $sdk
$env:ANDROID_USER_HOME = Join-Path $project '.tooling\android-home'
$env:GRADLE_USER_HOME = Join-Path $project '.gradle-user-home'
$gradleHome = Join-Path $project '.tooling\user-home'
$env:GRADLE_OPTS = "-Duser.home=$gradleHome"
Remove-Item Env:ANDROID_SDK_HOME -ErrorAction SilentlyContinue
Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue

$temp = Join-Path $project '.tmp'
$releases = Join-Path $project 'releases'
foreach ($directory in @($temp, $releases, $env:ANDROID_USER_HOME, $gradleHome)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$log = Join-Path $temp "build-verify-$version.log"
Push-Location $project
try {
    & $gradle :app:assembleDebug --offline --no-daemon *> $log
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $log -Tail 40 | Write-Output
        throw "Gradle build failed; full log: $log"
    }

    $apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $apk)) { throw "Build output missing: $apk" }
    $badging = & $aapt dump badging $apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'aapt could not read the built APK.' }
    $packageLine = $badging | Select-String -Pattern '^package:' | Select-Object -First 1
    if (-not $packageLine) { throw 'APK package metadata is missing.' }
    $package = $packageLine.Line
    if ($package -notmatch "name='com\.example\.timedisplay'" -or
            $package -notmatch "versionCode='$versionCode'" -or
            $package -notmatch "versionName='$([regex]::Escape($version))'") {
        throw "APK metadata does not match the project version: $package"
    }
    $manifest = Get-Content -LiteralPath (Join-Path $project 'app\src\main\AndroidManifest.xml') -Raw -Encoding utf8
    $labelMatch = [regex]::Match($manifest, 'android:label="([^"]+)"')
    if (-not $labelMatch.Success -or $labelMatch.Groups[1].Value.StartsWith('@')) {
        throw 'Cannot read the expected application label from AndroidManifest.xml.'
    }
    $expectedLabel = $labelMatch.Groups[1].Value
    $labelLine = $badging | Select-String -Pattern '^application-label:' | Select-Object -First 1
    if (-not $labelLine -or $labelLine.Line -ne "application-label:'$expectedLabel'") {
        throw 'APK application label does not match AndroidManifest.xml.'
    }

    $signing = & $apksigner verify --print-certs $apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'apksigner verification failed.' }
    $certificateLine = $signing | Select-String -Pattern 'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)' |
        Select-Object -First 1
    if (-not $certificateLine) { throw 'Signer certificate fingerprint is missing.' }
    $certificate = $certificateLine.Matches.Groups[1].Value.ToUpperInvariant()
    $expectedCertificate = '9E2A7F5106CA4D71913FFFF1ECC24A2120889EB06A3EBAD2155E87E60BAD0703'
    if ($certificate -ne $expectedCertificate) {
        throw "Unexpected signing certificate: $certificate"
    }
    $alignment = & $zipalign -c -v 4 $apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'zipalign verification failed.' }
    @($badging, $signing, $alignment) | Out-File -LiteralPath $log -Append -Encoding utf8

    $release = Join-Path $releases "time-display-$version-debug.apk"
    Copy-Item -LiteralPath $apk -Destination $release -Force
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $release).Hash
    Write-Output "OK: version $version (code $versionCode); APK signature and alignment verified"
    Write-Output "APK: $release"
    Write-Output "APK SHA-256: $hash"
    Write-Output "Signer SHA-256: $certificate"
    Write-Output "Log: $log"
} finally {
    Pop-Location
}
