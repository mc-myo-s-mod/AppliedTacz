// Offline preview of the shared frame, optionally filled with real TaCZ-exported quads.
// Usage: node render-preview.mjs <path-to-node_modules> [exported-workbenches.json|-] [variant-directory]
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { basename, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const modules = process.argv[2];
assert.ok(modules, 'Pass the directory containing sharp as the first argument');
const sharp = createRequire(resolve(modules, '../package.json'))('sharp');
const output = process.argv[4] ? resolve(process.argv[4]) : here;
const assets = process.argv[4] ? resolve(output, 'assets/appliedtacz')
    : resolve(here, '../../../common/src/main/resources/assets/appliedtacz');
const atlas = await sharp(readFileSync(resolve(assets, 'textures/block/tacz_assembler.png')))
    .ensureAlpha().raw().toBuffer({ resolveWithObject: true });
assert.ok(atlas.info.width > 0 && atlas.info.height > 0, 'Invalid atlas');

const width = 900;
const height = 700;
const footerHeight = 100;
const zoom = 24;
const forward = [-0.5773503, -0.5773503, 0.5773503];
const right = [0.7071068, 0, 0.7071068];
const up = [-0.4082483, 0.8164966, 0.4082483];
const faces = ['east', 'up', 'north'];
const model = JSON.parse(readFileSync(resolve(assets, 'models/block/tacz_assembler.json'), 'utf8'));
assert.equal(model.textures?.atlas, 'appliedtacz:block/tacz_assembler', 'shared-frame atlas');
const textures = { '#atlas': atlas };
if (model.textures.ae2frame) {
    assert.equal(model.textures.ae2frame, 'ae2:block/molecular_assembler');
    textures['#ae2frame'] = await sharp(resolve(here,
        '../../../../fork/Applied-Energistics-2_1_21_1/src/main/resources/assets/ae2/textures/block/molecular_assembler.png'))
        .ensureAlpha().raw().toBuffer({ resolveWithObject: true });
}
function rotateUv([u, v], rotation = 0) {
    if (rotation === 90) return [v, 1 - u];
    if (rotation === 180) return [1 - u, 1 - v];
    if (rotation === 270) return [1 - v, u];
    assert.equal(rotation, 0);
    return [u, v];
}
assert.deepEqual(rotateUv([0.25, 0.75], 90), [0.75, 0.75]);
assert.deepEqual(rotateUv([0.25, 0.75], 180), [0.75, 0.25]);
assert.deepEqual(rotateUv([0.25, 0.75], 270), [0.25, 0.25]);
assert.ok(model.elements?.length, 'shared frame: no cubes');
for (const cube of model.elements) {
    assert.ok([cube.from, cube.to].every(v => Array.isArray(v) && v.length === 3), 'cube bounds');
    for (let i = 0; i < 3; i++)
        assert.ok(Number.isFinite(cube.from[i]) && Number.isFinite(cube.to[i]) &&
            cube.from[i] >= 0 && cube.from[i] < cube.to[i] && cube.to[i] <= 16,
        'invalid cube bounds');
    for (const [face, f] of Object.entries(cube.faces)) {
        assert.ok(textures[f.texture], `${face} texture`);
        assert.ok(Array.isArray(f.uv) && f.uv.length === 4 &&
            f.uv.every(v => Number.isFinite(v) && v >= 0 && v <= 16),
        `${face} UV`);
    }
}

const background = [20, 27, 36];
function sampleFrame(cube, axis, pos) {
    const axes = axis === 1 ? [0, 2] : axis === 0 ? [2, 1] : [0, 1];
    let uv = axes.map(i => Math.min(0.999999, Math.max(0,
        (pos[i] - cube.from[i]) / (cube.to[i] - cube.from[i]))));
    if (axis !== 1) { uv[0] = 1 - uv[0]; uv[1] = 1 - uv[1]; }
    const face = cube.faces[faces[axis]];
    uv = rotateUv(uv, face.rotation);
    const crop = face.uv;
    const { data, info } = textures[face.texture];
    const tx = Math.min(info.width - 1, Math.max(0,
        Math.floor((crop[0] + uv[0] * (crop[2] - crop[0])) / 16 * info.width)));
    const ty = Math.min(info.height - 1, Math.max(0,
        Math.floor((crop[1] + uv[1] * (crop[3] - crop[1])) / 16 * info.height)));
    return data.subarray((ty * info.width + tx) * 4, (ty * info.width + tx) * 4 + 4);
}
if (model.elements.some(cube => cube.name === 'AE2 glass')) {
    const glass = model.elements.find(cube => cube.name === 'AE2 glass');
    assert.equal(sampleFrame(glass, 2, [8, 8, 2])[3], 0, 'Glass center must reveal the workbench');
    assert.ok(sampleFrame(glass, 2, [11.5, 12.5, 2])[3] >= 26, 'Original diagonal glass highlight must remain visible');
}
const pixels = Buffer.alloc(width * height * 3);
const frameDepth = new Float64Array(width * height).fill(Infinity);
const center = [8, 8, 8];
for (let py = 0; py < height; py++) for (let px = 0; px < width; px++) {
        const origin = center.map((v, i) => v + right[i] * (px - width / 2) / zoom
            + up[i] * (height / 2 - py) / zoom - forward[i] * 100);
        let nearest = Infinity;
        let hit;
        let hitAxis;
        let texel;
        for (const cube of model.elements) {
            let enter = -Infinity;
            let leave = Infinity;
            let axis = 0;
            for (let i = 0; i < 3; i++) {
                const a = (cube.from[i] - origin[i]) / forward[i];
                const b = (cube.to[i] - origin[i]) / forward[i];
                const near = Math.min(a, b);
                if (near > enter) { enter = near; axis = i; }
                leave = Math.min(leave, Math.max(a, b));
            }
            if (enter <= leave && enter >= 0 && enter < nearest && cube.faces[faces[axis]]) {
                const sample = sampleFrame(cube, axis, origin.map((v, i) => v + forward[i] * enter));
                if (sample[3] / 255 < 0.1) continue;
                nearest = enter;
                hit = cube;
                hitAxis = axis;
                texel = sample;
            }
        }
        let color = [24, 29, 37];
        if (hit) {
            frameDepth[py * width + px] = nearest - 100;
            const shade = hitAxis === 1 ? 1 : hitAxis === 2 ? 0.86 : 0.73;
            const alpha = texel[3] / 255;
            color = [0, 1, 2].map(i => Math.round(
                texel[i] * alpha * shade + background[i] * (1 - alpha)));
        } else {
            const distance = -origin[1] / forward[1];
            const x = origin[0] + forward[0] * distance;
            const z = origin[2] + forward[2] * distance;
            if (distance > 0 && x > -5 && x < 21 && z > -5 && z < 21) {
                const edge = Math.abs(x / 16 - Math.round(x / 16)) < 0.008 ||
                    Math.abs(z / 16 - Math.round(z / 16)) < 0.008;
                color = edge ? [53, 64, 78] : [32, 39, 49];
            }
        }
        const offset = (py * width + px) * 3;
        for (let i = 0; i < 3; i++) pixels[offset + i] = color[i];
}

function footer(title, detail) {
    return Buffer.from(`<svg width="${width}" height="${footerHeight}">
  <rect width="${width}" height="${footerHeight}" fill="#141b24"/>
  <path d="M48 0h804" stroke="#354454"/>
  <text x="48" y="48" fill="#edf3f7" font-family="sans-serif" font-size="24" font-weight="700" letter-spacing="2">${title}</text>
  <text x="48" y="73" fill="#a7b6c4" font-family="sans-serif" font-size="14" letter-spacing="1.2">${detail}</text>
</svg>`);
}

async function panel(image, title, detail) {
    return sharp({ create: { width, height: height + footerHeight, channels: 3, background: '#141b24' } })
        .composite([
            { input: await sharp(image, { raw: { width, height, channels: 3 } }).png().toBuffer(), left: 0, top: 0 },
            { input: footer(title, detail), left: 0, top: height },
        ]).png().toBuffer();
}

await sharp(await panel(pixels, process.argv[4] ? basename(output).replaceAll('-', ' ').toUpperCase()
    : model.textures.ae2frame ? 'AE2 OUTER / APPLIEDTACZ INNER' : 'SHARED ASSEMBLER FRAME',
    'OFFLINE GEOMETRY PREVIEW / NO WORKBENCH'))
    .toFile(resolve(output, 'preview.png'));
console.log(`Rendered assembler frame preview in ${output}`);

function dot(a, b) { return a.reduce((sum, v, i) => sum + v * b[i], 0); }

function project(vertex) {
    const relative = vertex.slice(0, 3).map((v, i) => v - center[i]);
    return [width / 2 + dot(relative, right) * zoom,
        height / 2 - dot(relative, up) * zoom, dot(relative, forward), ...vertex.slice(3)];
}

function barycentric(a, b, c, x, y) {
    const area = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
    if (Math.abs(area) < 1e-8) return null;
    const u = ((b[1] - c[1]) * (x - c[0]) + (c[0] - b[0]) * (y - c[1])) / area;
    const v = ((c[1] - a[1]) * (x - c[0]) + (a[0] - c[0]) * (y - c[1])) / area;
    return [u, v, 1 - u - v];
}

function rasterize(triangle, texture, image, depth) {
    const [a, b, c] = triangle;
    const minX = Math.max(0, Math.ceil(Math.min(a[0], b[0], c[0])));
    const maxX = Math.min(width - 1, Math.floor(Math.max(a[0], b[0], c[0])));
    const minY = Math.max(0, Math.ceil(Math.min(a[1], b[1], c[1])));
    const maxY = Math.min(height - 1, Math.floor(Math.max(a[1], b[1], c[1])));
    const normalLength = Math.hypot(...a.slice(5));
    const normal = texture ? a.slice(5).map(v => v / normalLength) : [0, 1, 0];
    const shade = Math.min(1, 0.73 + 0.13 * Math.abs(normal[2]) + 0.27 * Math.max(0, normal[1]));
    let drawn = 0;
    for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) {
        const weights = barycentric(a, b, c, x, y);
        if (!weights || weights.some(v => v < -1e-8)) continue;
        const interpolate = component => weights.reduce((sum, v, i) => sum + v * triangle[i][component], 0);
        const z = interpolate(2);
        const index = y * width + x;
        if (z >= depth[index] - 1e-6) continue;
        if (!texture) {
            const alpha = interpolate(6);
            for (let i = 0; i < 3; i++) image[index * 3 + i] = Math.min(255,
                Math.round(image[index * 3 + i] + interpolate(3 + i) * alpha * 255));
            drawn++;
            continue;
        }
        const tx = Math.min(texture.info.width - 1, Math.max(0, Math.floor(interpolate(3) * texture.info.width)));
        const ty = Math.min(texture.info.height - 1, Math.max(0, Math.floor(interpolate(4) * texture.info.height)));
        const texel = (ty * texture.info.width + tx) * 4;
        const alpha = texture.data[texel + 3] / 255;
        if (alpha === 0) continue;
        for (let i = 0; i < 3; i++) image[index * 3 + i] = Math.round(
            texture.data[texel + i] * shade * alpha + image[index * 3 + i] * (1 - alpha));
        depth[index] = z;
        drawn++;
    }
    return drawn;
}

