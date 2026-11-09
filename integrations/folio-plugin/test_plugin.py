"""Real MCP client/server over stdio; test Android API stand-in, not a physical tablet claim."""
import asyncio,base64,json,os,subprocess,sys,tempfile,threading,unittest
from pathlib import Path
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from mcp import ClientSession,StdioServerParameters
from mcp.client.stdio import stdio_client
from corpus import prepare
from handwriting import layout,sample_from_selection
from server import _layout_validation

ROOT=Path(__file__).resolve().parent
sys.stdout.reconfigure(encoding='utf-8');sys.stderr.reconfigure(encoding='utf-8')
PNG='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII='
class Phone(BaseHTTPRequestHandler):
    revision=0;strokes=[{'action':'user-note','points':[[9,9]]}];requests=0;recognized_override=None
    def log_message(self,*args):pass
    def do_POST(self):
        if self.headers.get('Authorization')!='Bearer '+'t'*43:self.send_error(401);return
        q=json.loads(self.rfile.read(int(self.headers['Content-Length'])));Phone.requests+=1
        state={'protocol':1,'document_id':'doc','page_id':'page','revision':str(Phone.revision),'viewport':[0,0,1000,1000],'stroke_count':len(Phone.strokes)}
        status=200
        if self.path=='/v1/snapshot':state['image_png']=PNG
        elif self.path=='/v1/notebooks':state={'notebooks':[{'id':'doc','title':'Notebook','pages':1}]}
        elif self.path=='/v1/notebook_pages':state={'document_id':'doc','revision':'1','pages':[{'page_id':'page','ink_bounds':[0,0,100,100]}]}
        elif self.path=='/v1/notebook_scan':state={'document_id':'doc','page_id':'page','revision':'1','bounds':[0,0,100,100],'image_png':PNG}
        elif self.path=='/v1/notebook_samples':state={'samples':[{'text':q['samples'][0]['text'],'strokes':[{'points':[0,0,1,0,0,10,20,.7,0,0]}]}]}
        elif self.path=='/v1/training_examples':
            doc=q['document_id'];ink=[{'points':[0,0,.5,0,0,10,20,.6,0,0]}]
            if doc!='doc':status=403;state={'error':'no permission'}
            else:state={'document_id':doc,'has_more':False,'next_cursor':'','examples':[{'document_id':doc,'page_id':'page','text':'Привет','source':'calibration','reviewed':True,'kind':'text','strokes':ink},{'document_id':doc,'page_id':'page','text':'догадка','source':'ocr','reviewed':False,'kind':'text','strokes':ink}]}
        elif self.path in ('/v1/ink','/v1/undo'):
            if q['document_id']!='doc' or q['page_id']!='page' or q['revision']!=str(Phone.revision):status=409;state={'error':'Canvas changed'}
            else:
                if self.path=='/v1/ink':
                    for s in q['strokes']:Phone.strokes.append({**s,'action':q['operation_id']})
                    state['added_strokes']=len(q['strokes'])
                else:
                    before=len(Phone.strokes);Phone.strokes=[s for s in Phone.strokes if s['action']!=q['operation_id']];state['removed_strokes']=before-len(Phone.strokes)
                Phone.revision+=1;state['revision']=str(Phone.revision);state['operation_id']=q['operation_id']
        elif self.path=='/v1/verify_text':
            if q['document_id']!='doc' or q['page_id']!='page' or q['revision']!=str(Phone.revision) or not any(s['action']==q['operation_id'] for s in Phone.strokes):status=409;state={'error':'Text operation changed'}
            else:
                recognized=Phone.recognized_override or q['expected_text'];exact=recognized.lower()==q['expected_text'].lower()
                state.update({'status':'pass' if exact else 'review','operation_id':q['operation_id'],'expected_text':q['expected_text'],'recognized_text':recognized,'exact_match':exact,'spelling_ok':exact,'similarity':1.0 if exact else .7,'readable':exact,'mean_confidence':99,'confidence_ok':True,'stroke_height':q.get('target_height',28)*.95,'target_height':q.get('target_height',28),'estimated_lines':1,'size_ratio':.95,'size_ok':True,'method':'simulated OCR response for MCP workflow tests'})
        elif self.path=='/v1/selection':state['strokes']=[{'points':[0,0,1,0,0,10,20,.7,0,0,20,0,1,0,0]}]
        data=json.dumps(state).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)

