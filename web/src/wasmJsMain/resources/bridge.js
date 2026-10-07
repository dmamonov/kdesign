// Browser-only IO. All application state, rendering, validation and editing live in Kotlin/Wasm.
window.designer = {
  mount(html) {
    document.getElementById('reference-picker')?.remove();
    const view=document.querySelector('main')?.dataset.view;
    const selectors=['.scene-scroll','.asset-list','.instances','.explorer','.table-scroll'];
    const positions=selectors.map(s=>{const el=document.querySelector(s);return el ? [el.scrollLeft,el.scrollTop] : [0,0];});
    document.getElementById('app').innerHTML = html;
    if(view===document.querySelector('main')?.dataset.view) selectors.forEach((s,i)=>{const el=document.querySelector(s);if(el) {el.scrollLeft=positions[i][0];el.scrollTop=positions[i][1];}});
  },
  value(id) { return document.getElementById(id)?.value ?? ''; },
  pixel(id, color) { const el = document.getElementById(id); if(el) el.style.backgroundColor = color; },
  request(method, url, body, done) {
    fetch(url, {method, signal: AbortSignal.timeout(url==='/api/generate' ? 100000 : 15000), headers: {'Content-Type':'application/json'}, ...(method === 'GET' ? {} : {body})})
      .then(async r => { const text = await r.text(); done(r.ok, text); })
      .catch(e => done(false, e.message));
  },
  bind(action) {
    let painting = false;
    let referenceEditor = null, referenceStart = 0, referenceEnd = 0, activeChoice = 0;
    const commitTable = editor => {
      if(editor.dataset.tableOwner && editor.dataset.tablePending==='true') {editor.dataset.tablePending='false';action('table-edit',editor.id,JSON.stringify({owner:editor.dataset.tableOwner,field:editor.dataset.tableField,content:editor.value}));}
    };
    const closeReferences = () => { document.getElementById('reference-picker')?.remove(); referenceEditor=null; };
    const chooseReference = id => {
      const editor=referenceEditor;
      if(!editor || !editor.isConnected) {closeReferences();return;}
      editor.setRangeText('[['+id+']]',referenceStart,referenceEnd,'end');
      closeReferences(); editor.focus(); action('reference-preview',editor.id,editor.value);editor.dataset.tablePending='true';commitTable(editor);if(!editor.dataset.tableOwner) action('form-edit',editor.id,editor.value);
    };
    const showChoices = query => {
      const list=document.getElementById('reference-choices'); if(!list) return;
      list.replaceChildren(); activeChoice=0;
      const matches=(window.designer.referenceObjects||[]).filter(o=>(o.name+' '+o.type+' '+o.id).toLowerCase().includes(query.toLowerCase()));
      matches.forEach(o=>{
        const button=document.createElement('button'); button.type='button';button.className='reference-choice';button.dataset.refChoice=o.id;
        button.setAttribute('role','option'); button.setAttribute('aria-selected','false');
        const name=document.createElement('b');name.textContent=o.name;
        const detail=document.createElement('small');detail.textContent=o.type+' · '+o.id;
        button.append(name,detail); list.append(button);
      });
      if(!matches.length) {const empty=document.createElement('p');empty.textContent='No matching objects';list.append(empty);}
      list.firstElementChild?.setAttribute('aria-selected','true');
    };
    const openReferences = (editor,start,end,query,searchable) => {
      closeReferences();referenceEditor=editor;referenceStart=start;referenceEnd=end;
      const picker=document.createElement('section');picker.id='reference-picker';picker.className='reference-picker';picker.setAttribute('aria-label','Object references');
      const title=document.createElement('div');title.className='reference-picker-title';title.textContent='REFERENCE AN OBJECT';
      const close=document.createElement('button');close.type='button';close.textContent='×';close.setAttribute('aria-label','Close references');close.addEventListener('click',()=>{closeReferences();editor.focus();});title.append(close);picker.append(title);
      if(searchable) {const search=document.createElement('input');search.placeholder='Search objects…';search.setAttribute('aria-label','Search objects');search.addEventListener('input',()=>showChoices(search.value));picker.append(search);}
      const list=document.createElement('div');list.id='reference-choices';list.setAttribute('role','listbox');list.setAttribute('aria-label','Matching objects');picker.append(list);document.body.append(picker);
      const rect=editor.getBoundingClientRect();picker.style.left=Math.max(8,Math.min(rect.left,window.innerWidth-370))+'px';
      picker.style.top=Math.max(8,Math.min(rect.bottom+5,window.innerHeight-340))+'px';showChoices(query);
      if(searchable) picker.querySelector('input').focus();
    };
    document.addEventListener('input',e=>{
      const editor=e.target;if(!editor.matches('input[id],textarea[id]')) return;
      if(!editor.dataset.tableOwner) action('form-edit',editor.id,editor.value);
      if(!editor.matches('[data-references]')) return;
      action('reference-preview',editor.id,editor.value);editor.dataset.tablePending='true';commitTable(editor);
      const end=editor.selectionStart, prefix=editor.value.slice(0,end), match=prefix.match(/(?:^|\s)@([^\n@]*)$/);
      if(match) openReferences(editor,prefix.lastIndexOf('@'),end,match[1],false);else closeReferences();
    });
    document.addEventListener('keydown',e=>{
      if(!document.getElementById('reference-picker')) return;
      const choices=Array.from(document.querySelectorAll('[data-ref-choice]'));
      if(e.key==='Escape'){e.preventDefault();closeReferences();return;}
      if(e.key==='ArrowDown'||e.key==='ArrowUp') {
        e.preventDefault();activeChoice=(activeChoice+(e.key==='ArrowDown'?1:-1)+choices.length)%choices.length;
        choices.forEach((el,i)=>el.setAttribute('aria-selected',String(i===activeChoice)));choices[activeChoice]?.scrollIntoView({block:'nearest'});
      }
      if(e.key==='Enter'&&choices.length) {e.preventDefault();chooseReference(choices[activeChoice].dataset.refChoice);}
    });
    document.addEventListener('click', e => {
      const choice=e.target.closest('[data-ref-choice]');
      if(choice){e.preventDefault();chooseReference(choice.dataset.refChoice);return;}
      const el = e.target.closest('[data-action]');
      if(el?.dataset.action==='reference-picker') {
        e.preventDefault();const editor=document.getElementById(el.dataset.id);
        openReferences(editor,editor.selectionStart??editor.value.length,editor.selectionEnd??editor.value.length,'',true);return;
      }
      if(e.target.closest('#reference-picker')) return;
      if(el?.dataset.action==='reference-open') e.preventDefault();
      const pendingEditor=referenceEditor;closeReferences();if(pendingEditor) commitTable(pendingEditor);
      if(el && !el.disabled && el.dataset.action !== 'paint') action(el.dataset.action, el.dataset.id ?? '', el.dataset.value ?? '');
    });
    document.addEventListener('focusout',e=>{if(e.target.dataset.tableOwner && !document.getElementById('reference-picker')) commitTable(e.target);});
    document.addEventListener('change', e => { const el=e.target; if(el.dataset.change) action(el.dataset.change,el.dataset.id??'',el.value);else if(el.id && el.tagName==='SELECT') action('form-edit',el.id,el.value); });
    document.addEventListener('pointerdown', e => {
      const el=e.target.closest('[data-action="paint"]');
      if(el) { e.preventDefault(); painting=true; action('stroke','',''); action('paint',el.dataset.id,''); }
    });
    document.addEventListener('pointerover', e => { const el=e.target.closest('[data-action="paint"]'); if(painting && el) action('paint',el.dataset.id,''); });
    document.addEventListener('pointerup', () => { if(painting) { painting=false; action('stroke-end','',''); } });
    document.addEventListener('keydown', e => { if((e.ctrlKey||e.metaKey)&&e.key==='s') {e.preventDefault();action('save','','');} });
    window.addEventListener('beforeunload', e => { if(window.designer.dirty) { e.preventDefault(); e.returnValue=''; } });
  },
  saveStatus(text, pending) {
    window.designer.dirty=pending;
    const status=document.querySelector('.save-state');
    if(status) {status.textContent=text;status.setAttribute('role','status');status.setAttribute('aria-live','polite');}
  },
  tableCellState(id, error) {
    const editor=document.getElementById(id);if(!editor) return;
    editor.setAttribute('aria-invalid',String(Boolean(error)));
    let hint=document.getElementById(id+'-error');
    if(!hint) {hint=document.createElement('small');hint.id=id+'-error';hint.className='cell-error';hint.setAttribute('role','alert');(editor.closest('.field-wrap') || editor.parentElement).append(hint);}
    hint.textContent=error;
    if(!error) document.getElementById('action-error')?.remove();
  },
  error(message) {
    let el=document.getElementById('action-error');
    if(!el) {el=document.createElement('p');el.id='action-error';el.className='modal-error';el.setAttribute('role','alert');(document.querySelector('.dialog') || document.querySelector('.page-body')).prepend(el);}
    el.textContent=message;
  },
  dirty: false
};
