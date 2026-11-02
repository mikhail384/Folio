const path=require('path');
const fs=require('fs');
const http=require('http');
const assert=require('assert/strict');
const {chromium}=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'));
const root=path.resolve(__dirname,'../app/src/main/assets');
const output=path.resolve(__dirname,'../build/qa');fs.mkdirSync(output,{recursive:true});
const server=http.createServer((req,res)=>{
 const file=path.join(root,req.url==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));
 if(!file.startsWith(root+path.sep)){res.writeHead(403).end();return;}
 try{res.setHeader('Content-Type',file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html; charset=utf-8');res.end(fs.readFileSync(file));}catch(e){res.writeHead(404).end();}
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const chrome='C:/Program Files/Google/Chrome/Application/chrome.exe';
 const browser=await chromium.launch({headless:true,...(fs.existsSync(chrome)?{executablePath:chrome}:{}),args:['--disable-gpu'],downloadsPath:path.join(output,'downloads')});
 try{
  const page=await browser.newPage({viewport:{width:1280,height:900},deviceScaleFactor:1});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:'+server.address().port);await page.locator('h1').first().waitFor();
  assert.equal(await page.locator('.action-row').count(),1);
  await page.locator('#addAction').click();await page.getByRole('button',{name:'Скриншот',exact:true}).click();
  assert.equal(await page.locator('.action-row').count(),2);
  await page.locator('#addAction').click();await page.getByRole('button',{name:'Пауза',exact:true}).click();
  await page.locator('#parameterValue').fill('700');await page.locator('#confirmAction').click();
  assert.equal(await page.locator('.action-row').count(),3);
  assert.match(await page.locator('.action-row').last().textContent(),/700 мс/);
  await page.locator('.action-row').last().getByRole('button',{name:'Поднять выше'}).click();
  assert.match(await page.locator('.action-row').nth(1).textContent(),/Пауза/);
  await page.reload();assert.equal(await page.locator('.action-row').count(),3);
  assert.equal(await page.locator('.gesture-option').count(),8);
  await page.locator('[data-gesture=swipe_up_down]').click();assert.equal(await page.locator('.action-row').count(),0);
  await page.locator('#addAction').click();await page.getByRole('button',{name:'Назад',exact:true}).click();assert.equal(await page.locator('.action-row').count(),1);
  await page.locator('[data-gesture=squeeze_double]').click();assert.equal(await page.locator('.action-row').count(),3);
  await page.locator('#addAction').click();await page.getByRole('button',{name:'Открыть приложение',exact:true}).click();
  await page.locator('#appSearch').fill('Настройки');await page.locator('.app-choice').click();
  assert.equal(await page.locator('.action-row').count(),4);
  await page.locator('.action-row').last().getByRole('button',{name:/Удалить/}).click();
  assert.equal(await page.locator('.action-row').count(),3);
  await page.screenshot({path:path.join(output,'desktop.png'),fullPage:true,animations:'disabled'});
  await page.locator('[data-page=settings]').click();
  assert.equal(await page.locator('#fastResponse').isChecked(),true);
  await page.locator('#fastResponse').uncheck();await page.reload();await page.locator('[data-page=settings]').click();assert.equal(await page.locator('#fastResponse').isChecked(),false);
  await page.locator('#fastResponse').check();
  await page.locator('#windowMs').evaluate(e=>{e.value=750;e.dispatchEvent(new Event('change'));});
  assert.equal(await page.locator('#rangeValue').textContent(),'750 мс');
  await page.locator('#importButton').click();await page.locator('#importValue').fill('{"actions":null}');await page.locator('#confirmImport').click();
  assert.equal(await page.locator('#importDialog').evaluate(e=>e.open),true);
  await page.locator('#importDialog .close-dialog').click();
  await page.locator('[data-page=flow]').click();
  await page.locator('#newGesture').click();await page.locator('#gestureLabel').fill('Сжатие и вверх-вниз');
  for(const token of ['pinch','up','down'])await page.locator('[data-token='+token+']').click();
  await page.locator('#gestureGap').fill('1400');await page.locator('#saveGesture').click();
  assert.equal(await page.locator('.gesture-option').count(),9);assert.equal(await page.locator('.action-row').count(),0);
  await page.locator('#addAction').click();await page.getByRole('button',{name:'Свайп между точками',exact:true}).click();
  for(const [id,value] of [['startX','10'],['startY','80'],['endX','90'],['endY','20'],['screenDuration','650']])await page.locator('#'+id).fill(value);
  await page.locator('#saveScreenAction').click();assert.equal(await page.locator('.action-row').count(),1);
  let saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('focusflow-preview'))),custom=saved.customGestures[0];
  assert.deepEqual(custom.steps,['pinch','up','down']);assert.equal(custom.gapMs,1400);let stroke=JSON.parse(saved.mappings[custom.id][0].value).strokes[0];assert.deepEqual(stroke.points,[[10,80],[90,20]]);assert.equal(stroke.durationMs,650);
  await page.locator('#addAction').click();await page.getByRole('button',{name:'Нарисованная траектория',exact:true}).click();
  const canvas=await page.locator('#pathCanvas').boundingBox();await page.mouse.move(canvas.x+30,canvas.y+30);await page.mouse.down();await page.mouse.move(canvas.x+100,canvas.y+100,{steps:8});await page.mouse.move(canvas.x+180,canvas.y+40,{steps:8});await page.mouse.up();await page.locator('#screenDuration').fill('1000');await page.locator('#saveScreenAction').click();
  saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('focusflow-preview')));assert.equal(saved.mappings[custom.id].length,2);assert.ok(JSON.parse(saved.mappings[custom.id][1].value).strokes[0].points.length>10);
  await page.reload();assert.equal(await page.locator('.gesture-option').count(),9);await page.locator('[data-gesture="'+custom.id+'"]').click();assert.equal(await page.locator('.action-row').count(),2);
  await page.locator('#editGesture').click();assert.equal(await page.locator('#gestureSteps .step-chip').count(),3);await page.locator('#gestureDialog .close-dialog').click();
  await page.locator('[data-gesture=squeeze_double]').click();assert.equal(await page.locator('.action-row').count(),3);
  for(const [name,width,height] of [['tablet',1000,700],['portrait',850,1275],['phone',390,844]]){
   await page.setViewportSize({width,height});await page.locator('[data-page=flow]').click();
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'Horizontal overflow: '+name);
   await page.screenshot({path:path.join(output,name+'.png'),fullPage:true,animations:'disabled'});
  }
  await page.locator('[data-page=diagnostics]').click();assert.equal(await page.locator('#diagService').textContent(),'Выключена');
  await page.locator('[data-page=start]').click();await page.locator('#pairAdb').click();assert.match(await page.locator('#toast').textContent(),/порт сопряжения/);
  assert.deepEqual(errors,[]);console.log('PASS: editor, custom gesture create/edit/persistence/profile isolation, coordinate swipe and drawn path, import + 4 viewports; no page errors');
 }finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);server.close();process.exit(1);});
