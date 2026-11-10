// Run with node design/assemblers/generate-models.mjs; --forge-1.20.1 selects Forge overrides, --check verifies assets.
import assert from 'node:assert/strict';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const forge = process.argv.includes('--forge-1.20.1');
const assets = resolve(here, `../../${forge ? 'forge-1.20.1' : 'common'}/src/main/resources/assets/appliedtacz`);
const models = resolve(here, forge ? 'models/1.20.1' : 'models');
const check = process.argv.includes('--check');
const textures = {
    particle: 'ae2:block/crafting/unit',
    body: 'ae2:block/crafting/unit',
    frame: 'ae2:block/crafting/unit_base',
    metal: 'ae2:block/crafting/unit',
    steel: 'ae2:block/molecular_assembler',
    dark: 'minecraft:block/black_concrete',
    belt: 'ae2:block/crafting/unit_base',
    copper: 'minecraft:block/orange_terracotta',
    brass: 'minecraft:block/gold_block',
    cyan: 'minecraft:block/light_blue_concrete',
    light: 'ae2:block/fluix_block',
    port: 'ae2:block/interface',
    display: 'ae2:block/interface',
};
const textureUv = {
    body: [6, 6, 10, 10],
    frame: [1, 1, 2, 2],
    metal: [4, 4, 5, 5],
    belt: [1, 1, 2, 2],
    steel: [0, 2, 1, 14], // Opaque quartz frame strip, not the assembler texture's transparent center.
    display: [3, 3, 13, 13],
    light: [3, 3, 13, 13],
};
// AE2 15's 16px sprite has the silver rim at x=1; x=0 is its dark outer border.
if (forge) textureUv.steel = [1, 2, 2, 14];
const directions = ['down', 'up', 'north', 'south', 'west', 'east'];
let cubes;
const box = (name, from, to, texture) => cubes.push({ name, from, to, texture });

function cabinet(x, z) {
    box('Foot plinth', [x + 0.5, 0, z + 0.5], [x + 15.5, 1, z + 15.5], 'frame');
    box('ME machine casing', [x + 1, 1, z + 1], [x + 15, 10, z + 15], 'body');
    box('Bench lip', [x, 10, z], [x + 16, 11, z + 16], 'frame');
    for (const dx of [0.5, 14]) {
        for (const dz of [0.5, 14]) {
            box('Corner post', [x + dx, 1, z + dz], [x + dx + 1.5, 10, z + dz + 1.5], 'frame');
            box('Corner fastener', [x + dx + 0.375, 8.5, z + dz - 0.1], [x + dx + 1.125, 9.25, z + dz + 1.6], 'steel');
        }
    }
    box('Inset ME panel surround', [x + 4, 1.5, z + 0.75], [x + 12, 9.5, z + 1], 'frame');
    box('ME interface panel', [x + 4.5, 2, z + 0.5], [x + 11.5, 9, z + 0.8], 'port');
    box('Fluix status bus', [x + 4, 1.4, z + 0.3], [x + 12, 1.9, z + 0.6], 'light');
    box('Side service panel', [x + 15, 2, z + 4.5], [x + 15.25, 9, z + 11.5], 'port');
    box('AE network socket', [x + 5, 3, z + 15], [x + 11, 9, z + 15.25], 'port');
    box('AE status light', [x + 1, 7, z + 0.2], [x + 2, 8, z + 0.5], 'light');
}

function belt(x, z, width, depth) {
    box('Conveyor bed', [x, 11, z], [x + width, 12, z + depth], 'dark');
    box('Conveyor rail left', [x - 0.5, 11, z], [x, 12.5, z + depth], 'steel');
    box('Conveyor rail right', [x + width, 11, z], [x + width + 0.5, 12.5, z + depth], 'steel');
    box('Fluix transfer rail', [x - 0.4, 12.5, z], [x - 0.1, 12.8, z + depth], 'light');
    for (let row = z + 0.3; row < z + depth; row += 1.25) {
        box('Conveyor roller', [x + 0.15, 12, row], [x + width - 0.15, 12.4, Math.min(row + 0.7, z + depth)], 'belt');
    }
}

