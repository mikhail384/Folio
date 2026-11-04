import importlib.util
from pathlib import Path
import unittest

from PIL import Image
import numpy as np
import torch

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('diffusion_samplers',ROOT/'integrations/folio-handwriting/diffusion_samplers.py')
samplers=importlib.util.module_from_spec(spec);spec.loader.exec_module(samplers)


class Reference:
    T=8; H=8; W=16; L=4; PPC=4; dev='cpu'; chars=list('абв')
    abar=torch.tensor([.99,.92,.83,.72,.6,.46,.29,.12])
    @staticmethod
    def render_content(text,width):
        return Image.new('L',(Reference.W,Reference.H),0 if text else 255)
    @staticmethod
    def encode(text):
        return [Reference.chars.index(c)+1 for c in text]+[0]*(Reference.L-len(text))


class Model(torch.nn.Module):
    def forward(self,x,content,ids,t):
        return .13*x+.03*content


class SamplerTests(unittest.TestCase):
    def test_deterministic_unclipped_default(self):
        args=(Model(),Reference,['аб'])
        default=samplers.ddim(*args,steps=4,tmax=6,seed=123)
        explicit=samplers.ddim(*args,steps=4,tmax=6,seed=123,clip=False)
        clipped=samplers.ddim(*args,steps=4,tmax=6,seed=123,clip=True)
        self.assertTrue(np.array_equal(default,explicit))
        self.assertFalse(np.array_equal(default,clipped))
        self.assertEqual(default.shape,(1,1,Reference.H,Reference.W))

    def test_unsupported_or_silently_truncated_text_is_rejected(self):
        for word in ('','абг','абвба'):
            with self.subTest(word=word),self.assertRaises(ValueError):
                samplers.ddim(Model(),Reference,[word],steps=4,tmax=6)

    def test_invalid_schedules_are_rejected(self):
        for settings in ({'steps':1},{'steps':4,'tmax':8},{'steps':4,'tmax':6,'eta':float('nan')},
                         {'steps':4,'tmax':6,'guidance':float('inf')}):
            with self.subTest(settings=settings),self.assertRaises(ValueError):
                samplers.ddim(Model(),Reference,['аб'],**settings)


if __name__=='__main__':unittest.main()
