'use strict';
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const native=typeof Native!=='undefined';
const defaults={nativePenOnly:true,schemaVersion:2,customGestures:[],enabled:true,intercept:false,haptic:true,fastResponse:true,windowMs:1000,swipeWindowMs:1000,actions:[{type:'home',label:'На главный экран',value:''}]};
const gestures=[['squeeze_single','Одиночное сжатие','Сожмите и отпустите корпус пера один раз.'],['squeeze_double','Двойное сжатие','Дважды сожмите и отпустите корпус пера.'],['swipe_up','Свайп вверх','Проведите пальцем по корпусу пера к его верхней части.'],['swipe_down','Свайп вниз','Проведите пальцем по корпусу пера к наконечнику.'],['swipe_up_up','Вверх → вверх','Два свайпа по корпусу пера вверх подряд.'],['swipe_down_down','Вниз → вниз','Два свайпа по корпусу пера вниз подряд.'],['swipe_up_down','Вверх → вниз','Проведите по корпусу вверх, затем вниз.'],['swipe_down_up','Вниз → вверх','Проведите по корпусу вниз, затем вверх.']];
let selectedGesture='squeeze_double';
const tokenLabels={pinch:'Сжатие',up:'Вверх',down:'Вниз'};
const gestureRows=()=>[...gestures,...(config.customGestures||[]).map(g=>[g.id,g.label,g.steps.map(s=>tokenLabels[s]).join(' → ')])];
function normalizeConfig(c){const migrate=!c.schemaVersion;c={...defaults,...c};if(migrate){c.windowMs=Math.max(1000,c.windowMs);c.swipeWindowMs=Math.max(1000,c.swipeWindowMs);}c.schemaVersion=2;c.customGestures=Array.isArray(c.customGestures)?c.customGestures:[];c.mappings=c.mappings||{};for(const id of [...gestures.map(g=>g[0]),...c.customGestures.map(g=>g.id)])if(!Array.isArray(c.mappings[id]))c.mappings[id]=id==='squeeze_double'?(c.actions||[]):[];c.actions=c.mappings.squeeze_double;return c;}
let config=normalizeConfig(JSON.parse(JSON.stringify(defaults))), currentState={}, initialized=false, editing=-1, pendingType='', applications=[],lastSqueezes=0,lastDoubles=0,lastLog='',toastTimer,lastRevision=-1,nativePicker=false;
const actions=()=>config.mappings[selectedGesture];
const catalog=[
 ['home','На главный экран','home','Вернуться на рабочий стол'],['back','Назад','back','Предыдущий экран'],
 ['recents','Недавние приложения','flow','Открыть список приложений'],['launch_app','Открыть приложение','app','Выберите приложение'],
 ['screenshot','Скриншот','shot','Сохранить снимок экрана'],['open_url','Открыть ссылку','link','Адрес в браузере'],
 ['notifications','Уведомления','flow','Развернуть шторку'],['quick_settings','Быстрые настройки','settings','Открыть панель управления'],
 ['volume_up','Увеличить громкость','volume','Громкость мультимедиа +'],['volume_down','Уменьшить громкость','volume','Громкость мультимедиа −'],
 ['play_pause','Пуск / пауза','play','Управление проигрывателем'],['next_track','Следующий трек','play','Переключить композицию'],
 ['previous_track','Предыдущий трек','back','Переключить композицию'],['lock','Заблокировать экран','app','Выключить экран планшета'],
 ['power','Меню питания','settings','Показать системное меню'],['tap','Нажатие на экран','pulse','Координаты в процентах'],
 ['swipe_up','Свайп вверх','pulse','Прокрутить экран'],['swipe_down','Свайп вниз','pulse','Прокрутить экран'],
 ['swipe_custom','Свайп между точками','pulse','Начало, конец и длительность'],['screen_path','Нарисованная траектория','pen','Записать движения по экрану'],['widget','Открыть виджет','app','Выбрать Android-виджет'],['shortcut','Ярлык приложения','link','Быстрые действия и экраны приложений'],
 ['wait','Пауза','clock','Подождать перед следующим действием']
];
function icon(name){const s=document.createElementNS('http://www.w3.org/2000/svg','svg'),u=document.createElementNS('http://www.w3.org/2000/svg','use');u.setAttribute('href','#i-'+name);s.append(u);return s;}
function el(tag,cls,text){const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e;}
function toast(message){$('#toast').textContent=message;$('#toast').classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').classList.remove('show'),3300);}
function showPage(page){if(!['flow','settings','diagnostics','start'].includes(page))page='flow';$$('.page').forEach(p=>p.classList.toggle('active',p.id==='page-'+page));$$('.nav-item').forEach(n=>n.classList.toggle('active',n.dataset.page===page));$('#pageName').textContent={flow:'Жесты пера',settings:'Настройки',diagnostics:'Сигналы пера',start:'Запуск без ПК'}[page];if(page==='start'&&native)Native.discoverAdb();window.scrollTo(0,0);}
$$('[data-page]').forEach(n=>n.onclick=()=>showPage(n.dataset.page));$$('[data-go]').forEach(n=>n.onclick=()=>showPage(n.dataset.go));$('.brand').onclick=()=>showPage('flow');
function save(){
 config.actions=config.mappings.squeeze_double;
 $('#saveState').lastChild.textContent='Сохраняем…';
 if(native)Native.save(JSON.stringify(config));
 else {try{localStorage.setItem('focusflow-preview',JSON.stringify(config));}catch(e){}window.saved(true);}
 renderActions();
}
window.saved=(success,message)=>{if(success){$('#saveState').lastChild.textContent='Сохранено на устройстве';}else{toast(message||'Не удалось сохранить');$('#saveState').lastChild.textContent='Ошибка сохранения';initialized=false;}};
function settingsToUI(){
 ['enabled','haptic','intercept','fastResponse','nativePenOnly'].forEach(k=>$('#'+k).checked=config[k]);$('#windowMs').value=config.windowMs;
 $('#enabledLabel').textContent=config.enabled?'Включён':'Выключен';$('#rangeValue').textContent=config.windowMs+' мс';$('#timingLabel').textContent=(selectedGesture.startsWith('swipe')?config.swipeWindowMs:config.windowMs)+' мс';
 if($('#swipeWindowMs')){$('#swipeWindowMs').value=config.swipeWindowMs;$('#swipeRangeValue').textContent=config.swipeWindowMs+' мс';}
}
['enabled','haptic','intercept','fastResponse','nativePenOnly'].forEach(k=>$('#'+k).onchange=e=>{config[k]=e.target.checked;settingsToUI();save();});
$('#windowMs').oninput=e=>{$('#rangeValue').textContent=e.target.value+' мс';};
$('#windowMs').onchange=e=>{config.windowMs=Number(e.target.value);settingsToUI();save();};
$('#swipeWindowMs').oninput=e=>{$('#swipeRangeValue').textContent=e.target.value+' мс';};
$('#swipeWindowMs').onchange=e=>{config.swipeWindowMs=Number(e.target.value);settingsToUI();save();};
function actionSubtitle(a){if(a.type==='wait')return a.value+' мс';if(a.type==='launch_app')return a.value;if(a.type==='open_url')return a.value;if(a.type==='tap')return 'X, Y: '+a.value+' %';if(a.type==='screen_path'||a.type==='swipe_custom'){try{const s=JSON.parse(a.value).strokes,end=s.at(-1);return s.length+' линий · '+(end.startMs+end.durationMs)+' мс';}catch(e){return 'Настройте траекторию';}}return (catalog.find(c=>c[0]===a.type)||[])[3]||a.type;}
function renderActions(){
 const selector=$('#gestureSelector');selector.replaceChildren();for(const [id,label,description] of gestureRows()){const b=el('button','gesture-option'+(id===selectedGesture?' active':''));b.dataset.gesture=id;b.append(el('b','',label),el('small','',config.mappings[id].length?config.mappings[id].length+' действ.':'Не настроен'));b.onclick=()=>{selectedGesture=id;renderActions();settingsToUI();};selector.append(b);}
 const chosen=gestureRows().find(g=>g[0]===selectedGesture),heroNames={squeeze_single:'Одно сжатие.',squeeze_double:'Два сжатия.',swipe_up:'Свайп вверх.',swipe_down:'Свайп вниз.',swipe_up_up:'Вверх → вверх.',swipe_down_down:'Вниз → вниз.',swipe_up_down:'Вверх → вниз.',swipe_down_up:'Вниз → вверх.'};$('#triggerName').textContent=chosen[1];$('#triggerDescription').textContent=chosen[2];$('#heroTitle').replaceChildren(el('span','',heroNames[selectedGesture]||chosen[1]),el('br'),document.createTextNode('Ваш сценарий.'));$('#heroCaption').textContent=chosen[2];$('.nav-count').textContent=gestureRows().filter(([id])=>config.mappings[id].length>0).length;$('#editGesture').classList.toggle('hidden',!selectedGesture.startsWith('custom_'));$('#deleteGesture').classList.toggle('hidden',!selectedGesture.startsWith('custom_'));
 const list=$('#actions');list.replaceChildren();
 actions().forEach((a,i)=>{
  const meta=catalog.find(c=>c[0]===a.type)||['','Действие','flow'];
  const row=el('div','action-row'),num=el('span','action-order',String(i+1).padStart(2,'0')),ico=el('div','action-icon');ico.append(icon(meta[2]));
  const text=el('button','action-text');text.type='button';text.title='Изменить действие';text.append(el('b','',a.label),el('small','',actionSubtitle(a)));text.onclick=()=>openCatalog(i);
  const controls=el('div','action-controls');
  [['↑','Поднять выше',-1],['↓','Опустить ниже',1]].forEach(([label,title,offset])=>{const b=el('button','icon-button move-button',label);b.title=title;b.setAttribute('aria-label',title);b.disabled=i+offset<0||i+offset>=actions().length;b.onclick=()=>{[actions()[i],actions()[i+offset]]=[actions()[i+offset],actions()[i]];save();};controls.append(b);});
  const del=el('button','icon-button');del.title='Удалить действие';del.setAttribute('aria-label','Удалить '+a.label);del.append(icon('trash'));del.onclick=()=>{actions().splice(i,1);save();};controls.append(del);
  row.append(num,ico,text,controls);list.append(row);
 });
 if(!actions().length)list.append(el('p','tip','Для этого жеста ещё нет действий. Добавьте первое действие ниже.'));
 $('#actionCount').textContent=actions().length;$('#heroActionCount').textContent=actions().length+' '+(actions().length===1?'действие':actions().length<5?'действия':'действий');
 $('#testButton').disabled=!actions().length;
}
function openCatalog(index=-1){
 if(index<0&&actions().length>=20){toast('В сценарии может быть до 20 действий');return;}
 editing=index;pendingType='';$('#dialogTitle').textContent=index>=0?'Изменить действие':'Добавить действие';
 $('#actionCatalog').classList.remove('hidden');$('#parameterForm').classList.add('hidden');$('#appPicker').classList.add('hidden');
 $('#actionDialog').showModal();
}
function choose(type){
 pendingType=type;const previous=editing>=0&&actions()[editing].type===type?actions()[editing]:null;
 $('#actionCatalog').classList.add('hidden');
 if(type==='swipe_custom'||type==='screen_path'){$('#actionDialog').close();openScreenEditor(type,previous);return;}
 if(type==='widget'||type==='shortcut'){if(native){nativePicker=true;Native.pickTarget(type,selectedGesture,editing);$('#actionDialog').close();}else toast('Выбор виджетов и ярлыков доступен на планшете');return;}
 if(type==='launch_app'){
  applications=native?JSON.parse(Native.apps()):[{label:'Заметки (предпросмотр)',package:'com.miui.notes'},{label:'Настройки',package:'com.android.settings'}];
  $('#appPicker').classList.remove('hidden');$('#appSearch').value='';renderApps('');return;
 }
 const params={open_url:['Адрес сайта','https://example.com','Откроется в браузере, выбранном на планшете.'],wait:['Пауза в миллисекундах','700','От 100 до 10 000 мс. Например, 700 мс после открытия приложения.'],tap:['Координаты X,Y в процентах','50,50','50,50 — центр экрана. Координаты зависят от ориентации планшета.']};
 if(params[type]){const [label,value,hint]=params[type];$('#parameterForm').classList.remove('hidden');$('#parameterLabel').textContent=label;$('#parameterHint').textContent=hint;$('#parameterValue').value=previous?previous.value:value;$('#parameterValue').type=type==='wait'?'number':'text';$('#confirmAction').textContent=editing>=0?'Сохранить':'Добавить';return;}
 commit({type,label:catalog.find(c=>c[0]===type)[1],value:''});
}
function commit(action){if(editing>=0)actions()[editing]=action;else actions().push(action);save();$('#actionDialog').close();toast(editing>=0?'Действие изменено':'Действие добавлено');}
function renderApps(filter){const list=$('#appList');list.replaceChildren();applications.filter(a=>(a.label+' '+a.package).toLowerCase().includes(filter.toLowerCase())).forEach(app=>{const b=el('button','app-choice');b.append(el('span','',app.label),el('small','',app.package));b.onclick=()=>commit({type:'launch_app',label:'Открыть '+app.label,value:app.package});list.append(b);});if(!list.children.length)list.append(el('p','tip','Приложения не найдены'));}
$('#appSearch').oninput=e=>renderApps(e.target.value);
$('#confirmAction').onclick=()=>{
 let value=$('#parameterValue').value.trim();
 if(pendingType==='open_url'&&!/^https?:\/\/[^\s]+$/i.test(value)){toast('Укажите адрес с https:// или http://');return;}
 if(pendingType==='wait'){const v=Number(value);if(!Number.isInteger(v)||v<100||v>10000){toast('Укажите от 100 до 10 000 мс');return;}}
 if(pendingType==='tap'){let parts=value.split(',').map(s=>s.trim());if(parts.length!==2||parts.some(v=>v===''||!Number.isFinite(Number(v))||Number(v)<0||Number(v)>100)){toast('Укажите две координаты от 0 до 100, например 50,50');return;}value=parts.join(',');}
 commit({type:pendingType,label:catalog.find(c=>c[0]===pendingType)[1],value});
};
catalog.forEach(([type,label,ico])=>{const b=el('button','catalog-item');b.append(icon(ico),el('span','',label));b.onclick=()=>choose(type);$('#actionCatalog').append(b);});
$('#addAction').onclick=()=>openCatalog();$$('.close-dialog').forEach(b=>b.onclick=()=>b.closest('dialog').close());
$$('dialog').forEach(d=>d.addEventListener('click',e=>{if(e.target===d){const r=d.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)d.close();}}));
$('#testButton').onclick=()=>{if(native){Native.test(selectedGesture);toast('Проверяем сценарий');}else toast('Действия выполняются в Android-приложении');};
$('#stopButton').onclick=()=>{if(native)Native.stop();toast('Последовательность остановлена');};
$('#accessibilityButton').onclick=()=>{if(native)Native.accessibility();else toast('Доступно на планшете');};
$('#appSettings').onclick=()=>{if(native)Native.appSettings();};
$('#setupButton').onclick=()=>{if(!currentState.accessibility&&native)Native.accessibility();else showPage('start');};
$('#exportButton').onclick=()=>{if(native)Native.exportConfig();else{const blob=new Blob([JSON.stringify(config,null,2)],{type:'application/json'}),url=URL.createObjectURL(blob),a=el('a');a.href=url;a.download='FocusFlow.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}};
$('#importButton').onclick=()=>{$('#importValue').value='';$('#importDialog').showModal();};
$('#confirmImport').onclick=()=>{try{const input=JSON.parse($('#importValue').value);if(!input.mappings&&!Array.isArray(input.actions))throw Error();const next=normalizeConfig(input),ids=new Set(),patterns=new Set(gestures.map(g=>JSON.stringify(g[0]==='squeeze_single'?['pinch']:g[0]==='squeeze_double'?['pinch','pinch']:g[0].substring(6).split('_'))));if(next.customGestures.length>30)throw Error();for(const g of next.customGestures){if(!/^custom_[A-Za-z0-9_-]{1,60}$/.test(g.id)||ids.has(g.id)||typeof g.label!=='string'||!g.label.trim()||g.label.length>80||!Array.isArray(g.steps)||g.steps.length<1||g.steps.length>12||g.steps.some(s=>!tokenLabels[s])||patterns.has(JSON.stringify(g.steps))||!Number.isInteger(g.gapMs)||g.gapMs<200||g.gapMs>3000)throw Error();ids.add(g.id);patterns.add(JSON.stringify(g.steps));}for(const list of Object.values(next.mappings)){if(!Array.isArray(list)||list.length>20||list.some(a=>!catalog.some(c=>c[0]===a.type)&&a.type!=='app_activity'||typeof a.label!=='string'))throw Error();}if(!Number.isFinite(next.windowMs)||next.windowMs<200||next.windowMs>3000||!Number.isFinite(next.swipeWindowMs)||next.swipeWindowMs<200||next.swipeWindowMs>3000)throw Error();config=next;if(!config.mappings[selectedGesture])selectedGesture='squeeze_double';save();settingsToUI();$('#importDialog').close();toast('Настройки импортированы');}catch(e){toast('Проверьте формат JSON и список действий');}};
function update(){
 try{
  const state=native?JSON.parse(Native.getState()):{config,accessibility:false,bridge:false,squeezes:0,doubles:0,log:[],model:'Xiaomi Pad 7',android:'16'};currentState=state;
  if(!initialized||(native&&state.configRevision!==undefined&&state.configRevision!==lastRevision)){config=normalizeConfig(state.config||defaults);if(!config.mappings[selectedGesture])selectedGesture='squeeze_double';settingsToUI();renderActions();initialized=true;lastRevision=state.configRevision;nativePicker=false;}
  if(capturePen&&state.recordedSteps){draftSteps=[...state.recordedSteps];$('#gestureGap').value=state.recordedGapMs||1200;renderDraft();$('#recordHint').textContent=state.recording?'Записываем движения пера…':'Запись остановлена. Можно исправить движения кнопками.';}
  const ready=!!state.accessibility&&!!state.bridge;
  $('#sidebarConnection').textContent=state.folioPriority?'Приоритет Folio':state.bridge?(state.sharedBridge?'Общий мост подключён':'Старый мост · требуется обновление'):'Ожидание ADB-помощника';
  $('#setupBanner').classList.toggle('warn',!ready||!config.enabled||!!state.bridgeError);
  $('#connectionTitle').textContent=state.folioPriority?'Folio управляет пером':state.stopped?'Команды FocusFlow остановлены':state.bridge&&!state.sharedBridge?'Нужен общий помощник Folio':!config.enabled?'Сценарий выключен':!state.accessibility?'Включите службу управления':!state.bridge?'Подключаем помощник жестов':state.grabbed?'Перо подключено · перехват активен':'Перо подключено · наблюдение за жестами';
  $('#connectionText').textContent=state.folioPriority?'Команды FocusFlow приостановлены, пока открыт Folio. После выхода они снова будут доступны.':state.stopped?'Нажмите «Запустить помощник» в разделе «Запуск без ПК», чтобы снова включить команды. Folio продолжает работать.':!config.enabled?'Включите переключатель, чтобы снова запускать действия.':!state.accessibility?'Один раз разрешите FocusFlow выполнять ваши действия.':!state.bridge?'Подключение проверяется автоматически. Если нужно первое сопряжение, откройте «Запуск без ПК».':state.bridgeError||(!state.grabbed?'Настроенные жесты запускают свои сценарии. Штатные жесты Xiaomi тоже могут сработать.':'Общий помощник готов. В Folio пером управляет Folio, в других приложениях — FocusFlow.');
  $('#setupButton').textContent=ready?'Настройки':'Настроить';$('#setupButton').append(icon('arrow'));
  $('#diagService').textContent=state.accessibility?'Включена':'Выключена';$('#diagBridge').textContent=state.bridge?'Подключён':'Не подключён';$('#diagDoubles').textContent=state.doubles;
  $('#squeezeCount').textContent=state.squeezes;$('#doubleCount').textContent=state.doubles;$('#lastSignal').textContent=state.lastSignal||'Сожмите перо, чтобы увидеть сигнал';
  ['signalDot','liveDot'].forEach(id=>$('#'+id).style.background=state.bridge?'var(--green)':'#626b7c');
  $('#interceptState').textContent=state.grabbed?'Перехват работает: системные жесты в канале пера отключены.':state.bridgeError||(config.intercept?'Перехват запрошен. Ожидаем подтверждение помощника.':'Без перехвата сценарий может выполняться вместе со штатным действием Xiaomi.');
  if(state.squeezes!==lastSqueezes){$('#signalBars').classList.remove('active');void $('#signalBars').offsetWidth;$('#signalBars').classList.add('active');}
  if(state.doubles>lastDoubles)toast('Двойное сжатие распознано');lastSqueezes=state.squeezes;lastDoubles=state.doubles;
  $('#stopButton').classList.toggle('hidden',!state.running);$('#testButton').disabled=!!state.running||!actions().length;
  const adb=state.localAdb||{};$('#localMessage').textContent=adb.message||'Запуск доступен в Android-приложении';$('#localTitle').textContent=state.bridge?'Помощник работает':adb.paired?'Сопряжение сохранено':'Первое сопряжение';['pairAdb','startLocalAdb'].forEach(id=>$('#'+id).disabled=!!adb.busy);
  for(const [id,value] of [['pairingPort',adb.pairingPort],['connectPort',adb.connectPort]])if(value>0&&document.activeElement!==$('#'+id))$('#'+id).value=value;
  $('#deviceInfo').textContent=(state.device||'Перо ещё не подключено')+' · '+state.model+' · Android '+state.android;
  const key=JSON.stringify(state.log);if(key!==lastLog){lastLog=key;$('#eventLog').replaceChildren();(state.log||[]).forEach(row=>{const r=el('div','log-row');r.append(el('span','log-time',row.time),el('span','log-text',row.text));$('#eventLog').append(r);});}
 }catch(e){console.error(e);}
}
$('#addQuickTile').onclick=()=>{if(native)Native.addQuickTile();else toast('Добавление плитки доступно на планшете');};
$('#wirelessSettings').onclick=()=>{if(native)Native.wirelessSettings();else toast('Откройте настройки на планшете');};
$('#discoverAdb').onclick=()=>{if(native)Native.discoverAdb();toast('Ищем локальную службу Android');};
$('#pairAdb').onclick=()=>{const port=Number($('#pairingPort').value),code=$('#pairingCode').value.trim();if(!Number.isInteger(port)||port<1||port>65535||!/^\d{6}$/.test(code)){toast('Введите порт сопряжения и код из 6 цифр');return;}if(native)Native.pairAdb(port,code);$('#pairingCode').value='';};
$('#startLocalAdb').onclick=()=>{const port=Number($('#connectPort').value)||0;if(port<0||port>65535){toast('Проверьте порт подключения');return;}if(native)Native.startLocalAdb(port);};
$('#stopLocalAdb').onclick=()=>{if(native)Native.stopLocalAdb();};$('#startAccessibility').onclick=()=>{if(native)Native.accessibility();};
let draftSteps=[],draftId=null,capturePen=false,screenMode='swipe_custom',screenStrokes=[],canvasStroke=null,canvasDown=0,canvasEpoch=0;
function renderDraft(){const box=$('#gestureSteps');box.replaceChildren();draftSteps.forEach((token,i)=>{const b=el('button','step-chip',(i+1)+'. '+tokenLabels[token]+' ×');b.onclick=()=>{stopPenRecording();draftSteps.splice(i,1);renderDraft();};box.append(b);});if(!draftSteps.length)box.append(el('p','tip','Пока нет движений'));}
function stopPenRecording(){if(native&&capturePen){try{const s=JSON.parse(Native.getState());if(Array.isArray(s.recordedSteps)){draftSteps=[...s.recordedSteps];$('#gestureGap').value=s.recordedGapMs||1200;renderDraft();}}catch(e){}}capturePen=false;if(native)Native.endRecording();}
function openGestureEditor(edit){stopPenRecording();const g=edit?config.customGestures.find(g=>g.id===selectedGesture):null;draftId=g?g.id:null;draftSteps=g?[...g.steps]:[];$('#gestureLabel').value=g?g.label:'';$('#gestureGap').value=g?g.gapMs:1200;$('#recordHint').textContent='Во время записи назначенные действия не выполняются.';renderDraft();$('#gestureDialog').showModal();}
$('#newGesture').onclick=()=>openGestureEditor(false);$('#editGesture').onclick=()=>openGestureEditor(true);
$('#deleteGesture').onclick=()=>{const id=selectedGesture;config.customGestures=config.customGestures.filter(g=>g.id!==id);delete config.mappings[id];selectedGesture='squeeze_double';save();};
$$('[data-token]').forEach(b=>b.onclick=()=>{stopPenRecording();if(draftSteps.length>=12){toast('Не более 12 движений');return;}draftSteps.push(b.dataset.token);renderDraft();});
$('#undoToken').onclick=()=>{stopPenRecording();draftSteps.pop();renderDraft();};
$('#recordPen').onclick=()=>{if(!native){toast('Запись пером доступна на планшете');return;}if(!currentState.bridge){toast('Сначала запустите помощник');return;}capturePen=true;draftSteps=[];renderDraft();Native.beginRecording();$('#recordHint').textContent='Записываем движения пера…';};
$('#stopRecordPen').onclick=stopPenRecording;$('#gestureDialog').addEventListener('close',stopPenRecording);
$('#saveGesture').onclick=()=>{
 stopPenRecording();const label=$('#gestureLabel').value.trim(),gapMs=Number($('#gestureGap').value);
 if(!label||!draftSteps.length||draftSteps.length>12||!Number.isInteger(gapMs)||gapMs<200||gapMs>3000){toast('Укажите название, движения и интервал от 200 до 3000 мс');return;}
 const builtinSequences=[['pinch'],['pinch','pinch'],['up'],['down'],['up','up'],['down','down'],['up','down'],['down','up']];
 if(builtinSequences.some(s=>JSON.stringify(s)===JSON.stringify(draftSteps))){toast('Этот жест уже есть среди готовых. Выберите его карточку.');return;}
 if(config.customGestures.some(g=>g.id!==draftId&&JSON.stringify(g.steps)===JSON.stringify(draftSteps))){toast('Такая комбинация уже есть');return;}
 if(!draftId&&config.customGestures.length>=30){toast('Не более 30 своих комбинаций');return;}
 const id=draftId||'custom_'+Date.now().toString(36),g={id,label,steps:[...draftSteps],gapMs};const index=config.customGestures.findIndex(g=>g.id===id);if(index>=0)config.customGestures[index]=g;else config.customGestures.push(g);if(!config.mappings[id])config.mappings[id]=[];selectedGesture=id;save();settingsToUI();$('#gestureDialog').close();toast('Комбинация сохранена. Назначьте ей действия.');
};
function openScreenEditor(mode,previous){screenMode=mode;screenStrokes=[];canvasEpoch=0;if(previous)try{screenStrokes=JSON.parse(previous.value).strokes;}catch(e){}$('#screenTitle').textContent=mode==='swipe_custom'?'Свайп между точками':'Нарисованная траектория';$('#swipeCoordinates').classList.toggle('hidden',mode!=='swipe_custom');$('#drawArea').classList.toggle('hidden',mode!=='screen_path');$('#captureOnScreen').textContent=mode==='swipe_custom'?'Выбрать точки на экране':'Нарисовать поверх приложения';const stroke=screenStrokes[0],a=stroke?stroke.points[0]:[50,75],b=stroke?stroke.points.at(-1):[50,25];['startX','startY','endX','endY'].forEach((id,i)=>$('#'+id).value=[...a,...b][i]);const last=screenStrokes.at(-1);$('#screenDuration').value=last?last.startMs+last.durationMs:500;$('#screenDialog').showModal();renderCanvas();}
function renderCanvas(){const canvas=$('#pathCanvas'),ctx=canvas.getContext('2d');ctx.clearRect(0,0,canvas.width,canvas.height);ctx.strokeStyle='#b8a2ff';ctx.lineWidth=5;ctx.lineCap='round';for(const s of screenStrokes){ctx.beginPath();s.points.forEach(([x,y],i)=>{if(i)ctx.lineTo(x*8,y*5);else ctx.moveTo(x*8,y*5);});ctx.stroke();}if(canvasStroke){ctx.beginPath();canvasStroke.forEach(([x,y],i)=>{if(i)ctx.lineTo(x*8,y*5);else ctx.moveTo(x*8,y*5);});ctx.stroke();}}
const pathCanvas=$('#pathCanvas');function canvasPoint(e){const r=pathCanvas.getBoundingClientRect();return [Math.round(Math.max(0,Math.min(100,(e.clientX-r.left)*100/r.width))*100)/100,Math.round(Math.max(0,Math.min(100,(e.clientY-r.top)*100/r.height))*100)/100];}
pathCanvas.onpointerdown=e=>{if(screenStrokes.length>=10){toast('Не более 10 линий');return;}e.preventDefault();pathCanvas.setPointerCapture(e.pointerId);canvasDown=performance.now();if(!canvasEpoch)canvasEpoch=canvasDown;canvasStroke=[canvasPoint(e)];renderCanvas();};
pathCanvas.onpointermove=e=>{if(canvasStroke&&canvasStroke.length<255){canvasStroke.push(canvasPoint(e));renderCanvas();}};
pathCanvas.onpointerup=e=>{if(!canvasStroke)return;canvasStroke.push(canvasPoint(e));const durationMs=Math.max(50,Math.round(performance.now()-canvasDown)),last=screenStrokes.at(-1);const startMs=last?Math.max(last.startMs+last.durationMs,Math.round(canvasDown-canvasEpoch)):0;screenStrokes.push({points:canvasStroke,startMs,durationMs});canvasStroke=null;$('#screenDuration').value=startMs+durationMs;renderCanvas();};pathCanvas.onpointercancel=()=>{canvasStroke=null;renderCanvas();};
$('#clearPath').onclick=()=>{screenStrokes=[];canvasStroke=null;canvasEpoch=0;renderCanvas();};
$('#captureOnScreen').onclick=()=>{if(!native){toast('Запись поверх приложений доступна на планшете');return;}if(!currentState.accessibility){toast('Сначала включите службу FocusFlow');return;}nativePicker=true;Native.recordScreen(screenMode,selectedGesture,editing,Number($('#screenDuration').value)||500);$('#screenDialog').close();};
$('#saveScreenAction').onclick=()=>{
 const duration=Number($('#screenDuration').value);if(!Number.isInteger(duration)||duration<50||duration>30000){toast('Длительность от 50 до 30 000 мс');return;}
 let strokes;if(screenMode==='swipe_custom'){const coords=['startX','startY','endX','endY'].map(id=>$('#'+id).value.trim());if(coords.some(v=>v===''||!Number.isFinite(Number(v))||Number(v)<0||Number(v)>100)){toast('Координаты должны быть от 0 до 100%');return;}const [x1,y1,x2,y2]=coords.map(Number);strokes=[{points:[[x1,y1],[x2,y2]],startMs:0,durationMs:duration}];}
 else{if(!screenStrokes.length){toast('Сначала нарисуйте траекторию');return;}const last=screenStrokes.at(-1),scale=duration/(last.startMs+last.durationMs);let end=0;strokes=screenStrokes.map(s=>{const startMs=Math.max(end,Math.round(s.startMs*scale)),durationMs=Math.max(50,Math.round(s.durationMs*scale));end=startMs+durationMs;return {points:s.points,startMs,durationMs};});if(end>30000){toast('Увеличьте длительность или уменьшите число линий');return;}}
 const action={type:screenMode,label:screenMode==='swipe_custom'?'Свайп между точками':'Нарисованная траектория',value:JSON.stringify({strokes})};if(editing>=0)actions()[editing]=action;else actions().push(action);save();$('#screenDialog').close();toast('Действие сохранено');
};
if(!native){try{const stored=localStorage.getItem('focusflow-preview');if(stored)config=normalizeConfig(JSON.parse(stored));}catch(e){}}
update();setInterval(update,700);
