import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const atlas = 'assets/appliedtacz/textures/block/assembler_materials.png';
const png = readFileSync(new URL(atlas, import.meta.url));
assert.equal(png.subarray(1, 4).toString(), 'PNG');
const width = png.readUInt32BE(16);
const height = png.readUInt32BE(20);
assert.equal(width, height, 'The material atlas must be square');

// Rows of the newly generated 4 x 4 atlas; no source texture pixels are sampled.
const tiles = {
    body: [1, 3], frame: [1, 0], steel: [3, 3], dark: [3, 0],
    copper: [0, 1], brass: [1, 1], cyan: [2, 1], light: [3, 1],
    port: [0, 3], interface_part_sides: [1, 2],
    interface_part_sidesStatus: [2, 2], interface_part_back: [3, 2],
    interface_part_front: [0, 3],
};
const textureUuid = 'c18b4270-bd0f-432a-a2a6-d325386d8f91';
const names = ['ammo', 'attachment', 'gun', 'universal'];
const files = names.flatMap(name => [
    `molecular_assembler_${name}.bbmodel`,
    `animated/molecular_assembler_${name}_animated.bbmodel`,
]);

const changes = files.map(file => {
    const url = new URL(file, import.meta.url);
    const model = JSON.parse(readFileSync(url, 'utf8'));
    if (model.textures.some(texture => texture.id === 'assembler_materials')) return null;
    const animations = JSON.stringify(model.animations);
    const geometry = model.elements.map(({ faces, ...cube }) => JSON.stringify(cube));
    for (const cube of model.elements) {
        for (const face of Object.values(cube.faces)) {
            if (face.texture === null || face.texture === false) continue;
            const old = model.textures.find(t => t.uuid === face.texture)
                ?? model.textures[Number(face.texture)];
            assert.ok(old && tiles[old.id], `${file}: unknown material ${face.texture}`);
            let tile = tiles[old.id];
            if (old.id === 'body' && cube.name === 'cube') tile = [3, 3];
            if (old.id === 'body' && cube.name === 'Case hopper') tile = [0, 0];
            if (cube.name === 'Spindle shaft' || cube.name === 'Workpiece barrel') tile = [2, 0];
            face.uv = face.uv.map((value, axis) => value + 16 * tile[axis % 2]);
            assert.ok(face.uv.every(value => Number.isFinite(value) && value >= 0 && value <= 64));
            face.texture = 0;
        }
    }
    model.resolution = { width: 64, height: 64 };
    model.textures = [{
        path: fileURLToPath(new URL(atlas, import.meta.url)).replaceAll('\\', '/'),
        relative_path: `${file.startsWith('animated/') ? '../' : ''}${atlas}`,
        name: 'assembler_materials.png', folder: 'block', namespace: 'appliedtacz',
        id: 'assembler_materials', uuid: textureUuid,
        width, height, uv_width: 64, uv_height: 64,
        particle: true, use_as_default: true, mode: 'link', saved: true,
        render_mode: 'default', render_sides: 'auto', pbr_channel: 'color',
    }];
    assert.equal(JSON.stringify(model.animations), animations, 'Animation data changed');
    assert.deepEqual(model.elements.map(({ faces, ...cube }) => JSON.stringify(cube)), geometry,
        'Cube geometry changed');
    return { file, url, model };
});

for (const change of changes.filter(Boolean)) {
    writeFileSync(change.url, `${JSON.stringify(change.model)}\n`);
    console.log(`${change.file}: independent atlas applied`);
}
console.log(`Atlas: ${width} x ${height}; geometry and animations preserved`);
