import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';

const length = 20;
const start = 12.3;
const speed = 6;
const pitch = 1.25;
const owned = /^(conveyor_belt_|conveyor_clamps$|payload_)/;
const center = cubes => [0, 1, 2].map(axis =>
    (Math.min(...cubes.map(c => c.from[axis])) + Math.max(...cubes.map(c => c.to[axis]))) / 2);
const clone = value => JSON.parse(JSON.stringify(value));
const read = path => JSON.parse(readFileSync(new URL(path, import.meta.url), 'utf8'));

function key(channel, time, values, interpolation = 'linear') {
    return { uuid: randomUUID(), channel, time, interpolation,
        data_points: [{ x: String(values[0]), y: String(values[1]), z: String(values[2]) }] };
}

function children(nodes, uuid) {
    for (const node of nodes) {
        if (typeof node === 'string') continue;
        if (node.uuid === uuid) return node.children;
        const found = children(node.children, uuid);
        if (found) return found;
    }
}

function group(model, name, cubes, origin = center(cubes)) {
    assert.ok(cubes.length, `No cubes for ${name}`);
    const ids = new Set(cubes.map(c => c.uuid));
    const remove = nodes => nodes.filter(node => !ids.has(node)).map(node =>
        typeof node === 'string' ? node : { ...node, children: remove(node.children) });
    model.outliner = remove(model.outliner);
    const uuid = randomUUID();
    model.groups.push({ name, uuid, origin, rotation: [0, 0, 0], children: [],
        visibility: true, export: true, shade: true, autouv: 0 });
    const frame = model.groups.find(g => g.name === 'machine_frame');
    children(model.outliner, frame.uuid).push({ uuid, isOpen: false, children: [...ids] });
    return { uuid, name, origin };
}

function track(animation, bone, keyframes) {
    animation.animators[bone.uuid] = { name: bone.name, type: 'bone', keyframes };
}

function panel(name, from, to, face) {
    const uv = [49, 49, 63, 63];
    const faces = Object.fromEntries(['north', 'east', 'south', 'west', 'up', 'down']
        .map(side => [side, { uv: side === face ? [0.5, 48.5, 15.5, 63.5] : uv,
            texture: 0, rotation: 0 }]));
    return { name, uuid: randomUUID(), type: 'cube', from, to, origin: [0, 0, 0],
        rotation: [0, 0, 0], autouv: 0, box_uv: false, faces,
        shade: true, visibility: true, export: true };
}

