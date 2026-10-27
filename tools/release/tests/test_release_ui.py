from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release_smoke import tap_control, wait_for_editor_closed, wait_for_profile


def document(target='', bounds='[0,300][300,350]', enabled='true'):
    return ET.fromstring(f'''<hierarchy><node bounds="[0,0][360,720]">
      <node text="{target}" enabled="{enabled}" bounds="{bounds}" />
      <node clickable="true" bounds="[0,640][120,720]"><node text="首页" bounds="[10,690][100,710]" /></node>
      <node clickable="true" bounds="[120,640][240,720]"><node text="工具" bounds="[130,690][220,710]" /></node>
    </node></hierarchy>''')


class ReleaseUiTests(unittest.TestCase):
    def test_waits_for_save_and_selected_home_before_starting_vpn(self):
        def frame(*labels):
            root = ET.Element('hierarchy')
            for label in labels:
                ET.SubElement(root, 'node', text=label)
            return root
        frames = iter([
            frame('编辑配置', 'Release-Verification', '启动'),
            frame('未选择配置', '启动'),
            frame('Release-Verification', '启动'),
        ])
        waits = []
        wait_for_profile(lambda: next(frames), 'Release-Verification', wait=waits.append)
        self.assertEqual(len(waits), 2)

    def test_does_not_accept_profile_name_in_unfinished_editor(self):
        root = ET.fromstring('<hierarchy><node text="编辑配置"/><node text="Release-Verification"/><node text="启动"/></hierarchy>')
        with self.assertRaisesRegex(ValueError, 'save and selection'):
            wait_for_profile(lambda: root, 'Release-Verification', wait=lambda _: None)

    def test_waits_for_editor_to_return_home_even_if_home_is_stale(self):
        def frame(*labels):
            root = ET.Element('hierarchy')
            for label in labels:
                ET.SubElement(root, 'node', text=label)
            return root
        frames = iter([frame('编辑配置', '启动'), frame('正在保存配置'), frame('未选择配置', '启动')])
        waits = []
        wait_for_editor_closed(lambda: next(frames), wait=waits.append)
        self.assertEqual(len(waits), 2)

    def test_scrolls_control_clear_of_bottom_navigation_before_tapping(self):
        frames = iter([document('清理 DNS 缓存','[0,630][300,690]'), document('清理 DNS 缓存')])
        calls = []
        tap_control(lambda: next(frames), lambda *args: calls.append(args), text='清理 DNS 缓存', wait=lambda _: None)
        self.assertEqual(calls[0][:3], ('shell','input','swipe'))
        self.assertEqual(calls[-1], ('shell','input','tap','150','325'))

    def test_waits_for_confirmation_dialog_without_tapping_background(self):
        frames = iter([document(),document('确认')])
        calls = []
        tap_control(lambda: next(frames),lambda *args: calls.append(args),text='确认',wait=lambda _:None)
        self.assertEqual(calls,[('shell','input','tap','150','325')])

    def test_bottom_navigation_itself_remains_clickable(self):
        calls = []
        tap_control(lambda:document(),lambda *args:calls.append(args),text='工具',wait=lambda _:None)
        self.assertEqual(calls[-1],('shell','input','tap','175','700'))

    def test_disabled_control_is_never_tapped(self):
        calls = []
        with self.assertRaises(ValueError):
            tap_control(lambda:document('确认',enabled='false'),lambda *args:calls.append(args),text='确认',wait=lambda _:None)
        self.assertFalse(any(args[:3]==('shell','input','tap') for args in calls))
