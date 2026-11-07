import contextlib
import io
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import migrate_cache_0_3_0 as m

K1, K2, K3 = "k-1", "k-2", "k-3"


class MigrateTest(unittest.TestCase):
    def setUp(self):
        self._td = tempfile.TemporaryDirectory()
        self.addCleanup(self._td.cleanup)
        self.d = self._td.name

    def write(self, name, content):
        with open(os.path.join(self.d, name), "w", encoding="utf-8") as f:
            f.write(content if isinstance(content, str) else json.dumps(content))

    def read(self, name, sub=None):
        with open(os.path.join(self.d, sub or "", name), encoding="utf-8") as f:
            return json.load(f)

    def run_main(self, *args):
        with contextlib.redirect_stdout(io.StringIO()) as out, contextlib.redirect_stderr(io.StringIO()):
            code = m.main([self.d, *args])
        return code, out.getvalue()

    def files(self):
        return sorted(os.listdir(self.d))

    def test_merge_chat(self):
        self.write("SimpleClassifier_gpt-4o-mini_1.json", {K1: '"Yes"'})
        self.write("ReasoningClassifier_gpt-4o-mini_1.json", {K2: '"No"'})
        code, _ = self.run_main()
        self.assertEqual(code, 0)
        self.assertEqual(self.read("Chat_gpt-4o-mini_1.json"), {K1: '"Yes"', K2: '"No"'})

    def test_conflict_first_wins(self):
        self.write("ReasoningClassifier_m_1.json", {K1: '"B"'})
        self.write("SimpleClassifier_m_1.json", {K1: '"A"'})
        code, out = self.run_main()
        self.assertEqual(code, 1)
        self.assertIn("CONFLICT", out)
        self.assertEqual(self.read("Chat_m_1.json"), {K1: '"B"'})

    def test_merge_embeddings(self):
        self.write("OpenAiEmbeddingCreator_text-embedding-3-small.json", {K1: "[0.1,0.2]"})
        self.write("BifrostEmbeddingCreator_openai--text-embedding-3-small.json", {K2: "[1,2]"})
        self.assertEqual(self.run_main()[0], 0)
        self.assertEqual(self.read("Embedding_text-embedding-3-small.json"), {K1: "[0.1,0.2]", K2: "[1,2]"})

    def test_prefix_handling(self):
        self.write("SimpleClassifier_openai--gpt-4o-mini_1.json", {K1: '"a"'})
        self.write("SimpleClassifier_meta-llama--x_1.json", {K1: '"b"'})
        self.run_main()
        self.assertEqual(self.files()[:2], ["Chat_meta-llama--x_1.json", "Chat_openai--gpt-4o-mini_1.json"])
        self.setUp()
        self.write("SimpleClassifier_openai--gpt-4o-mini_1.json", {K1: '"a"'})
        self.write("SimpleClassifier_meta-llama--x_1.json", {K1: '"b"'})
        self.run_main("--strip-provider-prefix")
        self.assertIn("Chat_gpt-4o-mini_1.json", self.files())
        self.assertIn("Chat_meta-llama--x_1.json", self.files())

    def test_default_providers_are_openai_and_ollama(self):
        self.write("SimpleClassifier_ollama--llama3.1__8b_1.json", {K1: '"a"'})
        self.write("SimpleClassifier_anthropic--claude_1.json", {K1: '"b"'})
        self.run_main("--strip-provider-prefix")
        self.assertIn("Chat_llama3.1__8b_1.json", self.files())
        self.assertIn("Chat_anthropic--claude_1.json", self.files())

    def test_custom_provider_list(self):
        self.write("SimpleClassifier_foo--x_1.json", {K1: '"a"'})
        self.run_main("--strip-provider-prefix=foo")
        self.assertIn("Chat_x_1.json", self.files())

    def test_underscores_and_temperature(self):
        self.write("ReasoningClassifier_llama3__8b_133742243_0.7.json", {K1: '"a"'})
        self.run_main()
        self.assertIn("Chat_llama3__8b_133742243_0.7.json", self.files())

    def test_existing_target_merged(self):
        self.write("Chat_m_1.json", {K1: '"keep"', K2: '"t"'})
        self.write("SimpleClassifier_m_1.json", {K1: '"keep"', K3: '"new"'})
        self.assertEqual(self.run_main()[0], 0)
        self.assertEqual(self.read("Chat_m_1.json"), {K1: '"keep"', K2: '"t"', K3: '"new"'})

    def test_dry_run(self):
        self.write("SimpleClassifier_m_1.json", {K1: '"a"'})
        before = self.files()
        code, out = self.run_main("--dry-run")
        self.assertEqual(code, 0)
        self.assertIn("dry run — nothing written", out)
        self.assertEqual(self.files(), before)
        self.assertEqual(self.read("SimpleClassifier_m_1.json"), {K1: '"a"'})

    def test_idempotent(self):
        self.write("SimpleClassifier_m_1.json", {K1: '"a"'})
        self.run_main()
        snapshot = (self.files(), self.read("Chat_m_1.json"))
        code, _ = self.run_main()
        self.assertEqual(code, 0)
        self.assertEqual((self.files(), self.read("Chat_m_1.json")), snapshot)

    def test_skipped(self):
        self.write("x.tmp.json", {})
        self.write("Blank_m_1.json", "  \n")
        self.write("nounderscore.json", {K1: '"a"'})
        self.write("OllamaEmbeddingCreator_m.json", {K1: '"text"'})
        self.write("SimpleClassifier_m_1.json", {K1: "[1,2]"})
        os.mkdir(os.path.join(self.d, "sub.json"))
        code, out = self.run_main()
        self.assertEqual(code, 1)
        for n in ("x.tmp.json", "Blank_m_1.json", "nounderscore.json", "OllamaEmbeddingCreator_m.json",
                  "SimpleClassifier_m_1.json"):
            self.assertIn("SKIPPED " + n, out)
            self.assertIn(n, self.files())
        self.assertNotIn("sub.json:", out)

    def test_backup_no_deletion(self):
        self.write("SimpleClassifier_m_1.json", {K1: '"a"'})
        self.write("ReasoningClassifier_m_1.json", {K2: '"b"'})
        os.makedirs(os.path.join(self.d, "pre-0.3.0-backup"))
        self.write("pre-0.3.0-backup/SimpleClassifier_m_1.json", {K3: '"old"'})
        self.run_main()
        bdir = os.path.join(self.d, "pre-0.3.0-backup")
        self.assertEqual(sorted(os.listdir(bdir)),
                         ["ReasoningClassifier_m_1.json", "SimpleClassifier_m_1.json",
                          "SimpleClassifier_m_1.json.1"])
        self.assertEqual(self.read("SimpleClassifier_m_1.json", "pre-0.3.0-backup"), {K3: '"old"'})

    def test_custom_backup_dir(self):
        self.write("SimpleClassifier_m_1.json", {K1: '"a"'})
        b = os.path.join(self._td.name, "bk")
        self.run_main("--backup-dir", b)
        self.assertEqual(os.listdir(b), ["SimpleClassifier_m_1.json"])

    def test_usage_error(self):
        self.assertEqual(m.main([os.path.join(self.d, "missing")]), 2)
        self.assertEqual(m.main([]), 2)


if __name__ == "__main__":
    unittest.main()
