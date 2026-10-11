// Pointer geometry and keyboard input; composition edits are validated in shared Kotlin.
window.designer.showRadial = (html,x,y) => {
  document.getElementById('block-picker')?.remove();
  const point=window.designer.mapPointer || [innerWidth/2,innerHeight/2];
  const picker=document.createElement('section');picker.id='block-picker';picker.className='block-picker';picker.setAttribute('role','dialog');picker.setAttribute('aria-label','Block picker');
  picker.style.left=Math.max(8,Math.min((x||point[0])-180,innerWidth-368))+'px';
  picker.style.top=Math.max(8,Math.min((y||point[1])-180,innerHeight-368))+'px';
  picker.innerHTML=html;document.body.append(picker);
};
window.designer.bindMap = action => {
  let drag=null, suppressClick=false, lastTap=null;
  const cell=(scene,x,y)=>{
    const r=scene.getBoundingClientRect(),snap=Number(scene.dataset.snap),w=Number(scene.dataset.width),h=Number(scene.dataset.height);
    return [Math.min(Math.floor((w-1)/snap)*snap,Math.max(0,Math.floor((x-r.left)/r.width*w/snap)*snap)),Math.min(Math.floor((h-1)/snap)*snap,Math.max(0,Math.floor((y-r.top)/r.height*h/snap)*snap))];
  };
  const preview=()=>{
    const {scene,start,end}=drag,w=Number(scene.dataset.width),h=Number(scene.dataset.height),snap=Number(scene.dataset.snap);
    let overlay=scene.querySelector('.area-selection');if(!overlay){overlay=document.createElement('div');overlay.className='area-selection';scene.append(overlay);}
    const x=Math.min(start[0],end[0]),y=Math.min(start[1],end[1]),width=Math.min(w,Math.max(start[0],end[0])+snap)-x,height=Math.min(h,Math.max(start[1],end[1])+snap)-y;
    Object.assign(overlay.style,{left:x/w*100+'%',top:y/h*100+'%',width:width/w*100+'%',height:height/h*100+'%'});
    const label=document.getElementById('selection-label');if(label)label.textContent=width+' × '+height+' px selected';
  };
  document.addEventListener('pointerdown',e=>{
    const scene=e.target.closest('.scene');if(!scene||e.button!==0||document.getElementById('block-picker'))return;
    scene.focus({preventScroll:true});
    {e.preventDefault();drag={scene,start:cell(scene,e.clientX,e.clientY),end:cell(scene,e.clientX,e.clientY),pointer:e.pointerId,originX:e.clientX,originY:e.clientY,moved:false};scene.setPointerCapture(e.pointerId);preview();}
  });
  document.addEventListener('pointermove',e=>{
    if(e.target.closest('.scene'))window.designer.mapPointer=[e.clientX,e.clientY];
    if(!drag)return;
    if(Math.hypot(e.clientX-drag.originX,e.clientY-drag.originY)>5)drag.moved=true;
    const scroller=drag.scene.closest('.scene-scroll'),bounds=scroller.getBoundingClientRect();
    if(e.clientY>bounds.bottom-20)scroller.scrollTop+=12;else if(e.clientY<bounds.top+20)scroller.scrollTop-=12;
    if(e.clientX>bounds.right-20)scroller.scrollLeft+=12;else if(e.clientX<bounds.left+20)scroller.scrollLeft-=12;
    drag.end=cell(drag.scene,e.clientX,e.clientY);preview();
  });
  document.addEventListener('pointerup',e=>{
    if(!drag)return;const selection=drag;drag=null;suppressClick=true;
    if(selection.scene.hasPointerCapture(e.pointerId))selection.scene.releasePointerCapture(e.pointerId);
    const point=selection.end.join(','),owner=selection.scene.dataset.mapId,now=performance.now();
    const tap=!selection.moved && selection.start.join(',')===point;
    const double=tap && lastTap && lastTap.owner===owner && lastTap.point===point && now-lastTap.time<450;
    lastTap=tap&&!double?{owner,point,time:now}:null;
    action(double?'map-apply':'map-select',double?point:[...selection.start,...selection.end].join(','),'');
    setTimeout(()=>{suppressClick=false;},0);
  });
  document.addEventListener('pointercancel',()=>{drag=null;lastTap=null;suppressClick=false;action('deselect','','');});
  document.addEventListener('click',e=>{
    if(suppressClick){suppressClick=false;e.preventDefault();e.stopImmediatePropagation();return;}
    if(document.getElementById('block-picker')&&!e.target.closest('#block-picker')){document.getElementById('block-picker').remove();e.preventDefault();e.stopImmediatePropagation();}
  },true);
  document.addEventListener('contextmenu',e=>{
    if(!e.target.closest('.scene'))return;e.preventDefault();action('radial-open',e.clientX+','+e.clientY,'');
  });
  document.addEventListener('keydown',e=>{
    const scene=document.querySelector('.scene');if(!scene||e.target.closest('input,textarea,select,[contenteditable],.overlay,#reference-picker')||e.altKey||e.repeat)return;
    if(e.ctrlKey||e.metaKey){
      const operation={c:'map-copy',x:'map-cut',v:'map-paste',z:'undo'}[e.key.toLowerCase()];
      if(operation&&!e.shiftKey){e.preventDefault();action(operation,'','');}return;
    }
    if(/^[a-z0-9]$/i.test(e.key)){e.preventDefault();action('map-key',e.key.toLowerCase(),e.shiftKey?'assign':'');return;}
    const key=e.key.toLowerCase();
    if(key===' ' && e.target.closest('button') && !e.target.closest('.scene'))return;
    if(key===' '){e.preventDefault();if(document.getElementById('block-picker'))action('radial-close','','');else action('radial-open',(window.designer.mapPointer||[]).join(','),'');}
    else if(key==='escape'){e.preventDefault();if(document.getElementById('block-picker'))action('radial-close','','');else action('deselect','','');}
    else if(key==='delete'||key==='backspace'){e.preventDefault();action('clear-area','','');}
  });
};
