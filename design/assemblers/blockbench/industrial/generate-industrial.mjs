import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { buildStation } from './stations.mjs';

const check = process.argv.includes('--check');
const clone = value => JSON.parse(JSON.stringify(value));
const materials = {
    white: [50, 50, 62, 62], frame: [18, 2, 30, 14], steel: [33, 3, 47, 13],
    dark: [50, 2, 62, 14], cyan: [33, 18, 47, 30], violet: [50, 18, 62, 30],
    bluegray: [49, 33, 63, 47], service: [1, 33, 15, 47],
    copper: [1, 17, 15, 31], brass: [17, 17, 31, 31],
    casing: [22, 54, 26, 58], casingFrame: [17, 1, 18, 2],
};
const faceAxes = {
    north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2],
};

for (const kind of ['ammo', 'attachment', 'gun', 'universal']) {
    const input = new URL(`../animated/molecular_assembler_${kind}_animated.bbmodel`, import.meta.url);
    const source = JSON.parse(readFileSync(input, 'utf8'));
    const model = clone(source);
    const retainedGroups = source.groups.filter(g => /^(payload_|conveyor_belt_)/.test(g.name));
    const retainedIds = new Set(retainedGroups.map(g => g.uuid));
    const movingCubes = new Set();
    const findNode = (nodes, id) => {
        for (const node of nodes) {
            if (typeof node === 'string') continue;
            if (node.uuid === id) return node;
            const found = findNode(node.children, id);
            if (found) return found;
        }
    };
    const collect = node => typeof node === 'string' ? movingCubes.add(node) : node.children.forEach(collect);
    for (const g of retainedGroups) collect(findNode(source.outliner, g.uuid));
    const keepStatic = cube => cube.to[1] <= 11 || cube.name === 'cube'
        || /^(Quartz worktop|Quartz perimeter |Universal bench rim|Assembly deck$|Interchangeable assembly deck$)/.test(cube.name)
        || /^(Cross-feed conveyor bed|Cross-feed rail|Fluix cross-feed bus|Fluix transfer rail|Conveyor (bed|rail)|Conveyor .*interface)/.test(cube.name);
    model.elements = model.elements.filter(c => movingCubes.has(c.uuid)
        || (keepStatic(c) && !(kind !== 'ammo' && c.to[1] <= 10)));
    const frame = clone(source.groups.find(g => g.name === 'machine_frame'));
    assert.ok(frame, `${kind}: machine_frame missing`);
    model.groups = [frame, ...clone(retainedGroups)];
    model.outliner = [{ uuid: frame.uuid, isOpen: true, children: [
        ...model.elements.filter(c => !movingCubes.has(c.uuid)).map(c => c.uuid),
        ...retainedGroups.map(g => clone(findNode(source.outliner, g.uuid))),
    ] }];
    const animation = model.animations[0];
    animation.animators = Object.fromEntries(Object.entries(animation.animators).filter(([id]) => retainedIds.has(id)));
    assert.equal(animation.length, 20, 'Expected the existing tray transport cycle');
    let serial = 0;
    const uuid = label => {
        const hash = createHash('sha256').update(`${kind}:${label}:${serial++}`).digest('hex').slice(0, 32);
        return `${hash.slice(0, 8)}-${hash.slice(8, 12)}-${hash.slice(12, 16)}-${hash.slice(16, 20)}-${hash.slice(20)}`;
    };
    const childList = parent => findNode(model.outliner, parent?.uuid ?? frame.uuid).children;
    function group(name, origin, parent = null) {
        const bone = { name: `industrial_${name}`, uuid: uuid(name), origin,
            rotation: [0, 0, 0], children: [], visibility: true, export: true, shade: true };
        model.groups.push(bone);
        childList(parent).push({ uuid: bone.uuid, isOpen: false, children: [] });
        return bone;
    }
    function box(name, from, to, material = 'white', parent = null) {
        const crop = materials[material];
        assert.ok(crop, `Unknown material ${material}`);
        const cube = { name, uuid: uuid(name), type: 'cube', from, to, origin: [0, 0, 0],
            rotation: [0, 0, 0], box_uv: false, autouv: 0, visibility: true, export: true, shade: true,
            faces: Object.fromEntries(Object.entries(faceAxes).map(([side, [u, v]]) => {
                const size = [to[u] - from[u], to[v] - from[v]];
                assert.ok(size.every(n => n > 0), `${name}: empty face`);
                const scale = Math.min((crop[2] - crop[0]) / size[0], (crop[3] - crop[1]) / size[1]);
                const x = (crop[0] + crop[2] - size[0] * scale) / 2;
                const y = (crop[1] + crop[3] - size[1] * scale) / 2;
                return [side, { uv: [x, y, x + size[0] * scale, y + size[1] * scale], texture: 0 }];
            })) };
        model.elements.push(cube);
        childList(parent).push(cube.uuid);
        return cube;
    }
    function track(bone, channel, frames) {
        assert.ok(frames.length > 1);
        const keys = frames.map(([time, values]) => ({ uuid: uuid(`${bone.name}:${channel}:${time}`),
            time, channel, interpolation: 'linear', data_points: [{ x: String(values[0]), y: String(values[1]), z: String(values[2]) }] }));
        if (keys[0].time > 0) keys.unshift({ ...clone(keys[0]), uuid: uuid('start'), time: 0 });
        if (keys.at(-1).time < 20) keys.push({ ...clone(keys[0]), uuid: uuid('end'), time: 20 });
        assert.deepEqual(keys[0].data_points, keys.at(-1).data_points, `${bone.name}: loop mismatch`);
        const animator = animation.animators[bone.uuid] ??= { name: bone.name, type: 'bone', keyframes: [] };
        animator.keyframes.push(...keys);
    }
    buildStation(kind, { box, group, track });

    if (kind !== 'ammo') {
        const depth = kind === 'attachment' ? 16 : 32;
        box('Plain lower casing', [1, 1, 1], [31, 10, depth - 1], 'casing');
        box('Continuous lower plinth', [0.5, 0, 0.5], [31.5, 1, depth - 0.5], 'casingFrame');
        for (const x of [0.5, 30]) for (const z of [0.5, depth - 2]) {
            box('Lower corner frame', [x, 1, z], [x + 1.5, 10, z + 1.5], 'casingFrame');
        }
        for (const edge of [0.5, depth - 2]) {
            box('Lower panel divider', [15.25, 1, edge], [16.75, 10, edge + 1.5], 'casingFrame');
            if (depth === 32) {
                box('Lower panel divider', [edge, 1, 15.25], [edge + 1.5, 10, 16.75], 'casingFrame');
            }
        }
    }
    for (const x of kind === 'ammo' ? [1.8, 29] : []) {
        box('Recessed service vent surround', [x, 3, 0.12], [x + 1.2, 6.7, 0.3], 'frame');
        for (let y = 3.4; y < 6.5; y += 0.65) {
            box('Service vent fin', [x + 0.2, y, 0.05], [x + 1, y + 0.18, 0.12], 'steel');
        }
    }
    model.name = `industrial_assembler_${kind}`;
    model.credit = 'AppliedTaCZ industrial assembly-line concept; AE2 interface images retained';
    model.meta.model_format = 'free';
    for (const texture of model.textures) {
        assert.ok(texture.relative_path.startsWith('../assets/'), 'Texture must remain linked to the existing asset folder');
    }
    const bounds = [32, kind === 'ammo' || kind === 'attachment' ? 24 : 28, kind === 'ammo' || kind === 'attachment' ? 16 : 32];
    for (const c of model.elements) for (let a = 0; a < 3; a++) {
        assert.ok(c.from[a] >= 0 && c.to[a] <= bounds[a] && c.to[a] > c.from[a], `${kind}: out-of-bounds ${c.name}`);
    }
    const output = new URL(`${model.name}.bbmodel`, import.meta.url);
    const text = `${JSON.stringify(model)}\n`;
    if (check) assert.ok(readFileSync(output, 'utf8') === text, `Regenerate ${model.name}`);
    else { mkdirSync(new URL('.', output), { recursive: true }); writeFileSync(output, text); }
    console.log(`${model.name}: ${model.elements.length} cubes, ${model.groups.length} groups, 20s loop${check ? ' verified' : ''}`);
}