class HandwritingTests(unittest.TestCase):
    def test_corpus_preserves_valid_stylus_timing_and_rejects_corrupt_timing(self):
        strokes=[{'points':[0,0,.5,0,0,10,20,.7,0,0],'pointTimesMs':[0,37]}]
        saved=prepare('∑',strokes,kind='formula')
        self.assertEqual(saved['strokes'][0]['pointTimesMs'],[0,37])
        for bad in ([0],[37,0],[0,600001]):
            with self.subTest(times=bad),self.assertRaises(ValueError):
                prepare('∑',[{'points':strokes[0]['points'],'pointTimesMs':bad}],kind='formula')

    def test_layout_validation_checks_height_spacing_and_naturalness(self):
        report={'line_count':1,'bounds':[0,0,52,20],'tokens':[
            {'text':'первое','line':0,'word':1,'bounds':[0,0,20,19]},
            {'text':'слово','line':0,'word':2,'bounds':[33,0,52,19]},
        ]}
        good=_layout_validation(report,20)
        self.assertEqual(good['status'],'pass')
        self.assertEqual(good['naturalness']['status'],'pass')
        report['tokens'][0]['text']='2';report['tokens'][1]['text']='4'
        self.assertTrue(_layout_validation(report,20)['size_ok'],'numeric equations retain size validation')
        report['tokens'][0]['text']='+';report['tokens'][1]['text']='='
        self.assertEqual(_layout_validation(report,20)['status'],'review','unmeasurable punctuation is not marked as validated')
        report['tokens'][0]['text']='первое';report['tokens'][1]['text']='слово'
        report['tokens'][1]['bounds']=[58,0,77,19]
        wide=_layout_validation(report,20)
        self.assertEqual(wide['status'],'review')
        self.assertFalse(wide['spacing_ok'])
        report['tokens'][1]['bounds']=[16,0,35,19]
        overlap=_layout_validation(report,20)
        self.assertEqual(overlap['status'],'review')
        self.assertFalse(overlap['spacing_ok'])

    def test_alphabets(self):
        for char in 'АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюяABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-=√π→':
            self.assertTrue(layout(char,0,0));
    def test_personal_exact_geometry(self):
        glyph=sample_from_selection([{'points':[10,20,1,0,0,20,40,.5,0,0]}]);path=layout('я',100,200,20,samples={'я':glyph})[0]['points'];self.assertEqual(path,[[100,200,1],[110,220,.5]])
    def test_personal_word_spacing_matches_notebook_gap(self):
        glyph={'advance':1,'paths':[[[0,0,1],[1,0,1]]]}
        strokes=layout('а а',0,0,20,samples={'а':glyph})
        self.assertEqual(strokes[1]['points'][0][0],28.4)
    def test_layout_metrics_report_uniform_word_heights_and_line_count(self):
        samples={
            'хорошая':{'advance':1,'natural_height':40,'paths':[[[0,0,.8],[1,1,.8]]]},
            'Проведём':{'advance':1,'natural_height':100,'paths':[[[0,0,.8],[1,1,.8]]]},
        }
        result=layout('хорошая Проведём\nхорошая',0,0,40,2,'#123456',900,samples,'report',True)
        self.assertEqual(result['metrics']['line_count'],2)
        self.assertEqual(len(result['metrics']['tokens']),3)
        for item in result['metrics']['tokens']:
            self.assertAlmostEqual((item['bounds'][3]-item['bounds'][1])/40,.95,places=3)

    def test_personal_words_keep_sampled_relative_height_and_shared_baseline(self):
        samples={
            'а':{'advance':1,'natural_height':20,'baseline_fraction':1,'paths':[[[0,0,1],[1,1,1]]]},
            'б':{'advance':1,'natural_height':40,'baseline_fraction':.75,'paths':[[[0,0,1],[1,1,1]]]}
        }
        first,second=layout('аб',0,0,30,samples=samples)
        self.assertEqual(first['points'][0][1],10)
        self.assertEqual(second['points'][0][0],20)
        self.assertEqual(second['points'][0][1],0)
        self.assertEqual(first['points'][-1][1],30)
        self.assertEqual(second['points'][-1][1],40)
    def test_personal_text_height_uses_prose_words_not_formula_sizes(self):
        from handwriting import _profile_reference_height
        samples={
            'слово':{'advance':1,'natural_height':20,'paths':[]},
            'другое':{'advance':1,'natural_height':40,'paths':[]},
            'NaOH':{'advance':1,'natural_height':100,'paths':[]},
            'А':{'advance':1,'natural_height':160,'paths':[]}
        }
        self.assertEqual(_profile_reference_height(samples),30)
    def test_personal_variants_are_repeatable_per_operation_and_change_between_writes(self):
        sample={'variants':[
            {'advance':1,'paths':[[[0,0,1],[1,1,1]]]},
            {'advance':1,'paths':[[[2,0,1],[3,1,1]]]}
        ]}
        samples={'а':sample}
        first=layout('а',0,0,samples=samples,variation_seed='write-1')
        self.assertEqual(first,layout('а',0,0,samples=samples,variation_seed='write-1'))
        outputs={tuple(tuple(p) for p in layout('а',0,0,samples=samples,variation_seed=f'write-{i}')[0]['points']) for i in range(64)}
        self.assertGreater(len(outputs),2)
    def test_repeated_personal_letters_get_stable_independent_variation(self):
        glyph={'advance':1.2,'paths':[[[0,0.1,1],[.45,.65,.8],[.9,.1,1]]],'natural_height':20}
        samples={'а':glyph}
        first=layout('ааа',0,0,20,samples=samples,variation_seed='operation-a')
        self.assertEqual(first,layout('ааа',0,0,20,samples=samples,variation_seed='operation-a'))
        self.assertEqual(len(first),3)
        self.assertEqual(len({tuple(tuple(p) for p in stroke['points']) for stroke in first}),3)
        self.assertNotEqual(first,layout('ааа',0,0,20,samples=samples,variation_seed='operation-b'))
    def test_personal_pressure_replays_safely_on_installed_folio(self):
        import struct
        pressures=[.019999999552965164,.02,0,2,3,float('nan'),None]
        glyph={'advance':1,'paths':[[[i,i,p] for i,p in enumerate(pressures)]]}
        points=layout('П',10,20,samples={'П':glyph})[0]['points']
        self.assertEqual([p[2] for p in points],[.02000001,.02000001,.02000001,2,2,1,1])
        self.assertTrue(all(.02<=struct.unpack('f',struct.pack('f',p[2]))[0]<=2 for p in points))
        self.assertEqual(glyph['paths'][0][0][2],pressures[0])
    def test_missing_personal_has_no_fake_fallback(self):
        with self.assertRaises(ValueError):layout('АБ',0,0,samples={'А':{'paths':[[[0,0,1],[1,1,1]]],'advance':1}})
    def test_rejects_large_and_unknown(self):
        for text in ['😀','я'*1501]:
            with self.assertRaises(ValueError):layout(text,0,0)

