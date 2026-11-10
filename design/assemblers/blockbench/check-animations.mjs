import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const variants = ['ammo', 'attachment', 'gun', 'universal'];

function readModel(path) {
    return JSON.parse(readFileSync(new URL(path, import.meta.url), 'utf8'));
}

function validateAtlas(model, modelPath, expectedPath, fail) {
    assert.notEqual(model.meta?.bitmaps, true, fail('textures must be linked'));
    assert.deepEqual(model.resolution, { width: 64, height: 64 }, fail('project resolution must be 64x64'));
    const ae2 = {
        interface_part_front: 'part/interface.png',
        interface_part_sides: 'part/interface_sides.png',
        interface_part_sidesStatus: 'part/monitor_sides_status.png',
        interface_part_back: 'part/interface_back.png',
        interface_block: 'block/interface.png',
    };
    assert.equal(model.textures.length, 6, fail('expected assembler atlas and five AE2 textures'));
    const pngs = new Map();
    for (const [index, texture] of model.textures.entries()) {
        const own = index === 0;
        const expected = own ? expectedPath :
            `${modelPath.startsWith('animated/') ? '../' : ''}assets/ae2/textures/${ae2[texture.id]}`;
        assert.ok(own ? texture.id === 'assembler_materials' : Object.hasOwn(ae2, texture.id),
            fail(`unexpected texture ${texture.id}`));
        assert.equal(texture.mode, 'link', fail(`${texture.id} must be linked`));
        assert.notEqual(texture.internal, true, fail(`${texture.id} must not be internal`));
        assert.equal(texture.namespace, own ? 'appliedtacz' : 'ae2', fail(`wrong namespace on ${texture.id}`));
        assert.equal(texture.relative_path, expected, fail(`wrong path on ${texture.id}`));
        assert.ok(!texture.source?.startsWith('data:'), fail(`${texture.id} must not be embedded`));
        const uv = own ? 64 : 16;
        assert.equal(texture.uv_width, uv, fail(`wrong UV width on ${texture.id}`));
        assert.equal(texture.uv_height, uv, fail(`wrong UV height on ${texture.id}`));
        const png = readFileSync(new URL(texture.relative_path, new URL(modelPath, import.meta.url)));
        assert.ok(png.length >= 24 && png.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) &&
            png.readUInt32BE(8) === 13 && png.toString('ascii', 12, 16) === 'IHDR',
            fail(`invalid PNG header on ${texture.id}`));
        assert.ok(png.readUInt32BE(16) > 0 && png.readUInt32BE(20) > 0,
            fail(`invalid PNG dimensions on ${texture.id}`));
        assert.equal(texture.width, png.readUInt32BE(16), fail(`PNG width mismatch on ${texture.id}`));
        assert.equal(texture.height, png.readUInt32BE(20), fail(`PNG height mismatch on ${texture.id}`));
        pngs.set(texture.id, png);
    }
    assert.equal(pngs.size, model.textures.length, fail('duplicate texture id'));

    for (const cube of model.elements) {
        for (const [side, face] of Object.entries(cube.faces)) {
            if (face.texture == null) continue;
            const reference = face.texture;
            const resolved = typeof reference === 'number' || /^\d+$/.test(String(reference))
                ? model.textures[Number(reference)]?.uuid
                : reference;
            const texture = model.textures.find(texture => texture.uuid === resolved);
            assert.ok(texture, fail(`unresolved texture on ${cube.name}.${side}`));
            if (/Conveyor.*interface/i.test(cube.name)) {
                assert.equal(texture.namespace, 'ae2', fail(`${cube.name}.${side} must use AE2 texture`));
            }
            assert.ok(Array.isArray(face.uv) && face.uv.length === 4 &&
                face.uv.every((value, axis) => Number.isFinite(value) && value >= 0 &&
                    value <= (axis % 2 === 0 ? texture.uv_width : texture.uv_height)),
                fail(`UV outside ${texture.id} on ${cube.name}.${side}`));
        }
    }
    return pngs;
}

