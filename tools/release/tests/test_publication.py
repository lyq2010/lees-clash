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
from mirror import load_assets, publish, publish_cos
from signing import restore
from published import is_published


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        folder = self.root / 'app/build/outputs/apk/alpha/release'
        folder.mkdir(parents=True)
        (self.root / 'docs').mkdir()
        (self.root / 'CHANGELOG.md').write_text('# 更新日志\n\n## 未发布\n\nFuture change\n\n## 0.1.1 (2026-10-27)\n\nRelease notes\n\n## 0.1.0 (2026-10-26)\n\nOlder notes\n')
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
        self.assertEqual('Release notes', manifest['notes'])
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

    def cos(self, broken=None, previous=None):
        class Missing(Exception):
            def get_status_code(self):
                return 404
        storage = {} if previous is None else {'latest-lees-clash.json': previous}
        events = []
        client = Mock(spec=['put_object', 'head_object', 'list_objects', 'delete_object', 'get_object'])
        def upload(**kwargs):
            self.assertTrue(kwargs['EnableMD5'])
            name, content = kwargs['Key'], kwargs['Body'].read()
            events.append(('upload', name))
            storage[name] = {'ETag': 'upload-' + name, 'Content-Length': str(len(content)), **kwargs['Metadata']}
            if broken:
                storage[name].update(broken(name) or {})
            return {'ETag': 'upload-' + name}
        def head(**kwargs):
            name = kwargs['Key']
            events.append(('head', name))
            if name not in storage:
                raise Missing()
            return storage[name]
        client.put_object.side_effect = upload
        client.head_object.side_effect = head
        client.get_object.side_effect = AssertionError('COS object downloads are forbidden')
        client.list_objects.return_value = {'IsTruncated': False, 'Contents': []}
        return client, events

    def test_cos_never_downloads_and_publishes_feed_after_verified_packages(self):
        stage(self.root, 'v0.1.1', self.assets)
        client, events = self.cos()
        with patch('mirror.urllib.request.urlopen', side_effect=AssertionError('HTTP downloads forbidden')):
            publish_cos(self.assets, client, 'bucket')
        feed = events.index(('upload', 'latest-lees-clash.json'))
        self.assertEqual(6, sum(kind == 'head' and name != 'latest-lees-clash.json' for kind, name in events[:feed]))
        self.assertEqual(('head', 'latest-lees-clash.json'), events[-1])
        self.assertEqual(7, client.put_object.call_count)
        client.get_object.assert_not_called()
        client.list_objects.assert_called_once()

    def test_cos_bad_package_metadata_keeps_feed_and_old_packages(self):
        stage(self.root, 'v0.1.1', self.assets)
        for invalid in ({'Content-Length': '-1'}, {'ETag': 'changed'},
                        {'x-cos-meta-sha256': 'wrong'}, {'x-cos-meta-version-code': '0'}):
            with self.subTest(invalid=invalid):
                client, events = self.cos(broken=lambda name: invalid if name.endswith('.apk') else None)
                with self.assertRaises(ValueError):
                    publish_cos(self.assets, client, 'bucket')
                self.assertNotIn(('upload', 'latest-lees-clash.json'), events)
                client.list_objects.assert_not_called()

    def test_cos_bad_feed_metadata_retains_old_packages(self):
        stage(self.root, 'v0.1.1', self.assets)
        client, _ = self.cos(broken=lambda name: {'Content-Length': '-1'} if name == 'latest-lees-clash.json' else None)
        with self.assertRaises(ValueError):
            publish_cos(self.assets, client, 'bucket')
        client.list_objects.assert_not_called()

    def test_cos_rollback_and_unknown_legacy_metadata_upload_nothing(self):
        stage(self.root, 'v0.1.1', self.assets)
        for previous in ({'x-cos-meta-version-code': '3'}, {},
                         {'x-cos-meta-version-code': '2', 'x-cos-meta-sha256': 'different'}):
            with self.subTest(previous=previous):
                client, _ = self.cos(previous=previous)
                with self.assertRaises(ValueError):
                    publish_cos(self.assets, client, 'bucket')
                client.put_object.assert_not_called()
                client.get_object.assert_not_called()

    def test_cos_same_release_retry_uses_head_only(self):
        stage(self.root, 'v0.1.1', self.assets)
        previous = {'x-cos-meta-version-code': '2', 'x-cos-meta-sha256': digest(self.assets / 'latest-lees-clash.json')}
        client, _ = self.cos(previous=previous)
        with patch('mirror.urllib.request.urlopen', side_effect=AssertionError('HTTP downloads forbidden')):
            publish_cos(self.assets, client, 'bucket')
        client.get_object.assert_not_called()

    def test_missing_independent_signing_secrets_fail(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaises(ValueError):
            restore()

    def test_first_release_version_can_be_created(self):
        with patch('published.urllib.request.urlopen', side_effect=HTTPError(
                'https://example.test', 404, 'Missing', {}, None)) as request:
            self.assertFalse(is_published('owner/project', 'v0.0.1', 'test-token'))
        self.assertTrue(request.call_args.args[0].full_url.endswith('/releases/tags/v0.0.1'))

    def test_existing_first_release_must_have_complete_assets(self):
        expected = {'latest-lees-clash.json', 'lees-clash-0.0.1.apk', 'lees-clash-0.0.1-SHA256SUMS.txt'}
        expected |= {f'lees-clash-0.0.1-{abi}.apk' for abi in ABIS}
        release = dict(tag_name='v0.0.1', draft=False, prerelease=False,
                       assets=[dict(name=name, state='uploaded', size=1) for name in expected])
        with patch('published.urllib.request.urlopen', return_value=io.BytesIO(json.dumps(release).encode())):
            self.assertTrue(is_published('owner/project', 'v0.0.1', 'test-token'))
        release['assets'].pop()
        with patch('published.urllib.request.urlopen', return_value=io.BytesIO(json.dumps(release).encode())):
            with self.assertRaises(ValueError):
                is_published('owner/project', 'v0.0.1', 'test-token')
