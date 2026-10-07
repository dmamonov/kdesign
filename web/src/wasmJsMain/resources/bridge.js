// Browser-only IO. All application state, rendering, validation and editing live in Kotlin/Wasm.
window.designer = {
  mount(html) {
    const view=document.querySelector('main')?.dataset.view;
    const selectors=['.scene-scroll','.asset-list','.instances','.explorer','.table-scroll'];
    const positions=selectors.map(s=>{const el=document.querySelector(s);return el ? [el.scrollLeft,el.scrollTop] : [0,0];});
    document.getElementById('app').innerHTML = html;
    if(view===document.querySelector('main')?.dataset.view) selectors.forEach((s,i)=>{const el=document.querySelector(s);if(el) {el.scrollLeft=positions[i][0];el.scrollTop=positions[i][1];}});
  },
  value(id) { return document.getElementById(id)?.value ?? ''; },
  pixel(id, color) { const el = document.getElementById(id); if(el) el.style.backgroundColor = color; },
  request(method, url, body, done) {
    fetch(url, {method, headers: {'Content-Type':'application/json'}, ...(method === 'GET' ? {} : {body})})
      .then(async r => { const text = await r.text(); done(r.ok, text); })
      .catch(e => done(false, e.message));
  },
  bind(action) {
    let painting = false;
    document.addEventListener('click', e => {
      const el = e.target.closest('[data-action]');
      if(el && !el.disabled && el.dataset.action !== 'paint') action(el.dataset.action, el.dataset.id ?? '', el.dataset.value ?? '');
    });
    document.addEventListener('change', e => { const el=e.target; if(el.dataset.change) action(el.dataset.change,el.dataset.id??'',el.value); });
    document.addEventListener('pointerdown', e => {
      const el=e.target.closest('[data-action="paint"]');
      if(el) { e.preventDefault(); painting=true; action('stroke','',''); action('paint',el.dataset.id,''); }
    });
    document.addEventListener('pointerover', e => { const el=e.target.closest('[data-action="paint"]'); if(painting && el) action('paint',el.dataset.id,''); });
    document.addEventListener('pointerup', () => { if(painting) { painting=false; action('stroke-end','',''); } });
    document.addEventListener('keydown', e => { if((e.ctrlKey||e.metaKey)&&e.key==='s') {e.preventDefault();action('save','','');} });
    window.addEventListener('beforeunload', e => { if(window.designer.dirty) { e.preventDefault(); e.returnValue=''; } });
  },
  error(message) {
    let el=document.getElementById('action-error');
    if(!el) {el=document.createElement('p');el.id='action-error';el.className='modal-error';el.setAttribute('role','alert');(document.querySelector('.dialog') || document.querySelector('.page-body')).prepend(el);}
    el.textContent=message;
  },
  dirty: false
};
