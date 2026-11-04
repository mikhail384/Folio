import importlib.util
from pathlib import Path
import unittest
import numpy as np
from PIL import Image,ImageDraw

root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('generated_vectorize',root/'integrations/folio-handwriting/vectorize_generated.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)


class GeneratedVectorTests(unittest.TestCase):
    def test_connected_branches_and_dot_create_editable_safe_strokes(self):
        image=Image.new('L',(120,60),255);d=ImageDraw.Draw(image)
        d.line([(10,35),(30,10),(50,35),(70,10),(90,35)],fill=0,width=3)
        d.line([(50,35),(50,50)],fill=0,width=3);d.ellipse((100,12,103,15),fill=0)
        result=module.vectorize(image,x=20,y=30,height=45)
        self.assertEqual(result,module.vectorize(image,x=20,y=30,height=45))
        self.assertTrue(result['estimated_pressure']);self.assertGreater(len(result['strokes']),1)
        self.assertGreater(result['advance'],0)
        for stroke in result['strokes']:
            self.assertEqual(stroke['tool'],'fountain');self.assertTrue(.3<=stroke['width']<=80)
            for x,y,p,roll in stroke['points']:
                self.assertTrue(20<=x<=20+result['advance'])
                self.assertTrue(30<=y<=75)
                self.assertTrue(.021<=p<=1.5);self.assertEqual(roll,0)

    def test_empty_images_and_invalid_coordinates_are_rejected(self):
        image=Image.new('L',(30,20),255)
        with self.assertRaises(ValueError):module.vectorize(image)
        with self.assertRaises(ValueError):module.vectorize(image,height=float('nan'))
        with self.assertRaises(ValueError):module.vectorize(image,color='red')

    def test_closed_loop_is_retained(self):
        image=Image.new('L',(40,40),255);ImageDraw.Draw(image).ellipse((6,6,30,30),outline=0,width=3)
        result=module.vectorize(image)
        self.assertGreaterEqual(sum(len(s['points']) for s in result['strokes']),20)

    def test_junction_edges_are_used_once_and_anchors_stay_fixed(self):
        mask=np.zeros((9,9),dtype=bool);mask[4,1:8]=True;mask[1:8,4]=True
        graph=module._graph(mask);paths=module._paths(graph)
        actual=[tuple(sorted((a,b))) for path in paths for a,b in zip(path,path[1:])]
        expected={tuple(sorted((a,b))) for a,neighbours in graph.items() for b in neighbours}
        self.assertEqual(set(actual),expected);self.assertEqual(len(actual),len(expected))
        self.assertLessEqual(len(paths),3)
        for path in paths:
            smoothed=module._smooth(path,graph)
            for index,point in enumerate(path):
                if index in (0,len(path)-1) or len(graph[point])!=2:
                    self.assertEqual(tuple(smoothed[index]),point)


if __name__=='__main__':unittest.main()
