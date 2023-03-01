/*! For license information please see 173.js.LICENSE.txt */
(self.webpackChunkHcsImageViewer=self.webpackChunkHcsImageViewer||[]).push([[173],{7197:function(e,t){var r,i,a,n,o,s,l,f,u,c,d,h,m;i={defaultNoDataValue:-34027999387901484e22,decode:function(e,t){var r=(t=t||{}).encodedMaskData||null===t.encodedMaskData,l=s(e,t.inputOffset||0,r),f=null!==t.noDataValue?t.noDataValue:i.defaultNoDataValue,u=a(l,t.pixelType||Float32Array,t.encodedMaskData,f,t.returnMask),c={width:l.width,height:l.height,pixelData:u.resultPixels,minValue:u.minValue,maxValue:l.pixels.maxValue,noDataValue:f};return u.resultMask&&(c.maskData=u.resultMask),t.returnEncodedMask&&l.mask&&(c.encodedMaskData=l.mask.bitset?l.mask.bitset:null),t.returnFileInfo&&(c.fileInfo=n(l),t.computeUsedBitDepths&&(c.fileInfo.bitDepths=o(l))),c}},a=function(e,t,r,i,a){var n,o,s,f=0,u=e.pixels.numBlocksX,c=e.pixels.numBlocksY,d=Math.floor(e.width/u),h=Math.floor(e.height/c),m=2*e.maxZError,p=Number.MAX_VALUE;r=r||(e.mask?e.mask.bitset:null),o=new t(e.width*e.height),a&&r&&(s=new Uint8Array(e.width*e.height));for(var g,y,x=new Float32Array(d*h),w=0;w<=c;w++){var k=w!==c?h:e.height%c;if(0!==k)for(var I=0;I<=u;I++){var b=I!==u?d:e.width%u;if(0!==b){var P,U,v,D,M=w*e.width*h+I*d,V=e.width-b,A=e.pixels.blocks[f];if(A.encoding<2?(0===A.encoding?P=A.rawData:(l(A.stuffedData,A.bitsPerPixel,A.numValidPixels,A.offset,m,x,e.pixels.maxValue),P=x),U=0):v=2===A.encoding?0:A.offset,r)for(y=0;y<k;y++){for(7&M&&(D=r[M>>3],D<<=7&M),g=0;g<b;g++)7&M||(D=r[M>>3]),128&D?(s&&(s[M]=1),p=p>(n=A.encoding<2?P[U++]:v)?n:p,o[M++]=n):(s&&(s[M]=0),o[M++]=i),D<<=1;M+=V}else if(A.encoding<2)for(y=0;y<k;y++){for(g=0;g<b;g++)p=p>(n=P[U++])?n:p,o[M++]=n;M+=V}else for(p=p>v?v:p,y=0;y<k;y++){for(g=0;g<b;g++)o[M++]=v;M+=V}if(1===A.encoding&&U!==A.numValidPixels)throw"Block and Mask do not match";f++}}}return{resultPixels:o,resultMask:s,minValue:p}},n=function(e){return{fileIdentifierString:e.fileIdentifierString,fileVersion:e.fileVersion,imageType:e.imageType,height:e.height,width:e.width,maxZError:e.maxZError,eofOffset:e.eofOffset,mask:e.mask?{numBlocksX:e.mask.numBlocksX,numBlocksY:e.mask.numBlocksY,numBytes:e.mask.numBytes,maxValue:e.mask.maxValue}:null,pixels:{numBlocksX:e.pixels.numBlocksX,numBlocksY:e.pixels.numBlocksY,numBytes:e.pixels.numBytes,maxValue:e.pixels.maxValue,noDataValue:e.noDataValue}}},o=function(e){for(var t=e.pixels.numBlocksX*e.pixels.numBlocksY,r={},i=0;i<t;i++){var a=e.pixels.blocks[i];0===a.encoding?r.float32=!0:1===a.encoding?r[a.bitsPerPixel]=!0:r[0]=!0}return Object.keys(r)},s=function(e,t,r){var i={},a=new Uint8Array(e,t,10);if(i.fileIdentifierString=String.fromCharCode.apply(null,a),"CntZImage"!==i.fileIdentifierString.trim())throw"Unexpected file identifier string: "+i.fileIdentifierString;t+=10;var n=new DataView(e,t,24);if(i.fileVersion=n.getInt32(0,!0),i.imageType=n.getInt32(4,!0),i.height=n.getUint32(8,!0),i.width=n.getUint32(12,!0),i.maxZError=n.getFloat64(16,!0),t+=24,!r)if(n=new DataView(e,t,16),i.mask={},i.mask.numBlocksY=n.getUint32(0,!0),i.mask.numBlocksX=n.getUint32(4,!0),i.mask.numBytes=n.getUint32(8,!0),i.mask.maxValue=n.getFloat32(12,!0),t+=16,i.mask.numBytes>0){var o=new Uint8Array(Math.ceil(i.width*i.height/8)),s=(n=new DataView(e,t,i.mask.numBytes)).getInt16(0,!0),l=2,f=0;do{if(s>0)for(;s--;)o[f++]=n.getUint8(l++);else{var u=n.getUint8(l++);for(s=-s;s--;)o[f++]=u}s=n.getInt16(l,!0),l+=2}while(l<i.mask.numBytes);if(-32768!==s||f<o.length)throw"Unexpected end of mask RLE encoding";i.mask.bitset=o,t+=i.mask.numBytes}else 0==(i.mask.numBytes|i.mask.numBlocksY|i.mask.maxValue)&&(i.mask.bitset=new Uint8Array(Math.ceil(i.width*i.height/8)));n=new DataView(e,t,16),i.pixels={},i.pixels.numBlocksY=n.getUint32(0,!0),i.pixels.numBlocksX=n.getUint32(4,!0),i.pixels.numBytes=n.getUint32(8,!0),i.pixels.maxValue=n.getFloat32(12,!0),t+=16;var c=i.pixels.numBlocksX,d=i.pixels.numBlocksY,h=c+(i.width%c>0?1:0),m=d+(i.height%d>0?1:0);i.pixels.blocks=new Array(h*m);for(var p=0,g=0;g<m;g++)for(var y=0;y<h;y++){var x=0,w=e.byteLength-t;n=new DataView(e,t,Math.min(10,w));var k={};i.pixels.blocks[p++]=k;var I=n.getUint8(0);if(x++,k.encoding=63&I,k.encoding>3)throw"Invalid block encoding ("+k.encoding+")";if(2!==k.encoding){if(0!==I&&2!==I){if(I>>=6,k.offsetType=I,2===I)k.offset=n.getInt8(1),x++;else if(1===I)k.offset=n.getInt16(1,!0),x+=2;else{if(0!==I)throw"Invalid block offset type";k.offset=n.getFloat32(1,!0),x+=4}if(1===k.encoding)if(I=n.getUint8(x),x++,k.bitsPerPixel=63&I,I>>=6,k.numValidPixelsType=I,2===I)k.numValidPixels=n.getUint8(x),x++;else if(1===I)k.numValidPixels=n.getUint16(x,!0),x+=2;else{if(0!==I)throw"Invalid valid pixel count type";k.numValidPixels=n.getUint32(x,!0),x+=4}}var b;if(t+=x,3!==k.encoding)if(0===k.encoding){var P=(i.pixels.numBytes-1)/4;if(P!==Math.floor(P))throw"uncompressed block has invalid length";b=new ArrayBuffer(4*P),new Uint8Array(b).set(new Uint8Array(e,t,4*P));var U=new Float32Array(b);k.rawData=U,t+=4*P}else if(1===k.encoding){var v=Math.ceil(k.numValidPixels*k.bitsPerPixel/8),D=Math.ceil(v/4);b=new ArrayBuffer(4*D),new Uint8Array(b).set(new Uint8Array(e,t,v)),k.stuffedData=new Uint32Array(b),t+=v}}else t++}return i.eofOffset=t,i},l=function(e,t,r,i,a,n,o){var s,l,f,u=(1<<t)-1,c=0,d=0,h=Math.ceil((o-i)/a),m=4*e.length-Math.ceil(t*r/8);for(e[e.length-1]<<=8*m,s=0;s<r;s++){if(0===d&&(f=e[c++],d=32),d>=t)l=f>>>d-t&u,d-=t;else{var p=t-d;l=(f&u)<<p&u,l+=(f=e[c++])>>>(d=32-p)}n[s]=l<h?i+l*a:o}return n},c=i,d=function(){"use strict";var e=function(e,t,r,i,a,n,o,s){var l,f,u,c,d,h=(1<<r)-1,m=0,p=0,g=4*e.length-Math.ceil(r*i/8);if(e[e.length-1]<<=8*g,a)for(l=0;l<i;l++)0===p&&(u=e[m++],p=32),p>=r?(f=u>>>p-r&h,p-=r):(f=(u&h)<<(c=r-p)&h,f+=(u=e[m++])>>>(p=32-c)),t[l]=a[f];else for(d=Math.ceil((s-n)/o),l=0;l<i;l++)0===p&&(u=e[m++],p=32),p>=r?(f=u>>>p-r&h,p-=r):(f=(u&h)<<(c=r-p)&h,f+=(u=e[m++])>>>(p=32-c)),t[l]=f<d?n+f*o:s},t=function(e,t,r,i,a,n,o,s){var l,f,u,c,d=(1<<r)-1,h=0,m=0,p=0;if(a)for(l=0;l<i;l++)0===m&&(u=e[h++],m=32,p=0),m>=r?(f=u>>>p&d,m-=r,p+=r):(f=u>>>p&d,m=32-(c=r-m),f|=((u=e[h++])&(1<<c)-1)<<r-c,p=c),t[l]=a[f];else{var g=Math.ceil((s-n)/o);for(l=0;l<i;l++)0===m&&(u=e[h++],m=32,p=0),m>=r?(f=u>>>p&d,m-=r,p+=r):(f=u>>>p&d,m=32-(c=r-m),f|=((u=e[h++])&(1<<c)-1)<<r-c,p=c),t[l]=f<g?n+f*o:s}return t},r={HUFFMAN_LUT_BITS_MAX:12,computeChecksumFletcher32:function(e){for(var t=65535,r=65535,i=e.length,a=Math.floor(i/2),n=0;a;){var o=a>=359?359:a;a-=o;do{t+=e[n++]<<8,r+=t+=e[n++]}while(--o);t=(65535&t)+(t>>>16),r=(65535&r)+(r>>>16)}return 1&i&&(r+=t+=e[n]<<8),((r=(65535&r)+(r>>>16))<<16|(t=(65535&t)+(t>>>16)))>>>0},readHeaderInfo:function(e,t){var r=t.ptr,i=new Uint8Array(e,r,6),a={};if(a.fileIdentifierString=String.fromCharCode.apply(null,i),0!==a.fileIdentifierString.lastIndexOf("Lerc2",0))throw"Unexpected file identifier string (expect Lerc2 ): "+a.fileIdentifierString;r+=6;var n,o=new DataView(e,r,8),s=o.getInt32(0,!0);if(a.fileVersion=s,r+=4,s>=3&&(a.checksum=o.getUint32(4,!0),r+=4),o=new DataView(e,r,12),a.height=o.getUint32(0,!0),a.width=o.getUint32(4,!0),r+=8,s>=4?(a.numDims=o.getUint32(8,!0),r+=4):a.numDims=1,o=new DataView(e,r,40),a.numValidPixel=o.getUint32(0,!0),a.microBlockSize=o.getInt32(4,!0),a.blobSize=o.getInt32(8,!0),a.imageType=o.getInt32(12,!0),a.maxZError=o.getFloat64(16,!0),a.zMin=o.getFloat64(24,!0),a.zMax=o.getFloat64(32,!0),r+=40,t.headerInfo=a,t.ptr=r,s>=3&&(n=s>=4?52:48,this.computeChecksumFletcher32(new Uint8Array(e,r-n,a.blobSize-14))!==a.checksum))throw"Checksum failed.";return!0},checkMinMaxRanges:function(e,t){var r=t.headerInfo,i=this.getDataTypeArray(r.imageType),a=r.numDims*this.getDataTypeSize(r.imageType),n=this.readSubArray(e,t.ptr,i,a),o=this.readSubArray(e,t.ptr+a,i,a);t.ptr+=2*a;var s,l=!0;for(s=0;s<r.numDims;s++)if(n[s]!==o[s]){l=!1;break}return r.minValues=n,r.maxValues=o,l},readSubArray:function(e,t,r,i){var a;if(r===Uint8Array)a=new Uint8Array(e,t,i);else{var n=new ArrayBuffer(i);new Uint8Array(n).set(new Uint8Array(e,t,i)),a=new r(n)}return a},readMask:function(e,t){var r,i,a=t.ptr,n=t.headerInfo,o=n.width*n.height,s=n.numValidPixel,l=new DataView(e,a,4),f={};if(f.numBytes=l.getUint32(0,!0),a+=4,(0===s||o===s)&&0!==f.numBytes)throw"invalid mask";if(0===s)r=new Uint8Array(Math.ceil(o/8)),f.bitset=r,i=new Uint8Array(o),t.pixels.resultMask=i,a+=f.numBytes;else if(f.numBytes>0){r=new Uint8Array(Math.ceil(o/8));var u=(l=new DataView(e,a,f.numBytes)).getInt16(0,!0),c=2,d=0,h=0;do{if(u>0)for(;u--;)r[d++]=l.getUint8(c++);else for(h=l.getUint8(c++),u=-u;u--;)r[d++]=h;u=l.getInt16(c,!0),c+=2}while(c<f.numBytes);if(-32768!==u||d<r.length)throw"Unexpected end of mask RLE encoding";i=new Uint8Array(o);var m=0,p=0;for(p=0;p<o;p++)7&p?(m=r[p>>3],m<<=7&p):m=r[p>>3],128&m&&(i[p]=1);t.pixels.resultMask=i,f.bitset=r,a+=f.numBytes}return t.ptr=a,t.mask=f,!0},readDataOneSweep:function(e,t,i,a){var n,o=t.ptr,s=t.headerInfo,l=s.numDims,f=s.width*s.height,u=s.imageType,c=s.numValidPixel*r.getDataTypeSize(u)*l,d=t.pixels.resultMask;if(i===Uint8Array)n=new Uint8Array(e,o,c);else{var h=new ArrayBuffer(c);new Uint8Array(h).set(new Uint8Array(e,o,c)),n=new i(h)}if(n.length===f*l)t.pixels.resultPixels=a?r.swapDimensionOrder(n,f,l,i,!0):n;else{t.pixels.resultPixels=new i(f*l);var m=0,p=0,g=0,y=0;if(l>1){if(a){for(p=0;p<f;p++)if(d[p])for(y=p,g=0;g<l;g++,y+=f)t.pixels.resultPixels[y]=n[m++]}else for(p=0;p<f;p++)if(d[p])for(y=p*l,g=0;g<l;g++)t.pixels.resultPixels[y+g]=n[m++]}else for(p=0;p<f;p++)d[p]&&(t.pixels.resultPixels[p]=n[m++])}return o+=c,t.ptr=o,!0},readHuffmanTree:function(e,t){var a=this.HUFFMAN_LUT_BITS_MAX,n=new DataView(e,t.ptr,16);if(t.ptr+=16,n.getInt32(0,!0)<2)throw"unsupported Huffman version";var o=n.getInt32(4,!0),s=n.getInt32(8,!0),l=n.getInt32(12,!0);if(s>=l)return!1;var f=new Uint32Array(l-s);r.decodeBits(e,t,f);var u,c,d,h,m=[];for(u=s;u<l;u++)m[c=u-(u<o?0:o)]={first:f[u-s],second:null};var p=e.byteLength-t.ptr,g=Math.ceil(p/4),y=new ArrayBuffer(4*g);new Uint8Array(y).set(new Uint8Array(e,t.ptr,p));var x,w=new Uint32Array(y),k=0,I=0;for(x=w[0],u=s;u<l;u++)(h=m[c=u-(u<o?0:o)].first)>0&&(m[c].second=x<<k>>>32-h,32-k>=h?32===(k+=h)&&(k=0,x=w[++I]):(k+=h-32,x=w[++I],m[c].second|=x>>>32-k));var b,P=0,U=new i;for(u=0;u<m.length;u++)void 0!==m[u]&&(P=Math.max(P,m[u].first));b=P>=a?a:P;var v,D,M,V,A,S=[];for(u=s;u<l;u++)if((h=m[c=u-(u<o?0:o)].first)>0)if(v=[h,c],h<=b)for(D=m[c].second<<b-h,M=1<<b-h,d=0;d<M;d++)S[D|d]=v;else for(D=m[c].second,A=U,V=h-1;V>=0;V--)D>>>V&1?(A.right||(A.right=new i),A=A.right):(A.left||(A.left=new i),A=A.left),0!==V||A.val||(A.val=v[1]);return{decodeLut:S,numBitsLUTQick:b,numBitsLUT:P,tree:U,stuffedData:w,srcPtr:I,bitPos:k}},readHuffman:function(e,t,i,a){var n,o,s,l,f,u,c,d,h,m=t.headerInfo.numDims,p=t.headerInfo.height,g=t.headerInfo.width,y=g*p,x=this.readHuffmanTree(e,t),w=x.decodeLut,k=x.tree,I=x.stuffedData,b=x.srcPtr,P=x.bitPos,U=x.numBitsLUTQick,v=x.numBitsLUT,D=0===t.headerInfo.imageType?128:0,M=t.pixels.resultMask,V=0;P>0&&(b++,P=0);var A,S=I[b],T=1===t.encodeMode,B=new i(y*m),C=B;if(m<2||T){for(A=0;A<m;A++)if(m>1&&(C=new i(B.buffer,y*A,y),V=0),t.headerInfo.numValidPixel===g*p)for(d=0,u=0;u<p;u++)for(c=0;c<g;c++,d++){if(o=0,f=l=S<<P>>>32-U,32-P<U&&(f=l|=I[b+1]>>>64-P-U),w[f])o=w[f][1],P+=w[f][0];else for(f=l=S<<P>>>32-v,32-P<v&&(f=l|=I[b+1]>>>64-P-v),n=k,h=0;h<v;h++)if(!(n=l>>>v-h-1&1?n.right:n.left).left&&!n.right){o=n.val,P=P+h+1;break}P>=32&&(P-=32,S=I[++b]),s=o-D,T?(s+=c>0?V:u>0?C[d-g]:V,s&=255,C[d]=s,V=s):C[d]=s}else for(d=0,u=0;u<p;u++)for(c=0;c<g;c++,d++)if(M[d]){if(o=0,f=l=S<<P>>>32-U,32-P<U&&(f=l|=I[b+1]>>>64-P-U),w[f])o=w[f][1],P+=w[f][0];else for(f=l=S<<P>>>32-v,32-P<v&&(f=l|=I[b+1]>>>64-P-v),n=k,h=0;h<v;h++)if(!(n=l>>>v-h-1&1?n.right:n.left).left&&!n.right){o=n.val,P=P+h+1;break}P>=32&&(P-=32,S=I[++b]),s=o-D,T?(c>0&&M[d-1]?s+=V:u>0&&M[d-g]?s+=C[d-g]:s+=V,s&=255,C[d]=s,V=s):C[d]=s}}else for(d=0,u=0;u<p;u++)for(c=0;c<g;c++)if(d=u*g+c,!M||M[d])for(A=0;A<m;A++,d+=y){if(o=0,f=l=S<<P>>>32-U,32-P<U&&(f=l|=I[b+1]>>>64-P-U),w[f])o=w[f][1],P+=w[f][0];else for(f=l=S<<P>>>32-v,32-P<v&&(f=l|=I[b+1]>>>64-P-v),n=k,h=0;h<v;h++)if(!(n=l>>>v-h-1&1?n.right:n.left).left&&!n.right){o=n.val,P=P+h+1;break}P>=32&&(P-=32,S=I[++b]),s=o-D,C[d]=s}t.ptr=t.ptr+4*(b+1)+(P>0?4:0),t.pixels.resultPixels=B,m>1&&!a&&(t.pixels.resultPixels=r.swapDimensionOrder(B,y,m,i))},decodeBits:function(r,i,a,n,o){var s=i.headerInfo,l=s.fileVersion,f=0,u=r.byteLength-i.ptr>=5?5:r.byteLength-i.ptr,c=new DataView(r,i.ptr,u),d=c.getUint8(0);f++;var h=d>>6,m=0===h?4:3-h,p=(32&d)>0,g=31&d,y=0;if(1===m)y=c.getUint8(f),f++;else if(2===m)y=c.getUint16(f,!0),f+=2;else{if(4!==m)throw"Invalid valid pixel count type";y=c.getUint32(f,!0),f+=4}var x,w,k,I,b,P,U,v,D,M=2*s.maxZError,V=s.numDims>1?s.maxValues[o]:s.zMax;if(p){for(i.counter.lut++,v=c.getUint8(f),f++,I=Math.ceil((v-1)*g/8),b=Math.ceil(I/4),w=new ArrayBuffer(4*b),k=new Uint8Array(w),i.ptr+=f,k.set(new Uint8Array(r,i.ptr,I)),U=new Uint32Array(w),i.ptr+=I,D=0;v-1>>>D;)D++;I=Math.ceil(y*D/8),b=Math.ceil(I/4),w=new ArrayBuffer(4*b),(k=new Uint8Array(w)).set(new Uint8Array(r,i.ptr,I)),x=new Uint32Array(w),i.ptr+=I,P=l>=3?function(e,t,r,i,a,n){var o,s=(1<<t)-1,l=0,f=0,u=0,c=0,d=0,h=0,m=[],p=Math.ceil((n-i)/a);for(f=0;f<r;f++)0===c&&(o=e[l++],c=32,h=0),c>=t?(d=o>>>h&s,c-=t,h+=t):(d=o>>>h&s,c=32-(u=t-c),d|=((o=e[l++])&(1<<u)-1)<<t-u,h=u),m[f]=d<p?i+d*a:n;return m.unshift(i),m}(U,g,v-1,n,M,V):function(e,t,r,i,a,n){var o,s=(1<<t)-1,l=0,f=0,u=0,c=0,d=0,h=[],m=4*e.length-Math.ceil(t*r/8);e[e.length-1]<<=8*m;var p=Math.ceil((n-i)/a);for(f=0;f<r;f++)0===c&&(o=e[l++],c=32),c>=t?(d=o>>>c-t&s,c-=t):(d=(o&s)<<(u=t-c)&s,d+=(o=e[l++])>>>(c=32-u)),h[f]=d<p?i+d*a:n;return h.unshift(i),h}(U,g,v-1,n,M,V),l>=3?t(x,a,D,y,P):e(x,a,D,y,P)}else i.counter.bitstuffer++,D=g,i.ptr+=f,D>0&&(I=Math.ceil(y*D/8),b=Math.ceil(I/4),w=new ArrayBuffer(4*b),(k=new Uint8Array(w)).set(new Uint8Array(r,i.ptr,I)),x=new Uint32Array(w),i.ptr+=I,l>=3?null==n?function(e,t,r,i){var a,n,o,s,l=(1<<r)-1,f=0,u=0,c=0;for(a=0;a<i;a++)0===u&&(o=e[f++],u=32,c=0),u>=r?(n=o>>>c&l,u-=r,c+=r):(n=o>>>c&l,u=32-(s=r-u),n|=((o=e[f++])&(1<<s)-1)<<r-s,c=s),t[a]=n}(x,a,D,y):t(x,a,D,y,!1,n,M,V):null==n?function(e,t,r,i){var a,n,o,s,l=(1<<r)-1,f=0,u=0,c=4*e.length-Math.ceil(r*i/8);for(e[e.length-1]<<=8*c,a=0;a<i;a++)0===u&&(o=e[f++],u=32),u>=r?(n=o>>>u-r&l,u-=r):(n=(o&l)<<(s=r-u)&l,n+=(o=e[f++])>>>(u=32-s)),t[a]=n}(x,a,D,y):e(x,a,D,y,!1,n,M,V))},readTiles:function(e,t,i,a){var n=t.headerInfo,o=n.width,s=n.height,l=o*s,f=n.microBlockSize,u=n.imageType,c=r.getDataTypeSize(u),d=Math.ceil(o/f),h=Math.ceil(s/f);t.pixels.numBlocksY=h,t.pixels.numBlocksX=d,t.pixels.ptr=0;var m,p,g,y,x,w,k,I,b,P,U=0,v=0,D=0,M=0,V=0,A=0,S=0,T=0,B=0,C=0,G=0,L=0,O=0,F=0,K=0,z=new i(f*f),E=s%f||f,N=o%f||f,j=n.numDims,Y=t.pixels.resultMask,R=t.pixels.resultPixels,X=n.fileVersion>=5?14:15,H=n.zMax;for(D=0;D<h;D++)for(V=D!==h-1?f:E,M=0;M<d;M++)for(C=D*o*f+M*f,G=o-(A=M!==d-1?f:N),I=0;I<j;I++){if(j>1?(P=R,C=D*o*f+M*f,R=new i(t.pixels.resultPixels.buffer,l*I*c,l),H=n.maxValues[I]):P=null,S=e.byteLength-t.ptr,p={},K=0,T=(m=new DataView(e,t.ptr,Math.min(10,S))).getUint8(0),K++,b=n.fileVersion>=5?4&T:0,B=T>>6&255,(T>>2&X)!=(M*f>>3&X))throw"integrity issue";if(b&&0===I)throw"integrity issue";if((x=3&T)>3)throw t.ptr+=K,"Invalid block encoding ("+x+")";if(2!==x)if(0===x){if(b)throw"integrity issue";if(t.counter.uncompressed++,t.ptr+=K,L=(L=V*A*c)<(O=e.byteLength-t.ptr)?L:O,g=new ArrayBuffer(L%c==0?L:L+c-L%c),new Uint8Array(g).set(new Uint8Array(e,t.ptr,L)),y=new i(g),F=0,Y)for(U=0;U<V;U++){for(v=0;v<A;v++)Y[C]&&(R[C]=y[F++]),C++;C+=G}else for(U=0;U<V;U++){for(v=0;v<A;v++)R[C++]=y[F++];C+=G}t.ptr+=F*c}else if(w=r.getDataTypeUsed(b&&u<6?4:u,B),k=r.getOnePixel(p,K,w,m),K+=r.getDataTypeSize(w),3===x)if(t.ptr+=K,t.counter.constantoffset++,Y)for(U=0;U<V;U++){for(v=0;v<A;v++)Y[C]&&(R[C]=b?Math.min(H,P[C]+k):k),C++;C+=G}else for(U=0;U<V;U++){for(v=0;v<A;v++)R[C]=b?Math.min(H,P[C]+k):k,C++;C+=G}else if(t.ptr+=K,r.decodeBits(e,t,z,k,I),K=0,b)if(Y)for(U=0;U<V;U++){for(v=0;v<A;v++)Y[C]&&(R[C]=z[K++]+P[C]),C++;C+=G}else for(U=0;U<V;U++){for(v=0;v<A;v++)R[C]=z[K++]+P[C],C++;C+=G}else if(Y)for(U=0;U<V;U++){for(v=0;v<A;v++)Y[C]&&(R[C]=z[K++]),C++;C+=G}else for(U=0;U<V;U++){for(v=0;v<A;v++)R[C++]=z[K++];C+=G}else{if(b)if(Y)for(U=0;U<V;U++)for(v=0;v<A;v++)Y[C]&&(R[C]=P[C]),C++;else for(U=0;U<V;U++)for(v=0;v<A;v++)R[C]=P[C],C++;t.counter.constant++,t.ptr+=K}}j>1&&!a&&(t.pixels.resultPixels=r.swapDimensionOrder(t.pixels.resultPixels,l,j,i))},formatFileInfo:function(e){return{fileIdentifierString:e.headerInfo.fileIdentifierString,fileVersion:e.headerInfo.fileVersion,imageType:e.headerInfo.imageType,height:e.headerInfo.height,width:e.headerInfo.width,numValidPixel:e.headerInfo.numValidPixel,microBlockSize:e.headerInfo.microBlockSize,blobSize:e.headerInfo.blobSize,maxZError:e.headerInfo.maxZError,pixelType:r.getPixelType(e.headerInfo.imageType),eofOffset:e.eofOffset,mask:e.mask?{numBytes:e.mask.numBytes}:null,pixels:{numBlocksX:e.pixels.numBlocksX,numBlocksY:e.pixels.numBlocksY,maxValue:e.headerInfo.zMax,minValue:e.headerInfo.zMin,noDataValue:e.noDataValue}}},constructConstantSurface:function(e,t){var r=e.headerInfo.zMax,i=e.headerInfo.zMin,a=e.headerInfo.maxValues,n=e.headerInfo.numDims,o=e.headerInfo.height*e.headerInfo.width,s=0,l=0,f=0,u=e.pixels.resultMask,c=e.pixels.resultPixels;if(u)if(n>1){if(t)for(s=0;s<n;s++)for(f=s*o,r=a[s],l=0;l<o;l++)u[l]&&(c[f+l]=r);else for(l=0;l<o;l++)if(u[l])for(f=l*n,s=0;s<n;s++)c[f+n]=a[s]}else for(l=0;l<o;l++)u[l]&&(c[l]=r);else if(n>1&&i!==r)if(t)for(s=0;s<n;s++)for(f=s*o,r=a[s],l=0;l<o;l++)c[f+l]=r;else for(l=0;l<o;l++)for(f=l*n,s=0;s<n;s++)c[f+s]=a[s];else for(l=0;l<o*n;l++)c[l]=r},getDataTypeArray:function(e){var t;switch(e){case 0:t=Int8Array;break;case 1:t=Uint8Array;break;case 2:t=Int16Array;break;case 3:t=Uint16Array;break;case 4:t=Int32Array;break;case 5:t=Uint32Array;break;case 6:default:t=Float32Array;break;case 7:t=Float64Array}return t},getPixelType:function(e){var t;switch(e){case 0:t="S8";break;case 1:t="U8";break;case 2:t="S16";break;case 3:t="U16";break;case 4:t="S32";break;case 5:t="U32";break;case 6:default:t="F32";break;case 7:t="F64"}return t},isValidPixelValue:function(e,t){if(null==t)return!1;var r;switch(e){case 0:r=t>=-128&&t<=127;break;case 1:r=t>=0&&t<=255;break;case 2:r=t>=-32768&&t<=32767;break;case 3:r=t>=0&&t<=65536;break;case 4:r=t>=-2147483648&&t<=2147483647;break;case 5:r=t>=0&&t<=4294967296;break;case 6:r=t>=-34027999387901484e22&&t<=34027999387901484e22;break;case 7:r=t>=-17976931348623157e292&&t<=17976931348623157e292;break;default:r=!1}return r},getDataTypeSize:function(e){var t=0;switch(e){case 0:case 1:t=1;break;case 2:case 3:t=2;break;case 4:case 5:case 6:t=4;break;case 7:t=8;break;default:t=e}return t},getDataTypeUsed:function(e,t){var r=e;switch(e){case 2:case 4:r=e-t;break;case 3:case 5:r=e-2*t;break;case 6:r=0===t?e:1===t?2:1;break;case 7:r=0===t?e:e-2*t+1;break;default:r=e}return r},getOnePixel:function(e,t,r,i){var a=0;switch(r){case 0:a=i.getInt8(t);break;case 1:a=i.getUint8(t);break;case 2:a=i.getInt16(t,!0);break;case 3:a=i.getUint16(t,!0);break;case 4:a=i.getInt32(t,!0);break;case 5:a=i.getUInt32(t,!0);break;case 6:a=i.getFloat32(t,!0);break;case 7:a=i.getFloat64(t,!0);break;default:throw"the decoder does not understand this pixel type"}return a},swapDimensionOrder:function(e,t,r,i,a){var n=0,o=0,s=0,l=0,f=e;if(r>1)if(f=new i(t*r),a)for(n=0;n<t;n++)for(l=n,s=0;s<r;s++,l+=t)f[l]=e[o++];else for(n=0;n<t;n++)for(l=n,s=0;s<r;s++,l+=t)f[o++]=e[l];return f}},i=function(e,t,r){this.val=e,this.left=t,this.right=r};return{decode:function(e,t){var i=(t=t||{}).noDataValue,a=0,n={};if(n.ptr=t.inputOffset||0,n.pixels={},r.readHeaderInfo(e,n)){var o=n.headerInfo,s=o.fileVersion,l=r.getDataTypeArray(o.imageType);if(s>5)throw"unsupported lerc version 2."+s;r.readMask(e,n),o.numValidPixel===o.width*o.height||n.pixels.resultMask||(n.pixels.resultMask=t.maskData);var f=o.width*o.height;n.pixels.resultPixels=new l(f*o.numDims),n.counter={onesweep:0,uncompressed:0,lut:0,bitstuffer:0,constant:0,constantoffset:0};var u,c=!t.returnPixelInterleavedDims;if(0!==o.numValidPixel)if(o.zMax===o.zMin)r.constructConstantSurface(n,c);else if(s>=4&&r.checkMinMaxRanges(e,n))r.constructConstantSurface(n,c);else{var d=new DataView(e,n.ptr,2),h=d.getUint8(0);if(n.ptr++,h)r.readDataOneSweep(e,n,l,c);else if(s>1&&o.imageType<=1&&Math.abs(o.maxZError-.5)<1e-5){var m=d.getUint8(1);if(n.ptr++,n.encodeMode=m,m>2||s<4&&m>1)throw"Invalid Huffman flag "+m;m?r.readHuffman(e,n,l,c):r.readTiles(e,n,l,c)}else r.readTiles(e,n,l,c)}n.eofOffset=n.ptr,t.inputOffset?(u=n.headerInfo.blobSize+t.inputOffset-n.ptr,Math.abs(u)>=1&&(n.eofOffset=t.inputOffset+n.headerInfo.blobSize)):(u=n.headerInfo.blobSize-n.ptr,Math.abs(u)>=1&&(n.eofOffset=n.headerInfo.blobSize));var p={width:o.width,height:o.height,pixelData:n.pixels.resultPixels,minValue:o.zMin,maxValue:o.zMax,validPixelCount:o.numValidPixel,dimCount:o.numDims,dimStats:{minValues:o.minValues,maxValues:o.maxValues},maskData:n.pixels.resultMask};if(n.pixels.resultMask&&r.isValidPixelValue(o.imageType,i)){var g=n.pixels.resultMask;for(a=0;a<f;a++)g[a]||(p.pixelData[a]=i);p.noDataValue=i}return n.noDataValue=i,t.returnFileInfo&&(p.fileInfo=r.formatFileInfo(n)),p}},getBandCount:function(e){for(var t=0,i=0,a={ptr:0,pixels:{}};i<e.byteLength-58;)r.readHeaderInfo(e,a),i+=a.headerInfo.blobSize,t++,a.ptr=i;return t}}}(),f=new ArrayBuffer(4),u=new Uint8Array(f),new Uint32Array(f)[0]=1,h=1===u[0],m={decode:function(e,t){if(!h)throw"Big endian system is not supported.";var r,i,a=(t=t||{}).inputOffset||0,n=new Uint8Array(e,a,10),o=String.fromCharCode.apply(null,n);if("CntZImage"===o.trim())r=c,i=1;else{if("Lerc2"!==o.substring(0,5))throw"Unexpected file identifier string: "+o;r=d,i=2}for(var s,l,f,u,m,p,g=0,y=e.byteLength-10,x=[],w={width:0,height:0,pixels:[],pixelType:t.pixelType,mask:null,statistics:[]},k=0;a<y;){var I=r.decode(e,{inputOffset:a,encodedMaskData:s,maskData:f,returnMask:0===g,returnEncodedMask:0===g,returnFileInfo:!0,returnPixelInterleavedDims:t.returnPixelInterleavedDims,pixelType:t.pixelType||null,noDataValue:t.noDataValue||null});a=I.fileInfo.eofOffset,f=I.maskData,0===g&&(s=I.encodedMaskData,w.width=I.width,w.height=I.height,w.dimCount=I.dimCount||1,w.pixelType=I.pixelType||I.fileInfo.pixelType,w.mask=f),i>1&&(f&&x.push(f),I.fileInfo.mask&&I.fileInfo.mask.numBytes>0&&k++),g++,w.pixels.push(I.pixelData),w.statistics.push({minValue:I.minValue,maxValue:I.maxValue,noDataValue:I.noDataValue,dimStats:I.dimStats})}if(i>1&&k>1){for(p=w.width*w.height,w.bandMasks=x,(f=new Uint8Array(p)).set(x[0]),u=1;u<x.length;u++)for(l=x[u],m=0;m<p;m++)f[m]=f[m]&l[m];w.maskData=f}return w}},void 0===(r=function(){return m}.apply(t,[]))||(e.exports=r)},4173:function(e,t,r){"use strict";r.r(t),r.d(t,{default:function(){return s}});var i=r(7885),a=r(7197),n=r(7737),o=r(2499);class s extends n.Z{constructor(e){super(),this.planarConfiguration=void 0!==e.PlanarConfiguration?e.PlanarConfiguration:1,this.samplesPerPixel=void 0!==e.SamplesPerPixel?e.SamplesPerPixel:1,this.addCompression=e.LercParameters[o.L5.AddCompression]}decodeBlock(e){switch(this.addCompression){case o.Qb.None:break;case o.Qb.Deflate:e=(0,i.rr)(new Uint8Array(e)).buffer;break;default:throw new Error(`Unsupported LERC additional compression method identifier: ${this.addCompression}`)}return a.decode(e,{returnPixelInterleavedDims:1===this.planarConfiguration}).pixels[0].buffer}}},2499:function(e,t,r){"use strict";r.d(t,{Ie:function(){return l},It:function(){return n},L:function(){return i},L5:function(){return u},P1:function(){return d},Qb:function(){return c},pd:function(){return f},sf:function(){return s}});const i={315:"Artist",258:"BitsPerSample",265:"CellLength",264:"CellWidth",320:"ColorMap",259:"Compression",33432:"Copyright",306:"DateTime",338:"ExtraSamples",266:"FillOrder",289:"FreeByteCounts",288:"FreeOffsets",291:"GrayResponseCurve",290:"GrayResponseUnit",316:"HostComputer",270:"ImageDescription",257:"ImageLength",256:"ImageWidth",271:"Make",281:"MaxSampleValue",280:"MinSampleValue",272:"Model",254:"NewSubfileType",274:"Orientation",262:"PhotometricInterpretation",284:"PlanarConfiguration",296:"ResolutionUnit",278:"RowsPerStrip",277:"SamplesPerPixel",305:"Software",279:"StripByteCounts",273:"StripOffsets",255:"SubfileType",263:"Threshholding",282:"XResolution",283:"YResolution",326:"BadFaxLines",327:"CleanFaxData",343:"ClipPath",328:"ConsecutiveBadFaxLines",433:"Decode",434:"DefaultImageColor",269:"DocumentName",336:"DotRange",321:"HalftoneHints",346:"Indexed",347:"JPEGTables",285:"PageName",297:"PageNumber",317:"Predictor",319:"PrimaryChromaticities",532:"ReferenceBlackWhite",339:"SampleFormat",340:"SMinSampleValue",341:"SMaxSampleValue",559:"StripRowCounts",330:"SubIFDs",292:"T4Options",293:"T6Options",325:"TileByteCounts",323:"TileLength",324:"TileOffsets",322:"TileWidth",301:"TransferFunction",318:"WhitePoint",344:"XClipPathUnits",286:"XPosition",529:"YCbCrCoefficients",531:"YCbCrPositioning",530:"YCbCrSubSampling",345:"YClipPathUnits",287:"YPosition",37378:"ApertureValue",40961:"ColorSpace",36868:"DateTimeDigitized",36867:"DateTimeOriginal",34665:"Exif IFD",36864:"ExifVersion",33434:"ExposureTime",41728:"FileSource",37385:"Flash",40960:"FlashpixVersion",33437:"FNumber",42016:"ImageUniqueID",37384:"LightSource",37500:"MakerNote",37377:"ShutterSpeedValue",37510:"UserComment",33723:"IPTC",34675:"ICC Profile",700:"XMP",42112:"GDAL_METADATA",42113:"GDAL_NODATA",34377:"Photoshop",33550:"ModelPixelScale",33922:"ModelTiepoint",34264:"ModelTransformation",34735:"GeoKeyDirectory",34736:"GeoDoubleParams",34737:"GeoAsciiParams",50674:"LercParameters"},a={};for(const e in i)i.hasOwnProperty(e)&&(a[i[e]]=parseInt(e,10));const n=[a.BitsPerSample,a.ExtraSamples,a.SampleFormat,a.StripByteCounts,a.StripOffsets,a.StripRowCounts,a.TileByteCounts,a.TileOffsets,a.SubIFDs],o={1:"BYTE",2:"ASCII",3:"SHORT",4:"LONG",5:"RATIONAL",6:"SBYTE",7:"UNDEFINED",8:"SSHORT",9:"SLONG",10:"SRATIONAL",11:"FLOAT",12:"DOUBLE",13:"IFD",16:"LONG8",17:"SLONG8",18:"IFD8"},s={};for(const e in o)o.hasOwnProperty(e)&&(s[o[e]]=parseInt(e,10));const l={WhiteIsZero:0,BlackIsZero:1,RGB:2,Palette:3,TransparencyMask:4,CMYK:5,YCbCr:6,CIELab:8,ICCLab:9},f={Unspecified:0,Assocalpha:1,Unassalpha:2},u={Version:0,AddCompression:1},c={None:0,Deflate:1},d={1024:"GTModelTypeGeoKey",1025:"GTRasterTypeGeoKey",1026:"GTCitationGeoKey",2048:"GeographicTypeGeoKey",2049:"GeogCitationGeoKey",2050:"GeogGeodeticDatumGeoKey",2051:"GeogPrimeMeridianGeoKey",2052:"GeogLinearUnitsGeoKey",2053:"GeogLinearUnitSizeGeoKey",2054:"GeogAngularUnitsGeoKey",2055:"GeogAngularUnitSizeGeoKey",2056:"GeogEllipsoidGeoKey",2057:"GeogSemiMajorAxisGeoKey",2058:"GeogSemiMinorAxisGeoKey",2059:"GeogInvFlatteningGeoKey",2060:"GeogAzimuthUnitsGeoKey",2061:"GeogPrimeMeridianLongGeoKey",2062:"GeogTOWGS84GeoKey",3072:"ProjectedCSTypeGeoKey",3073:"PCSCitationGeoKey",3074:"ProjectionGeoKey",3075:"ProjCoordTransGeoKey",3076:"ProjLinearUnitsGeoKey",3077:"ProjLinearUnitSizeGeoKey",3078:"ProjStdParallel1GeoKey",3079:"ProjStdParallel2GeoKey",3080:"ProjNatOriginLongGeoKey",3081:"ProjNatOriginLatGeoKey",3082:"ProjFalseEastingGeoKey",3083:"ProjFalseNorthingGeoKey",3084:"ProjFalseOriginLongGeoKey",3085:"ProjFalseOriginLatGeoKey",3086:"ProjFalseOriginEastingGeoKey",3087:"ProjFalseOriginNorthingGeoKey",3088:"ProjCenterLongGeoKey",3089:"ProjCenterLatGeoKey",3090:"ProjCenterEastingGeoKey",3091:"ProjCenterNorthingGeoKey",3092:"ProjScaleAtNatOriginGeoKey",3093:"ProjScaleAtCenterGeoKey",3094:"ProjAzimuthAngleGeoKey",3095:"ProjStraightVertPoleLongGeoKey",3096:"ProjRectifiedGridAngleGeoKey",4096:"VerticalCSTypeGeoKey",4097:"VerticalCitationGeoKey",4098:"VerticalDatumGeoKey",4099:"VerticalUnitsGeoKey"},h={};for(const e in d)d.hasOwnProperty(e)&&(h[d[e]]=parseInt(e,10))}}]);