import bpy, math, sys
sys.path.insert(0, '/home/claude/rada3d')
from scene import *

PLAT = lambda: mat('plat', srgb('#B9B4AB'), metal=0.55, rough=0.32)
DARK = lambda: mat('dark', srgb('#1E1D1C'), metal=0.0, rough=0.62)
LED = lambda: mat('led', srgb('#C10206'), rough=0.3, emit=srgb('#FF1A1E'))
STEEL = lambda: mat('steel', srgb('#8C8A86'), metal=1.0, rough=0.22)
GLASS = lambda: mat('glass', srgb('#0B0F12'), metal=0.0, rough=0.06, coat=1.0)

def pivot():
    bpy.ops.object.empty_add(location=(0, 0, 0)); return bpy.context.object

def headphones():
    root = pivot(); plat, dark, led, steel = PLAT(), DARK(), LED(), STEEL()
    R = 8.6
    k = 0.5523 * R
    # headband: half ellipse over the top
    pts = [((-R, 0, 0), (-R, 0, -k), (-R, 0, k)), ((0, 0, R * 1.08), (-k, 0, R * 1.08), (k, 0, R * 1.08)), ((R, 0, 0), (R, 0, k), (R, 0, -k))]
    band = curve('band', pts, 0.62, plat, root); band.data.bevel_mode = 'ROUND'; band.scale = (1, 2.1, 1)
    r2 = R - 0.75; k2 = 0.5523 * r2
    pad = curve('pad', [((-r2 * 0.82, 0, r2 * 0.55), (-r2 * 0.9, 0, r2 * 0.2), (-r2 * 0.7, 0, r2 * 0.85)), ((0, 0, r2 * 1.06), (-k2, 0, r2 * 1.06), (k2, 0, r2 * 1.06)), ((r2 * 0.82, 0, r2 * 0.55), (r2 * 0.7, 0, r2 * 0.85), (r2 * 0.9, 0, r2 * 0.2))], 0.45, dark, root)
    pad.scale = (1, 2.2, 1)
    for sx in (-1, 1):
        bpy.ops.mesh.primitive_cylinder_add(radius=0.32, depth=2.6, location=(sx * R, 0, -1.2)); s = bpy.context.object; add(s, steel, root); smooth(s)
        # yoke
        bpy.ops.mesh.primitive_torus_add(major_radius=3.6, minor_radius=0.22, location=(sx * (R + 0.25), 0, -6.0), rotation=(0, math.radians(90), 0))
        y = bpy.context.object; y.scale = (1, 0.98, 1.22); add(y, plat, root); smooth(y)
        # ear cup: rounded oval shell
        bpy.ops.mesh.primitive_cylinder_add(vertices=64, radius=3.3, depth=2.0, location=(sx * (R + 0.55), 0, -6.0), rotation=(0, math.radians(90), 0))
        c = bpy.context.object; c.scale = (1.0, 0.86, 1.0); c.scale = (1.2, 0.88, 1.0)
        c.rotation_euler = (0, math.radians(90), 0); c.scale = (1.22, 0.9, 1.0)
        add(c, plat, root); smooth(c, bevel=0.55, seg=6)
        # cushion facing in
        bpy.ops.mesh.primitive_torus_add(major_radius=2.7, minor_radius=0.85, major_segments=64, minor_segments=24, location=(sx * (R - 0.75), 0, -6.0), rotation=(0, math.radians(90), 0))
        t = bpy.context.object; t.scale = (1.22, 0.9, 1.0); add(t, dark, root); smooth(t)
        bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=2.4, depth=0.3, location=(sx * (R - 0.55), 0, -6.0), rotation=(0, math.radians(90), 0))
        f = bpy.context.object; f.scale = (1.22, 0.9, 1.0); add(f, dark, root); smooth(f)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.16, location=(R + 1.62, -1.6, -8.2)); add(bpy.context.object, led, root)
    root.location = (0, 0, -1.0)
    return root, -10.6, (34, 6, 58)

def earbuds():
    root = pivot(); plat, dark, led = PLAT(), DARK(), LED()
    def bud(x, rz, tint):
        g = pivot(); g.parent = root; g.location = (x, 0, 0); g.rotation_euler = (0, 0, math.radians(rz))
        bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=32, radius=1.0, location=(0, 0, 2.0)); b = bpy.context.object; b.scale = (1.25, 1.05, 1.15); add(b, tint, g); smooth(b)
        bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=0.42, depth=3.6, location=(0.25, 0.1, -0.2), rotation=(math.radians(-6), math.radians(8), 0)); s = bpy.context.object; add(s, tint, g); smooth(s, bevel=0.2, seg=5)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=0.62, location=(-0.85, -0.55, 2.25)); t = bpy.context.object; t.scale = (0.8, 1, 0.9); add(t, dark, g); smooth(t)
        bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=0.43, depth=0.16, location=(0, 0, -1.84)); e = bpy.context.object; e.data.materials.append(mat('end', srgb('#8C8A86'), metal=1, rough=0.25)); e.parent = s
        return g
    bud(-2.0, 25, plat); bud(2.0, 155, plat)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.11, location=(2.35, -0.5, 1.2)); add(bpy.context.object, led, root)
    return root, -2.6, (17, 3, 60)