function pallet(x, z, w = 5, d = 4) {
    box('Fixture pallet', [x, 12.5, z], [x + w, 13.1, z + d], 'steel');
    box('Fixture inset', [x + 0.5, 13.1, z + 0.5], [x + w - 0.5, 13.4, z + d - 0.5], 'dark');
    for (const dx of [0, w - 0.7]) {
        box('Fixture clamp', [x + dx, 13.1, z + 1], [x + dx + 0.7, 14, z + d - 1], 'steel');
    }
}

function spindle(x, z, top) {
    box('Fluix assembly core', [x - 1.35, top - 4.4, z - 1.35], [x + 1.35, top - 0.6, z + 1.35], 'light');
    box('Core upper cap', [x - 2, top - 0.5, z - 2], [x + 2, top, z + 2], 'steel');
    box('Core lower cap', [x - 2, top - 5, z - 2], [x + 2, top - 4.5, z + 2], 'steel');
    for (const dx of [-2, 1.4]) for (const dz of [-2, 1.4]) {
        box('Core cage post', [x + dx, top - 4.5, z + dz], [x + dx + 0.6, top - 0.5, z + dz + 0.6], 'steel');
    }
    box('Spindle shaft', [x - 0.75, top - 7, z - 0.75], [x + 0.75, top - 5, z + 0.75], 'steel');
    box('Tool tip', [x - 0.4, top - 8, z - 0.4], [x + 0.4, top - 7, z + 0.4], 'dark');
}

function ammo() {
    cabinet(0, 0);
    cabinet(16, 0);
    box('Quartz worktop', [0, 11, 0], [32, 11.5, 16], 'steel');
    box('Cross-feed conveyor bed', [1.5, 11.5, 4], [30.5, 12, 11], 'dark');
    for (const z of [3.5, 11]) {
        box('Cross-feed rail', [1.5, 11.5, z], [30.5, 12.5, z + 0.5], 'steel');
    }
    box('Fluix cross-feed bus', [1.5, 12.5, 3.6], [30.5, 12.8, 3.9], 'light');
    for (let x = 2; x < 30; x += 1.25) {
        box('Cross-feed roller', [x, 12, 4.2], [x + 0.7, 12.4, 10.8], 'belt');
    }
    for (const station of [7, 21]) {
        pallet(station, 5, 5, 5);
        for (const x of [station + 1.2, station + 3.2]) for (const z of [6.2, 8.2]) {
            box('Cartridge case', [x, 13.4, z], [x + 0.65, 14.7, z + 0.65], 'brass');
            if (station === 21) {
                box('Cartridge tip', [x + 0.1, 14.7, z + 0.1], [x + 0.55, 15.1, z + 0.55], 'copper');
            }
        }
    }
    for (const x of [17, 28]) {
        box('Press pillar', [x, 11.5, 10], [x + 2, 21.5, 13], 'frame');
        box('Press guide', [x + 0.6, 13, 9.7], [x + 1.4, 20, 10], 'steel');
    }
    box('Quartz press bridge', [16.5, 20.5, 4.5], [30.5, 22, 13.5], 'steel');
    box('Press bridge front rail', [16.5, 20.5, 4], [30.5, 21.5, 4.5], 'frame');
    box('Press fluix channel', [18, 20.8, 3.8], [29, 21.3, 4], 'light');
    spindle(23.5, 8, 24);
    box('Case hopper', [2, 11.5, 11.5], [6, 18, 15.5], 'body');
    box('Hopper well', [2.5, 18, 12], [5.5, 18.2, 15], 'dark');
    box('Feed chute', [4.5, 13.5, 8], [8.5, 14.5, 12], 'steel');
    for (const x of [3, 4.5]) {
        box('Hopper cases', [x, 18.2, 12.5], [x + 0.7, 19, 13.2], 'brass');
    }
    box('Feeder display stand', [11, 11.5, 13.5], [12.5, 16.5, 15], 'frame');
    box('Feeder display housing', [8.5, 16, 13], [15, 19.5, 14], 'frame');
    box('Feeder ME display', [9, 16.5, 12.8], [14.5, 19, 13], 'display');
}

