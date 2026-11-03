# Folio handwriting research

This directory prepares **explicitly reviewed** handwriting examples and
experiments with models that generate new shapes. It is not yet the production
writing engine: readable Russian cursive, editability, mathematical layout,
inference speed and similarity to a particular user all need independent
evaluation before enabling it in Folio.

Keep notebook files, extracted trajectories, rendered crops, trained personal
weights and exported archives in private storage. They are personal data and
must not be included in public source releases. The local corpus retains
multiple case-sensitive examples and excludes generated ink. Mi Canvas input is
read-only and is limited to the observed v20 format; unknown structures are
rejected, and automatic transcription must be checked before training. Its
pressure is masked in `word_data.py` because some examples are marker strokes.

`diffusion_data.py` creates word crops from the reviewed corpus, holding out
whole words together with their capitalization and punctuation variants. The
experimental `diffusion_train.py` and `evaluate_diffusion.py` use the separately
installed [handwriting-diffusion](https://github.com/Levos06/handwriting-diffusion)
reference implementation and generic weights, under its MIT license. Their
image output is not editable ink: a separately validated image-to-strokes stage
is required. The independent vector VAE files are also research prototypes and
must not silently become the user's handwriting profile.

Example on Windows, after installing the reference and PyTorch CUDA into a
private environment:

```powershell
python -X utf8 integrations/folio-handwriting/diffusion_data.py PRIVATE_CORPUS PRIVATE_PROJECT/data/words REFERENCE/src/charset.json
python -X utf8 integrations/folio-handwriting/diffusion_train.py PRIVATE_PROJECT/data/words REFERENCE/checkpoints/v9pre_fp16.pt PRIVATE_MODELS --reference REFERENCE
python -X utf8 integrations/folio-handwriting/evaluate_diffusion.py PRIVATE_PROJECT/data/words PRIVATE_COMPARISON.png PRIVATE_MODELS/step-00000.pt PRIVATE_MODELS/step-01000.pt --reference REFERENCE
```

The numeric validation loss measures denoising, not legibility or faithfulness;
inspect generated unseen words and test the complete path on a tablet.
