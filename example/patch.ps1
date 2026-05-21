$SERVER_JAR = "server.jar"
$PROFILES_JSON = "profiles.json"

$null = New-Item -ItemType Directory -Force -Path out/
$AUTHLIB_ID = java -jar ../build/libs/server-extractor.jar $SERVER_JAR com.mojang:authlib out/

if ($LASTEXITCODE -eq 0) {
    Write-Host "Found: $AUTHLIB_ID"
    $AUTHLIB_JAR = Get-ChildItem -Path out/ -Filter "authlib-*.jar"
    if ($AUTHLIB_JAR) {
        Write-Host "Authlib JAR: $($AUTHLIB_JAR.FullName)"
        if($AUTHLIB_JAR.FullName.EndsWith("_patched.jar")){
            Write-Host "Authlib JAR is already patched"
            $null = Remove-Item -Path out/ -Recurse -Force
            exit 0
        }

        $AUTHLIB_PATCHED_JAR = Join-Path $AUTHLIB_JAR.DirectoryName ($AUTHLIB_JAR.BaseName + "_patched" + $AUTHLIB_JAR.Extension)
        java -jar ../build/libs/authlib-patcher.jar $AUTHLIB_JAR.FullName $PROFILES_JSON
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Authlib patch successful ($AUTHLIB_PATCHED_JAR)"
            java -jar ../build/libs/server-patcher.jar $SERVER_JAR $AUTHLIB_ID $AUTHLIB_PATCHED_JAR
            if ($LASTEXITCODE -eq 0) {
                Write-Host "Server patch successful"
                $null = Remove-Item -Path out/ -Recurse -Force
                exit 0
            } else {
                Write-Host "Server patch failed"
                $null = Remove-Item -Path out/ -Recurse -Force
                exit 1
            }
        } else {
            Write-Host "Authlib patch failed"
            $null = Remove-Item -Path out/ -Recurse -Force
            exit 1
        }
    } else {
        Write-Host "Authlib JAR not found after extraction"
        $null = Remove-Item -Path out/ -Recurse -Force
        exit 1
    }
} else {
    Write-Host "Authlib extraction failed"
    $null = Remove-Item -Path out/ -Recurse -Force
    exit 1
}
