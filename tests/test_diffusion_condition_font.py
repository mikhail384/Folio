import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('render_diffusion',ROOT/'integrations/folio-handwriting/render_diffusion.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)


class ConditioningFontTests(unittest.TestCase):
    def test_checkpoint_rejects_a_different_or_missing_font(self):
        with tempfile.TemporaryDirectory() as directory:
            font=Path(directory)/'font.ttf';font.write_bytes(b'conditioning-font')
            saved={'condition_font_sha256':hashlib.sha256(font.read_bytes()).hexdigest()}
            module.validate_condition_font(saved,font)
            font.write_bytes(b'different-font')
            with self.assertRaises(ValueError):module.validate_condition_font(saved,font)
            with self.assertRaises(ValueError):module.validate_condition_font({},Path(directory)/'missing.ttf')
            # Old research checkpoints lack the metadata, but still require an existing font.
            module.validate_condition_font({},font)


if __name__=='__main__':unittest.main()
