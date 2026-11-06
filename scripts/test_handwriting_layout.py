"""Offline regression checks for Folio's sample-based handwriting layout."""
import copy
import importlib.util
import math
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SPEC=importlib.util.spec_from_file_location('folio_handwriting',ROOT/'integrations/folio-plugin/handwriting.py')
HANDWRITING=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HANDWRITING)


class HandwritingLayoutTests(unittest.TestCase):
    def test_incomplete_trailing_payload_does_not_create_a_fake_point(self):
        stroke={'points':[0,0,.8,0,0, 0,10,.8,0,0, 10,10,.8,0,0, 10,0,.7,0,0, 999]}
        captured=HANDWRITING.sample_from_selection([stroke])
        self.assertEqual(len(captured['paths'][0]),4)
        self.assertEqual(captured['paths'][0][-1],[1,0,.7])

    def test_variation_is_repeatable_bounded_and_does_not_edit_samples(self):
        glyph={'advance':1.2,'natural_height':10,'paths':[[[0,.1,1],[.25,.45,.8],[.5,.6,.7],[.75,.3,.8],[1,.1,1]]]}
        samples={'слово':glyph}
        before=copy.deepcopy(samples)
        plain=HANDWRITING.layout('слово',0,0,40,2,'#123456',900,samples)[0]['points']
        first=HANDWRITING.layout('слово',0,0,40,2,'#123456',900,samples,variation_seed='write-a')[0]['points']
        again=HANDWRITING.layout('слово',0,0,40,2,'#123456',900,samples,variation_seed='write-a')[0]['points']
        other=HANDWRITING.layout('слово',0,0,40,2,'#123456',900,samples,variation_seed='write-b')[0]['points']
        self.assertEqual(first,again)
        self.assertNotEqual(first,other)
        self.assertEqual(samples,before)
        self.assertTrue(all(math.isfinite(p[0]) and math.isfinite(p[1]) for p in first))
        self.assertTrue(all(first[i][0]>first[i-1][0] for i in range(1,len(first))))
        self.assertLess(max(math.hypot(p[0]-plain[i][0],p[1]-plain[i][1]) for i,p in enumerate(first)),3)

    def test_samples_from_different_canvas_scales_cannot_blow_up_or_shrink_a_word(self):
        path=[[[0,0,.8],[1,1,.8]]]
        samples={
            'хорошая':{'advance':1,'natural_height':10,'paths':path},
            'Проведём':{'advance':1,'natural_height':100,'paths':path},
        }
        small=HANDWRITING.layout('хорошая',0,0,40,2,'#123456',900,samples)[0]['points']
        tall=HANDWRITING.layout('Проведём',0,0,40,2,'#123456',900,samples)[0]['points']
        self.assertAlmostEqual(small[1][1]-small[0][1],33.6,places=3)
        self.assertAlmostEqual(tall[1][1]-tall[0][1],47.2,places=3)


if __name__=='__main__':
    unittest.main(verbosity=2)
