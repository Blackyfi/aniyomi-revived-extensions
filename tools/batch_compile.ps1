# Compile all src/<lang>/<name> modules in chunks (1,400+ Android app modules can't configure at
# once even with configure-on-demand + 6g heap). Aggregates output to wave2_all.log.
param([int]$Size = 90)
$ErrorActionPreference = 'Continue'
Set-Location "C:\Users\nicol\Documents\Projects\aniyomi-revived-extensions"
$mods = @()
Get-ChildItem src -Directory | ForEach-Object {
    $lang = $_.Name
    Get-ChildItem $_.FullName -Directory | ForEach-Object {
        $mods += ":src:${lang}:$($_.Name):compileReleaseKotlin"
    }
}
Set-Content wave2_all.log "total modules: $($mods.Count)" -Encoding utf8
for ($i = 0; $i -lt $mods.Count; $i += $Size) {
    $end = [math]::Min($i + $Size - 1, $mods.Count - 1)
    $chunk = $mods[$i..$end]
    Add-Content wave2_all.log "=== CHUNK $i..$end ===" -Encoding utf8
    & .\gradlew.bat @chunk --continue 2>&1 | Add-Content wave2_all.log -Encoding utf8
}
Add-Content wave2_all.log "=== ALL CHUNKS DONE ===" -Encoding utf8