def dac():
    root = pivot(); plat, dark, led, steel = PLAT(), DARK(), LED(), STEEL()
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 0)); b = bpy.context.object; b.scale = (6.2, 3.4, 1.5); add(b, plat, root); smooth(b, bevel=0.5, seg=8)
    bpy.ops.mesh.primitive_cylinder_add(vertices=64, radius=1.05, depth=0.7, location=(1.4, 0, 1.0)); k = bpy.context.object; add(k, steel, root); smooth(k, bevel=0.12, seg=4)
    bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=0.9, depth=0.05, location=(1.4, 0, 1.37)); add(bpy.context.object, dark, root)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.14, location=(-2.2, -1.0, 0.76)); add(bpy.context.object, led, root)
    bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=0.38, depth=0.3, location=(-3.1, 0.5, 0), rotation=(0, math.radians(90), 0)); add(bpy.context.object, dark, root)
    bpy.ops.mesh.primitive_cube_add(size=1, location=(3.2, 0, 0)); p = bpy.context.object; p.scale = (0.4, 1.0, 0.45); add(p, dark, root); smooth(p, bevel=0.1)
    cable = curve('cable', [((3.4, 0, 0), (3.0, 0, 0), (5.0, 0, 0)), ((7.0, -1.5, -0.6), (6.0, -0.5, -0.6), (8.0, -2.5, -0.6))], 0.22, dark, root)
    root.location = (-0.8, 0, 0)
    return root, -0.78, (16, 6, 58)

def phone():
    root = pivot(); plat, glass, steel = PLAT(), GLASS(), STEEL()
    bpy.ops.mesh.primitive_cube_add(size=1); b = bpy.context.object; b.scale = (7.4, 0.85, 15.4); add(b, plat, root); smooth(b, bevel=1.0, seg=10)
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, -0.44, 0)); s = bpy.context.object; s.scale = (6.95, 0.04, 14.95); add(s, glass, root); smooth(s, bevel=0.85, seg=10)
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0.45, 0)); bk = bpy.context.object; bk.scale = (6.95, 0.04, 14.95); add(bk, glass, root); smooth(bk, bevel=0.85, seg=10)
    bpy.ops.mesh.primitive_cube_add(size=1, location=(-1.6, 0.55, 5.0)); cb = bpy.context.object; cb.scale = (3.2, 0.3, 4.0); add(cb, plat, root); smooth(cb, bevel=0.6, seg=8)
    for (x, z) in [(-2.3, 6.1), (-0.9, 6.1), (-2.3, 4.0)]:
        bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=0.55, depth=0.3, location=(x, 0.72, z), rotation=(math.radians(90), 0, 0)); add(bpy.context.object, glass, root)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.12, location=(0, -0.48, 6.9)); add(bpy.context.object, mat('led2', srgb('#C10206'), emit=srgb('#FF1A1E')), root)
    root.rotation_euler = (math.radians(-12), 0, 0)
    return root, -8.4, (40, 2, 56)

MODELS = {'head': headphones, 'bud': earbuds, 'dac': dac, 'spk': phone}

def build(kind):
    reset(); lights()
    root, floor, (dist, h, lens) = MODELS[kind]()
    spin = pivot(); root.parent = spin
    zmin = fit_camera(lens=50, elev=12, margin={"head": 1.34}.get(kind, 1.14))
    shadow_floor(zmin - 0.02)
    return spin

if __name__ == '__main__':
    kind = sys.argv[sys.argv.index('--') + 1]; frames = int(sys.argv[sys.argv.index('--') + 2]); out = sys.argv[sys.argv.index('--') + 3]
    only = int(sys.argv[sys.argv.index('--') + 4]) if len(sys.argv) > sys.argv.index('--') + 4 else -1
    spin = build(kind)
    import os; os.makedirs(out, exist_ok=True)
    for i in range(frames):
        if only >= 0 and i != only: continue
        spin.rotation_euler = (0, 0, math.radians(-30 + 360 * i / frames))
        bpy.context.scene.render.filepath = f'{out}/{kind}_{i:02d}.png'
        bpy.ops.render.render(write_still=True)
