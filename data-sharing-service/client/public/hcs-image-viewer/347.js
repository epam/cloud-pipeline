"use strict";(self.webpackChunkHcsImageViewer=self.webpackChunkHcsImageViewer||[]).push([[347],{9347:function(e,n,t){t.r(n),t.d(n,{default:function(){return w}});var r=t(7737);const s=new Int32Array([0,1,8,16,9,2,3,10,17,24,32,25,18,11,4,5,12,19,26,33,40,48,41,34,27,20,13,6,7,14,21,28,35,42,49,56,57,50,43,36,29,22,15,23,30,37,44,51,58,59,52,45,38,31,39,46,53,60,61,54,47,55,62,63]),o=4017,a=799,c=3406,i=2276,l=1567,f=3784,u=5793,h=2896;function m(e,n){let t=0;const r=[];let s=16;for(;s>0&&!e[s-1];)--s;r.push({children:[],index:0});let o,a=r[0];for(let c=0;c<s;c++){for(let s=0;s<e[c];s++){for(a=r.pop(),a.children[a.index]=n[t];a.index>0;)a=r.pop();for(a.index++,r.push(a);r.length<=c;)r.push(o={children:[],index:0}),a.children[a.index]=o.children,a=o;t++}c+1<s&&(r.push(o={children:[],index:0}),a.children[a.index]=o.children,a=o)}return r[0].children}function b(e,n,t,r,o,a,c,i,l){const{mcusPerLine:f,progressive:u}=t,h=n;let m=n,b=0,d=0;function p(){if(d>0)return d--,b>>d&1;if(b=e[m++],255===b){const n=e[m++];if(n)throw new Error(`unexpected marker: ${(b<<8|n).toString(16)}`)}return d=7,b>>>7}function w(e){let n,t=e;for(;null!==(n=p());){if(t=t[n],"number"==typeof t)return t;if("object"!=typeof t)throw new Error("invalid huffman sequence")}return null}function k(e){let n=e,t=0;for(;n>0;){const e=p();if(null===e)return;t=t<<1|e,--n}return t}function C(e){const n=k(e);return n>=1<<e-1?n:n+(-1<<e)+1}let g,y=0,P=0;function T(e,n,t,r,s){const o=t%f,a=(t/f|0)*e.v+r,c=o*e.h+s;n(e,e.blocks[a][c])}function v(e,n,t){const r=t/e.blocksPerLine|0,s=t%e.blocksPerLine;n(e,e.blocks[r][s])}const x=r.length;let A,L,I,E,D,q;q=u?0===a?0===i?function(e,n){const t=w(e.huffmanTableDC),r=0===t?0:C(t)<<l;e.pred+=r,n[0]=e.pred}:function(e,n){n[0]|=p()<<l}:0===i?function(e,n){if(y>0)return void y--;let t=a;const r=c;for(;t<=r;){const r=w(e.huffmanTableAC),o=15&r,a=r>>4;if(0===o){if(a<15){y=k(a)+(1<<a)-1;break}t+=16}else t+=a,n[s[t]]=C(o)*(1<<l),t++}}:function(e,n){let t=a;const r=c;let o=0;for(;t<=r;){const r=s[t],a=n[r]<0?-1:1;switch(P){case 0:{const n=w(e.huffmanTableAC),t=15&n;if(o=n>>4,0===t)o<15?(y=k(o)+(1<<o),P=4):(o=16,P=1);else{if(1!==t)throw new Error("invalid ACn encoding");g=C(t),P=o?2:3}continue}case 1:case 2:n[r]?n[r]+=(p()<<l)*a:(o--,0===o&&(P=2===P?3:0));break;case 3:n[r]?n[r]+=(p()<<l)*a:(n[r]=g<<l,P=0);break;case 4:n[r]&&(n[r]+=(p()<<l)*a)}t++}4===P&&(y--,0===y&&(P=0))}:function(e,n){const t=w(e.huffmanTableDC),r=0===t?0:C(t);e.pred+=r,n[0]=e.pred;let o=1;for(;o<64;){const t=w(e.huffmanTableAC),r=15&t,a=t>>4;if(0===r){if(a<15)break;o+=16}else o+=a,n[s[o]]=C(r),o++}};let z,O,U=0;O=1===x?r[0].blocksPerLine*r[0].blocksPerColumn:f*t.mcusPerColumn;const M=o||O;for(;U<O;){for(L=0;L<x;L++)r[L].pred=0;if(y=0,1===x)for(A=r[0],D=0;D<M;D++)v(A,q,U),U++;else for(D=0;D<M;D++){for(L=0;L<x;L++){A=r[L];const{h:e,v:n}=A;for(I=0;I<n;I++)for(E=0;E<e;E++)T(A,q,U,I,E)}if(U++,U===O)break}if(d=0,z=e[m]<<8|e[m+1],z<65280)throw new Error("marker was not found");if(!(z>=65488&&z<=65495))break;m+=2}return m-h}function d(e,n){const t=[],{blocksPerLine:r,blocksPerColumn:s}=n,m=r<<3,b=new Int32Array(64),d=new Uint8Array(64);function p(e,t,r){const s=n.quantizationTable;let m,b,d,p,w,k,C,g,y;const P=r;let T;for(T=0;T<64;T++)P[T]=e[T]*s[T];for(T=0;T<8;++T){const e=8*T;0!==P[1+e]||0!==P[2+e]||0!==P[3+e]||0!==P[4+e]||0!==P[5+e]||0!==P[6+e]||0!==P[7+e]?(m=u*P[0+e]+128>>8,b=u*P[4+e]+128>>8,d=P[2+e],p=P[6+e],w=h*(P[1+e]-P[7+e])+128>>8,g=h*(P[1+e]+P[7+e])+128>>8,k=P[3+e]<<4,C=P[5+e]<<4,y=m-b+1>>1,m=m+b+1>>1,b=y,y=d*f+p*l+128>>8,d=d*l-p*f+128>>8,p=y,y=w-C+1>>1,w=w+C+1>>1,C=y,y=g+k+1>>1,k=g-k+1>>1,g=y,y=m-p+1>>1,m=m+p+1>>1,p=y,y=b-d+1>>1,b=b+d+1>>1,d=y,y=w*i+g*c+2048>>12,w=w*c-g*i+2048>>12,g=y,y=k*a+C*o+2048>>12,k=k*o-C*a+2048>>12,C=y,P[0+e]=m+g,P[7+e]=m-g,P[1+e]=b+C,P[6+e]=b-C,P[2+e]=d+k,P[5+e]=d-k,P[3+e]=p+w,P[4+e]=p-w):(y=u*P[0+e]+512>>10,P[0+e]=y,P[1+e]=y,P[2+e]=y,P[3+e]=y,P[4+e]=y,P[5+e]=y,P[6+e]=y,P[7+e]=y)}for(T=0;T<8;++T){const e=T;0!==P[8+e]||0!==P[16+e]||0!==P[24+e]||0!==P[32+e]||0!==P[40+e]||0!==P[48+e]||0!==P[56+e]?(m=u*P[0+e]+2048>>12,b=u*P[32+e]+2048>>12,d=P[16+e],p=P[48+e],w=h*(P[8+e]-P[56+e])+2048>>12,g=h*(P[8+e]+P[56+e])+2048>>12,k=P[24+e],C=P[40+e],y=m-b+1>>1,m=m+b+1>>1,b=y,y=d*f+p*l+2048>>12,d=d*l-p*f+2048>>12,p=y,y=w-C+1>>1,w=w+C+1>>1,C=y,y=g+k+1>>1,k=g-k+1>>1,g=y,y=m-p+1>>1,m=m+p+1>>1,p=y,y=b-d+1>>1,b=b+d+1>>1,d=y,y=w*i+g*c+2048>>12,w=w*c-g*i+2048>>12,g=y,y=k*a+C*o+2048>>12,k=k*o-C*a+2048>>12,C=y,P[0+e]=m+g,P[56+e]=m-g,P[8+e]=b+C,P[48+e]=b-C,P[16+e]=d+k,P[40+e]=d-k,P[24+e]=p+w,P[32+e]=p-w):(y=u*r[T+0]+8192>>14,P[0+e]=y,P[8+e]=y,P[16+e]=y,P[24+e]=y,P[32+e]=y,P[40+e]=y,P[48+e]=y,P[56+e]=y)}for(T=0;T<64;++T){const e=128+(P[T]+8>>4);t[T]=e<0?0:e>255?255:e}}for(let e=0;e<s;e++){const s=e<<3;for(let e=0;e<8;e++)t.push(new Uint8Array(m));for(let o=0;o<r;o++){p(n.blocks[e][o],d,b);let r=0;const a=o<<3;for(let e=0;e<8;e++){const n=t[s+e];for(let e=0;e<8;e++)n[a+e]=d[r++]}}}return t}class p{constructor(){this.jfif=null,this.adobe=null,this.quantizationTables=[],this.huffmanTablesAC=[],this.huffmanTablesDC=[],this.resetFrames()}resetFrames(){this.frames=[]}parse(e){let n=0;function t(){const t=e[n]<<8|e[n+1];return n+=2,t}function r(){const r=t(),s=e.subarray(n,n+r-2);return n+=s.length,s}function o(e){let n,t,r=0,s=0;for(t in e.components)e.components.hasOwnProperty(t)&&(n=e.components[t],r<n.h&&(r=n.h),s<n.v&&(s=n.v));const o=Math.ceil(e.samplesPerLine/8/r),a=Math.ceil(e.scanLines/8/s);for(t in e.components)if(e.components.hasOwnProperty(t)){n=e.components[t];const c=Math.ceil(Math.ceil(e.samplesPerLine/8)*n.h/r),i=Math.ceil(Math.ceil(e.scanLines/8)*n.v/s),l=o*n.h,f=a*n.v,u=[];for(let e=0;e<f;e++){const e=[];for(let n=0;n<l;n++)e.push(new Int32Array(64));u.push(e)}n.blocksPerLine=c,n.blocksPerColumn=i,n.blocks=u}e.maxH=r,e.maxV=s,e.mcusPerLine=o,e.mcusPerColumn=a}let a=t();if(65496!==a)throw new Error("SOI not found");for(a=t();65497!==a;){switch(a){case 65280:break;case 65504:case 65505:case 65506:case 65507:case 65508:case 65509:case 65510:case 65511:case 65512:case 65513:case 65514:case 65515:case 65516:case 65517:case 65518:case 65519:case 65534:{const e=r();65504===a&&74===e[0]&&70===e[1]&&73===e[2]&&70===e[3]&&0===e[4]&&(this.jfif={version:{major:e[5],minor:e[6]},densityUnits:e[7],xDensity:e[8]<<8|e[9],yDensity:e[10]<<8|e[11],thumbWidth:e[12],thumbHeight:e[13],thumbData:e.subarray(14,14+3*e[12]*e[13])}),65518===a&&65===e[0]&&100===e[1]&&111===e[2]&&98===e[3]&&101===e[4]&&0===e[5]&&(this.adobe={version:e[6],flags0:e[7]<<8|e[8],flags1:e[9]<<8|e[10],transformCode:e[11]});break}case 65499:{const r=t()+n-2;for(;n<r;){const r=e[n++],o=new Int32Array(64);if(r>>4==0)for(let t=0;t<64;t++)o[s[t]]=e[n++];else{if(r>>4!=1)throw new Error("DQT: invalid table spec");for(let e=0;e<64;e++)o[s[e]]=t()}this.quantizationTables[15&r]=o}break}case 65472:case 65473:case 65474:{t();const r={extended:65473===a,progressive:65474===a,precision:e[n++],scanLines:t(),samplesPerLine:t(),components:{},componentsOrder:[]},s=e[n++];let c;for(let t=0;t<s;t++){c=e[n];const t=e[n+1]>>4,s=15&e[n+1],o=e[n+2];r.componentsOrder.push(c),r.components[c]={h:t,v:s,quantizationIdx:o},n+=3}o(r),this.frames.push(r);break}case 65476:{const r=t();for(let t=2;t<r;){const r=e[n++],s=new Uint8Array(16);let o=0;for(let t=0;t<16;t++,n++)s[t]=e[n],o+=s[t];const a=new Uint8Array(o);for(let t=0;t<o;t++,n++)a[t]=e[n];t+=17+o,r>>4==0?this.huffmanTablesDC[15&r]=m(s,a):this.huffmanTablesAC[15&r]=m(s,a)}break}case 65501:t(),this.resetInterval=t();break;case 65498:{t();const r=e[n++],s=[],o=this.frames[0];for(let t=0;t<r;t++){const t=o.components[e[n++]],r=e[n++];t.huffmanTableDC=this.huffmanTablesDC[r>>4],t.huffmanTableAC=this.huffmanTablesAC[15&r],s.push(t)}const a=e[n++],c=e[n++],i=e[n++],l=b(e,n,o,s,this.resetInterval,a,c,i>>4,15&i);n+=l;break}case 65535:255!==e[n]&&n--;break;default:if(255===e[n-3]&&e[n-2]>=192&&e[n-2]<=254){n-=3;break}throw new Error(`unknown JPEG marker ${a.toString(16)}`)}a=t()}}getResult(){const{frames:e}=this;if(0===this.frames.length)throw new Error("no frames were decoded");this.frames.length>1&&console.warn("more than one frame is not supported");for(let e=0;e<this.frames.length;e++){const n=this.frames[e].components;for(const e of Object.keys(n))n[e].quantizationTable=this.quantizationTables[n[e].quantizationIdx],delete n[e].quantizationIdx}const n=e[0],{components:t,componentsOrder:r}=n,s=[],o=n.samplesPerLine,a=n.scanLines;for(let e=0;e<r.length;e++){const o=t[r[e]];s.push({lines:d(0,o),scaleX:o.h/n.maxH,scaleY:o.v/n.maxV})}const c=new Uint8Array(o*a*s.length);let i=0;for(let e=0;e<a;++e)for(let n=0;n<o;++n)for(let t=0;t<s.length;++t){const r=s[t];c[i]=r.lines[0|e*r.scaleY][0|n*r.scaleX],++i}return c}}class w extends r.Z{constructor(e){super(),this.reader=new p,e.JPEGTables&&this.reader.parse(e.JPEGTables)}decodeBlock(e){return this.reader.resetFrames(),this.reader.parse(new Uint8Array(e)),this.reader.getResult().buffer}}}}]);