function attachment() {
    cabinet(0, 0);
    cabinet(16, 0);
    belt(2, 2, 28, 10);
    // The conveyor crosses the two cabinets; these fixtures distinguish it from the press.
    pallet(5, 4, 7, 5);
    pallet(20, 4, 7, 5);
    box('Optic housing', [7, 13.4, 5], [10, 15.4, 8], 'frame');
    box('Optic glass', [7.5, 14, 4.8], [9.5, 15, 5], 'cyan');
    box('Accessory body', [22, 13.4, 5], [25, 14.7, 8], 'dark');
    box('Accessory rail', [22.5, 14.7, 5.3], [24.5, 15.1, 7.7], 'metal');
    for (const x of [1, 28.5]) {
        box('Gantry foot', [x - 0.5, 11, 12.5], [x + 3, 12, 16], 'frame');
        box('Quartz gantry upright', [x, 12, 13], [x + 2.5, 24, 15.5], 'steel');
        box('Upright inset', [x + 0.6, 13, 12.8], [x + 1.9, 22, 13], 'frame');
    }
    box('Quartz gantry beam', [3.5, 21, 13], [28.5, 23.5, 15.5], 'steel');
    box('Gantry slide', [3.5, 23, 12.3], [28.5, 23.8, 13], 'steel');
    box('Gantry fluix channel', [3.5, 21.8, 12.8], [28.5, 22.4, 13], 'light');
    box('Spindle reach', [15, 21.5, 6], [17, 23, 14], 'frame');
    spindle(16, 7, 24);
    box('Display housing', [19, 15.5, 13], [27, 20, 14], 'frame');
    box('ME display', [19.75, 16.2, 12.8], [26.25, 19.3, 13], 'display');
    box('Display readout', [20.5, 17.2, 12.6], [24.5, 17.7, 12.8], 'steel');
    box('Front display surround', [4, 5.5, 0.2], [11, 8.5, 0.5], 'frame');
    box('Front ME display', [4.5, 6, 0], [10.5, 8, 0.2], 'display');
}

function gun() {
    for (const x of [0, 16]) for (const z of [0, 16]) cabinet(x, z);
    box('Quartz perimeter front', [0, 11, 0], [32, 12, 1.5], 'steel');
    box('Quartz perimeter back', [0, 11, 30.5], [32, 12, 32], 'steel');
    box('Quartz perimeter left', [0, 11, 1.5], [1.5, 12, 30.5], 'steel');
    box('Quartz perimeter right', [30.5, 11, 1.5], [32, 12, 30.5], 'steel');
    belt(3, 3, 6, 24);
    belt(23, 3, 6, 24);
    box('Assembly deck', [10, 11, 5], [22, 12, 26], 'metal');
    pallet(10, 8, 12, 9);
    // Abstract workpiece, not a functional firearm model.
    box('Workpiece receiver', [12, 13.4, 11], [19, 15, 13], 'frame');
    box('Workpiece stock', [10.5, 13.4, 10.5], [13, 14.5, 13.5], 'dark');
    box('Workpiece barrel', [19, 14, 11.5], [22, 14.8, 12.3], 'metal');
    box('Workpiece grip', [14, 13.4, 13], [15.5, 14.4, 15], 'dark');
    for (const x of [4.5, 24.5]) {
        pallet(x - 0.25, 19, 4, 5);
        box('Parts cassette', [x, 13.4, 20], [x + 3, 15, 23], 'frame');
    }
    for (const x of [1.5, 28.5]) {
        for (const z of [4, 28]) {
            box('Gantry column', [x, 12, z], [x + 2, 26, z + 2], 'frame');
            box('Gantry column highlight', [x + 0.65, 13, z - 0.2], [x + 1.35, 24.5, z], 'steel');
        }
        box('Longitudinal gantry rail', [x, 24.5, 4], [x + 2, 26, 30], 'metal');
        box('Longitudinal slide', [x + 0.5, 26, 4], [x + 1.5, 26.5, 30], 'steel');
    }
    box('Front traverse', [3.5, 24, 8], [28.5, 25.5, 10], 'metal');
    box('Rear traverse', [3.5, 24, 21], [28.5, 25.5, 23], 'metal');
    spindle(16, 9, 28);
    spindle(7, 22, 28);
    box('ME control backplane', [3.5, 12, 30], [28.5, 27, 32], 'body');
    box('ME control backplane inset', [4.5, 14, 29.7], [27.5, 26, 30], 'frame');
    for (const x of [5, 13, 21]) {
        box('ME processor panel', [x, 17, 29.4], [x + 6, 23, 29.7], 'port');
    }
    box('Backplane fluix bus', [5, 25, 29.4], [27, 25.7, 29.7], 'light');
    box('Parts tray', [11, 12, 23], [21, 13.2, 28], 'steel');
    box('Tool tray inset', [11.5, 13.2, 23.5], [20.5, 13.4, 27.5], 'dark');
    for (const x of [12, 15, 18]) {
        box('Tray divider', [x, 13.4, 24], [x + 0.5, 14.2, 27], 'steel');
    }
}

