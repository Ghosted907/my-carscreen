param(
    [string]$JdkPath = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$KotlinPath = 'C:/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc',
    [string]$SdkPath = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$GradleCachePath = "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1"
)
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
$outputPath = Join-Path $repoPath 't1launcher/build/offline-checks'
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$junitPath = (Get-ChildItem -LiteralPath "$GradleCachePath/junit/junit/4.13.2" -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1).FullName
$hamcrestPath = (Get-ChildItem -LiteralPath "$GradleCachePath/org.hamcrest/hamcrest-core/1.3" -Recurse -Filter 'hamcrest-core-1.3.jar' | Select-Object -First 1).FullName
$androidJarPath = (Get-ChildItem -LiteralPath "$SdkPath/platforms" -Recurse -Filter 'android.jar' | Sort-Object FullName -Descending | Select-Object -First 1).FullName
if (!$junitPath -or !$hamcrestPath -or !$androidJarPath) { throw 'Required cached JUnit/Hamcrest/Android jars were not found.' }
$javaPath = Join-Path $JdkPath 'bin/java.exe'
$compilerArgs = @('-cp', "$KotlinPath/lib/*", 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-kotlin-home', $KotlinPath, '-jvm-target', '17')
$mainPath = Join-Path $repoPath 't1launcher/src/main/java/com/ghosted907/t1launcher'
$testPath = Join-Path $repoPath 't1launcher/src/test/java/com/ghosted907/t1launcher'
& $javaPath @compilerArgs "$mainPath/Telemetry.kt" "$mainPath/SignalCatalog.kt" "$mainPath/CarBridge.kt" -classpath $androidJarPath -d "$outputPath/telemetry.jar"
if ($LASTEXITCODE -ne 0) { throw 'Telemetry compilation failed.' }
& $javaPath @compilerArgs "$mainPath/Telemetry.kt" "$mainPath/SignalCatalog.kt" "$mainPath/ClusterPresets.kt" "$mainPath/EdgeGesture.kt" "$testPath/TelemetryTest.kt" "$testPath/ClusterPresetsTest.kt" "$testPath/EdgeGestureTest.kt" -classpath "$junitPath;$hamcrestPath" -d "$outputPath/tests.jar"
if ($LASTEXITCODE -ne 0) { throw 'Unit test compilation failed.' }
& $javaPath -cp "$outputPath/tests.jar;$junitPath;$hamcrestPath;$KotlinPath/lib/kotlin-stdlib.jar" org.junit.runner.JUnitCore com.ghosted907.t1launcher.TelemetryTest com.ghosted907.t1launcher.ClusterPresetsTest com.ghosted907.t1launcher.EdgeGestureTest
if ($LASTEXITCODE -ne 0) { throw 'Unit tests failed.' }
Write-Output 'Offline checks passed. These do not compile Compose or produce an APK. Run the Gradle checks with the pinned SDK/dependencies before installation.'