for (const kind of ['ammo', 'attachment', 'gun', 'universal']) {
    const base = `molecular_assembler_${kind}`;
    const sourcePath = `${base}.bbmodel`;
    const animatedPath = `animated/${base}_animated.bbmodel`;
    const source = read(sourcePath);
    const model = read(animatedPath);
    const animation = model.animations[0];
    const hasIntake = animation.length === length;
    const oldGroups = new Set(model.groups.filter(g => owned.test(g.name)).map(g => g.uuid));
    const flatten = nodes => nodes.flatMap(node => typeof node === 'string' ? [node]
        : oldGroups.has(node.uuid) ? flatten(node.children)
        : [{ ...node, children: flatten(node.children) }]);
    model.outliner = flatten(model.outliner);
    model.groups = model.groups.filter(g => !oldGroups.has(g.uuid));
    for (const id of oldGroups) delete animation.animators[id];
    animation.length = length;
    for (const animator of Object.values(animation.animators)) {
        if (!animator.keyframes?.length) continue;
        if (!hasIntake) {
            animator.keyframes = animator.keyframes.filter(k => k.time <= 6);
            const initial = animator.keyframes.filter(k => k.time === 0).map(clone);
            for (const k of animator.keyframes) k.time += 6;
            animator.keyframes.push(...initial.map(k => ({ ...k, uuid: randomUUID() })));
        }
        for (const channel of ['position', 'rotation', 'scale']) {
            const keys = (animator.keyframes ?? []).filter(k => k.channel === channel).sort((a, b) => a.time - b.time);
            if (keys.length && keys.at(-1).time < length) {
                animator.keyframes.push({ ...clone(keys.at(-1)), uuid: randomUUID(), time: length });
            }
        }
    }

    for (const target of [source, model]) {
        const removed = new Set(target.elements.filter(c => /^(Parts tray|Tool tray inset|Tray divider)$/.test(c.name)).map(c => c.uuid));
        target.elements = target.elements.filter(c => !removed.has(c.uuid));
        const prune = nodes => nodes.filter(node => !removed.has(node)).map(node =>
            typeof node === 'string' ? node : { ...node, children: prune(node.children) });
        target.outliner = prune(target.outliner);
    }

    function addCube(cube) {
        const existing = source.elements.find(c => c.name === cube.name);
        const saved = existing ?? cube;
        if (!existing) { source.elements.push(saved); source.outliner.push(saved.uuid); }
        if (!model.elements.some(c => c.uuid === saved.uuid)) {
            model.elements.push(clone(saved));
            children(model.outliner, model.groups.find(g => g.name === 'machine_frame').uuid).push(saved.uuid);
        }
    }

    const axis = kind === 'ammo' || kind === 'attachment' ? 0 : 2;
    const laneCenters = axis === 0 ? [kind === 'ammo' ? 7.5 : 7] : [6, 26];
    const mouth = kind === 'attachment' ? 30 : kind === 'ammo' ? 30.5 : kind === 'gun' ? 27.5 : 28.5;
    const inlet = kind === 'attachment' ? 1.5 : kind === 'ammo' ? 1 : 2.5;
    for (const [lane, cross] of laneCenters.entries()) {
        if (kind !== 'attachment') {
            addCube(axis === 0
                ? panel(`Conveyor intake interface ${lane}`, [0.5, 11.5, 3.5], [1.5, 18.5, 11.5], 'east')
                : panel(`Conveyor intake interface ${lane}`, [cross - 3, 11.5, 2], [cross + 3, 18.5, 3], 'south'));
            addCube(axis === 0
                ? panel(`Conveyor output interface ${lane}`, [mouth, 11.5, 3.5], [mouth + 1, 18.5, 11.5], 'west')
                : panel(`Conveyor output interface ${lane}`, [cross - 3, 11.5, mouth], [cross + 3, 18.5, mouth + 1], 'north'));
        }
        const rollers = source.elements.filter(c => /^(Cross-feed roller|Conveyor roller)$/.test(c.name)
            && (axis === 0 || Math.abs(center([c])[0] - cross) < 1));
        assert.ok(rollers.length > 10, `${kind}: missing belt lane ${lane}`);
        rollers.sort((a, b) => a.from[axis] - b.from[axis]);
        const wrap = clone(rollers[0]);
        wrap.uuid = randomUUID(); wrap.name = `Conveyor wrap slat ${lane}`;
        wrap.from[axis] -= pitch; wrap.to[axis] -= pitch;
        addCube(wrap);
        const ids = new Set(rollers.map(c => c.uuid));
        const belt = group(model, `conveyor_belt_${lane}`,
            model.elements.filter(c => ids.has(c.uuid) || c.name === wrap.name));
        const keys = [];
        for (const phase of [0, start]) {
            keys.push(key('position', phase, [0, 0, 0]));
            for (let step = 1; step <= (phase === 0 ? 24 : 28); step++) {
                const end = phase + step * pitch / speed;
                const offset = [0, 0, 0]; offset[axis] = pitch;
                keys.push(key('position', end - 0.0001, offset, 'step'), key('position', end, [0, 0, 0]));
            }
            keys.at(-1).interpolation = 'step';
        }
        keys.push(key('position', length, [0, 0, 0]));
        track(animation, belt, keys);
    }

    function payload(name, itemCubes, launch, route = null) {
        const itemCenter = center(itemCubes);
        const pallets = model.elements.filter(c => c.name === 'Fixture pallet');
        const distanceToItem = c => Math.hypot(center([c])[0] - itemCenter[0], center([c])[2] - itemCenter[2]);
        const pallet = pallets.sort((a, b) => distanceToItem(a) - distanceToItem(b))[0];
        assert.ok(pallet, `${kind}: missing tray for ${name}`);
        const tray = model.elements.filter(c => /^Fixture (pallet|inset|clamp)$/.test(c.name)
            && [0, 2].every(a => center([c])[a] >= pallet.from[a] && center([c])[a] <= pallet.to[a]));
        const cubes = [...itemCubes, ...tray];
        const bone = group(model, name, cubes);
        const offset = [0, 0, 0];
        const departure = route?.time ?? start;
        const rotated = route?.rotate ?? false;
        if (route) offset[0] = route.x - bone.origin[0];
        // Large center trays use the compact transfer pose while on the side belts.
        const transferScale = route ? 0.5 : 1;
        const bottom = Math.min(...cubes.map(c => c.from[1]));
        offset[1] = 12.4 - (bone.origin[1] + (bottom - bone.origin[1]) * transferScale);
        const distance = mouth + 0.5 - bone.origin[axis];
        const arrival = departure + distance / speed;
        const extentAxis = rotated ? 0 : axis;
        const half = (Math.max(...cubes.map(c => c.to[extentAxis])) - Math.min(...cubes.map(c => c.from[extentAxis]))) / 2 * transferScale;
        const absorb = departure + (mouth - half - bone.origin[axis]) / speed;
        const entry = [...offset]; entry[axis] = inlet - bone.origin[axis];
        const park = launch + (bone.origin[axis] - inlet) / speed;
        const ready = park + (route ? 1.7 : 0);
        const emerge = launch + (half + 0.5) / speed;
        assert.ok(emerge < park && ready < 6 && absorb > departure && arrival < 19,
            `${kind}: invalid payload route ${name}`);
        const keys = [key('position', 0, entry), key('position', launch, entry),
            key('position', park, route ? offset : [0, 0, 0])];
        const scales = [[0, 0], [launch, 0], [emerge, transferScale], [park, transferScale]];
        if (route) {
            keys.push(key('position', park + 0.3, [offset[0], 1.3, 0]),
                key('position', park + 1.3, [0, 1.3, 0]), key('position', ready, [0, 0, 0]));
            scales.push([park + 0.3, transferScale], [ready, 1],
                [departure - 1.7, 1], [departure - 1.3, transferScale]);
            keys.push(key('position', departure - 1.7, [0, 0, 0]),
                key('position', departure - 1.3, [0, 1.3, 0]),
                key('position', departure - 0.3, [offset[0], 1.3, 0]),
                key('position', departure, offset));
        } else keys.push(key('position', departure, [0, 0, 0]));
        const destination = [...offset]; destination[axis] += distance;
        keys.push(key('position', arrival, destination, 'step'),
            key('position', 19, entry, 'step'), key('position', length, entry));
        for (const [time, scale] of [...scales, [absorb, transferScale], [arrival, 0], [length, 0]]) {
            keys.push(key('scale', time, [scale, scale, scale]));
        }
        if (rotated) {
            keys.push(...[[0, 90], [park + 0.3, 90], [ready, 0], [departure - 1.7, 0], [departure - 1.3, 90], [arrival, 90], [length, 90]]
                .map(([time, y]) => key('rotation', time, [0, y, 0], time === arrival ? 'step' : 'linear')));
        }
        track(animation, bone, keys);
    }

    const select = predicate => model.elements.filter(predicate);
    if (kind === 'ammo') {
        for (let i = 0; i < 2; i++) payload(`payload_${i}`, select(c => /^Cartridge (case|tip)$/.test(c.name) && (center([c])[0] < 16) === (i === 0)), i === 0 ? 2.2 : 0.2);
    } else if (kind === 'attachment') {
        payload('payload_0', select(c => c.name.startsWith('Optic ')), 2.2);
        payload('payload_1', select(c => c.name.startsWith('Accessory ')), 0.2);
    } else if (kind === 'gun') {
        for (let i = 0; i < 2; i++) payload(`payload_${i}`, select(c => c.name === 'Parts cassette' && (center([c])[0] < 16) === (i === 0)), 0.2);
        payload('payload_product', select(c => c.name.startsWith('Workpiece ')), 1.4, { time: 14, x: 6, rotate: true });
    } else {
        payload('payload_0', select(c => c.name.startsWith('Ammunition feed ')), 3.4);
        payload('payload_1', select(c => c.name.startsWith('Attachment feed ')), 3.4);
        for (let i = 0; i < 2; i++) payload(`payload_${i + 2}`, select(c => /^(Modular parts cassette|Cassette identifier)$/.test(c.name) && (center([c])[0] < 16) === (i === 0)), 0.2);
        payload('payload_product', select(c => /^Universal fixture( inlay)?$/.test(c.name)), 1.2, { time: 15.5, x: 26 });
    }

    writeFileSync(new URL(sourcePath, import.meta.url), `${JSON.stringify(source)}\n`);
    writeFileSync(new URL(animatedPath, import.meta.url), `${JSON.stringify(model)}\n`);
    console.log(`${kind}: ${laneCenters.length} belts, ${Object.values(animation.animators).filter(a => a.name.startsWith('payload_')).length} payloads, ${length}s loop`);
}
