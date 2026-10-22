"""Generate a deterministic, dependency-light Xcode project (Python 3, any OS)."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / 'Folio.xcodeproj'
PROJECT.mkdir(exist_ok=True)
objects = {}
def uid(name): return hashlib.sha1(name.encode()).hexdigest()[:24].upper()
def obj(name, content):
    key = uid(name); objects[key] = content; return key
def quoted(s): return json.dumps(str(s), ensure_ascii=False)
def refs(items): return '(' + ', '.join(items) + ',)'
def source_group(folder, product):
    files, builds = [], []
    for path in sorted((ROOT / folder).glob('*.swift')):
        ref = obj(str(path.relative_to(ROOT)), f'isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = {quoted(path.name)}; sourceTree = "<group>";')
        files.append(ref); builds.append(obj('build:' + folder + '/' + path.name, f'isa = PBXBuildFile; fileRef = {ref};'))
    if folder == 'Folio':
        asset = obj('assets', 'isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = Assets.xcassets; sourceTree = "<group>";')
        files.append(asset)
    group = obj('group:' + folder, f'isa = PBXGroup; children = {refs(files)}; path = {quoted(folder)}; sourceTree = "<group>";')
    sources = obj('sources:' + folder, f'isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = {refs(builds)}; runOnlyForDeploymentPostprocessing = 0;')
    return group, sources

app_group, app_sources = source_group('Folio', 'Folio')
test_group, test_sources = source_group('FolioTests', 'FolioTests')
ui_group, ui_sources = source_group('FolioUITests', 'FolioUITests')
asset_build = obj('build:assets', f'isa = PBXBuildFile; fileRef = {uid("assets")};')
resources = obj('resources', f'isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = ({asset_build},); runOnlyForDeploymentPostprocessing = 0;')
package = obj('zip-package', 'isa = XCRemoteSwiftPackageReference; repositoryURL = "https://github.com/weichsel/ZIPFoundation.git"; requirement = {kind = exactVersion; version = 0.9.20;};')
zip_product = obj('zip-product', f'isa = XCSwiftPackageProductDependency; package = {package}; productName = ZIPFoundation;')
zip_build = obj('zip-build', f'isa = PBXBuildFile; productRef = {zip_product};')
frameworks = obj('frameworks', f'isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = ({zip_build},); runOnlyForDeploymentPostprocessing = 0;')
products = []
targets = []
for name, sources, test_type in [('Folio', app_sources, None), ('FolioTests', test_sources, 'unit-test'), ('FolioUITests', ui_sources, 'ui-testing')]:
    product = obj('product:' + name, f'isa = PBXFileReference; explicitFileType = {"wrapper.cfbundle" if test_type else "wrapper.application"}; includeInIndex = 0; path = {name}{".xctest" if test_type else ".app"}; sourceTree = BUILT_PRODUCTS_DIR;')
    products.append(product)
    configs = []
    for config in ['Debug', 'Release']:
        settings = {
            'PRODUCT_NAME': '$(TARGET_NAME)', 'PRODUCT_BUNDLE_IDENTIFIER': 'ru.folio.notes.ipad' + ('.' + name if test_type else ''),
            'SWIFT_VERSION': '5.0', 'IPHONEOS_DEPLOYMENT_TARGET': '17.0', 'TARGETED_DEVICE_FAMILY': '2',
            'CODE_SIGN_STYLE': 'Automatic', 'GENERATE_INFOPLIST_FILE': 'YES' if test_type else 'NO',
            'SWIFT_OPTIMIZATION_LEVEL': '-Onone' if config == 'Debug' else '-O',
            'DEBUG_INFORMATION_FORMAT': 'dwarf' if config == 'Debug' else 'dwarf-with-dsym',
            'SWIFT_ACTIVE_COMPILATION_CONDITIONS': 'DEBUG' if config == 'Debug' else '',
            'ENABLE_TESTABILITY': 'YES' if config == 'Debug' else 'NO',
            'SUPPORTED_PLATFORMS': 'iphoneos iphonesimulator', 'SUPPORTS_MACCATALYST': 'NO',
        }
        if not test_type:
            settings.update(INFOPLIST_FILE='Folio/Info.plist', ASSETCATALOG_COMPILER_APPICON_NAME='AppIcon', MARKETING_VERSION='0.1.0', CURRENT_PROJECT_VERSION='1', LD_RUNPATH_SEARCH_PATHS='$(inherited) @executable_path/Frameworks')
        elif test_type == 'unit-test':
            settings.update(TEST_HOST='$(BUILT_PRODUCTS_DIR)/Folio.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/Folio', BUNDLE_LOADER='$(TEST_HOST)')
        else: settings['TEST_TARGET_NAME'] = 'Folio'
        content = ' '.join(f'{k} = {quoted(v)};' for k, v in settings.items())
        configs.append(obj('config:' + name + config, f'isa = XCBuildConfiguration; buildSettings = {{{content}}}; name = {config};'))
    config_list = obj('configs:' + name, f'isa = XCConfigurationList; buildConfigurations = {refs(configs)}; defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;')
    phases = [sources] + ([] if test_type else [frameworks, resources])
    deps = []
    if test_type:
        proxy = obj('proxy:' + name, f'isa = PBXContainerItemProxy; containerPortal = {uid("project")}; proxyType = 1; remoteGlobalIDString = {uid("target:Folio")}; remoteInfo = Folio;')
        deps.append(obj('dependency:' + name, f'isa = PBXTargetDependency; target = {uid("target:Folio")}; targetProxy = {proxy};'))
    target = obj('target:' + name, f'isa = PBXNativeTarget; buildConfigurationList = {config_list}; buildPhases = {refs(phases)}; buildRules = (); dependencies = {refs(deps) if deps else "()"}; name = {name}; productName = {name}; productReference = {product}; productType = "com.apple.product-type.{"bundle." + test_type if test_type else "application"}"; packageProductDependencies = {"(" + zip_product + ",)" if not test_type else "()"};')
    targets.append(target)
product_group = obj('group:products', f'isa = PBXGroup; children = {refs(products)}; name = Products; sourceTree = "<group>";')
main = obj('main', f'isa = PBXGroup; children = {refs([app_group, test_group, ui_group, product_group])}; sourceTree = "<group>";')
project_configs = []
for config in ['Debug', 'Release']:
    project_configs.append(obj('project:' + config, f'isa = XCBuildConfiguration; buildSettings = {{CLANG_ENABLE_MODULES = YES; SDKROOT = iphoneos; IPHONEOS_DEPLOYMENT_TARGET = 17.0;}}; name = {config};'))
project_configs = obj('project:configs', f'isa = XCConfigurationList; buildConfigurations = {refs(project_configs)}; defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;')
obj('project', f'isa = PBXProject; attributes = {{BuildIndependentTargetsInParallel = YES; LastUpgradeCheck = 1600;}}; buildConfigurationList = {project_configs}; compatibilityVersion = "Xcode 14.0"; developmentRegion = ru; knownRegions = (ru, en, Base); mainGroup = {main}; productRefGroup = {product_group}; projectDirPath = ""; projectRoot = ""; targets = {refs(targets)}; packageReferences = ({package},);')
body = '\n'.join(f'\t\t{k} = {{{v}}};' for k, v in sorted(objects.items()))
(PROJECT / 'project.pbxproj').write_text('// !$*UTF8*$!\n{archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n' + body + '\n}; rootObject = ' + uid('project') + ';}\n', encoding='utf-8')
scheme_dir = PROJECT / 'xcshareddata' / 'xcschemes'; scheme_dir.mkdir(parents=True, exist_ok=True)
def buildable(name): return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{uid("target:" + name)}" BuildableName="{name}{".app" if name == "Folio" else ".xctest"}" BlueprintName="{name}" ReferencedContainer="container:Folio.xcodeproj"/>'
scheme = f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.7">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{buildable('Folio')}</BuildActionEntry></BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">{buildable('FolioTests')}</TestableReference><TestableReference skipped="NO">{buildable('FolioUITests')}</TestableReference></Testables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{buildable('Folio')}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{buildable('Folio')}</BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>'''
(scheme_dir / 'Folio.xcscheme').write_text(scheme, encoding='utf-8')
print('Generated', PROJECT)
