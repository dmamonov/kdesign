window.designer.bindTileSelection = action => {
  let drag=null;
  const point=(grid,e)=>{
    const r=grid.getBoundingClientRect(),n=Number(grid.dataset.tileSize);
    return [Math.max(0,Math.min(n-1,Math.floor((e.clientX-r.left)*n/r.width))),
      Math.max(0,Math.min(n-1,Math.floor((e.clientY-r.top)*n/r.height)))];
  };
  const preview=e=>{
    const d=drag,n=d.n,p=point(d.grid,e);
    if(d.moving){
      d.area=[Math.max(0,Math.min(n-d.source[2],d.source[0]+p[0]-d.start[0])),
        Math.max(0,Math.min(n-d.source[3],d.source[1]+p[1]-d.start[1])),d.source[2],d.source[3]];
      const colors=d.colors.slice(),[sx,sy,w,h]=d.source,[x,y]=d.area;
      for(let dy=0;dy<h;dy++)for(let dx=0;dx<w;dx++)colors[(sy+dy)*n+sx+dx]='transparent';
      for(let dy=0;dy<h;dy++)for(let dx=0;dx<w;dx++)colors[(y+dy)*n+x+dx]=d.colors[(sy+dy)*n+sx+dx];
      d.buttons.forEach((b,i)=>b.style.backgroundColor=colors[i]);
    }else d.area=[Math.min(p[0],d.start[0]),Math.min(p[1],d.start[1]),Math.abs(p[0]-d.start[0])+1,Math.abs(p[1]-d.start[1])+1];
    let overlay=d.grid.querySelector('.tile-selection');
    if(!overlay){overlay=document.createElement('div');overlay.className='tile-selection';d.grid.append(overlay);}
    const [x,y,w,h]=d.area;
    Object.assign(overlay.style,{left:x*100/n+'%',top:y*100/n+'%',width:w*100/n+'%',height:h*100/n+'%'});
  };
  document.addEventListener('pointerdown',e=>{
    const grid=e.target.closest('.pixel-grid');
    if(!grid||grid.dataset.tileSelect!=='true'||e.button!==0)return;
    e.preventDefault();grid.focus({preventScroll:true});
    const start=point(grid,e),source=grid.dataset.tileArea?grid.dataset.tileArea.split(',').map(Number):null;
    const moving=source&&!e.shiftKey&&start[0]>=source[0]&&start[1]>=source[1]&&start[0]<source[0]+source[2]&&start[1]<source[1]+source[3];
    const buttons=[...grid.querySelectorAll('[data-action="paint"]')];
    drag={grid,start,source,moving,n:Number(grid.dataset.tileSize),buttons,colors:buttons.map(b=>b.style.backgroundColor),pointer:e.pointerId};
    grid.setPointerCapture(e.pointerId);preview(e);
  });
  document.addEventListener('pointermove',e=>{if(drag&&e.pointerId===drag.pointer)preview(e);});
  document.addEventListener('pointerup',e=>{
    if(!drag||e.pointerId!==drag.pointer)return;
    preview(e);const d=drag;drag=null;
    if(d.grid.hasPointerCapture(e.pointerId))d.grid.releasePointerCapture(e.pointerId);
    action(d.moving?'tile-move-area':'tile-select-area',(d.moving?d.area.slice(0,2):d.area).join(','),'');
  });
  const cancel=()=>{if(drag){drag=null;action('tile-select-tool','','');}};
  document.addEventListener('pointercancel',cancel);window.addEventListener('blur',cancel);
  document.addEventListener('keydown',e=>{
    if(!e.target.closest('.pixel-grid[data-tile-select="true"]'))return;
    if(e.key==='Escape'){e.preventDefault();drag=null;action('tile-deselect','','');}
    if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='z')drag=null;
  });
};