// Small self-checks cover projection, degenerate triangles, UV interpolation and depth rejection.
assert.deepEqual(project([...center, 0, 0, 0, 1, 0]).slice(0, 3), [width / 2, height / 2, 0]);
assert.deepEqual(barycentric([0, 0], [4, 0], [0, 4], 1, 1), [0.5, 0.25, 0.25]);
assert.equal(barycentric([0, 0], [1, 0], [2, 0], 1, 0), null);
const testImage = Buffer.alloc(pixels.length);
const testDepth = new Float64Array(frameDepth.length).fill(Infinity);
const testTexture = { info: { width: 2, height: 1 }, data: Buffer.from([255, 0, 0, 255, 0, 255, 0, 255]) };
const testTriangle = [[0, 0, 1, 0, 0, 0, 1, 0], [4, 0, 1, 1, 0, 0, 1, 0], [0, 4, 1, 0, 0, 0, 1, 0]];
assert.ok(rasterize(testTriangle, testTexture, testImage, testDepth) > 0);
assert.deepEqual([...testImage.subarray(9, 12)], [0, 255, 0]);
assert.equal(rasterize(testTriangle.map(v => v.map((n, i) => i === 2 ? 2 : n)),
    testTexture, testImage, testDepth), 0, 'farther geometry must be occluded');
