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
        self.assertLess(max(math.hypot(p[0]-plain[i][0],p[1]-plain[i][1]) for i,p in enumerate(first)),7)
        width=lambda path:max(point[0] for point in path)-min(point[0] for point in path)
        self.assertGreater(width(first),width(plain)*1.05)

    def test_rounding_softens_a_corner_without_moving_stroke_endpoints(self):
        path=[[0,0,.5],[1,0,.7],[1,1,.9]]
        rounded=HANDWRITING._round_path(path)
        self.assertGreater(len(rounded),len(path))
        self.assertEqual(rounded[0],path[0])
        self.assertEqual(rounded[-1],path[-1])
        self.assertNotIn([1,0,.7],rounded)
        self.assertTrue(all(.5<=point[2]<=.9 for point in rounded))

    def test_dense_descender_turn_is_softened_over_a_glyph_relative_radius(self):
        path=[[i*.025,0,.8] for i in range(13)]+[[.3,i*.025,.8] for i in range(1,13)]
        softened=HANDWRITING._soften_dense_path(path)
        self.assertEqual(softened[0],path[0])
        self.assertEqual(softened[-1],path[-1])
        corner=softened[12]
        self.assertGreater(corner[1],0)
        self.assertLess(corner[0],.3)
        rounded=HANDWRITING._round_path(softened)
        self.assertEqual(rounded[0],path[0])
        self.assertEqual(rounded[-1],path[-1])

    def test_short_upward_hook_on_a_round_letter_is_removed(self):
        oval=[]
        for index in range(41):
            angle=2*math.pi*index/40
            oval.append([.2+.12*math.cos(angle),.55+.18*math.sin(angle),.8])
        top=30;anchor=oval[top]
        path=oval[:top]+[anchor,[anchor[0],anchor[1]-.06,.8],[anchor[0]+.005,anchor[1]-.12,.8],[anchor[0]+.01,anchor[1]-.06,.8],anchor]+oval[top+1:]
        trimmed=HANDWRITING._trim_short_hooks(path)
        self.assertEqual(trimmed[0],path[0])
        self.assertEqual(trimmed[-1],path[-1])
        self.assertGreater(min(point[1] for point in trimmed),min(point[1] for point in path)+.1)

    def test_personal_words_keep_a_consistent_height_and_clear_gap(self):
        path=[[[0,0,.8],[1,1,.8]]]
        samples={
            'хорошая':{'advance':1.3,'natural_height':10,'paths':path},
            'Проведём':{'advance':1.3,'natural_height':100,'paths':path},
        }
        small=HANDWRITING.layout('хорошая',0,0,40,2,'#123456',900,samples)[0]['points']
        tall=HANDWRITING.layout('Проведём',0,0,40,2,'#123456',900,samples)[0]['points']
        small_height=max(p[1] for p in small)-min(p[1] for p in small)
        tall_height=max(p[1] for p in tall)-min(p[1] for p in tall)
        self.assertLess(abs(tall_height-small_height),40*.09)
        phrase=HANDWRITING.layout('хорошая Проведём',0,0,40,2,'#123456',900,samples,variation_seed='space-check')
        first_right=max(p[0] for p in phrase[0]['points'])
        second_left=min(p[0] for p in phrase[1]['points'])
        self.assertGreater(second_left-first_right,40*.35)

    def test_long_personal_words_get_more_horizontal_room(self):
        word='увеличивается'
        sample={word:{'advance':1.0,'natural_height':40,'paths':[[[0,0,.8],[1,1,.8]]]}}
        stroke=HANDWRITING.layout(word,0,0,40,2,'#123456',900,sample,variation_seed='long-word')[0]
        width=max(point[0] for point in stroke['points'])-min(point[0] for point in stroke['points'])
        self.assertGreater(width,40*1.14)

    def test_samples_from_different_canvas_scales_cannot_blow_up_or_shrink_a_word(self):
        path=[[[0,0,.8],[1,1,.8]]]
        samples={
            'хорошая':{'advance':1,'natural_height':10,'paths':path},
            'Проведём':{'advance':1,'natural_height':100,'paths':path},
        }
        small=HANDWRITING.layout('хорошая',0,0,40,2,'#123456',900,samples)[0]['points']
        tall=HANDWRITING.layout('Проведём',0,0,40,2,'#123456',900,samples)[0]['points']
        self.assertAlmostEqual(small[1][1]-small[0][1],38.4,places=3)
        self.assertAlmostEqual(tall[1][1]-tall[0][1],41.6,places=3)


if __name__=='__main__':
    unittest.main(verbosity=2)