async def mcp_checks():
    phone=ThreadingHTTPServer(('127.0.0.1',0),Phone);threading.Thread(target=phone.serve_forever,daemon=True).start()
    with tempfile.TemporaryDirectory() as folder:
        config=Path(folder)/'connection.json';config.write_text(json.dumps({'url':f'http://127.0.0.1:{phone.server_port}','token':'t'*43}))
        env={**os.environ,'FOLIO_CONFIG':str(config),'FOLIO_DATA':folder}
        params=StdioServerParameters(command=sys.executable,args=[str(ROOT/'server.py')],env=env)
        async with stdio_client(params) as streams:
            async with ClientSession(*streams) as client:
                await client.initialize();tools=await client.list_tools();assert len(tools.tools)==13
                notebooks=await client.call_tool('folio_list_notebooks',{});assert not notebooks.isError
                pages=await client.call_tool('folio_list_notebook_pages',{'document_id':'doc'});assert not pages.isError
                scanned=await client.call_tool('folio_inspect_notebook_page',{'document_id':'doc','page_id':'page'});assert not scanned.isError and any(x.type=='image' for x in scanned.content)
                learned=await client.call_tool('folio_learn_notebook_regions',{'document_id':'doc','page_id':'page','revision':'1','bounds':[0,0,100,100],'samples':[{'text':'х','rect':[0,0,1,1]}]});assert not learned.isError
                collected=await client.call_tool('folio_collect_training_examples',{'document_id':'doc'});assert not collected.isError,collected
                examples=[json.loads(p.read_text(encoding='utf-8')) for p in (Path(folder)/'corpus/my-handwriting').glob('*.json')]
                assert {'х','Привет'}=={v['text'] for v in examples} and all(v['text']!='догадка' for v in examples)
                repeated=await client.call_tool('folio_collect_training_examples',{'document_id':'doc'});assert not repeated.isError and len(list((Path(folder)/'corpus/my-handwriting').glob('*.json')))==2
                denied=await client.call_tool('folio_collect_training_examples',{'document_id':'other'});assert denied.isError
                assert 'х' in json.loads((Path(folder)/'handwriting/my-handwriting.json').read_text(encoding='utf-8'))['samples']
                image=await client.call_tool('folio_read_canvas',{});assert not image.isError and any(c.type=='image' for c in image.content),image
                state=json.loads(next(c.text for c in image.content if c.type=='text'));args={'document_id':'doc','page_id':'page','revision':state['revision']}
                result=await client.call_tool('folio_write_text',{**args,'text':'Привет, Folio!','x':30,'y':40,'operation_id':'test_operation'});assert not result.isError,result
                validation=json.loads(result.content[0].text)['text_validation'];assert validation['status']=='pass' and validation['layout']['status']=='pass' and validation['layout']['naturalness']['status']=='pass' and validation['ocr']['exact_match'] is True
                assert any(s['action']=='test_operation' for s in Phone.strokes)
                Phone.recognized_override='увюличивается'
                verify=await client.call_tool('folio_verify_text',{'expected_text':'увеличивается','operation_id':'test_operation','document_id':'doc','page_id':'page','revision':str(Phone.revision)});assert not verify.isError
                wrong=json.loads(verify.content[0].text);assert wrong['exact_match'] is False and wrong['status']=='review' and wrong['spelling_ok'] is False
                Phone.recognized_override=None
                conflict=await client.call_tool('folio_write_text',{**args,'text':'Старый холст','x':30,'y':100});assert conflict.isError
                undo=await client.call_tool('folio_undo_agent_write',{'operation_id':'test_operation','document_id':'doc','page_id':'page','revision':str(Phone.revision)});assert not undo.isError and len(Phone.strokes)==1 and Phone.strokes[0]['action']=='user-note'
                learned=await client.call_tool('folio_learn_handwriting_sample',{'text':'я','document_id':'doc','page_id':'page'});assert not learned.isError,learned
                own=await client.call_tool('folio_write_text',{'text':'я','style':'personal','document_id':'doc','page_id':'page','revision':str(Phone.revision),'x':100,'y':100});assert not own.isError,own
                before=len(Phone.strokes);missing=await client.call_tool('folio_write_text',{'text':'нет образцов','style':'personal','document_id':'doc','page_id':'page','revision':str(Phone.revision),'x':100,'y':100});assert missing.isError and before==len(Phone.strokes)
                profiles=await client.call_tool('folio_handwriting_profiles',{});assert not profiles.isError
    phone.shutdown();print('PASS real MCP stdio initialize/discovery, PNG read, vector write, stale-page conflict, selective undo preserving user ink, personal sample capture/replay, missing-glyph rejection; Android endpoint is simulated')

if __name__=='__main__':
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(HandwritingTests);r=unittest.TextTestRunner().run(suite)
    if not r.wasSuccessful():sys.exit(1)
    asyncio.run(mcp_checks())
