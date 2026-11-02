"""离线验证上传后的内容校验，不连接 HDFS，也不写数据库。"""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

import requests


SCRIPT = Path(__file__).resolve().parents[1] / "jobs" / "upload_to_hdfs.py"
spec = importlib.util.spec_from_file_location("hdfs_upload_under_test", SCRIPT)
upload_module = importlib.util.module_from_spec(spec)
# 隔离本机 .env；测试只用模拟的 HTTP 响应。
test_config = types.SimpleNamespace(HDFS_HOST="127.0.0.1", HDFS_WEB_PORT=50070,
                                    HDFS_USER="tester", HDFS_BASE="/taptap")
with patch.dict(sys.modules, {"config": test_config}):
    spec.loader.exec_module(upload_module)


class Response:
    def __init__(self, status, body=b"", headers=None):
        self.status_code = status
        self.body = body
        self.headers = headers or {}
        self.text = "simulated response"
        self.closed = False

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.closed = True

    def json(self):
        return {"boolean": True}

    def iter_content(self, chunk_size):
        for start in range(0, len(self.body), chunk_size):
            yield self.body[start:start + chunk_size]


class UploadVerificationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.local = Path(self.temp.name) / "source.bin"
        self.local.write_bytes(b"abc")
        self.received = []
        self.put_patch = patch.object(upload_module.requests, "put", side_effect=self.put)
        self.put_patch.start()
        self.addCleanup(self.put_patch.stop)
        self.output_patch = patch("sys.stdout", new_callable=io.StringIO)
        self.output_patch.start()
        self.addCleanup(self.output_patch.stop)

    def put(self, url, **kwargs):
        if kwargs.get("params", {}).get("op") == "CREATE":
            return Response(307, headers={"Location":
                "http://unresolvable-vm:50075/webhdfs/v1/taptap/source.bin?op=WRITE"})
        self.received.append(kwargs["data"].read())
        return Response(201)

    def redirected_read(self, body):
        return [Response(307, headers={"Location":
            "http://unresolvable-vm:50075/webhdfs/v1/taptap/source.bin?op=OPEN"}),
            Response(200, body)]

    def test_matching_multichunk_content_passes(self):
        payload = b"abc\x00\xff" * 500000
        self.local.write_bytes(payload)
        responses = self.redirected_read(payload)
        with patch.object(upload_module, "ensure_parent_dirs"), \
             patch.object(upload_module.requests, "get", side_effect=responses) as get:
            self.assertEqual(upload_module.upload(self.local, "/taptap/source.bin"), len(payload))
        self.assertEqual(self.received, [payload])
        self.assertTrue(all(r.closed for r in responses))
        self.assertTrue(get.call_args_list[1].args[0].startswith("http://127.0.0.1:50075/"))
        self.assertEqual(upload_module.local_sha256(self.local),
                         (len(payload), hashlib.sha256(payload).hexdigest()))

    def test_same_size_different_content_fails(self):
        with patch.object(upload_module, "ensure_parent_dirs"), \
             patch.object(upload_module.requests, "get", side_effect=self.redirected_read(b"xyz")):
            with self.assertRaisesRegex(RuntimeError, "SHA-256 校验失败"):
                upload_module.upload(self.local, "/taptap/source.bin")

    def test_truncated_content_fails(self):
        with patch.object(upload_module, "ensure_parent_dirs"), \
             patch.object(upload_module.requests, "get", return_value=Response(200, b"ab")):
            with self.assertRaisesRegex(RuntimeError, "SHA-256 校验失败"):
                upload_module.upload(self.local, "/taptap/source.bin")

    def test_read_timeout_is_not_success(self):
        with patch.object(upload_module, "ensure_parent_dirs"), \
             patch.object(upload_module.requests, "get", side_effect=requests.Timeout("read timed out")):
            with self.assertRaises(requests.Timeout):
                upload_module.upload(self.local, "/taptap/source.bin")

    def test_remote_http_failure_is_not_success(self):
        with patch.object(upload_module, "ensure_parent_dirs"), \
             patch.object(upload_module.requests, "get", return_value=Response(404)):
            with self.assertRaisesRegex(RuntimeError, "HTTP 404"):
                upload_module.upload(self.local, "/taptap/source.bin")

    def test_missing_read_redirect_fails(self):
        with patch.object(upload_module.requests, "get", return_value=Response(307)):
            with self.assertRaisesRegex(RuntimeError, "Location"):
                upload_module.hdfs_sha256("/taptap/source.bin")

    def test_direct_create_response_still_checks_nonempty_content(self):
        with patch.object(upload_module, "ensure_parent_dirs"), \
             patch.object(upload_module.requests, "put", return_value=Response(201)), \
             patch.object(upload_module.requests, "get", return_value=Response(200, b"")):
            with self.assertRaisesRegex(RuntimeError, "SHA-256 校验失败"):
                upload_module.upload(self.local, "/taptap/source.bin")

    def test_empty_file_can_pass_verification(self):
        self.local.write_bytes(b"")
        with patch.object(upload_module, "ensure_parent_dirs"), \
             patch.object(upload_module.requests, "put", return_value=Response(201)), \
             patch.object(upload_module.requests, "get", return_value=Response(200, b"")):
            self.assertEqual(upload_module.upload(self.local, "/taptap/source.bin"), 0)

    def test_stream_interruption_is_not_success_and_closes_response(self):
        response = Response(200)

        def interrupted(chunk_size):
            yield b"ab"
            raise requests.ConnectionError("connection lost")

        response.iter_content = interrupted
        with patch.object(upload_module.requests, "get", return_value=response):
            with self.assertRaises(requests.ConnectionError):
                upload_module.hdfs_sha256("/taptap/source.bin")
        self.assertTrue(response.closed)

    def test_main_counts_only_verified_files_and_records_failure(self):
        base = Path(self.temp.name)
        (base / "data" / "raw").mkdir(parents=True)
        other = base / "other.bin"
        other.write_bytes(b"def")
        # 连通性、首个文件成功、第二个文件大小相同但内容不同。
        responses = [Response(200), Response(200, b"abc"), Response(200, b"xyz")]
        with patch.object(upload_module, "BASE", base), \
             patch.object(upload_module, "FILES", [(self.local, "/a"), (other, "/b")]), \
             patch.object(upload_module, "ensure_parent_dirs"), \
             patch.object(upload_module, "write_crawl_log") as log, \
             patch.object(upload_module.requests, "get", side_effect=responses), \
             patch.object(sys, "argv", [str(SCRIPT)]):
            with self.assertRaises(SystemExit) as exit_result:
                upload_module.main()
        self.assertEqual(exit_result.exception.code, 1)
        report = json.loads((base / "data" / "raw" / "hdfs_upload_log.json").read_text("utf-8"))
        self.assertEqual((report["success"], report["failed"], report["bytes"]), (1, 1, 3))
        self.assertEqual([r["verified"] for r in report["file_results"]], [True, False])
        self.assertIn("SHA-256", report["file_results"][1]["error"])
        self.assertEqual(log.call_args.args[:4], (2, 1, 1, 3))


if __name__ == "__main__":
    unittest.main()