function universal() {
    for (const x of [0, 16]) for (const z of [0, 16]) cabinet(x, z);
    for (const z of [0, 30.5]) {
        box('Universal bench rim', [0, 11, z], [32, 12, z + 1.5], 'steel');
    }
    for (const x of [0, 30.5]) {
        box('Universal bench rim', [x, 11, 1.5], [x + 1.5, 12, 30.5], 'steel');
    }
    belt(3, 3, 6, 25);
    belt(23, 3, 6, 25);
    box('Interchangeable assembly deck', [10, 11, 6], [22, 12.5, 27], 'metal');
    pallet(10.5, 10.5, 11, 11);
    box('Universal fixture', [13, 13.4, 13], [19, 14.2, 19], 'frame');
    box('Universal fixture inlay', [14, 14.2, 14], [18, 14.4, 18], 'light');
    for (const x of [1.5, 28.5]) {
        for (const z of [3, 28]) {
            box('Universal gantry column', [x, 12, z], [x + 2, 26, z + 2], 'frame');
            box('Column highlight', [x + 0.65, 13, z - 0.2], [x + 1.35, 25, z], 'steel');
        }
        box('Universal longitudinal rail', [x, 25, 3], [x + 2, 26.5, 30], 'steel');
    }
    for (const z of [7, 24]) {
        box('Universal cross rail', [3.5, 25, z], [28.5, 26.5, z + 2], 'steel');
    }
    box('Universal tool slide', [15, 25, 7], [17, 26.5, 26], 'metal');
    spindle(16, 16, 28);
    for (const x of [4, 24]) {
        pallet(x, 5, 4, 5);
        pallet(x, 21, 4, 5);
    }
    box('Ammunition feed cassette', [4.5, 13.4, 5.5], [7.5, 14, 9.5], 'steel');
    for (const z of [6, 7.5]) {
        box('Ammunition feed cases', [5.2, 14, z], [6.8, 15.3, z + 0.7], 'brass');
    }
    box('Attachment feed cassette', [24.5, 13.4, 5.5], [27.5, 15.5, 9.5], 'frame');
    box('Attachment feed lens', [25, 14, 5.3], [27, 15, 5.5], 'cyan');
    for (const x of [4.5, 24.5]) {
        box('Modular parts cassette', [x, 13.4, 21.5], [x + 3, 15, 25.5], 'frame');
        box('Cassette identifier', [x + 0.5, 15, 22], [x + 2.5, 15.2, 23], 'light');
    }
    box('Universal control housing', [10.5, 6, 0], [21.5, 10, 1], 'frame');
    box('Universal ME control display', [11, 6.5, 0], [21, 9.5, 0.2], 'display');
    for (const x of [12, 15, 18]) {
        box('Recipe type indicator', [x, 7.2, 0], [x + 1.5, 8.7, 0.1], 'light');
    }
}

const definitions = [
    { kind: 'ammo', width: 2, depth: 1, height: 24, create: ammo },
    { kind: 'attachment', width: 2, depth: 1, height: 24, create: attachment },
    { kind: 'gun', width: 2, depth: 2, height: 28, create: gun },
    { kind: 'universal', width: 2, depth: 2, height: 28, create: universal },
];

