import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from audit_apk import check_native


def elf(elf_class, machine, alignment):
    header_size, program_size = (64, 56) if elf_class == 2 else (52, 32)
    data = bytearray(header_size + program_size)
    data[:6] = b'\x7fELF' + bytes([elf_class, 1])
    struct.pack_into('<H', data, 18, machine)
    if elf_class == 2:
        struct.pack_into('<Q', data, 32, header_size)
        struct.pack_into('<HH', data, 54, program_size, 1)
        struct.pack_into('<Q', data, header_size + 48, alignment)
    else:
        struct.pack_into('<I', data, 28, header_size)
        struct.pack_into('<HH', data, 42, program_size, 1)
        struct.pack_into('<I', data, header_size + 28, alignment)
    struct.pack_into('<I', data, header_size, 1)
    return bytes(data)


class NativeAuditTests(unittest.TestCase):
    def test_all_four_native_architectures(self):
        for abi, cls, machine, align in [('arm64-v8a', 2, 183, 16384), ('x86_64', 2, 62, 16384),
                                         ('armeabi-v7a', 1, 40, 4096), ('x86', 1, 3, 4096)]:
            with self.subTest(abi=abi):
                check_native(elf(cls, machine, align), abi)

    def test_wrong_cpu_is_rejected(self):
        with self.assertRaises(ValueError):
            check_native(elf(2, 62, 16384), 'arm64-v8a')

    def test_64_bit_4kb_load_is_rejected(self):
        for abi, machine in [('arm64-v8a', 183), ('x86_64', 62)]:
            with self.subTest(abi=abi), self.assertRaises(ValueError):
                check_native(elf(2, machine, 4096), abi)

    def test_missing_load_is_rejected(self):
        data = bytearray(elf(2, 183, 16384))
        struct.pack_into('<I', data, 64, 0)
        with self.assertRaises(ValueError):
            check_native(data, 'arm64-v8a')

    def test_truncated_native_header_is_rejected(self):
        with self.assertRaises(ValueError):
            check_native(b'\x7fELF', 'arm64-v8a')