testDepth.fill(Infinity);
assert.equal(rasterize(testTriangle, { info: { width: 1, height: 1 }, data: Buffer.alloc(4) },
    testImage, testDepth), 0, 'transparent texels must not occlude');
testImage.fill(10);
const testGlow = [[0, 0, 1, 1, 0, 0, 0.5], [4, 0, 1, 1, 0, 0, 0.5], [0, 4, 1, 1, 0, 0, 0.5]];
assert.ok(rasterize(testGlow, null, testImage, testDepth) > 0);
assert.deepEqual([...testImage.subarray(0, 3)], [138, 10, 10], 'alpha-weighted additive VFX');
assert.equal(testDepth[0], Infinity, 'VFX must not write depth');
testDepth.fill(0);
assert.equal(rasterize(testGlow, null, testImage, testDepth), 0, 'VFX must remain occluded by solid geometry');

if (process.argv[3] && process.argv[3] !== '-') {
    const workbenches = JSON.parse(readFileSync(process.argv[3], 'utf8'));
    const rendered = new Map();
    for (const name of ['ammo_workbench', 'attachment_workbench', 'gun_smith_table']) {
        const workbench = workbenches[name];
        assert.equal(workbench?.facing, 'north');
        assert.ok(workbench.vertices.length > 0 && workbench.vertices.length % 4 === 0);
        for (const vertex of workbench.vertices) {
            assert.equal(vertex.length, 8);
            assert.ok(vertex.every(Number.isFinite));
            assert.ok(Math.hypot(...vertex.slice(5)) > 0, 'nonzero normal');
        }
        const texture = await sharp(readFileSync(workbench.texture)).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
        const triangles = [];
        for (let i = 0; i < workbench.vertices.length; i += 4) {
            const quad = workbench.vertices.slice(i, i + 4).map(project);
            triangles.push([quad[0], quad[1], quad[2]], [quad[0], quad[2], quad[3]]);
        }
        // shortcut: offline diffuse lighting has no Minecraft lightmap/shader effects; use in-game screenshots for those.
        triangles.sort((a, b) => b.reduce((s, v) => s + v[2], 0) - a.reduce((s, v) => s + v[2], 0));
        const image = Buffer.from(pixels);
        const depth = frameDepth.slice();
        let fragments = 0;
        for (const triangle of triangles) fragments += rasterize(triangle, texture, image, depth);
        assert.ok(fragments > 100, `${name} must be visible inside the frame`);
        const vfx = workbenches.vfx;
        assert.equal(vfx.length, 720, 'crafting VFX vertex budget');
        let glowFragments = 0;
        for (let i = 0; i < vfx.length; i += 4) {
            const quad = vfx.slice(i, i + 4).map(vertex => {
                assert.equal(vertex.length, 7);
                assert.ok(vertex.every(Number.isFinite));
                return project(vertex);
            });
            glowFragments += rasterize([quad[0], quad[1], quad[2]], null, image, depth);
            glowFragments += rasterize([quad[0], quad[2], quad[3]], null, image, depth);
        }
        assert.ok(glowFragments > 0, `${name}: VFX must be visible`);
        rendered.set(name, image);
        console.log(`${name}: ${triangles.length / 2} source quads, ${fragments} visible fragments`);
    }
    const panels = [];
    for (const [index, [name, label]] of [
        ['ammo_workbench', 'AMMO WORKBENCH'], ['attachment_workbench', 'ATTACHMENT WORKBENCH'],
        ['gun_smith_table', 'GUNSMITH WORKBENCH'], ['gun_smith_table', 'UNIVERSAL / DEFAULT GUNSMITH'],
    ].entries()) panels.push({ input: await panel(rendered.get(name), label,
        'OFFLINE RENDER / ONLINE STATE / CRAFTING VFX / TIME 0'),
    left: index % 2 * width, top: Math.floor(index / 2) * (height + footerHeight) });
    await sharp({ create: { width: width * 2, height: (height + footerHeight) * 2, channels: 3, background: '#141b24' } })
        .composite(panels).png().toFile(resolve(output, 'preview-workbenches.png'));
    console.log(`Rendered actual-workbench gallery in ${output}`);
}
console.log('PASS: projection, UV sampling, occlusion, transparency, additive VFX and depth-write self-checks');
