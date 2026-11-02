"""Host orchestration checks with fake ADB; never sends commands to a device."""
import contextlib,io,sys,unittest
from unittest.mock import patch
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import device

class HostChecks(unittest.TestCase):
 def quiet(self):return contextlib.redirect_stdout(io.StringIO())
 def test_no_tablet_cannot_claim_success(self):
  with patch.object(device,'adb',return_value='List of devices attached\n'),patch.object(device.time,'monotonic',side_effect=[0,1]),self.quiet():
   with self.assertRaisesRegex(RuntimeError,'не подключён'):device.wait_for_tablet(seconds=0)
 def test_unauthorized_not_accepted(self):
  with patch.object(device,'adb',return_value='List of devices attached\n123\tunauthorized'),patch.object(device.time,'monotonic',side_effect=[0,1]),self.quiet():
   with self.assertRaises(RuntimeError):device.wait_for_tablet(seconds=0)
 def test_two_devices_rejected(self):
  with patch.object(device,'adb',return_value='List of devices attached\na\tdevice\nb\tdevice'),self.quiet():
   with self.assertRaisesRegex(RuntimeError,'один'):device.wait_for_tablet()
 def test_legacy_bridge_not_shared_success(self):
  with patch.object(device,'adb',return_value='Bundle[{connected=true, device=/dev/input/event19, shared=false}]'),patch.object(device.time,'monotonic',side_effect=[0,1]):
   with self.assertRaisesRegex(RuntimeError,'не подтверждено'):device.confirm_bridge(seconds=0)
 def test_no_pen_not_success(self):
  with patch.object(device,'adb',return_value='Bundle[{connected=true, shared=true, device=}]'),patch.object(device.time,'monotonic',side_effect=[0,1]):
   with self.assertRaisesRegex(RuntimeError,'перо'):device.confirm_bridge(seconds=0)
 def test_both_endpoints_required(self):
  with patch.object(device,'adb',side_effect=['Bundle[{connected=true, shared=true, device=/dev/input/event19}]','Bundle[{connected=false}]']),patch.object(device.time,'monotonic',side_effect=[0,1]):
   with self.assertRaisesRegex(RuntimeError,'не подтверждено'):device.confirm_bridge(seconds=0,focusflow=True)
 def test_both_endpoints_confirmed(self):
  with patch.object(device,'adb',side_effect=['Bundle[{connected=true, shared=true, device=/dev/input/event19}]','Bundle[{connected=true}]']),self.quiet():device.confirm_bridge(focusflow=True)
 def test_install_is_upgrade_never_uninstall(self):
  with patch.object(sys,'argv',['device.py','install-shared']),patch.object(device,'adb',return_value='List of devices attached\na\tdevice') as adb,patch.object(device,'start_shared') as start,self.quiet():
   device.main();self.assertIn(('install','-r',str(device.ROOT/'dist/Folio.apk')),[c.args for c in adb.call_args_list]);self.assertIn(('install','-r',str(device.ROOT/'dist/FocusFlow.apk')),[c.args for c in adb.call_args_list]);self.assertFalse(any('uninstall' in c.args or 'clear' in c.args for c in adb.call_args_list));start.assert_not_called()
 def test_other_shell_commands_never_stopped(self):
  with patch.object(device,'adb',return_value='USER PID ARGS\nshell 123 sh -c folio-pen-shared\nu0_a99 234 folio-pen-shared\nshell 456 focusflow-bridge') as adb:
   device.stop_shared();self.assertEqual(adb.call_count,1)
 def test_identity_change_never_killed(self):
  with patch.object(device,'adb',side_effect=['shell 123 folio-pen-shared','other process']) as adb:
   with self.assertRaisesRegex(RuntimeError,'identity changed'):device.stop_shared()
   self.assertFalse(any('kill' in c.args for c in adb.call_args_list))
 def test_shared_stop_identity(self):
  with patch.object(device,'adb',side_effect=['shell 123 folio-pen-shared','folio-pen-shared\0','']) as adb:
   device.stop_shared();self.assertEqual(adb.call_args.args,('shell','kill','123'))
 def test_launcher_no_old_conflict_or_duplicate_reader(self):
  with patch.object(device,'adb',side_effect=['package:/data/app/test/base.apk','FOLIO_FOCUSFLOW_STARTED']) as adb,patch.object(device,'confirm_bridge') as confirm:
   device.start_shared();command=adb.call_args.args[1];self.assertIn('if ! pidof folio-pen-shared',command);self.assertIn('ru.folio.penbridge.SharedPenBridge',command);self.assertIn('"$user" = shell',command);self.assertNotIn('@APK@',command);confirm.assert_called_once()

if __name__=='__main__':
 stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(HostChecks));text=stream.getvalue();print(text);(device.ROOT/'dist/shared-host-tests.txt').write_text('Fake ADB orchestration; no physical tablet used.\n'+text,encoding='utf-8');sys.exit(not result.wasSuccessful())
