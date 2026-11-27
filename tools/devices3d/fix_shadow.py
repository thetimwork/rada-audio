import numpy as np, glob, os, sys
from PIL import Image
def fix(path):
    im=np.array(Image.open(path).convert('RGBA')).astype(np.float32)
    h,w=im.shape[:2]
    a=im[...,3]
    ys,xs=np.mgrid[0:h,0:w]
    ex=np.clip(np.minimum(xs,w-1-xs)/(0.22*w),0,1); ey=np.clip(np.minimum(ys,h-1-ys)/(0.20*h),0,1)
    m=(ex*ey); m=m*m*(3-2*m)          # smoothstep
    shadow=a<250                       # object pixels are fully opaque
    a2=np.where(shadow,a*0.6*m,a)
    im[...,3]=a2
    return Image.fromarray(im.clip(0,255).astype(np.uint8),'RGBA')
src=sorted(glob.glob('/tmp/r3d/*_[0-9][0-9].png'))
os.makedirs('/tmp/r3d_fixed',exist_ok=True)
for f in src: fix(f).save('/tmp/r3d_fixed/'+os.path.basename(f))
print(len(src))
