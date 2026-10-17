"""Offline regression tests; no connection to the VPS and no file mutation."""
import importlib.util
from pathlib import Path
import sys
import unittest

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('runtime_repair',
    Path(__file__).with_name('repair-connection-diagnostics-runtime.py'))
repair = importlib.util.module_from_spec(spec)
spec.loader.exec_module(repair)


class RuntimeRepairTests(unittest.TestCase):
    original = (b'[Unit]\nAfter=network.target\n[Service]\nType=notify\n'
        b'RuntimeMaxSec=353100\nUMask=0077\nProtectSystem=strict\n'
        b'ReadWritePaths=/var/lib/pusula-connection-diag-20261014 /run/pusula-connection-diag-20261014\n'
        b'MemoryMax=64M\nCPUQuota=15%\n')

    def test_only_systemd_owned_ephemeral_directory_settings_are_added(self):
        result = repair.repaired_unit(self.original)
        insertion = b'RuntimeDirectory=pusula-connection-diag-20261014\nRuntimeDirectoryMode=0750\n'
        self.assertEqual(result.replace(insertion, b'', 1), self.original)
        self.assertEqual(result.count(b'RuntimeDirectory='), 1)

    def test_reapplying_is_refused_instead_of_overwriting_operator_changes(self):
        with self.assertRaises(RuntimeError):
            repair.repaired_unit(repair.repaired_unit(self.original))

    def test_unexpected_runtime_write_path_is_refused(self):
        with self.assertRaises(RuntimeError):
            repair.repaired_unit(self.original.replace(b'/run/pusula-', b'/run/other-'))

    def test_ambiguous_service_section_is_refused(self):
        for value in (b'', self.original + b'[Service]\n'):
            with self.assertRaises(RuntimeError):
                repair.repaired_unit(value)


if __name__ == '__main__':
    unittest.main(verbosity=2)
