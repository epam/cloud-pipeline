"use strict";(self.webpackChunkHcsImageViewer=self.webpackChunkHcsImageViewer||[]).push([[411],{7411:function(e,t,n){n.r(t),n.d(t,{default:function(){return s}});var r=n(7737);class s extends r.Z{decodeBlock(e){const t=new DataView(e),n=[];for(let r=0;r<e.byteLength;++r){let e=t.getInt8(r);if(e<0){const s=t.getUint8(r+1);e=-e;for(let t=0;t<=e;++t)n.push(s);r+=1}else{for(let s=0;s<=e;++s)n.push(t.getUint8(r+s+1));r+=e+1}}return new Uint8Array(n).buffer}}}}]);