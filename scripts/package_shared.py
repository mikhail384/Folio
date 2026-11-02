"""Package and describe the verified pair without claiming an on-device installation."""
from pathlib import Path
import datetime,hashlib,json,zipfile,re
from build import ROOT,DIST

artifacts=json.loads((DIST/'shared-artifact-checks.json').read_text(encoding='utf-8'))
report={
 'version':'1.14.0','versionCode':30,'checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'installed':False,'installedSha256':None,'deviceStatus':'No authorized ADB device connected. Neither new APK installed or tested physically.',
 'apps':artifacts['apps'],
 'namingAndPersistenceValidation':{'canvasUpgradeJvm':{'checks':96597,'report':'canvas-upgrade-tests.txt'},'documentSerializationJvm':{'checks':13008,'report':'document-upgrade-tests.txt'},'contentModelJvm':{'checks':27,'report':'content-model-tests.txt'}},
 'validation':{'stylusCompatibilityJvmWithAndroidTransportSubstituted':{'checks':51,'report':'stylus-compatibility-tests.txt'},'eraserOverlappingMarkerJvm':{'checks':int(re.search(r'PASS (\d+)',(DIST/'eraser-overlap-tests.txt').read_text(encoding='utf-8')).group(1)),'report':'eraser-overlap-tests.txt','benchmark':'eraser-overlap-benchmark.txt','scope':'Desktop production geometry and independent software composition, not Android latency or FPS'},'nativeCompatibilityFixture':{'compiled':True,'executed':False,'source':'tests/CompatibilityEraserTests.java','report':'compat-native-build-checks.json'},'notebookNamingWithRealTemporaryFiles':{'checks':228,'report':'notebook-name-tests.txt'},'providerProtocolAndReadOnlyHealthJvm':{'checks':13,'report':'provider-health-tests.txt'},'automaticStartupPolicyJvm':{'checks':12,'report':'auto-adb-tests.txt'},'productionLocalAdbWithAndroidTransportSubstituted':{'checks':30,'report':'auto-adb-tests.txt'},'sharedRoutingJvm':{'checks':30019,'report':'shared-pen-tests.txt'},'productionFocusFlowRuntimeWithAndroidTransportSubstituted':{'checks':16,'report':'shared-pen-tests.txt'},'gestureRegressionJvm':{'checks':40,'report':'shared-pen-tests.txt'},'configurationMigrationJvm':{'checks':18,'report':'shared-pen-tests.txt'},'hostOrchestrationWithFakeAdb':{'tests':12,'report':'shared-host-tests.txt'},'folioCommandTimingJvm':{'checks':17,'report':'command-timing-tests.txt'},'productionArtifactAudit':'shared-artifact-checks.json','focusflowJavascriptSyntax':'node --check passed'},
 'previousInstalledValidation':'release-checks-1.12.1.json','previousOfflineBuild':'release-checks-1.13.3.json',
 'limits':['No native device test in this release; no physical pen input or wireless ADB startup tested','Already dispatched Android actions cannot be recalled','The helper starts when either app is opened; first-time wireless pairing and enabled wireless debugging are still required; there is no OS-boot receiver','FocusFlow needs its accessibility service for commands outside Folio']}
(DIST/'release-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
files=['Install-Folio-FocusFlow.cmd','Start-Shared-Pen.cmd','scripts/device.py','shared/penbridge/launch.sh','dist/Folio.apk','dist/Folio.apk.sha256','dist/FocusFlow.apk','dist/FocusFlow.apk.sha256','dist/release-checks.json','dist/shared-artifact-checks.json','dist/auto-adb-tests.txt','dist/provider-health-tests.txt','dist/notebook-name-tests.txt','dist/eraser-overlap-tests.txt','dist/eraser-overlap-benchmark.txt','dist/stylus-compatibility-tests.txt','research/device-compatibility.md','dist/compat-native-build-checks.json','dist/eraser-performance.md']
readme='''Folio 1.14.0 + FocusFlow 1.7.1

В этом обновлении изменён Folio. Если FocusFlow 1.7.1 уже установлен, повторное обновление не требуется. APK устанавливаются поверх существующих, без удаления и очистки данных.

Ластик: промежуточные отсчёты объединяются в одну перерисовку к следующему кадру. Кэшируется точная геометрия плоского маркера; сохраняются порядок полупрозрачных штрихов, защищённые слои, отмена и повтор. Обновление включает края области стирания, чтобы не оставлять цветной ореол.

Совместимость Android: нормализация нажима по устройству, фильтрация некорректных осей, устранение двойных сообщений одной кнопки, переход от ладони к стилусу и восстановление инструмента после обратного ластика. Стандартный ввод на устройствах других производителей работает отдельно от служб Xiaomi. Реальные сигналы зависят от пера и прошивки. Список возможностей и ограничений — research/device-compatibility.md. Эта APK не является приложением для iPadOS или HarmonyOS без Android.

Установка с ПК: подключите планшет по USB, разрешите отладку и запустите Install-Folio-FocusFlow.cmd. Нужны Python и Android platform-tools. Скрипт обновляет оба APK и проверяет общий помощник Xiaomi.

Без ПК: установите dist/Folio.apk. Для расширенных жестов Focus Pen Pro нужен также FocusFlow 1.7.1. Откройте приложение; при включённой беспроводной отладке помощник проверяется и запускается через сохранённое сопряжение. Первое сопряжение выполняется по коду в FocusFlow. При выключенной отладке карточка предлагает переход в настройки. В открытом Folio команды FocusFlow приостановлены.

Подписи APK и настольные проверки пройдены. Нативный пакет тестов собран, но не запускался. Планшет не подключён: новая версия не установлена и не проверена на физических устройствах. Цифры dist/eraser-overlap-benchmark.txt относятся только к геометрии на настольной JVM, не к FPS или задержке пера.
'''
with zipfile.ZipFile(DIST/'Folio-FocusFlow.zip','w',zipfile.ZIP_DEFLATED) as z:
 for name in files:z.write(ROOT/name,name)
 z.writestr('Начните здесь.txt',readme.encode('utf-8-sig'))
with zipfile.ZipFile(DIST/'Folio-FocusFlow.zip') as z:
 assert z.testzip() is None
 for name in files:assert z.read(name)==(ROOT/name).read_bytes()
 assert not any(Path(n).suffix.lower() in {'.jks','.pem','.key','.p12'} for n in z.namelist())
(DIST/'Folio-FocusFlow.zip.sha256').write_text(hashlib.sha256((DIST/'Folio-FocusFlow.zip').read_bytes()).hexdigest()+'  Folio-FocusFlow.zip\n',encoding='ascii')
print('PASS: matching APKs and launch scripts packaged; hashes/ZIP contents verified; no keys included')
