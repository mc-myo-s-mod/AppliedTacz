// Design studies only: leave the applied model, inner frame and original atlas untouched.
// node generate-variants.mjs <node_modules> [--check]
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
assert.ok(process.argv[2] && process.argv[2] !== '--check', 'Pass the directory containing sharp');
const modules = resolve(process.argv[2]);
const sharp = createRequire(resolve(modules, '../package.json'))('sharp');
const check = process.argv.includes('--check');
const assets = resolve(here, '../../../common/src/main/resources/assets/appliedtacz');
const modelPath = resolve(assets, 'models/block/tacz_assembler.json');
const texturePath = resolve(assets, 'textures/block/tacz_assembler.png');
const bbPath = resolve(here, 'tacz_assembler.bbmodel');
const protectedFiles = [modelPath, texturePath, bbPath, resolve(here, 'preview.png'), resolve(here, 'preview-workbenches.png')];
const originals = protectedFiles.map(path => readFileSync(path));
// Keep the original design studies independent of later changes to the applied frame.
const baseline = resolve(here, 'variants/a-pure-white');
const model = JSON.parse(readFileSync(resolve(baseline, 'assets/appliedtacz/models/block/tacz_assembler.json')));
const bb = JSON.parse(readFileSync(resolve(baseline, 'tacz_assembler.bbmodel')));
const atlas = await sharp(readFileSync(resolve(baseline, 'assets/appliedtacz/textures/block/tacz_assembler.png')))
    .ensureAlpha().raw().toBuffer({ resolveWithObject: true });
