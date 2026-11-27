# Device turntables

The spinning 3D devices on the device card are rendered with Blender (Cycles) from these scripts:
procedural models of over-ear headphones, earbuds, a USB DAC and a phone, platinum satin with dark
cushions so they read on both the black and the warm white card.

    pip install bpy
    for k in head bud dac spk; do python3 models.py -- $k 48 /tmp/r3d; done

48 frames per device (every 7.5°), 660 × 384, transparent background, then converted to WebP into
`app/src/main/assets/devices/`.

After rendering, `fix_shadow.py` softens the floor shadow and fades it out before the frame edges, so it does not show as a box on the warm white card.
