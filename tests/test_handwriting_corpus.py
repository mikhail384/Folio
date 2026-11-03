import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

source=Path(__file__).resolve().parents[1]/'integrations/folio-plugin/corpus.py'
spec=importlib.util.spec_from_file_location('folio_corpus',source)
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class CorpusTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.corpus=module.Corpus(self.temp.name,'personal')
        self.ink=[{'tool':'pen','action':'ordinary','points':[0,0,.1,0,0,10,10,.3,0,0]}]
    def tearDown(self):self.temp.cleanup()
    def test_unique_case_sensitive_examples_and_retry(self):
        self.assertTrue(self.corpus.add('А',self.ink,'text',document_id='doc',page_id='page'))
        self.assertFalse(self.corpus.add('А',self.ink,'text',document_id='doc',page_id='page'))
        self.assertTrue(self.corpus.add('а',self.ink,'text',document_id='doc',page_id='page'))
        ink2=[{'points':[0,0,.2,0,0,11,9,.25,0,0]}]
        self.assertTrue(self.corpus.add('а',ink2,'text',document_id='doc',page_id='page'))
        examples=self.corpus.examples();self.assertEqual(len(examples),3)
        self.assertEqual({x['text'] for x in examples},{'А','а'})
        self.assertTrue(all(x['document_id']=='doc' for x in examples))
    def test_formula_and_invalid_or_generated_ink(self):
        self.corpus.add('F = ma',self.ink,'formula',document_id='d',page_id='p')
        self.assertEqual(self.corpus.examples()[0]['kind'],'formula')
        for ink in ([{'action':'ai:123','points':[0,0,.1,0,0]}],
                    [{'tool':'marker','points':[0,0,.1,0,0]}],
                    [{'cuts':[1,2,3],'points':[0,0,.1,0,0]}],
                    [{'points':[0,0,float('nan'),0,0]}]):
            with self.assertRaises(ValueError):self.corpus.add('тест',ink)
        with self.assertRaises(ValueError):self.corpus.add('тест',self.ink,document_id='../other')
    def test_corruption_is_not_overwritten(self):
        self.corpus.add('П',self.ink);p=next((Path(self.temp.name)/'personal').glob('*.json'));p.write_text('{}',encoding='utf-8')
        with self.assertRaises(ValueError):self.corpus.examples()
        with self.assertRaises(ValueError):self.corpus.add('П',self.ink)
        self.assertEqual(p.read_text(encoding='utf-8'),'{}')

if __name__=='__main__':unittest.main()
