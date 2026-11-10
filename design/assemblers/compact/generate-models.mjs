// AE2 Molecular Assembler exterior with the original AppliedTaCZ inner frame.
// AE2 asset attribution: common/src/main/resources/assets/appliedtacz/AE2_ASSET_NOTICE.txt
// node generate-models.mjs <node_modules> [--check]
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const assets = resolve(here, '../../../common/src/main/resources/assets/appliedtacz');
const check = process.argv.includes('--check');
assert.ok(process.argv[2] && process.argv[2] !== '--check', 'Pass the directory containing sharp');
const sharp = createRequire(resolve(process.argv[2], '../package.json'))('sharp');
const id = 'tacz_assembler';
const textureId = 'appliedtacz:block/' + id;
const texturePath = resolve(assets, 'textures/block/' + id + '.png');
const ae2Assets = resolve(here, '../../../../fork/Applied-Energistics-2_1_21_1/src/main/resources/assets/ae2');
const ae2Model = JSON.parse(readFileSync(resolve(ae2Assets, 'models/block/molecular_assembler.json'), 'utf8'));
const ae2TexturePath = resolve(ae2Assets, 'textures/block/molecular_assembler.png');
const ae2Texture = await sharp(ae2TexturePath).metadata();
assert.equal(ae2Model.textures.base, 'ae2:block/molecular_assembler');
assert.deepEqual(ae2Model.elements.at(-1).from, [2, 2, 2]);
assert.deepEqual(ae2Model.elements.at(-1).to, [14, 14, 14]);
const ae2Beams = ae2Model.elements.slice(0, -1);
assert.equal(ae2Beams.length, 12, 'Separate the outer beams from the glass shell');
const palette = {
    ceramic: [232, 233, 239], graphite: [79, 76, 99],
};
const pixels = Buffer.alloc(64 * 64 * 4);
const swatches = {};
// Two original 32px face layouts keep joints aligned across adjacent beams.
for (let p = 0; p < 64 * 64; p++) pixels.set([...palette.graphite, 255], p * 4);
for (const [tile, [name, base]] of Object.entries(palette).entries()) {
    const ox = tile * 32, oy = 0;
    swatches[name] = [ox, oy];
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
        const sx = Math.min(x, 31 - x), sy = Math.min(y, 31 - y);
        const edge = Math.min(sx, sy), along = Math.max(sx, sy);
        let color = base;
        if (edge < 4) {
            if (name === 'ceramic') {
                if (edge === 0) color = [184, 188, 201];
                if (edge === 1) color = [248, 249, 252];
                if (edge === 3) color = [194, 196, 210];
                if (along === 4) color = [154, 158, 177];
                if (along >= 13 && edge === 2) color = [174, 178, 197];
                if (along === 11 && edge === 2) color = [131, 136, 159];
                if (sx === 1 && sy === 1) color = [99, 103, 124];
            } else {
                if (edge === 0) color = [116, 112, 141];
                if (edge === 3) color = [53, 52, 73];
                if (along === 4) color = [44, 44, 63];
                if (along >= 10 && (edge === 1 || edge === 2)) color = [62, 60, 83];
                if (along >= 11 && along % 2 === 1 && edge === 1) color = [119, 114, 147];
                if (sx === 1 && sy === 1) color = [167, 164, 190];
            }
        }
        pixels.set([...color, 255], ((oy + y) * 64 + ox + x) * 4);
    }
}
const png = await sharp(pixels, { raw: { width: 64, height: 64, channels: 4 } }).png().toBuffer();
if (check) assert.ok(readFileSync(texturePath).equals(png), 'Regenerate original texture');
else { mkdirSync(dirname(texturePath), { recursive: true }); writeFileSync(texturePath, png); }

