import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';

const variants = ['ammo', 'attachment', 'gun', 'universal'];
const textures = {
    assembler_materials: ['appliedtacz', 'block/assembler_materials.png', 64],
    interface_part_front: ['ae2', 'part/interface.png', 16],
    interface_part_sides: ['ae2', 'part/interface_sides.png', 16],
    interface_part_sidesStatus: ['ae2', 'part/monitor_sides_status.png', 16],
    interface_part_back: ['ae2', 'part/interface_back.png', 16],
    interface_block: ['ae2', 'block/interface.png', 16],
};
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

function read(path) {
    const url = new URL(path, import.meta.url);
    assert.ok(existsSync(url), `missing ${path}`);
    return JSON.parse(readFileSync(url, 'utf8'));
}

function descendants(outliner, uuid) {
    const visit = nodes => {
        for (const node of nodes) {
            if (typeof node === 'string') continue;
            if (node.uuid === uuid) return node.children;
            const found = visit(node.children);
            if (found) return found;
        }
    };
    const collect = nodes => nodes.flatMap(node => typeof node === 'string' ? [node] : collect(node.children));
    const group = visit(outliner, uuid);
    assert.ok(group, `missing outliner group ${uuid}`);
    return collect(group).sort();
}

function validate(variant) {
    const name = `industrial_assembler_${variant}`;
    const model = read(`${name}.bbmodel`);
    const original = read(`../animated/molecular_assembler_${variant}_animated.bbmodel`);
    const fail = message => `${name}: ${message}`;
    const bounds = variant === 'ammo' || variant === 'attachment' ? [32, 24, 16] : [32, 28, 32];

    assert.equal(model.meta?.model_format, 'free', fail('model must use Free format'));
    assert.deepEqual(model.resolution, { width: 64, height: 64 }, fail('project resolution changed'));
    assert.equal(model.textures?.length, 6, fail('expected six linked textures'));
    assert.deepEqual(model.textures, original.textures, fail('texture links changed'));
    const textureByUuid = new Map(model.textures.map(texture => [texture.uuid, texture]));
    for (const texture of model.textures) {
        const expected = textures[texture.id];
        assert.ok(expected, fail(`unexpected texture ${texture.id}`));
        const [namespace, path, uv] = expected;
        assert.equal(texture.namespace, namespace, fail(`wrong namespace on ${texture.id}`));
        assert.equal(texture.relative_path, `../assets/${namespace}/textures/${path}`, fail(`wrong path on ${texture.id}`));
        assert.equal(texture.mode, 'link', fail(`${texture.id} must be linked`));
        assert.notEqual(texture.internal, true, fail(`${texture.id} must not be internal`));
        assert.ok(!texture.source?.startsWith('data:'), fail(`${texture.id} must not be embedded`));
        assert.equal(texture.uv_width, uv, fail(`wrong UV width on ${texture.id}`));
        assert.equal(texture.uv_height, uv, fail(`wrong UV height on ${texture.id}`));
        const png = readFileSync(new URL(texture.relative_path, new URL(`${name}.bbmodel`, import.meta.url)));
        assert.ok(png.length >= 24 && png.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) &&
            png.readUInt32BE(8) === 13 && png.toString('ascii', 12, 16) === 'IHDR' &&
            png.readUInt32BE(16) > 0 && png.readUInt32BE(20) > 0, fail(`invalid PNG ${texture.id}`));
        assert.equal(texture.width, png.readUInt32BE(16), fail(`PNG width mismatch on ${texture.id}`));
        assert.equal(texture.height, png.readUInt32BE(20), fail(`PNG height mismatch on ${texture.id}`));
    }
    assert.equal(textureByUuid.size, model.textures.length, fail('duplicate texture UUID'));

    const cubes = new Map(model.elements.map(cube => [cube.uuid, cube]));
    if (variant !== 'ammo') {
        const lower = model.elements.filter(cube => cube.to[1] <= 10);
        assert.equal(lower.length, variant === 'attachment' ? 8 : 10,
            fail('plain lower casing must contain only its body, plinth and frame'));
        assert.ok(lower.every(cube => /^(Plain lower casing|Continuous lower plinth|Lower corner frame|Lower panel divider)$/.test(cube.name)),
            fail('decorative lower panels returned'));
        assert.equal(lower.filter(cube => cube.name === 'Lower corner frame').length, 4,
            fail('lower casing needs four corner frames'));
        assert.equal(lower.filter(cube => cube.name === 'Lower panel divider').length,
            variant === 'attachment' ? 2 : 4, fail('lower casing divider count changed'));
        const casing = lower.find(cube => cube.name === 'Plain lower casing');
        assert.deepEqual(casing?.from, [1, 1, 1], fail('lower casing origin changed'));
        assert.deepEqual(casing?.to, [31, 10, bounds[2] - 1],
            fail('lower casing must span the cabinet without an open center seam'));
    }
    const groups = new Map(model.groups.map(group => [group.uuid, group]));
    assert.equal(cubes.size, model.elements.length, fail('duplicate cube UUID'));
    assert.equal(groups.size, model.groups.length, fail('duplicate group UUID'));
    const seenCubes = new Set();
    const seenGroups = new Set();
    const parents = new Map();
    const walk = (nodes, parent) => nodes.forEach(node => {
        if (typeof node === 'string') {
            assert.ok(cubes.has(node) && !seenCubes.has(node), fail(`unmapped or repeated cube ${node}`));
            seenCubes.add(node);
            parents.set(node, parent);
        } else {
            assert.ok(groups.has(node.uuid) && !seenGroups.has(node.uuid), fail(`unmapped or repeated group ${node.uuid}`));
            seenGroups.add(node.uuid);
            parents.set(node.uuid, parent);
            walk(node.children, node.uuid);
        }
    });
    walk(model.outliner, null);
    assert.equal(seenCubes.size, cubes.size, fail('cube missing from outliner'));
    assert.equal(seenGroups.size, groups.size, fail('group missing from outliner'));
    for (const cube of model.elements) {
        assert.ok(!/^(?:Fluix assembly core|Core cage post|Core lower cap|Core upper cap|Spindle\b.*|Tool tip)$/i.test(cube.name),
            fail(`legacy machinery remains: ${cube.name}`));
        for (let axis = 0; axis < 3; axis++) {
            assert.ok(Number.isFinite(cube.from[axis]) && Number.isFinite(cube.to[axis]) &&
                cube.from[axis] >= 0 && cube.from[axis] < cube.to[axis] && cube.to[axis] <= bounds[axis],
                fail(`${cube.name} outside ${bounds.join('x')} bounds`));
        }
        for (const [side, face] of Object.entries(cube.faces)) {
            if (face.texture == null) continue;
            const resolved = typeof face.texture === 'number' || /^\d+$/.test(String(face.texture))
                ? model.textures[Number(face.texture)]?.uuid : face.texture;
            const texture = textureByUuid.get(resolved);
            assert.ok(texture, fail(`unresolved texture on ${cube.name}.${side}`));
            assert.ok(Array.isArray(face.uv) && face.uv.length === 4 && face.uv.every((value, index) =>
                Number.isFinite(value) && value >= 0 && value <= (index % 2 ? texture.uv_height : texture.uv_width)),
                fail(`invalid UV on ${cube.name}.${side}`));
        }
        if (/^Fixture (pallet|inset|clamp)$/.test(cube.name)) {
            let parent = parents.get(cube.uuid);
            while (parent && !groups.get(parent)?.name.startsWith('payload_')) parent = parents.get(parent);
            assert.ok(parent, fail(`${cube.name} is not inside a payload`));
        }
    }

    const animation = model.animations?.[0];
    assert.equal(model.animations?.length, 1, fail('expected one animation'));
    assert.equal(animation.name, 'animation.crafting', fail('animation name changed'));
    assert.equal(animation.loop, 'loop', fail('animation must loop'));
    assert.equal(animation.length, 20, fail('animation must last 20 seconds'));
    let movingIndustrial = 0;
    for (const [uuid, animator] of Object.entries(animation.animators ?? {})) {
        assert.ok(groups.has(uuid), fail(`animator references missing group ${uuid}`));
        const channels = new Map();
        for (const key of animator.keyframes ?? []) {
            assert.ok(['position', 'rotation', 'scale'].includes(key.channel), fail(`invalid channel in ${uuid}`));
            assert.ok(['linear', 'step'].includes(key.interpolation), fail(`invalid interpolation in ${uuid}`));
            assert.ok(Number.isFinite(key.time) && key.time >= 0 && key.time <= 20, fail(`invalid time in ${uuid}`));
            assert.ok(key.data_points?.length && key.data_points.every(point => ['x', 'y', 'z'].every(axis =>
                (typeof point[axis] === 'number' ||
                    (typeof point[axis] === 'string' && /^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?$/.test(point[axis]))) &&
                Number.isFinite(Number(point[axis])))), fail(`non-numeric key in ${uuid}`));
            const frames = channels.get(key.channel) ?? [];
            frames.push(key);
            channels.set(key.channel, frames);
        }
        for (const [channel, frames] of channels) {
            frames.sort((a, b) => a.time - b.time);
            assert.equal(frames[0].time, 0, fail(`${uuid} ${channel} must start at 0`));
            assert.equal(frames.at(-1).time, 20, fail(`${uuid} ${channel} must end at 20`));
            assert.ok(same(frames[0].data_points, frames.at(-1).data_points),
                fail(`${uuid} ${channel} loop endpoints differ`));
            if (groups.get(uuid).name.startsWith('industrial_') &&
                frames.some((frame, index) => index > 0 && frame.time >= 6 && frame.time <= 12 &&
                    !same(frame.data_points, frames[index - 1].data_points))) movingIndustrial++;
        }
        if (groups.get(uuid).name.startsWith('payload_')) {
            const scales = channels.get('scale') ?? [];
            assert.ok(scales.length && [scales[0], scales.at(-1)].every(frame =>
                frame.data_points.every(point => ['x', 'y', 'z'].every(axis => Number(point[axis]) === 0))),
                fail(`${groups.get(uuid).name} must be hidden at both loop boundaries`));
        }
    }
    assert.ok(movingIndustrial > 0, fail('no industrial motion during crafting'));

    const preserved = group => /^(payload_|conveyor_belt_)/.test(group.name);
    const groupsByName = new Map(model.groups.map(group => [group.name, group]));
    const beforeCubes = new Map(original.elements.map(cube => [cube.uuid, cube]));
    for (const before of original.groups.filter(preserved)) {
        const group = groupsByName.get(before.name);
        assert.ok(group, fail(`missing preserved group ${before.name}`));
        assert.deepEqual(group, before, fail(`${group.name} group changed`));
        assert.deepEqual(animation.animators[group.uuid], original.animations[0].animators[before.uuid],
            fail(`${group.name} animation changed`));
        assert.deepEqual(descendants(model.outliner, group.uuid), descendants(original.outliner, before.uuid),
            fail(`${group.name} contents changed`));
        for (const id of descendants(model.outliner, group.uuid)) {
            assert.deepEqual(cubes.get(id), beforeCubes.get(id), fail(`${group.name} cube changed: ${id}`));
        }
    }
    assert.ok(model.groups.some(group => group.name.startsWith('payload_')), fail('no payloads'));
    assert.ok(model.groups.some(group => group.name.startsWith('conveyor_belt_')), fail('no belts'));
    console.log(`${name}: OK`);
}

for (const variant of variants) validate(variant);
