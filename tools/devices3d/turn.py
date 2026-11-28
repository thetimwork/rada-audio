# Renders a quarter-turn (once, on connect) and one high-quality still at the resting angle.
import sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
from models import build
kind, out = sys.argv[sys.argv.index('--')+1], sys.argv[sys.argv.index('--')+2]
os.makedirs(out, exist_ok=True)
spin = build(kind); sc = bpy.context.scene
REST, START, N = -30.0, -120.0, 31          # 90 degrees in 3-degree steps
sc.render.resolution_x, sc.render.resolution_y = 540, 314; sc.cycles.samples = 16
for i in range(N):
    a = START + (REST - START) * i / (N - 1)
    spin.rotation_euler = (0, 0, math.radians(a))
    sc.render.filepath = f'{out}/{kind}_t{i:02d}.png'; bpy.ops.render.render(write_still=True)
spin.rotation_euler = (0, 0, math.radians(REST))
sc.render.resolution_x, sc.render.resolution_y = 1320, 768; sc.cycles.samples = 64
sc.render.filepath = f'{out}/{kind}_still.png'; bpy.ops.render.render(write_still=True)