const axes = { north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2] };
const elements = [];
const frames = [
    { name: 'Outer frame', inset: 0 },
    { name: 'Inner frame', inset: 1 },
];
function box(name, from, to, material) {
    for (let i = 0; i < 3; i++) assert.ok(from[i] >= 0 && from[i] < to[i] && to[i] <= 16, name);
    const faces = Object.fromEntries(Object.entries(axes).map(([side, [u, v]]) => {
        const crop = swatches[material];
        return [side, { texture: '#atlas', uv: [crop[0] + from[u] * 2, crop[1] + from[v] * 2,
            crop[0] + to[u] * 2, crop[1] + to[v] * 2].map(n => n / 4) }];
    }));
    elements.push({ name, from, to, faces });
}

const beams = [];
for (const y of [0, 14]) {
    for (const x of [0, 14]) beams.push([[x, y, 0], [x + 2, y + 2, 16]]);
    for (const z of [0, 14]) beams.push([[2, y, z], [14, y + 2, z + 2]]);
}
for (const x of [0, 14]) for (const z of [0, 14]) beams.push([[x, 2, z], [x + 2, 14, z + 2]]);
// Preserve the existing inner frame, including its element order and UVs.
for (const [from, to] of beams) box('Inner frame beam', from.map(n => Math.max(1, n)),
    to.map(n => Math.min(15, n)), 'graphite');
const normalAxes = { west: [0, false], east: [0, true], down: [1, false], up: [1, true], north: [2, false], south: [2, true] };
const reverse = range => [1 - range[1], 1 - range[0]];
function croppedFace(cube, from, to, side, face) {
    const ranges = axes[side].map(axis => [(from[axis] - cube.from[axis]) / (cube.to[axis] - cube.from[axis]),
        (to[axis] - cube.from[axis]) / (cube.to[axis] - cube.from[axis])]);
    let [u, v] = ranges;
    if (side === 'north' || side === 'east') u = reverse(u);
    if (side !== 'up') v = reverse(v);
    const rotation = face.rotation ?? 0;
    assert.ok([0, 90, 180, 270].includes(rotation));
    if (rotation === 90) [u, v] = [v, reverse(u)];
    if (rotation === 180) [u, v] = [reverse(u), reverse(v)];
    if (rotation === 270) [u, v] = [reverse(v), u];
    const [u0, v0, u1, v1] = face.uv;
    return { ...face, texture: '#ae2frame', uv: [u0 + (u1 - u0) * u[0], v0 + (v1 - v0) * v[0],
        u0 + (u1 - u0) * u[1], v0 + (v1 - v0) * v[1]] };
}
assert.deepEqual(croppedFace(ae2Beams[0], [0, 0, 0], [1, 2, 16], 'up', ae2Beams[0].faces.up).uv,
    [0, 15, 16, 14], 'Rotated, reversed top-face crop');
assert.deepEqual(croppedFace(ae2Beams[0], [0, 0, 0], [1, 2, 16], 'north', ae2Beams[0].faces.north).uv,
    [15, 14, 16, 16], 'North-face mirrored crop');
assert.deepEqual(croppedFace(ae2Beams[0], [1, 0, 0], [2, 1, 16], 'east', ae2Beams[0].faces.east).uv,
    [16, 15, 0, 14], 'Reversed side-face crop');