function cubeGeometry(cube, textures) {
    const faces = Object.fromEntries(Object.entries(cube.faces).map(([side, face]) => {
        const texture = face.texture;
        const resolved = typeof texture === 'number' || /^\d+$/.test(String(texture))
            ? textures[Number(texture)]?.uuid
            : texture;
        return [side, { uv: face.uv, rotation: face.rotation, texture: resolved }];
    }));
    return {
        name: cube.name,
        from: cube.from,
        to: cube.to,
        origin: cube.origin,
        rotation: cube.rotation,
        faces,
    };
}

function validate(variant) {
    const name = `molecular_assembler_${variant}`;
    const sourcePath = `${name}.bbmodel`;
    const animatedPath = `animated/${name}_animated.bbmodel`;
    const source = readModel(sourcePath);
    const animated = readModel(animatedPath);
    const fail = (message) => `${name}: ${message}`;

    const sourcePngs = validateAtlas(source, sourcePath, 'assets/appliedtacz/textures/block/assembler_materials.png', fail);
    const animatedPngs = validateAtlas(animated, animatedPath, '../assets/appliedtacz/textures/block/assembler_materials.png', fail);
    for (const [id, png] of sourcePngs) {
        assert.ok(png.equals(animatedPngs.get(id)), fail(`static and animated ${id} textures differ`));
    }

    assert.equal(animated.meta?.model_format, 'free', fail('model must use free format'));
    assert.equal(animated.elements.length, source.elements.length, fail('cube count changed'));
    const cubes = new Map(animated.elements.map(cube => [cube.uuid, cube]));
    assert.equal(cubes.size, animated.elements.length, fail('duplicate cube UUID'));
    for (const cube of source.elements) {
        const copy = cubes.get(cube.uuid);
        assert.ok(copy, fail(`missing cube ${cube.uuid}`));
        assert.deepEqual(cubeGeometry(copy, animated.textures), cubeGeometry(cube, source.textures),
            fail(`cube geometry changed: ${cube.name} (${cube.uuid})`));
    }

    const groups = new Map(animated.groups.map(group => [group.uuid, group]));
    const parents = new Map();
    const visit = (nodes, parent) => nodes.forEach(node => {
        if (typeof node === 'string') parents.set(node, parent);
        else {
            parents.set(node.uuid, parent);
            visit(node.children, node.uuid);
        }
    });
    visit(animated.outliner);
    for (const cube of animated.elements.filter(cube => /^Fixture (pallet|inset|clamp)$/.test(cube.name))) {
        let parent = parents.get(cube.uuid);
        while (parent && !groups.get(parent)?.name.startsWith('payload_')) parent = parents.get(parent);
        assert.ok(parent, fail(`${cube.name} must travel with a payload`));
    }
    if (variant === 'gun') {
        for (const model of [source, animated]) {
            assert.ok(!model.elements.some(cube => ['Parts tray', 'Tool tray inset', 'Tray divider'].includes(cube.name)),
                fail('obsolete gun tray remains'));
        }
    }
    for (const cube of animated.elements.filter(cube => cube.name === 'Cassette identifier')) {
        const expected = cube.from[0] < 16 ? 'payload_2' : 'payload_3';
        assert.equal(groups.get(parents.get(cube.uuid))?.name, expected,
            fail('cassette identifier must travel with its cassette'));
    }
    for (const group of source.groups ?? []) {
        assert.equal(groups.get(group.uuid)?.name, group.name,
            fail(`source group changed: ${group.name}`));
    }

    assert.equal(animated.animations?.length, 1, fail('expected exactly one animation'));
    const animation = animated.animations[0];
    assert.equal(animation.name, 'animation.crafting', fail('animation must be named animation.crafting'));
    assert.equal(animation.loop, 'loop', fail('animation must loop'));
    assert.equal(animation.length, 20, fail('animation must last 20 seconds'));
    assert.ok(Object.values(animation.animators ?? {}).some(animator => animator.keyframes?.length),
        fail('animation has no keyframes'));

    let movingBelts = 0;
    let movingPayloads = 0;
    for (const [uuid, animator] of Object.entries(animation.animators)) {
        assert.ok(groups.has(uuid), fail(`animator references missing group ${uuid}`));
        const groupName = groups.get(uuid).name;
        const channels = new Map();
        for (const keyframe of animator.keyframes ?? []) {
            assert.ok(['position', 'rotation', 'scale'].includes(keyframe.channel),
                fail(`unsupported channel ${keyframe.channel}`));
            assert.ok(Number.isFinite(keyframe.time) && keyframe.time >= 0 && keyframe.time <= animation.length,
                fail(`invalid keyframe time in ${uuid}`));
            assert.ok(keyframe.data_points?.length, fail(`missing data points in ${uuid}`));
            const points = keyframe.data_points.map(point => {
                const values = {};
                for (const axis of ['x', 'y', 'z']) {
                    const value = point[axis];
                    assert.ok(typeof value === 'number' ||
                        (typeof value === 'string' && /^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?$/.test(value)),
                        fail(`non-numeric ${keyframe.channel}.${axis} in ${uuid}`));
                    values[axis] = Number(value);
                    assert.ok(Number.isFinite(values[axis]),
                        fail(`non-numeric ${keyframe.channel}.${axis} in ${uuid}`));
                }
                if (keyframe.channel === 'scale') {
                    assert.ok(Object.values(values).every(value => value > 0) ||
                        (groupName.startsWith('payload_') && Object.values(values).every(value => value === 0)),
                        fail(`invalid scale in ${groupName}`));
                }
                return values;
            });
            const frames = channels.get(keyframe.channel) ?? [];
            frames.push({ time: keyframe.time, interpolation: keyframe.interpolation, points });
            channels.set(keyframe.channel, frames);
        }
        for (const [channel, frames] of channels) {
            frames.sort((a, b) => a.time - b.time);
            assert.equal(frames[0].time, 0, fail(`${uuid} ${channel} must start at 0`));
            assert.equal(frames.at(-1).time, animation.length,
                fail(`${uuid} ${channel} must end at animation length`));
            assert.deepEqual(frames[0].points, frames.at(-1).points,
                fail(`${uuid} ${channel} must return to its starting value`));
        }
        const positions = channels.get('position') ?? [];
        if (groupName.startsWith('conveyor_belt_') &&
            positions.some(frame => !isSamePosition(frame, positions[0]))) movingBelts++;
        if (groupName.startsWith('payload_')) {
            const scales = channels.get('scale') ?? [];
            const reset = positions.find((frame, index) => index > 0 && frame.interpolation === 'step' &&
                isSamePosition(frame, positions[0]) && !isSamePosition(positions[index - 1], positions[0]));
            const isScale = (frame, value) => frame.points.every(point =>
                Object.values(point).every(axis => axis === value));
            assert.ok(scales.length && isScale(scales[0], 0) && isScale(scales.at(-1), 0),
                fail(`${groupName} must be hidden at the loop boundary`));
            assert.ok(positions.some((frame, index) => index > 0 && positions[index - 1].time <= 6 &&
                frame.time >= 12 && isSamePosition(frame, positions[index - 1])) &&
                scales.some((frame, index) => index > 0 && scales[index - 1].time <= 6 &&
                    frame.time >= 12 && isScale(scales[index - 1], 1) && isScale(frame, 1)),
                fail(`${groupName} must remain parked and visible during crafting`));
            assert.ok(positions.some(frame => !isSamePosition(frame, positions[0])) &&
                scales.some(frame => frame.time >= 12.3 && frame.time <= 18 && isScale(frame, 0)) &&
                reset?.time === 19 && scales.some((frame, index) => index > 0 &&
                    scales[index - 1].time <= reset.time && reset.time <= frame.time &&
                    isScale(scales[index - 1], 0) && isScale(frame, 0)),
                fail(`${groupName} must move and reset home while hidden`));
            movingPayloads++;
        }
    }
    assert.ok(movingBelts > 0, fail('no moving conveyor belt'));
    assert.ok(movingPayloads > 0, fail('no moving payload'));
    console.log(`${name}: OK`);
}

function isSamePosition(a, b) {
    return a.points.every((point, index) => ['x', 'y', 'z'].every(axis =>
        point[axis] === b.points[index][axis]));
}

for (const variant of variants) validate(variant);