// Minecraft's unrotated face UV axes and reversed directions.
const faceAxes = {
    down: [0, 2, false, true], up: [0, 2, false, false],
    north: [0, 1, true, true], south: [0, 1, false, true],
    west: [2, 1, false, true], east: [2, 1, true, true],
};

function faceUv(cube, face, from = cube.from, to = cube.to) {
    const [u, v, flipU, flipV] = faceAxes[face];
    const crop = textureUv[cube.texture] ?? [0, 0, 16, 16];
    const size = [u, v].map(axis => cube.to[axis] - cube.from[axis]);
    // Center-crop to the face's aspect ratio instead of squeezing a square sprite onto it.
    const scale = Math.min((crop[2] - crop[0]) / size[0], (crop[3] - crop[1]) / size[1]);
    const start = size.map((length, i) => (crop[i] + crop[i + 2] - length * scale) / 2);
    const uv = [];
    for (const [i, axis, flip] of [[0, u, flipU], [1, v, flipV]]) {
        uv[i] = start[i] + (flip ? cube.to[axis] - to[axis] : from[axis] - cube.from[axis]) * scale;
        uv[i + 2] = start[i] + (flip ? cube.to[axis] - from[axis] : to[axis] - cube.from[axis]) * scale;
    }
    return uv;
}

function element(cube, from = cube.from, to = cube.to, clipOrigin = null) {
    // Cell fragments sample only their original texture region; scaled item models keep all of it.
    const textureFrom = clipOrigin ? from.map((value, i) => value + clipOrigin[i]) : cube.from;
    const textureTo = clipOrigin ? to.map((value, i) => value + clipOrigin[i]) : cube.to;
    return {
        name: cube.name, from, to,
        ...(cube.texture === 'light' ? { shade: false } : {}),
        faces: Object.fromEntries(directions.map(face => [face, {
            uv: faceUv(cube, face, textureFrom, textureTo), texture: `#${cube.texture}`,
            ...(cube.texture === 'light' ? {
                forge_data: { block_light: 15, sky_light: 15 },
            } : {}),
        }])),
    };
}

if (check) {
    const sample = { name: 'UV check', from: [0, 0, 0], to: [32, 32, 32], texture: 'port' };
    for (const face of directions) {
        const [u, v, flipU, flipV] = faceAxes[face];
        for (const [i, axis, flip] of [[0, u, flipU], [1, v, flipV]]) {
            const half = [...sample.to];
            half[axis] = 16;
            const uv = faceUv(sample, face, sample.from, half);
            assert.deepEqual([uv[i], uv[i + 2]], flip ? [8, 16] : [0, 8], `Clipped ${face} UV`);
        }
        const wide = { ...sample, to: [32, 8, 16] };
        const uv = faceUv(wide, face);
        assert.ok(Math.abs((uv[2] - uv[0]) / wide.to[u] - (uv[3] - uv[1]) / wide.to[v]) < 1e-9,
            `Stretched ${face} texture`);
    }
    assert.deepEqual(element(sample, [0, 0, 0], [16, 16, 16]).faces, element(sample).faces, 'Scaled icon UV');
    assert.deepEqual(element(sample, [0, 0, 0], [16, 16, 16], [16, 0, 0]).faces.north.uv,
        [0, 8, 8, 16], 'Cell-local UV offset');
}

let files = 0;
function emit(path, value) {
    const text = JSON.stringify(value, null, 2) + '\n';
    if (check) assert.equal(readFileSync(path, 'utf8'), text, `Regenerate ${path}`);
    else {
        mkdirSync(dirname(path), { recursive: true });
        writeFileSync(path, text);
    }
    files++;
}

