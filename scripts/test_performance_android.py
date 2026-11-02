"""Run isolated Android performance workload; always restore the ordinary APK."""
import sys
from device import adb,ROOT
phase=sys.argv[1] if len(sys.argv)>1 else 'after'
assert phase in ('before','after')
try:
    adb('install','-r',str(ROOT/'build/embedded/Folio.apk'))
    result=adb('shell','am','instrument','-w','-e','phase',phase,'ru.folio.notes/ru.folio.notes.PerformanceTests')
    (ROOT/'dist'/('native-performance-'+phase+'.txt')).write_text(result+'\n',encoding='utf-8')
    if 'PASS ' not in result or 'FAIL ' in result or 'INSTRUMENTATION_FAILED' in result:raise RuntimeError('Performance checks failed')
    adb('pull','/sdcard/Android/data/ru.folio.notes/files/qa/performance-'+phase+'.json',str(ROOT/'dist'/('performance-'+phase+'.json')))
finally:
    adb('install','-r',str(ROOT/'dist/Folio.apk'))
    adb('shell','am','start','-n','ru.folio.notes/.MainActivity')
