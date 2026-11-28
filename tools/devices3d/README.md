# Device pictures

The device card shows one still render per device type, plus a quarter-turn it plays once when a
device connects. Rendered with Blender (Cycles) from procedural models: a portable speaker, over-ear
headphones, earbuds, a USB DAC and a phone, platinum satin with dark rubber and cushions so they
read on both the black and the warm white card.

    pip install bpy
    for k in spk head bud dac phone; do python3 turn.py -- $k /tmp/still; done

`turn.py` renders 31 frames from -120° to -30° (3° apart, 540 × 314) and a 1320 × 768 still at
-30°. Soften the floor shadow with the same method as `fix_shadow.py`, then pack the frames into an
8 × 4 sheet: `devices/<kind>_turn.webp` and `devices/<kind>_still.webp` in `app/src/main/assets/`.

`models.py -- <kind> 48 <dir>` still renders a full 360° turntable if it is ever needed.