function outerPiece(cube, from, to) {
    const faces = {};
    for (const [side, face] of Object.entries(cube.faces)) {
        const [axis, positive] = normalAxes[side];
        if ((positive ? to[axis] === cube.to[axis] : from[axis] === cube.from[axis])) {
            faces[side] = croppedFace(cube, from, to, side, face);
        }
    }
    elements.push({ name: 'Outer frame beam', from, to, faces });
}
for (const cube of ae2Beams) {
    assert.equal(cube.rotation?.angle ?? 0, 0, 'Axis-aligned AE2 frame');
    for (const [side, face] of Object.entries(cube.faces)) assert.deepEqual(
        croppedFace(cube, cube.from, cube.to, side, face), { ...face, texture: '#ae2frame' }, 'Full face keeps AE2 UV/rotation');
    const { from, to } = cube;
    const innerFrom = from.map(n => Math.max(1, n));
    const innerTo = to.map(n => Math.min(15, n));
    const remainingFrom = [...from], remainingTo = [...to];
    // Partition each solid beam so the two frames meet without overlapping faces on the exterior.
    for (let axis = 0; axis < 3; axis++) {
        if (remainingFrom[axis] < innerFrom[axis]) {
            const end = [...remainingTo];
            end[axis] = innerFrom[axis];
            outerPiece(cube, [...remainingFrom], end);
            remainingFrom[axis] = innerFrom[axis];
        }
        if (remainingTo[axis] > innerTo[axis]) {
            const start = [...remainingFrom];
            start[axis] = innerTo[axis];
            outerPiece(cube, start, [...remainingTo]);
            remainingTo[axis] = innerTo[axis];
        }
    }
}
assert.equal(elements.length, 44, 'Solid joined frame partition');
for (const frame of frames) {
    const beams = elements.filter(cube => cube.name.startsWith(frame.name));
    assert.equal(beams.length, frame.inset === 0 ? 32 : 12, frame.name);
    for (const cube of beams) {
        assert.ok([...cube.from, ...cube.to].every(Number.isInteger), 'Exact voxel coverage checks require integer bounds');
        assert.ok(cube.from.every(n => n >= frame.inset) && cube.to.every(n => n <= 16 - frame.inset));
        assert.ok(Object.values(cube.faces).every(face => face.texture === (frame.inset === 0 ? '#ae2frame' : '#atlas')),
            'Only the outer frame uses the AE2 material');
    }
}
const openings = [
    [[2, 2, 0], [14, 14, 2]],
    [[2, 2, 14], [14, 14, 16]],
    [[0, 2, 2], [2, 14, 14]],
    [[14, 2, 2], [16, 14, 14]],
    [[2, 14, 2], [14, 16, 14]],
    [[2, 0, 2], [14, 2, 14]],
];
for (let x = 0; x < 16; x++) for (let y = 0; y < 16; y++) for (let z = 0; z < 16; z++) {
    const point = [x + 0.5, y + 0.5, z + 0.5];
    const owners = elements.filter(cube => point.every((n, i) => n > cube.from[i] && n < cube.to[i]));
    const onFrame = point.filter(n => n < 2 || n > 14).length >= 2;
    assert.equal(owners.length, Number(onFrame), 'Gap or overlap at ' + point);
    if (onFrame) assert.ok(owners[0].name.startsWith(point.every(n => n > 1 && n < 15)
        ? 'Inner frame' : 'Outer frame'), 'Connected inner and outer material layers');
}
for (const cube of elements) {
    assert.ok(cube.to.some((n, i) => n <= 2 || cube.from[i] >= 14),
        cube.name + ' occupies the workbench cavity');
    for (const [from, to] of openings) {
        assert.ok(from.some((n, i) => cube.to[i] <= n || cube.from[i] >= to[i]),
            cube.name + ' blocks one of the six openings');
    }
    for (const [side, face] of Object.entries(cube.faces)) {
        assert.ok(face.uv.every(n => Number.isFinite(n) && n >= 0 && n <= 16));
        let [u, v] = axes[side];
        if ((face.rotation ?? 0) % 180 === 90) [u, v] = [v, u];
        const density = face.texture === '#atlas' ? 2 : 1;
        assert.equal(Math.abs(face.uv[2] - face.uv[0]) * density, cube.to[u] - cube.from[u], 'Uniform UV density');
        assert.equal(Math.abs(face.uv[3] - face.uv[1]) * density, cube.to[v] - cube.from[v], 'Uniform UV density');
    }
}
const display = {
    gui: { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.625, 0.625, 0.625] },
    ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.25, 0.25, 0.25] },
    fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
    thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
    firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
};
const glass = structuredClone(ae2Model.elements.at(-1));
glass.name = 'AE2 glass';
for (const face of Object.values(glass.faces)) face.texture = '#ae2frame';
assert.deepEqual(Object.keys(glass.faces).sort(), Object.keys(normalAxes).sort(), 'Glass covers all six faces');
assert.ok(Object.values(glass.faces).every(face => !face.cullface
    && JSON.stringify(face.uv) === '[2,2,14,14]'), 'Inset glass retains the original AE2 UVs');
