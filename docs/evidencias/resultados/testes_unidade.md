# Saída do comando de teste

- Comando: `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks`
- Commit: `b9c70d3` (com alterações não comitadas) · executado por juliano.ramos@estudante.ufla.br em CORSA / Microsoft Windows NT 10.0.26100.0
- Início: 21/10/2026 20:38:13 · fim: 20:38:57 · código de saída: 0

| Classe | Testes | Falhas | Erros | Ignorados | Tempo (s) |
|---|---|---|---|---|---|
| com.example.rastro.av1.AnalisesTest | 2 | 0 | 0 | 0 | 0.106 |
| com.example.rastro.av1.DetectorErroGrosseiroTest | 3 | 0 | 0 | 0 | 0.086 |
| com.example.rastro.av1.EnsaioSensibilidadeTest | 2 | 0 | 0 | 0 | 0.278 |
| com.example.rastro.av1.EstatisticaTest | 1 | 0 | 0 | 0 | 0.001 |
| com.example.rastro.av1.EvidenciasAv1Test | 3 | 0 | 0 | 3 | 0.017 |
| com.example.rastro.av1.InjetorFalhasTest | 3 | 0 | 0 | 0 | 0.002 |
| com.example.rastro.av1.MatrizTest | 3 | 0 | 0 | 0 | 0.004 |
| com.example.rastro.av1.ReconciliadorTest | 6 | 0 | 0 | 0 | 0.037 |
| com.example.rastro.av1.RegistroCsvTest | 4 | 0 | 0 | 0 | 0.006 |
| com.example.rastro.av1.ReprodutorRegistroTest | 1 | 0 | 0 | 0 | 0.018 |
| com.example.rastro.chat.ChatProtocolTest | 8 | 0 | 0 | 0 | 0.581 |
| com.example.rastro.chat.ChatStoreTest | 6 | 0 | 0 | 0 | 9.07 |
| com.example.rastro.chat.CustodyEngineTest | 11 | 0 | 0 | 0 | 1.449 |
| com.example.rastro.chat.ForwardEngineTest | 16 | 0 | 0 | 0 | 2.042 |
| com.example.rastro.ExampleUnitTest | 1 | 0 | 0 | 0 | 0.009 |
| com.example.rastro.IMUReadTest | 9 | 0 | 0 | 0 | 0.01 |
| com.example.rastro.mapa.PerfilAlcanceTest | 4 | 0 | 0 | 0 | 0.004 |
| com.example.rastro.network.IdentidadeNoTest | 4 | 0 | 0 | 0 | 0.004 |
| com.example.rastro.network.MensagemTest | 4 | 0 | 0 | 0 | 0.005 |
| com.example.rastro.network.MeshRouterTest | 5 | 0 | 0 | 0 | 0.003 |
| com.example.rastro.network.ProtocoloV3Test | 7 | 0 | 0 | 0 | 0.007 |
| com.example.rastro.PaginasPrincipaisTest | 4 | 0 | 0 | 0 | 4.319 |
| com.example.rastro.PosicaoTest | 13 | 0 | 0 | 0 | 0.014 |
| com.example.rastro.s2.CircularImuBufferConcurrencyTest | 10 | 0 | 0 | 0 | 0.058 |
| com.example.rastro.s2.CircularImuBufferTest | 4 | 0 | 0 | 0 | 0.002 |
| com.example.rastro.s2.CovarianciaTest | 6 | 0 | 0 | 0 | 0.003 |
| com.example.rastro.sos.HistoricoSessaoTest | 5 | 0 | 0 | 0 | 0.003 |
| com.example.rastro.TrajetoTest | 12 | 0 | 0 | 0 | 0.01 |

Testes ignorados em EvidenciasAv1Test significam que aquele registro físico ainda não foi copiado para docs\evidencias\dados.