assert.equal(atlas.info.width, 64);
assert.equal(atlas.info.height, 64);
const outer = cube => cube.name.startsWith('Outer frame');
const variants = [
    { id: 'a-pure-white', inset: 0, middle: false },
    { id: 'b-fine-edge', inset: 0.25, middle: false },
    { id: 'c-corner-cap', inset: 0.25, middle: true },
    { id: 'd-recessed-rail', inset: 0.5, middle: true },
];
const gallery = [];
function emit(path, bytes) {
    if (check) assert.ok(readFileSync(path).equals(bytes), 'Regenerate ' + path);
    else { mkdirSync(dirname(path), { recursive: true }); writeFileSync(path, bytes); }
}
function json(path, value) { emit(path, Buffer.from(JSON.stringify(value, null, 2) + '\n')); }
function uuid(text) {
    const h = createHash('sha256').update(text).digest('hex').slice(0, 32);
    return [h.slice(0, 8), h.slice(8, 12), h.slice(12, 16), h.slice(16, 20), h.slice(20)].join('-');
}
const axes = { north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2] };
for (const [index, variant] of variants.entries()) {
    const directory = resolve(here, 'variants', variant.id);
    const elements = model.elements.flatMap(cube => {
        if (!outer(cube)) return [structuredClone(cube)];
        const size = cube.to.map((n, i) => n - cube.from[i]);
        const axis = size.indexOf(Math.max(...size));
        const cuts = [cube.from[axis], ...(variant.middle && size[axis] > 6
            ? [3, 13].filter(n => n > cube.from[axis] && n < cube.to[axis]) : []), cube.to[axis]];
        return cuts.slice(0, -1).map((start, part) => {
            const from = [...cube.from], to = [...cube.to];
            from[axis] = start;
            to[axis] = cuts[part + 1];
            const inset = !variant.middle || (start >= 3 && to[axis] <= 13) ? variant.inset : 0;
            const result = { ...cube, from: from.map(n => Math.max(inset, n)),
                to: to.map(n => Math.min(16 - inset, n)) };
            result.faces = Object.fromEntries(Object.entries(axes).map(([face, [u, v]]) =>
                [face, { texture: '#atlas', uv: [result.from[u], result.from[v], result.to[u], result.to[v]].map(n => n / 2) }]));
            return result;
        });
    });
    assert.deepEqual(elements.filter(cube => !outer(cube)), model.elements.filter(cube => !outer(cube)), 'Inner geometry/UV unchanged');
    for (const cube of elements) for (let axis = 0; axis < 3; axis++) {
        assert.ok(cube.from[axis] >= 0 && cube.from[axis] < cube.to[axis] && cube.to[axis] <= 16);
        assert.ok(Number.isInteger(cube.from[axis] * 4) && Number.isInteger(cube.to[axis] * 4));
    }
    // Test every cell in the exact boundary partition, including quarter-pixel recesses.
    const boundaries = [0, 1, 2].map(axis => [...new Set([0, 2, 3, 13, 14, 16,
        ...elements.flatMap(cube => [cube.from[axis], cube.to[axis]])])].sort((a, b) => a - b));
    const centers = boundaries.map(values => values.slice(1).map((n, i) => (n + values[i]) / 2));
    for (const x of centers[0]) for (const y of centers[1]) for (const z of centers[2]) {
        const point = [x, y, z];
        const inset = !variant.middle || point.some(n => n > 3 && n < 13) ? variant.inset : 0;
        const expected = point.filter(n => n < 2 || n > 14).length >= 2
            && point.every(n => n > inset && n < 16 - inset);
        const owners = elements.filter(cube => point.every((n, axis) => n > cube.from[axis] && n < cube.to[axis]));
        assert.equal(owners.length, Number(expected), variant.id + ': gap, overlap or obstructed opening at ' + point);
        if (expected) assert.equal(outer(owners[0]), !point.every(n => n > 1 && n < 15), 'Joined material layers');
    }
    const pixels = Buffer.from(atlas.data);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
        const sx = Math.min(x, 31 - x), sy = Math.min(y, 31 - y);
        const edge = Math.min(sx, sy), along = Math.max(sx, sy);
        let color = [235, 238, 241];
        if (index === 1) color = edge === 0 ? [168, 183, 197] : edge === 1 ? [249, 251, 253] : [222, 230, 237];
        if (index === 2) color = along < 6 ? [184, 196, 208] : along === 6 ? [165, 180, 193] : [239, 242, 245];
        if (index === 3) color = along < 6 ? [242, 245, 249] : edge === 2 ? [251, 252, 254]
            : edge === 3 ? [189, 201, 214] : [222, 228, 236];
        pixels.set([...color, 255], (y * 64 + x) * 4);
    }
    for (let y = 0; y < 64; y++) {
        const start = (y * 64 + (y < 32 ? 32 : 0)) * 4;
        assert.ok(pixels.subarray(start, (y + 1) * 64 * 4).equals(atlas.data.subarray(start, (y + 1) * 64 * 4)), 'Inner atlas unchanged');
    }
    const texture = resolve(directory, 'assets/appliedtacz/textures/block/tacz_assembler.png');
    emit(texture, await sharp(pixels, { raw: { width: 64, height: 64, channels: 4 } }).png().toBuffer());
    json(resolve(directory, 'assets/appliedtacz/models/block/tacz_assembler.json'), { ...model, elements });
    const editable = structuredClone(bb);
    editable.name = variant.id;
    editable.textures[0].path = texture.replaceAll('\\', '/');
    editable.textures[0].relative_path = 'assets/appliedtacz/textures/block/tacz_assembler.png';
    editable.elements = elements.map(cube => {
        if (!outer(cube)) return structuredClone(bb.elements.find(item => !outer(item)
            && JSON.stringify(item.from) === JSON.stringify(cube.from) && JSON.stringify(item.to) === JSON.stringify(cube.to)));
        return { ...cube, type: 'cube', uuid: uuid(variant.id + cube.from + '/' + cube.to),
            box_uv: false, autouv: 0, origin: [8, 8, 8], rotation: [0, 0, 0],
            faces: Object.fromEntries(Object.entries(cube.faces).map(([face, value]) =>
                [face, { texture: 0, uv: value.uv.map(n => n * 4) }])) };
    });
    assert.deepEqual(editable.elements.filter(cube => !outer(cube)), bb.elements.filter(cube => !outer(cube)), 'Inner Blockbench elements unchanged');
    assert.equal(new Set(editable.elements.map(cube => cube.uuid)).size, elements.length);
    for (const group of editable.outliner) group.children = editable.elements
        .filter(cube => cube.name.startsWith(group.name)).map(cube => cube.uuid);
    json(resolve(directory, 'tacz_assembler.bbmodel'), editable);
    if (!check) {
        const result = spawnSync(process.execPath, [resolve(here, 'render-preview.mjs'), modules, '-', directory], { stdio: 'inherit' });
        assert.equal(result.status, 0, variant.id + ' preview failed');
        gallery.push({ input: readFileSync(resolve(directory, 'preview.png')), left: index % 2 * 900, top: Math.floor(index / 2) * 800 });
    }
    console.log(`${variant.id}: ${elements.length} cubes; connected shell, unchanged inner frame/atlas verified`);
}
const comparison = resolve(here, 'variants/comparison.png');
if (!check) await sharp({ create: { width: 1800, height: 1600, channels: 3, background: '#141b24' } })
    .composite(gallery).png().toFile(comparison);
const dimensions = await sharp(comparison).metadata();
assert.equal(dimensions.width, 1800);
assert.equal(dimensions.height, 1600);
for (const [index, path] of protectedFiles.entries()) assert.ok(readFileSync(path).equals(originals[index]), 'Applied asset changed: ' + path);
console.log('PASS: four design-only variants; applied model, texture, Blockbench file and previews unchanged');
