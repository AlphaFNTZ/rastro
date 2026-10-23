# Saída do comando de teste

- Comando: `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks`
- Commit: `710ef50` (árvore limpa) · executado por joao.silva86@estudante.ufla.br em JOAO-PAULO / Microsoft Windows NT 10.0.26300.0
- Início: 22/10/2026 23:26:56 · fim: 23:27:23 · código de saída: 0

| Classe | Testes | Falhas | Erros | Ignorados | Tempo (s) |
|---|---|---|---|---|---|
| com.example.rastro.av1.AnalisesTest | 3 | 0 | 0 | 0 | 0.133 |
| com.example.rastro.av1.DetectorErroGrosseiroTest | 3 | 0 | 0 | 0 | 0.092 |
| com.example.rastro.av1.EnsaioSensibilidadeTest | 2 | 0 | 0 | 0 | 0.298 |
| com.example.rastro.av1.EstatisticaTest | 1 | 0 | 0 | 0 | 0.001 |
| com.example.rastro.av1.EvidenciasAv1Test | 5 | 0 | 0 | 0 | 1.719 |
| com.example.rastro.av1.InjetorFalhasTest | 3 | 0 | 0 | 0 | 0.004 |
| com.example.rastro.av1.JanelasTest | 1 | 0 | 0 | 0 | 0.0 |
| com.example.rastro.av1.MatrizTest | 3 | 0 | 0 | 0 | 0.007 |
| com.example.rastro.av1.ReconciliadorTest | 6 | 0 | 0 | 0 | 0.032 |
| com.example.rastro.av1.RegistroCsvTest | 4 | 0 | 0 | 0 | 0.003 |
| com.example.rastro.av1.ReprodutorRegistroTest | 1 | 0 | 0 | 0 | 0.02 |
| com.example.rastro.chat.ChatProtocolTest | 8 | 0 | 0 | 0 | 0.533 |
| com.example.rastro.chat.ChatStoreTest | 6 | 0 | 0 | 0 | 3.241 |
| com.example.rastro.chat.CustodyEngineTest | 11 | 0 | 0 | 0 | 2.385 |
| com.example.rastro.chat.ForwardEngineTest | 16 | 0 | 0 | 0 | 2.785 |
| com.example.rastro.ExampleUnitTest | 1 | 0 | 0 | 0 | 0.004 |
| com.example.rastro.IMUReadTest | 9 | 0 | 0 | 0 | 0.006 |
| com.example.rastro.mapa.PerfilAlcanceTest | 4 | 0 | 0 | 0 | 0.003 |
| com.example.rastro.network.IdentidadeNoTest | 4 | 0 | 0 | 0 | 0.003 |
| com.example.rastro.network.MensagemTest | 4 | 0 | 0 | 0 | 0.006 |
| com.example.rastro.network.MeshRouterTest | 5 | 0 | 0 | 0 | 0.005 |
| com.example.rastro.network.ProtocoloV3Test | 7 | 0 | 0 | 0 | 0.008 |
| com.example.rastro.PaginasPrincipaisTest | 4 | 0 | 0 | 0 | 3.455 |
| com.example.rastro.PosicaoTest | 13 | 0 | 0 | 0 | 0.017 |
| com.example.rastro.s2.CircularImuBufferConcurrencyTest | 10 | 0 | 0 | 0 | 0.054 |
| com.example.rastro.s2.CircularImuBufferTest | 4 | 0 | 0 | 0 | 0.002 |
| com.example.rastro.s2.CovarianciaTest | 6 | 0 | 0 | 0 | 0.003 |
| com.example.rastro.sos.HistoricoSessaoTest | 5 | 0 | 0 | 0 | 0.004 |
| com.example.rastro.TrajetoTest | 12 | 0 | 0 | 0 | 0.011 |

Testes ignorados em EvidenciasAv1Test significam que aquele registro físico ainda não foi copiado para docs\evidencias\dados.

```text
Reusing configuration cache.
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugUnitTestBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mapDebugSourceSetPaths
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:javaPreCompileDebug
> Task :app:javaPreCompileDebugUnitTest
> Task :app:generateDebugResources
> Task :app:processDebugNavigationResources
> Task :app:compileDebugNavigationResources
> Task :app:checkDebugAarMetadata
> Task :app:processDebugMainManifest
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
> Task :app:bundleDebugClassesToRuntimeJar
> Task :app:bundleDebugClassesToCompileJar

> Task :app:compileDebugUnitTestKotlin
w: file:///C:/Users/joaop/Documents/GitHub/rastro/app/src/test/java/com/example/rastro/av1/EvidenciasAv1Test.kt:30:38 Java type mismatch: inferred type is 'String?', but 'String' was expected.
w: file:///C:/Users/joaop/Documents/GitHub/rastro/app/src/test/java/com/example/rastro/av1/EvidenciasAv1Test.kt:44:85 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'File?'.

> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes
gradlew.bat : WARNING: A restricted method in java.lang.System has been called
At C:\Users\joaop\Documents\GitHub\rastro\scripts\av1_evidencias.ps1:34 char:8
+ $log = & .\gradlew.bat :app:testDebugUnitTest --rerun-tasks --console ...
+        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : NotSpecified: (WARNING: A rest...has been called:String) [], RemoteException
    + FullyQualifiedErrorId : NativeCommandError
 
WARNING: java.lang.System::load has been called by org.conscrypt.NativeLibraryUtil in an unnamed module 
(file:/C:/Users/joaop/.gradle/caches/modules-2/files-2.1/org.conscrypt/conscrypt-openjdk-uber/2.6.2/65c5977595ee9cc12e6017b9d43b1d2e3d1bab63/conscrypt-openjdk-uber-2.6.2.jar)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled

WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::arrayBaseOffset has been called by com.google.crypto.tink.shaded.protobuf.UnsafeUtil$MemoryAccessor 
(file:/C:/Users/joaop/.gradle/caches/modules-2/files-2.1/com.google.crypto.tink/tink-android/1.18.0/f88825263151788186d0590835e3938e153bb6cf/tink-android-1.18.0.jar)
WARNING: Please consider reporting this to the maintainers of class com.google.crypto.tink.shaded.protobuf.UnsafeUtil$MemoryAccessor
WARNING: sun.misc.Unsafe::arrayBaseOffset will be removed in a future release
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 26s
24 actionable tasks: 24 executed
Configuration cache entry reused.

```
