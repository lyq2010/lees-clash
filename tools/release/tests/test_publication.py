import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from stage import ABIS, digest, stage, version_of
from mirror import load_assets, publish
from signing import restore


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        folder = self.root / 'app/build/outputs/apk/alpha/release'
        folder.mkdir(parents=True)
        (self.root / 'docs').mkdir()
        (self.root / 'docs/RELEASE_NOTES.md').write_text('Release notes')
        elements = []
        for abi in sorted(ABIS | {'universal'}):
            name = abi + '.apk'
            (folder / name).write_bytes(b'apk-' + abi.encode())
            elements.append(dict(versionName='0.1.1', versionCode=2, outputFile=name,
                                 filters=[] if abi == 'universal' else [dict(filterType='ABI', value=abi)]))
        self.metadata = folder / 'output-metadata.json'
        self.metadata.write_text(json.dumps(dict(applicationId='io.github.leenban.clash', elements=elements)))
        self.assets = self.root / 'assets'

    def test_stage_requires_all_abis_and_canonical_identity(self):
        metadata = json.loads(self.metadata.read_text())
        metadata['applicationId'] += '.verify'
        self.metadata.write_text(json.dumps(metadata))
        with self.assertRaises(ValueError):
            stage(self.root, 'v0.1.1', self.assets)
        self.assertFalse(self.assets.exists())
        metadata['applicationId'] = 'io.github.leenban.clash'
        metadata['elements'].pop()
        self.metadata.write_text(json.dumps(metadata))
        with self.assertRaises(ValueError):
            stage(self.root, 'v0.1.1', self.assets)

    def test_version_carry_and_complete_asset_checksums(self):
        with self.assertRaises(ValueError):
            version_of('v0.1.50')
        stage(self.root, 'v0.1.1', self.assets)
        manifest, files = load_assets(self.assets)
        self.assertEqual(6, len(files))
        self.assertEqual(digest(self.assets / manifest['apk']), manifest['sha256'])
        (self.assets / 'unexpected.apk').write_bytes(b'bad')
        with self.assertRaises(ValueError):
            load_assets(self.assets)

    def remote(self, storage, events, broken=False):
        def fetch(name):
            events.append(('fetch', name))
            if name not in storage:
                raise HTTPError('https://example.test/' + name, 404, 'Missing', {}, None)
            if broken and name.endswith('.apk'):
                return io.BytesIO(b'wrong')
            return io.BytesIO(storage[name])
        return fetch

    def test_feed_published_only_after_packages_verified_then_cleanup(self):
        stage(self.root, 'v0.1.1', self.assets)
        storage, events = {}, []
        def upload(path):
            events.append(('upload', path.name))
            storage[path.name] = path.read_bytes()
        publish(self.assets, 'https://example.test', upload,
                lambda version: events.append(('cleanup', version)), self.remote(storage, events))
        feed = events.index(('upload', 'latest-lees-clash.json'))
        self.assertEqual(6, sum(kind == 'fetch' and name != 'latest-lees-clash.json' for kind, name in events[:feed]))
        self.assertEqual(('cleanup', '0.1.1'), events[-1])

    def test_bad_remote_package_keeps_feed_and_old_packages(self):
        stage(self.root, 'v0.1.1', self.assets)
        storage, events = {}, []
        cleanup = Mock()
        def upload(path):
            events.append(('upload', path.name))
            storage[path.name] = path.read_bytes()
        with self.assertRaises(ValueError):
            publish(self.assets, 'https://example.test', upload, cleanup, self.remote(storage, events, True))
        self.assertNotIn('latest-lees-clash.json', storage)
        cleanup.assert_not_called()

    def test_rollback_uploads_nothing(self):
        stage(self.root, 'v0.1.1', self.assets)
        storage = {'latest-lees-clash.json': json.dumps(dict(versionCode=3)).encode()}
        upload, cleanup = Mock(), Mock()
        with self.assertRaises(ValueError):
            publish(self.assets, 'https://example.test', upload, cleanup, self.remote(storage, []))
        upload.assert_not_called()
        cleanup.assert_not_called()

    def test_missing_independent_signing_secrets_fail(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaises(ValueError):
            restore()
