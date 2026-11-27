import bpy, math, sys, os
from mathutils import Vector

def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = 20
    sc.cycles.use_denoising = True
    try: sc.cycles.denoiser = 'OPENIMAGEDENOISE'
    except Exception: pass
    sc.render.film_transparent = True
    sc.render.resolution_x, sc.render.resolution_y = 660, 384
    sc.render.image_settings.file_format = 'PNG'
    sc.render.image_settings.color_mode = 'RGBA'
    sc.view_settings.view_transform = 'AgX' if 'AgX' in [i.identifier for i in sc.view_settings.bl_rna.properties['view_transform'].enum_items] else 'Filmic'
    w = bpy.data.worlds.new('w'); sc.world = w; w.use_nodes = True
    w.node_tree.nodes['Background'].inputs[0].default_value = (0.55, 0.55, 0.56, 1)
    w.node_tree.nodes['Background'].inputs[1].default_value = 0.35
    return sc

def mat(name, rgb, metal=0.0, rough=0.4, emit=None, coat=0.0):
    m = bpy.data.materials.new(name); m.use_nodes = True
    b = m.node_tree.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (*rgb, 1)
    b.inputs['Metallic'].default_value = metal
    b.inputs['Roughness'].default_value = rough
    if coat and 'Coat Weight' in b.inputs: b.inputs['Coat Weight'].default_value = coat
    if emit:
        b.inputs['Emission Color'].default_value = (*emit, 1); b.inputs['Emission Strength'].default_value = 6
    return m

def srgb(h):
    h = h.lstrip('#'); c = [int(h[i:i+2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c)

def smooth(o, bevel=0.0, seg=4, sub=0):
    for x in bpy.context.selected_objects: x.select_set(False)
    o.select_set(True); bpy.context.view_layer.objects.active = o
    if o.type == 'MESH' and tuple(o.scale) != (1.0, 1.0, 1.0):
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel:
        m = o.modifiers.new('bv', 'BEVEL'); m.width = bevel; m.segments = seg; m.limit_method = 'ANGLE'
    if sub:
        m = o.modifiers.new('ss', 'SUBSURF'); m.levels = sub; m.render_levels = sub
    try: bpy.ops.object.shade_smooth()
    except Exception: pass
    for p in o.data.polygons: p.use_smooth = True
    return o

def add(o, m, parent):
    o.data.materials.append(m); o.parent = parent; return o

def curve(name, pts, depth, m, parent, res=24):
    cu = bpy.data.curves.new(name, 'CURVE'); cu.dimensions = '3D'; cu.bevel_depth = depth; cu.bevel_resolution = 6
    cu.resolution_u = res; cu.use_fill_caps = True
    sp = cu.splines.new('BEZIER'); sp.bezier_points.add(len(pts) - 1)
    for bp, (co, hl, hr) in zip(sp.bezier_points, pts):
        bp.co = co; bp.handle_left = hl; bp.handle_right = hr
    o = bpy.data.objects.new(name, cu); bpy.context.collection.objects.link(o); cu.materials.append(m); o.parent = parent
    return o

def lights():
    def area(loc, energy, size, rot):
        bpy.ops.object.light_add(type='AREA', location=loc); l = bpy.context.object
        l.data.energy = energy; l.data.size = size; l.rotation_euler = rot
    area((6, -8, 10), 1400, 8, (math.radians(45), 0, math.radians(35)))     # key
    area((-9, -4, 5), 500, 10, (math.radians(70), 0, math.radians(-65)))    # fill
    area((0, 9, 9), 900, 6, (math.radians(-45), 0, math.radians(180)))       # rim

def shadow_floor(z):
    bpy.ops.mesh.primitive_plane_add(size=4000, location=(0, 0, z)); p = bpy.context.object
    p.is_shadow_catcher = True
    return p

def fit_camera(lens=50, elev=14, margin=1.18):
    import math
    from mathutils import Vector
    bpy.context.view_layer.update()
    r = 0.0; zmin, zmax = 1e9, -1e9
    for o in bpy.context.scene.objects:
        if o.type not in ('MESH', 'CURVE') or o.is_shadow_catcher: continue
        for c in o.bound_box:
            w = o.matrix_world @ Vector(c)
            r = max(r, math.hypot(w.x, w.y)); zmin = min(zmin, w.z); zmax = max(zmax, w.z)
    sc = bpy.context.scene; asp = sc.render.resolution_x / sc.render.resolution_y
    th = math.atan(18 / lens); tv = math.atan(18 / lens / asp)
    zc = (zmin + zmax) / 2
    d = max(r * margin / math.tan(th), (zmax - zmin) / 2 * margin / math.tan(tv)) + r
    e = math.radians(elev)
    bpy.ops.object.camera_add(location=(0, -d * math.cos(e), zc + d * math.sin(e))); c = bpy.context.object
    c.data.lens = lens
    dv = Vector((0, 0, zc)) - c.location; c.rotation_euler = dv.to_track_quat('-Z', 'Y').to_euler()
    sc.camera = c
    return zmin
