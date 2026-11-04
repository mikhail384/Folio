import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('diffusion_data',ROOT/'integrations/folio-handwriting/diffusion_data.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)


class PrivateWordDataTests(unittest.TestCase):
    @staticmethod
    def example(text,kind='text',offset=0):
        return {'text':text,'kind':kind,'document_id':'test_doc','page_id':'page0',
                'strokes':[{'points':[offset,0,.3,0,0,offset+5,8,.5,0,0,offset+12,1,.4,0,0]}]}

    def test_variants_share_split_and_formulas_are_not_words(self):
        examples=[self.example('слово'+str(i),offset=i*20) for i in range(20)]
        examples += [self.example('Слово0.',offset=440),self.example('H2O','formula',500),self.example('..',offset=550)]
        charset=list('словС0123456789.HO')
        with tempfile.TemporaryDirectory() as temp:
            destination=Path(temp)/'dataset';counts=module.prepare(examples,destination,charset)
            train=json.loads((destination/'train.json').read_text(encoding='utf8'))
            val=json.loads((destination/'val.json').read_text(encoding='utf8'))
            self.assertEqual(counts['train']+counts['val'],21)
            self.assertEqual(len(list((destination/'img').glob('*.png'))),21)
            self.assertFalse({module.split_key(x['text']) for x in train}&{module.split_key(x['text']) for x in val})
            self.assertNotIn('H2O',[x['text'] for x in train+val])
            with self.assertRaises(ValueError):module.prepare(examples,destination,charset)

    def test_requires_independent_reviewed_words(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(ValueError):module.prepare([self.example('слово')],Path(temp)/'dataset',list('слово'))

    def test_high_resolution_keeps_the_reviewed_split_and_dimensions(self):
        examples=[self.example('слово'+str(i),offset=i*20) for i in range(20)]
        charset=list('слово0123456789')
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'high';counts=module.prepare(examples,path,charset,height=96,width=432)
            self.assertEqual(counts['train']+counts['val'],20)
            self.assertEqual(json.loads((path/'geometry.json').read_text()),{'height':96,'width':432})
            from PIL import Image
            with Image.open(next((path/'img').glob('*.png'))) as image:
                self.assertLessEqual(image.width,432);self.assertLessEqual(image.height,96)

    def test_growing_corpus_does_not_move_previous_word_groups(self):
        first=[self.example('слово'+str(i),offset=i*20) for i in range(20)]
        more=first+[self.example('слово'+str(i),offset=i*20) for i in range(20,40)]
        charset=list('слово0123456789')
        with tempfile.TemporaryDirectory() as temp:
            old=Path(temp)/'old';new=Path(temp)/'new'
            module.prepare(first,old,charset);module.prepare(more,new,charset)
            original=module.previous_splits(old);expanded=module.previous_splits(new)
            self.assertEqual({k:expanded[k] for k in original},original)
            previous=dict(original);previous['слово0']='val' if original['слово0']=='train' else 'train'
            preserved=Path(temp)/'preserved';module.prepare(more,preserved,charset,assignments=previous)
            self.assertEqual(module.previous_splits(preserved)['слово0'],previous['слово0'])


if __name__=='__main__':unittest.main()