if (!forge) emit(resolve(assets, 'models/block/molecular_assembler_empty.json'), { textures: { particle: textures.particle }, elements: [] });
for (const def of definitions) {
    cubes = [];
    def.create();
    if (forge && check) {
        const shared = JSON.parse(readFileSync(resolve(here, `models/molecular_assembler_${def.kind}.json`), 'utf8'));
        const geometry = values => values.map(({ name, from, to }) => ({ name, from, to }));
        assert.deepEqual(geometry(cubes), geometry(shared.elements), `Forge geometry changed: ${def.kind}`);
        assert.deepEqual(textures, shared.textures, `Forge must resolve the native AE2 texture IDs: ${def.kind}`);
    }
    for (const cube of cubes) {
        assert.ok(textures[cube.texture], `Unknown texture: ${cube.texture}`);
        for (let axis = 0; axis < 3; axis++) {
            assert.ok(cube.to[axis] > cube.from[axis], `Empty ${cube.name}`);
            assert.ok(cube.from[axis] >= 0, `Negative ${cube.name}`);
            assert.ok(cube.to[axis] <= [def.width * 16, def.height, def.depth * 16][axis], `Oversize ${cube.name}`);
        }
    }
    const id = `molecular_assembler_${def.kind}`;
    const variants = {};
    let clippedVolume = 0;
    for (let y = 0; y < 2; y++) for (let z = 0; z < def.depth; z++) for (let x = 0; x < def.width; x++) {
        const part = y * def.width * def.depth + z * def.width + x;
        const origin = [x * 16, y * 16, z * 16];
        const elements = [];
        for (const cube of cubes) {
            const from = cube.from.map((v, i) => Math.max(v, origin[i]) - origin[i]);
            const to = cube.to.map((v, i) => Math.min(v, origin[i] + 16) - origin[i]);
            if (to.some((v, i) => v <= from[i])) continue;
            clippedVolume += to.reduce((v, n, i) => v * (n - from[i]), 1);
            elements.push(element(cube, from, to, origin));
        }
        assert.ok(elements.length, `Invisible occupied cell: ${id} part ${part}`);
        emit(resolve(assets, `models/block/${id}_${part}.json`), { textures, elements });
        for (const [facing, rotation] of [['north', 0], ['east', 90], ['south', 180], ['west', 270]]) {
            variants[`facing=${facing},part=${part}`] = { model: `appliedtacz:block/${id}_${part}`, y: rotation };
        }
    }
    const volume = cubes.reduce((sum, c) => sum + c.to.reduce((v, n, i) => v * (n - c.from[i]), 1), 0);
    assert.ok(Math.abs(volume - clippedVolume) < 0.0001, `Cell clipping lost geometry: ${id}`);
    // The shared BlockState property has eight values; placement uses only the real cells.
    for (let part = def.width * def.depth * 2; part < 8; part++) {
        for (const facing of ['north', 'east', 'south', 'west']) {
            variants[`facing=${facing},part=${part}`] = { model: 'appliedtacz:block/molecular_assembler_empty' };
        }
    }
    assert.equal(Object.keys(variants).length, 32);
    // Forge overrides only the visible models; blockstates and the empty model remain shared.
    if (!forge) emit(resolve(assets, `blockstates/${id}.json`), { variants });
    // Full-size editable Java Block/Item model: import into Blockbench, no plugin required.
    emit(resolve(models, `${id}.json`), { credit: 'AppliedTaCZ assembly-line models', textures, elements: cubes.map(c => element(c)) });
    const scale = 16 / Math.max(def.width * 16, def.depth * 16, def.height);
    const center = [def.width * 8, def.height / 2, def.depth * 8];
    const icon = cubes.map(c => element(c,
        c.from.map((v, i) => Math.round(((v - center[i]) * scale + 8) * 10000) / 10000),
        c.to.map((v, i) => Math.round(((v - center[i]) * scale + 8) * 10000) / 10000)));
    emit(resolve(assets, `models/item/${id}.json`), {
        parent: 'minecraft:block/block', textures, elements: icon,
        display: {
            gui: { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.625, 0.625, 0.625] },
            ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.25, 0.25, 0.25] },
            fixed: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
            thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
            firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
        },
    });
    console.log(`${id}: ${cubes.length} cuboids, ${def.width * def.depth * 2} occupied parts, ${def.width}x${def.depth} footprint`);
}
console.log(`${check ? 'Verified' : 'Generated'} ${files} model/blockstate files.`);