elements.push(glass);
function emit(path, object) {
    const text = JSON.stringify(object, null, 2) + '\n';
    if (check) assert.equal(readFileSync(path, 'utf8'), text, 'Regenerate ' + path);
    else { mkdirSync(dirname(path), { recursive: true }); writeFileSync(path, text); }
}
const model = { credit: 'AE2 Molecular Assembler exterior and glass (CC BY-NC-SA 3.0); original AppliedTaCZ inner frame. See AE2_ASSET_NOTICE.txt',
    parent: 'minecraft:block/block', render_type: 'cutout',
    textures: { particle: ae2Model.textures.base, atlas: textureId, ae2frame: ae2Model.textures.base }, elements, display };
emit(resolve(assets, 'models/block/' + id + '.json'), model);
for (const kind of ['ammo', 'attachment', 'gun', 'universal']) {
    emit(resolve(assets, 'models/item/' + id + '_' + kind + '.json'), { parent: 'appliedtacz:block/' + id });
    const variants = Object.fromEntries(['north', 'east', 'south', 'west'].map((facing, index) =>
        ['facing=' + facing, { model: 'appliedtacz:block/' + id, y: index * 90 }]));
    emit(resolve(assets, 'blockstates/' + id + '_' + kind + '.json'), { variants });
}
const uuid = text => {
    const h = createHash('sha256').update(text).digest('hex').slice(0, 32);
    return [h.slice(0, 8), h.slice(8, 12), h.slice(12, 16), h.slice(16, 20), h.slice(20)].join('-');
};
const bbElements = elements.map(cube => ({ ...cube, type: 'cube', uuid: uuid(cube.name + cube.from),
    box_uv: false, autouv: 0, origin: [8, 8, 8], rotation: [0, 0, 0],
    faces: Object.fromEntries(Object.entries(cube.faces).map(([face, value]) =>
        [face, { texture: value.texture === '#atlas' ? 0 : 1,
            uv: value.uv.map(n => n * (value.texture === '#atlas' ? 4 : ae2Texture.width / 16)),
            ...(value.rotation ? { rotation: value.rotation } : {}),
            ...(value.cullface ? { cullface: value.cullface } : {}) }])) }));
assert.equal(new Set(bbElements.map(cube => cube.uuid)).size, bbElements.length);
emit(resolve(here, id + '.bbmodel'), {
    meta: { format_version: '5.0', model_format: 'java_block', box_uv: false },
    name: id, resolution: { width: 64, height: 64 },
    textures: [{ path: texturePath.split(String.fromCharCode(92)).join('/'), name: id + '.png', id: 'atlas',
        namespace: 'appliedtacz', folder: 'block', width: 64, height: 64, uv_width: 64, uv_height: 64,
        mode: 'bitmap', saved: true, uuid: uuid('original-texture'),
        relative_path: '../../../common/src/main/resources/assets/appliedtacz/textures/block/' + id + '.png' },
        { path: ae2TexturePath.replaceAll('\\', '/'), name: 'molecular_assembler.png', id: 'ae2frame',
            namespace: 'ae2', folder: 'block', width: ae2Texture.width, height: ae2Texture.height,
            uv_width: ae2Texture.width, uv_height: ae2Texture.height, mode: 'bitmap', saved: true,
            uuid: uuid('ae2-molecular-assembler'), relative_path: relative(here, ae2TexturePath).replaceAll('\\', '/') }],
    elements: bbElements,
    outliner: [...frames, { name: 'AE2 glass' }].map(frame => ({ name: frame.name, origin: [8, 8, 8], uuid: uuid(frame.name),
        children: bbElements.filter(cube => cube.name.startsWith(frame.name)).map(cube => cube.uuid) })), display,
});
console.log(elements.length + ' cubes, AE2 outer frame and glass, unchanged original inner frame, 4 bindings' + (check ? ' verified' : ''));
if (check) {
    assert.deepEqual(readdirSync(resolve(assets, 'models/block')).filter(name => name.startsWith(id)), [id + '.json']);
    assert.deepEqual(readdirSync(here).filter(name => name.endsWith('.bbmodel')), [id + '.bbmodel']);
}
