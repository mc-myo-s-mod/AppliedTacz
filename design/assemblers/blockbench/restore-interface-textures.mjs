import assert from 'node:assert/strict';
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const ae2 = resolve(here, '../../../../fork/Applied-Energistics-2_1_21_1/src/main/resources/assets/ae2');
const assets = resolve(here, 'assets/ae2');
const names = ['ammo', 'attachment', 'gun', 'universal'];
const files = names.flatMap(name => [
    `molecular_assembler_${name}.bbmodel`,
    `animated/molecular_assembler_${name}_animated.bbmodel`,
]);
const sources = [
    ['interface_part_front', 'textures/part/interface.png', 'textures/part/interface.png'],
    ['interface_part_sides', 'textures/part/interface_sides.png', 'textures/part/interface_sides.png'],
    ['interface_part_sidesStatus', 'textures/part/monitor_sides_status.png', 'textures/part/monitor_sides_status.png'],
    ['interface_part_back', 'textures/part/interface_back.png', 'textures/part/interface_back.png'],
    ['interface_block', 'textures/block/interface.png', 'textures/block/interface.png'],
];
const textureUuids = {
    interface_part_front: 'd2a23be9-8f89-4fe4-a8ca-6b8c83c8be01',
    interface_part_sides: 'd2a23be9-8f89-4fe4-a8ca-6b8c83c8be02',
    interface_part_sidesStatus: 'd2a23be9-8f89-4fe4-a8ca-6b8c83c8be03',
    interface_part_back: 'd2a23be9-8f89-4fe4-a8ca-6b8c83c8be04',
    interface_block: 'd2a23be9-8f89-4fe4-a8ca-6b8c83c8be05',
};
const tileOrigins = new Map([
    ['interface_part_sides', [16, 32]],
    ['interface_part_sidesStatus', [32, 32]],
    ['interface_part_back', [48, 32]],
    ['interface_part_front', [0, 48]],
]);

for (const [id, source, target] of sources) {
    const sourcePath = resolve(ae2, source);
    const targetPath = resolve(assets, target);
    const png = readFileSync(sourcePath);
    assert.equal(png.subarray(1, 4).toString(), 'PNG', `${source} is not a PNG`);
    assert.deepEqual([png.readUInt32BE(16), png.readUInt32BE(20)], [16, 16], `${source} must be 16x16`);
    mkdirSync(dirname(targetPath), { recursive: true });
    copyFileSync(sourcePath, targetPath);
}

function materialAt(uv) {
    const center = [(uv[0] + uv[2]) / 2, (uv[1] + uv[3]) / 2];
    for (const [id, origin] of tileOrigins) {
        if (center[0] >= origin[0] && center[0] < origin[0] + 16
            && center[1] >= origin[1] && center[1] < origin[1] + 16) return id;
    }
    return null;
}

function textureObject(id, target, animated) {
    const relative = `assets/ae2/${target}`;
    const projectPath = resolve(assets, target).replaceAll('\\', '/');
    return {
        path: projectPath,
        relative_path: `${animated ? '../' : ''}${relative}`,
        name: target.split('/').at(-1), folder: target.split('/').at(-2), namespace: 'ae2', id,
        uuid: textureUuids[id], width: 16, height: 16, uv_width: 16, uv_height: 16,
        particle: false, use_as_default: false, mode: 'link', saved: true,
        render_mode: 'default', render_sides: 'auto', pbr_channel: 'color',
    };
}

for (const file of files) {
    const modelPath = resolve(here, file);
    const model = JSON.parse(readFileSync(modelPath, 'utf8'));
    assert.ok(model.textures.some(texture => texture.id === 'assembler_materials'), `${file} has no material atlas`);
    const animated = file.startsWith('animated/');
    const indices = new Map();
    for (const [id, , target] of sources) {
        let index = model.textures.findIndex(texture => texture.id === id);
        if (index < 0) {
            index = model.textures.length;
            model.textures.push(textureObject(id, target, animated));
        }
        indices.set(id, index);
    }

    for (const cube of model.elements) {
        const conveyor = cube.name.startsWith('Conveyor interface ')
            || cube.name.startsWith('Conveyor intake interface ')
            || cube.name.startsWith('Conveyor output interface ');
        const panel = cube.name.startsWith('Conveyor intake interface ')
            || cube.name.startsWith('Conveyor output interface ');
        for (const [direction, face] of Object.entries(cube.faces)) {
            if (face.texture === null || face.texture === false) continue;
            if (conveyor) {
                const id = materialAt(face.uv);
                if (id) {
                    const origin = tileOrigins.get(id);
                    face.uv = face.uv.map((value, axis) => value - origin[axis % 2]);
                    face.texture = indices.get(id);
                }
            } else if (materialAt(face.uv) === 'interface_part_front') {
                face.uv = face.uv.map((value, axis) => value - tileOrigins.get('interface_part_front')[axis % 2]);
                face.texture = indices.get('interface_block');
            }
            if (!panel) continue;

            const thinAxis = cube.to[0] - cube.from[0] < cube.to[2] - cube.from[2] ? 'x' : 'z';
            const inward = thinAxis === 'x'
                ? (cube.name.includes('intake') ? 'east' : 'west')
                : (cube.name.includes('intake') ? 'south' : 'north');
            if (direction === inward) {
                face.uv = [2, 2, 14, 14];
                face.texture = indices.get('interface_part_front');
            } else if (direction === (inward === 'east' ? 'west' : inward === 'west' ? 'east' : inward === 'north' ? 'south' : 'north')) {
                face.uv = [2, 2, 14, 14];
                face.texture = indices.get('interface_part_back');
            } else {
                const sideUvs = {
                    east: [14, 2, 16, 14], west: [0, 2, 2, 14],
                    up: [2, 0, 14, 2], down: [2, 14, 14, 16],
                    north: [2, 2, 14, 14], south: [2, 2, 14, 14],
                };
                face.uv = sideUvs[direction];
                face.texture = indices.get('interface_part_sides');
            }
        }
    }
    writeFileSync(modelPath, `${JSON.stringify(model)}\n`);
}

console.log(`Restored AE2 interface textures in ${files.length} Blockbench models.`);