```text

C:\Projetos\rastro>if "Windows_NT" == "Windows_NT" setlocal

C:\Projetos\rastro>set DIRNAME=C:\Projetos\rastro\ 

C:\Projetos\rastro>if "C:\Projetos\rastro\" == "" set DIRNAME=. 

C:\Projetos\rastro>set APP_BASE_NAME=gradlew 

C:\Projetos\rastro>set APP_HOME=C:\Projetos\rastro\ 

C:\Projetos\rastro>for %i in ("C:\Projetos\rastro\") do set APP_HOME=%~fi 

C:\Projetos\rastro>set APP_HOME=C:\Projetos\rastro\ 

C:\Projetos\rastro>set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m" 

C:\Projetos\rastro>if defined JAVA_HOME goto findJavaFromJavaHome 

C:\Projetos\rastro>set JAVA_EXE=java.exe 

C:\Projetos\rastro>java.exe -version  1>NUL 2>&1 

C:\Projetos\rastro>if 0 EQU 0 goto execute 

C:\Projetos\rastro>set CLASSPATH= 

C:\Projetos\rastro>"java.exe" "-Xmx64m" "-Xms64m"   "-Dorg.gradle.appname=gradlew" -classpath "" -jar "C:\Projetos\rastro\\gradle\wrapper\gradle-wrapper.jar" :app:testDebugUnitTest --rerun-tasks --console=plain 
Calculating task graph as no cached configuration is available for tasks: :app:testDebugUnitTest
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugUnitTestBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests
> Task :app:mapDebugSourceSetPaths
> Task :app:extractDeepLinksDebug
> Task :app:generateDebugResources
> Task :app:javaPreCompileDebugUnitTest
> Task :app:javaPreCompileDebug
> Task :app:processDebugNavigationResources
> Task :app:processDebugMainManifest
> Task :app:checkDebugAarMetadata
> Task :app:compileDebugNavigationResources
> Task :app:processDebugManifest
> Task :app:processDebugManifestForPackage
> Task :app:packageDebugResources
> Task :app:parseDebugLocalResources
> Task :app:generateDebugRFile
> Task :app:mergeDebugResources
> Task :app:processDebugResources
> Task :app:compileDebugKotlin
> Task :app:compileDebugJavaWithJavac NO-SOURCE
> Task :app:processDebugJavaRes
> Task :app:bundleDebugClassesToCompileJar
> Task :app:bundleDebugClassesToRuntimeJar

> Task :app:compileDebugUnitTestKotlin
w: file:///C:/Projetos/rastro/app/src/test/java/com/example/rastro/av1/EvidenciasAv1Test.kt:29:38 Java type mismatch: inferred type is 'String?', but 'String' was expected.
w: file:///C:/Projetos/rastro/app/src/test/java/com/example/rastro/av1/EvidenciasAv1Test.kt:43:85 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'File?'.

> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes
gradlew.bat : WARNING: A restricted method in java.lang.System has been called
No C:\Projetos\rastro\scripts\av1_evidencias.ps1:34 caractere:8
+ $log = & .\gradlew.bat :app:testDebugUnitTest --rerun-tasks --console ...
+        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : NotSpecified: (WARNING: A rest...has been called:String) [], RemoteException
    + FullyQualifiedErrorId : NativeCommandError
 
WARNING: java.lang.System::load has been called by org.conscrypt.NativeLibraryUtil in an unnamed module (file:/C:/Users
/Juliano%20Cesar/.gradle/caches/modules-2/files-2.1/org.conscrypt/conscrypt-openjdk-uber/2.6.2/65c5977595ee9cc12e6017b9
d43b1d2e3d1bab63/conscrypt-openjdk-uber-2.6.2.jar)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled

WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::arrayBaseOffset has been called by 
com.google.crypto.tink.shaded.protobuf.UnsafeUtil$MemoryAccessor (file:/C:/Users/Juliano%20Cesar/.gradle/caches/modules
-2/files-2.1/com.google.crypto.tink/tink-android/1.18.0/f88825263151788186d0590835e3938e153bb6cf/tink-android-1.18.0.ja
r)
WARNING: Please consider reporting this to the maintainers of class 
com.google.crypto.tink.shaded.protobuf.UnsafeUtil$MemoryAccessor
WARNING: sun.misc.Unsafe::arrayBaseOffset will be removed in a future release
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 43s
24 actionable tasks: 24 executed
Configuration cache entry stored.

C:\Projetos\rastro>if 0 EQU 0 goto mainEnd 

C:\Projetos\rastro>if "Windows_NT" == "Windows_NT" endlocal

```
