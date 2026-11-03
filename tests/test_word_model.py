import importlib.util
import math
from pathlib import Path
import sys
import tempfile
import unittest
import subprocess

import numpy as np
import torch

SOURCE=Path(__file__).resolve().parents[1]/'integrations/folio-handwriting'
sys.path.insert(0,str(SOURCE))
from word_data import trajectory
from word_model import WordInkVAE,loss
from generate_words import generate,units,load_model
from import_archive import decrypt


class WordModelTests(unittest.TestCase):
    def test_resample_preserves_stroke_boundaries_pressure_and_case_inputs(self):
        paths=[[[0,0,.2],[4,8,.4],[8,2,.5]],[[9,3,.7],[11,7,.9]]]
        value,ratio=trajectory(paths,count=32)
        self.assertEqual(value.shape,(32,4));self.assertEqual(int(value[:,3].sum()),2)
        self.assertAlmostEqual(float(value[0,2]),.1,places=5);self.assertTrue(math.isfinite(ratio))
        self.assertNotEqual(units('Аа','text')[0][0],units('Аа','text')[0][1])
        self.assertEqual(units('F = ma','formula'),['F',' ','=',' ','m','a'])
        self.assertEqual(units('Привет мир','text'),['Привет',' ','мир'])
    def test_generator_is_bounded_case_sensitive_and_retry_deterministic(self):
        torch.manual_seed(2);alphabet=list('АаF=ma');net=WordInkVAE(len(alphabet));net.eval();model=(net,{c:i+1 for i,c in enumerate(alphabet)},torch.zeros(net.latent))
        a=generate(model,'Аа',1,2,operation_id='same-operation');b=generate(model,'Аа',1,2,operation_id='same-operation')
        self.assertEqual(a,b)
        self.assertNotEqual(a,generate(model,'Аа',1,2,operation_id='another-operation'))
        self.assertTrue(all(.021<=p[2]<=2 and all(math.isfinite(v) for v in p) for s in a for p in s['points']))
        self.assertTrue(generate(model,'F = ma',1,2,mode='formula',operation_id='formula'))
        with self.assertRaises(ValueError):generate(model,'Я',0,0,operation_id='missing-symbol')
        with self.assertRaises(ValueError):generate(model,'А',0,0,operation_id='')
    def test_unpersonalized_checkpoint_is_not_enabled_by_default(self):
        model=WordInkVAE(1)
        with tempfile.TemporaryDirectory() as folder:
            file=Path(folder)/'generic.pt'
            torch.save({'format':'folio-word-vae-research-v1','alphabet':['а'],'points':128,'model':model.state_dict(),'style':torch.zeros(model.latent),'personal':False},file)
            with self.assertRaises(ValueError):load_model(file)
            self.assertIsNotNone(load_model(file,allow_generic=True))
    def test_browser_archive_can_be_decrypted_for_private_training(self):
        module=(SOURCE.parent/'folio-cloud/lib/training-archive.mjs').as_uri()
        code="""import {exportExamples} from '%s';
const items=['А','а'].map(text=>({format:'folio-training-example-v1',text,kind:'text',source:'manual',document_id:'doc',page_id:'page',strokes:[{points:[0,0,.5,0,0,10,20,.6,0,0]}]}));
process.stdout.write(await exportExamples(items,'test password'));
""" % module
        result=subprocess.run(['node','--input-type=module','-e',code],capture_output=True,check=True)
        self.assertEqual([e['text'] for e in decrypt(result.stdout,'test password')],['А','а'])
        with self.assertRaises(ValueError):decrypt(result.stdout,'wrong password')

if __name__=='__main__':unittest.main()
