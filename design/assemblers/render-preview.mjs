// Offline visual check of the actual model cuboids and installed Minecraft/AE2 textures.
// node render-preview.mjs <node_modules> <Minecraft client-extra.jar> <AE2 jar> [--forge-1.20.1]
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const [modules, clientJar, ae2Jar] = process.argv.slice(2);
const require = createRequire(resolve(modules, '../package.json'));
const sharp = require('sharp');
const JSZip = require('jszip');
const vanilla = await JSZip.loadAsync(readFileSync(clientJar));
const ae2 = await JSZip.loadAsync(readFileSync(ae2Jar));
const here = dirname(fileURLToPath(import.meta.url));
const forge = process.argv.includes('--forge-1.20.1');
const output = resolve(here, forge ? 'model-preview-1.20.1.png' : 'model-preview.png');
const models = resolve(here, forge ? 'models/1.20.1' : 'models');
const width = 500, height = 500;
const panels = [];
const textureCache = new Map();
for (const kind of ['ammo', 'attachment', 'gun', 'universal']) {
    const model = JSON.parse(readFileSync(resolve(models, `molecular_assembler_${kind}.json`), 'utf8'));
    for (const id of Object.values(model.textures)) {
        if (textureCache.has(id)) continue;
        const [namespace, path] = id.split(':');
        const resource = `assets/${namespace}/textures/${path}.png`;
        const bytes = await (namespace === 'minecraft' ? vanilla : ae2).file(resource).async('nodebuffer');
        textureCache.set(id, await sharp(bytes).ensureAlpha().raw().toBuffer({ resolveWithObject: true }));
    }
    const cubes = model.elements.map(c => ({ ...c, tex: textureCache.get(model.textures[c.faces.up.texture.substring(1)]) }));
    const extent = [0, 1, 2].map(i => Math.max(...cubes.map(c => c.to[i])));
    const center = extent.map(v => v / 2);
    const pixels = Buffer.alloc(width * height * 3);
    const zoom = 8.5;
    const forward = [-0.5773503, -0.5773503, 0.5773503];
    const right = [0.7071068, 0, 0.7071068];
    const up = [-0.4082483, 0.8164966, 0.4082483];
    for (let py = 0; py < height; py++) for (let px = 0; px < width; px++) {
        const origin = center.map((v, i) => v + right[i] * (px - width / 2) / zoom
            + up[i] * (height / 2 - py) / zoom - forward[i] * 100);
        let nearest = Infinity, hit, hitAxis;
        for (const cube of cubes) {
            let enter = -Infinity, leave = Infinity, axis = 0;
            for (let i = 0; i < 3; i++) {
                const a = (cube.from[i] - origin[i]) / forward[i];
                const b = (cube.to[i] - origin[i]) / forward[i];
                const near = Math.min(a, b);
                if (near > enter) { enter = near; axis = i; }
                leave = Math.min(leave, Math.max(a, b));
            }
            if (enter <= leave && enter < nearest) {
                nearest = enter; hit = cube; hitAxis = axis;
            }
        }
        let color = [244, 241, 234];
        if (hit) {
            const pos = origin.map((v, i) => v + forward[i] * nearest);
            const axes = hitAxis === 1 ? [0, 2] : hitAxis === 0 ? [2, 1] : [0, 1];
            const uv = axes.map(i => Math.min(0.999999, Math.max(0, (pos[i] - hit.from[i]) / (hit.to[i] - hit.from[i]))));
            // The fixed camera sees north/east sides, whose U and V both run backwards in Minecraft.
            if (hitAxis !== 1) { uv[0] = 1 - uv[0]; uv[1] = 1 - uv[1]; }
            const { data, info } = hit.tex;
            const face = hit.faces[hitAxis === 1 ? 'up' : hitAxis === 0 ? 'east' : 'north'];
            const crop = face.uv ?? [0, 0, 16, 16];
            const frameHeight = Math.min(info.width, info.height); // First frame of animated block sprites.
            const tx = Math.min(info.width - 1, Math.floor((crop[0] + uv[0] * (crop[2] - crop[0])) / 16 * info.width));
            const ty = Math.min(frameHeight - 1, Math.floor((crop[1] + uv[1] * (crop[3] - crop[1])) / 16 * frameHeight));
            const index = (ty * info.width + tx) * 4;
            const shade = face.forge_data?.block_light === 15 ? 1 : hitAxis === 1 ? 1 : hitAxis === 2 ? 0.85 : 0.7;
            color = [0, 1, 2].map(i => Math.round(data[index + i] * shade));
        } else {
            const ground = -origin[1] / forward[1];
            const x = origin[0] + forward[0] * ground;
            const z = origin[2] + forward[2] * ground;
            if (x > -12 && x < extent[0] + 12 && z > -12 && z < extent[2] + 12) {
                const grid = Math.abs(x / 16 - Math.round(x / 16)) < 0.008 || Math.abs(z / 16 - Math.round(z / 16)) < 0.008;
                color = grid ? [213, 210, 204] : [236, 233, 226];
            }
        }
        const offset = (py * width + px) * 3;
        for (let i = 0; i < 3; i++) pixels[offset + i] = color[i];
    }
    const label = `${kind.toUpperCase()} / 2 x ${kind === 'ammo' || kind === 'attachment' ? '1' : '2'}`;
    const title = Buffer.from(`<svg width="500" height="44"><text x="250" y="28" text-anchor="middle" fill="#292c30" font-family="monospace" font-size="20" font-weight="bold">${label}</text></svg>`);
    panels.push({ input: await sharp(pixels, { raw: { width, height, channels: 3 } }).composite([{ input: title, top: 450, left: 0 }]).png().toBuffer(), left: (panels.length % 2) * width, top: Math.floor(panels.length / 2) * height });
}
await sharp({ create: { width: width * 2, height: height * 2, channels: 3, background: '#f4f1ea' } })
    .composite(panels).png().toFile(output);
console.log(`Rendered actual model geometry: ${output}`);
