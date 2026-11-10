// Decorative AE2-style station equipment. The conveyors, payloads and interfaces are built elsewhere.
export function buildStation(kind, { box, group, track }) {
    const b = (name, from, to, material = 'white', parent = null) =>
        box(name, from, to, material, parent);
    const rail = (name, x1, x2, y, z1, z2, material = 'steel') =>
        b(name, [x1, y, z1], [x2, y + 0.35, z2], material);
    const bolts = (name, points, y) => {
        for (const [i, x, z] of points.map((point, index) => [index, ...point]))
            b(`${name} ${i}`, [x - 0.18, y, z - 0.18], [x + 0.18, y + 0.15, z + 0.18], 'steel');
    };
    const stroke = (name, x, z, top, amount, [down, hold, up], parent = null) => {
        const rod = group(`${name}_rod`, [x, top, z], parent);
        b(`${name} telescoping rod`, [x - 0.28, top - 2, z - 0.28],
            [x + 0.28, top, z + 0.28], 'steel', rod);
        track(rod, 'scale', [[6, [1, 1, 1]], [down, [1, 1 + amount / 2, 1]],
            [hold, [1, 1 + amount / 2, 1]], [up, [1, 1, 1]], [12, [1, 1, 1]]]);
        const tip = group(`${name}_tip`, [x, top - 2.5, z], parent);
        const half = name === 'press_platen' ? 2.15 : 0.8;
        b(`${name} covered head`, [x - half, top - 3, z - half],
            [x + half, top - 2, z + half], 'frame', tip);
        b(`${name} contact lens`, [x - 0.48, top - 3.15, z - 0.48],
            [x + 0.48, top - 3, z + 0.48], name === 'press_platen' ? 'steel' : 'cyan', tip);
        track(tip, 'position', [[6, [0, 0, 0]], [down, [0, -amount, 0]],
            [hold, [0, -amount, 0]], [up, [0, 0, 0]], [12, [0, 0, 0]]]);
        return tip;
    };

    if (kind === 'ammo') {
        // A closed press bay to the right, a low optical gate to the left.
        for (const x of [17.5, 29]) {
            b('press rear column', [x, 11.5, 12.3], [x + 1.15, 22.7, 13.6], 'frame');
            b('press column inset', [x + 0.22, 14, 12.12], [x + 0.93, 21.7, 12.3], 'bluegray');
            b('press column light', [x + 0.41, 17, 12.08], [x + 0.74, 20.5, 12.12], 'cyan');
            b('press foot', [x - 0.35, 11.5, 11.9], [x + 1.5, 12.3, 14], 'dark');
        }
        b('press top shroud', [17.5, 22.2, 5], [30.15, 23.8, 13.7], 'white');
        b('press front fascia', [18.3, 22.55, 4.75], [29.35, 23.25, 5], 'white');
        b('press fluix stripe', [19, 22.8, 4.68], [28.7, 23.02, 4.75], 'violet');
        b('press dark underside', [19, 21.85, 5.5], [28.7, 22.2, 11.8], 'dark');
        for (const x of [21.8, 25.2]) {
            b('press silver guide', [x - 0.22, 17.6, 7.25], [x + 0.22, 21.85, 7.75], 'steel');
            b('press guide socket', [x - 0.55, 21.5, 6.95], [x + 0.55, 22.1, 8.05], 'bluegray');
        }
        stroke('press_platen', 23.5, 7.5, 21.2, 2.5, [7.05, 7.65, 8.25]);
        b('press lower shield', [18.7, 14.8, 11.6], [28.8, 18.4, 12.2], 'bluegray');
        b('press shield window', [20.2, 15.5, 11.52], [27.2, 17.55, 11.6], 'dark');
        for (const x of [8.1, 10.8]) {
            b('inspection arch leg', [x, 11.5, 11.6], [x + 0.55, 17.4, 12.25], 'frame');
            b('inspection arch foot', [x - 0.3, 11.5, 11.4], [x + 0.85, 12, 12.65], 'steel');
        }
        b('inspection arch crossbar', [8.1, 17.05, 10.8], [11.35, 17.65, 12.3], 'frame');
        b('inspection lens', [9.2, 16.8, 10.62], [10.25, 17.05, 10.8], 'cyan');
        b('inspection status strip', [8.8, 17.45, 10.72], [10.65, 17.57, 10.8], 'violet');
        b('sealed feeder cabinet', [2.4, 11.5, 12], [6.8, 18.8, 15.2], 'white');
        b('feeder front service door', [3.1, 13.5, 11.78], [6.1, 17.4, 12], 'bluegray');
        b('feeder viewport', [3.7, 15.2, 11.7], [5.5, 16.7, 11.78], 'dark');
        b('feeder lid', [2.1, 18.8, 11.8], [7.1, 19.25, 15.35], 'white');
        b('feeder control housing', [10.3, 16.8, 12.5], [13.4, 19.3, 14.5], 'frame');
        b('feeder control screen', [10.65, 17.35, 12.28], [13.05, 18.8, 12.5], 'cyan');
        for (const x of [19, 22, 25, 28])
            b('press vent slot', [x, 22.55, 13.7], [x + 1.2, 22.95, 13.79], 'dark');
        bolts('press roof fastener', [[18, 5.5], [29.6, 5.5], [18, 13.1], [29.6, 13.1]], 23.8);
        bolts('feeder lid fastener', [[2.6, 12.3], [6.6, 12.3], [2.6, 14.9], [6.6, 14.9]], 19.25);
        for (const x of [3.3, 4.15, 5, 5.85])
            b('feeder vent', [x, 12.5, 15.2], [x + 0.45, 16.8, 15.29], 'dark');
        for (const x of [18.2, 19.8, 27.5, 29.1]) {
            b('press fascia rivet', [x, 22.68, 4.62], [x + 0.2, 22.88, 4.75], 'steel');
            b('press fascia lower rivet', [x, 22.98, 4.62], [x + 0.2, 23.18, 4.75], 'steel');
        }
        for (const x of [3.05, 4.05, 5.05, 6.05])
            b('feeder service seam', [x, 13.7, 11.68], [x + 0.12, 14.4, 11.78], 'steel');
        for (const x of [8.5, 9.3, 10.1, 10.9])
            b('inspection witness light', [x, 17.27, 10.69], [x + 0.28, 17.4, 10.8], 'cyan');
    } else if (kind === 'attachment') {
        // Two small rear cantilevers leave the whole front conveyor visually open.
        for (const [i, x] of [8.5, 23.5].entries()) {
            b('precision pedestal', [x - 1.35, 11.5, 13.25], [x + 1.35, 20.9, 15.5], 'frame');
            b('pedestal dark spine', [x - 0.85, 13, 13.05], [x + 0.85, 19.7, 13.25], 'bluegray');
            b('pedestal fluix gauge', [x - 0.55, 15, 13], [x + 0.55, 18.2, 13.05], 'violet');
            b('pedestal foot', [x - 1.65, 11.5, 12.8], [x + 1.65, 12.2, 15.7], 'dark');
            b('cantilever shoulder', [x - 1.6, 20.2, 10.8], [x + 1.6, 21.8, 15.4], 'white');
            b('cantilever black joint', [x - 0.8, 20.45, 9.8], [x + 0.8, 21.55, 10.8], 'dark');
            b('cantilever arm', [x - 0.65, 20.7, 6.3], [x + 0.65, 21.25, 10.8], 'steel');
            b('cantilever upper cover', [x - 1.05, 21.25, 7.5], [x + 1.05, 21.75, 11.7], 'frame');
            b('cantilever side stripe', [x - 1.1, 21.37, 8], [x - 1.05, 21.57, 11.2], 'cyan');
            b('cantilever wrist housing', [x - 1.1, 19.7, 5.55], [x + 1.1, 20.7, 7.5], 'frame');
            b('wrist black bellows', [x - 0.65, 19.25, 6.2], [x + 0.65, 19.7, 7.1], 'dark');
            stroke(`precision_${i}`, x, 6.5, 20.5, 1.9, [7.3 + i * 0.9, 7.85 + i * 0.9, 8.4 + i * 0.9]);
            b('optical side lens', [x - 1.22, 18.45, 6.1], [x - 1.1, 19.3, 7], 'cyan');
            b('calibration hood', [x - 1.8, 17.8, 10], [x + 1.8, 19, 12.5], 'white');
            b('calibration dark window', [x - 1.2, 18, 9.82], [x + 1.2, 18.75, 10], 'dark');
            b('hood indicator', [x - 0.55, 18.82, 9.77], [x + 0.55, 18.97, 9.82], 'violet');
            bolts(`pedestal ${i} fastener`, [[x - 1.1, 13.5], [x + 1.1, 13.5],
                [x - 1.1, 15.2], [x + 1.1, 15.2]], 20.9);
            for (const dx of [-0.9, 0, 0.9])
                b('pedestal vent', [x + dx - 0.18, 12.9, 15.5], [x + dx + 0.18, 15.2, 15.58], 'dark');
            for (const dx of [-1.1, -0.4, 0.4, 1.1]) {
                b('shoulder hinge pin', [x + dx - 0.12, 21.25, 10.52],
                    [x + dx + 0.12, 21.45, 10.8], 'steel');
                b('calibration tick', [x + dx - 0.1, 18.82, 9.75],
                    [x + dx + 0.1, 18.92, 9.82], 'cyan');
            }
        }
        b('rear utility duct', [3, 12, 15.1], [29, 13, 15.6], 'bluegray');
        b('rear fluix trace', [3.4, 12.72, 15.02], [28.6, 12.88, 15.1], 'violet');
        for (const x of [4, 16, 28])
            b('rear utility bracket', [x - 0.35, 11.5, 14.8], [x + 0.35, 13.3, 15.7], 'steel');
    } else if (kind === 'gun') {
        // A rear-supported C-frame, two independent tools, and an exit inspection arch.
        for (const x of [11, 21]) {
            b('C-frame rear upright', [x - 1.1, 11.5, 27], [x + 1.1, 24.7, 29.5], 'frame');
            b('C-frame inset', [x - 0.55, 14.5, 26.82], [x + 0.55, 23.4, 27], 'bluegray');
            b('C-frame foot', [x - 1.55, 11.5, 26.4], [x + 1.55, 12.3, 29.8], 'dark');
        }
        b('C-frame high crossbar', [9.9, 23.7, 26.7], [22.1, 25.2, 29.5], 'white');
        b('C-frame upper fluix line', [10.4, 24.2, 26.6], [21.6, 24.45, 26.7], 'violet');
        for (const x of [12.6, 19.4]) {
            b('cantilever spine', [x - 0.75, 24, 10], [x + 0.75, 25.3, 27.4], 'frame');
            b('spine underside', [x - 0.45, 23.7, 11], [x + 0.45, 24, 26.3], 'dark');
            b('spine service rail', [x - 0.82, 24.4, 11], [x - 0.75, 24.65, 26.4], 'cyan');
            b('tool carriage shell', [x - 1.5, 22.3, 10.8], [x + 1.5, 24, 14.2], 'white');
            b('carriage bellows', [x - 1, 21.7, 11.3], [x + 1, 22.3, 13.7], 'dark');
            b('carriage service tag', [x - 0.7, 23, 10.66], [x + 0.7, 23.45, 10.8], 'violet');
        }
        const leftTip = stroke('left_mount', 12.6, 12.5, 22, 3.35, [7, 7.6, 8.2]);
        stroke('right_mount', 19.4, 12.5, 22, 3.35, [8.7, 9.3, 10]);
        for (const x of [11.65, 13.55])
            b('mounting gripper jaw', [x - 0.15, 18.4, 12.05],
                [x + 0.15, 19.9, 12.95], 'white', leftTip);
        for (const x of [22.3, 28.85]) {
            b('rear inspection pillar', [x, 11.5, 24], [x + 0.85, 19.7, 25.1], 'frame');
            b('rear pillar reflector', [x + 0.15, 14.7, 23.87], [x + 0.7, 17.8, 24], 'cyan');
        }
        b('exit inspection lintel', [22.3, 19.2, 23.8], [29.7, 20.1, 25.25], 'white');
        b('exit inspection sensor', [25.1, 18.9, 23.55], [26.9, 19.2, 23.8], 'violet');
        b('low front console', [12, 11.5, 1.1], [20, 15.2, 3.2], 'frame');
        b('console dark display', [13.1, 13.3, 0.95], [18.9, 14.6, 1.1], 'dark');
        b('console ME glyph', [14.1, 13.65, 0.9], [17.9, 14.2, 0.95], 'cyan');
        for (const x of [12.7, 14.5, 16.3, 18.1])
            b('console lower vent', [x, 12, 0.96], [x + 1.1, 12.25, 1.1], 'bluegray');
        bolts('C-frame roof fastener', [[10.5, 27.4], [21.5, 27.4], [10.5, 28.9], [21.5, 28.9]], 25.2);
        bolts('console fastener', [[12.6, 1.5], [19.4, 1.5], [12.6, 2.8], [19.4, 2.8]], 15.2);
        for (const x of [11.3, 12.25, 19.75, 20.7])
            b('C-frame rear vent', [x, 19, 29.5], [x + 0.5, 21.5, 29.58], 'dark');
        for (const x of [11.5, 12.3, 13.1, 18.5, 19.3, 20.1])
            b('C-frame spine rivet', [x, 25.3, 12], [x + 0.18, 25.45, 12.18], 'steel');
        for (const x of [23, 24.2, 25.4, 26.6, 27.8, 29])
            b('inspection scan pixel', [x, 19.95, 23.72], [x + 0.45, 20.1, 23.8], 'cyan');
        for (const x of [13.1, 14.15, 15.2, 16.25, 17.3, 18.35])
            b('console input key', [x, 15.2, 1.5], [x + 0.5, 15.33, 2], 'bluegray');
    } else if (kind === 'universal') {
        // An open-front robot cell and a high, rear-hung tool magazine.
        for (const x of [9.4, 22.6]) {
            for (const z of [9.4, 22.6]) {
                b('robot roof stanchion', [x - 0.55, 11.5, z - 0.55],
                    [x + 0.55, 26, z + 0.55], 'frame');
                b('roof stanchion inset', [x - 0.3, 16, z - 0.67],
                    [x + 0.3, 23.5, z - 0.55], 'bluegray');
                b('stanchion foot', [x - 0.95, 11.5, z - 0.95],
                    [x + 0.95, 12.2, z + 0.95], 'dark');
            }
        }
        rail('robot roof left', 9.4, 22.6, 25.9, 9.4, 10.3, 'white');
        rail('robot roof right', 9.4, 22.6, 25.9, 21.7, 22.6, 'white');
        for (const x of [9.4, 22.6])
            b('robot roof side', [x - 0.45, 25.9, 10.3], [x + 0.45, 26.25, 21.7], 'white');
        const bridge = group('robot_xy_bridge', [16, 25.75, 16]);
        b('robot XY bridge', [10.1, 25.2, 15.35], [21.9, 26.3, 16.65], 'steel', bridge);
        track(bridge, 'position', [[6, [0, 0, 0]], [7.2, [0, 0, 1.5]],
            [8.3, [0, 0, -1.5]], [9.5, [0, 0, 1.5]], [10.7, [0, 0, 0]], [12, [0, 0, 0]]]);
        const slider = group('robot_x_slider', [16, 25, 16], bridge);
        b('XY bridge dark slide', [13.2, 24.9, 15], [18.8, 25.2, 17], 'dark', slider);
        track(slider, 'position', [[6, [0, 0, 0]], [7, [-1.5, 0, 0]],
            [8.2, [1.5, 0, 0]], [9.4, [-1.5, 0, 0]], [10.6, [0, 0, 0]], [12, [0, 0, 0]]]);
        b('robot compact pivot', [14.5, 23.2, 14.5], [17.5, 25, 17.5], 'frame', slider);
        b('pivot inset lens', [15.25, 23.65, 14.38], [16.75, 24.5, 14.5], 'violet', slider);
        b('pivot shoulder link', [15.25, 21.8, 15.25], [16.75, 23.2, 16.75], 'steel', slider);
        b('pivot black gaiter', [15.45, 21.25, 15.45], [16.55, 21.8, 16.55], 'dark', slider);
        const gripper = stroke('robot_gripper', 16, 16, 21.2, 3.3, [7.6, 8.8, 10], slider);
        for (const x of [15.05, 16.95])
            b('robot gripper jaw', [x - 0.15, 17.85, 15.62],
                [x + 0.15, 18.9, 16.38], 'white', gripper);
        for (const x of [11, 14.5, 18]) {
            b('rear tool magazine socket', [x, 20.8, 25.7], [x + 2.8, 23.6, 28.6], 'frame');
            b('magazine dark cavity', [x + 0.4, 21.25, 25.48], [x + 2.4, 23.1, 25.7], 'dark');
            b('magazine tool silhouette', [x + 1.15, 21.55, 25.35], [x + 1.65, 22.85, 25.48], 'steel');
            b('magazine status', [x + 0.55, 23.4, 25.4], [x + 2.25, 23.55, 25.48], 'cyan');
        }
        b('magazine back rail', [10.5, 23.4, 27.8], [21.5, 24.4, 29.5], 'bluegray');
        b('magazine opaque back cover', [10.5, 20.8, 28.6], [21.5, 24.4, 29.5], 'white');
        b('magazine fluix accent', [11.2, 24, 27.68], [20.8, 24.15, 27.8], 'violet');
        for (const x of [11.5, 20.5]) {
            b('magazine roof hanger', [x - 0.55, 23.4, 21.7],
                [x + 0.55, 26.1, 28.5], 'frame');
            b('magazine hanger steel inset', [x - 0.2, 24, 22.6],
                [x + 0.2, 25.8, 27.8], 'steel');
        }
        for (const z of [11, 13.5, 16, 18.5, 21]) {
            b('cell edge light left', [9.27, 24.9, z], [9.4, 25.05, z + 1.1], 'cyan');
            b('cell edge light right', [22.6, 24.9, z], [22.73, 25.05, z + 1.1], 'cyan');
        }
        bolts('robot roof fastener', [[10, 9.9], [22, 9.9], [10, 22.1], [22, 22.1]], 26.25);
        for (const x of [11.5, 13.3, 15.1, 16.9, 18.7, 20.5])
            b('magazine rear vent', [x, 21.2, 29.5], [x + 0.7, 22.5, 29.58], 'dark');
        for (const x of [11.6, 13, 15.1, 16.5, 18.6, 20])
            b('magazine latch', [x, 20.65, 26], [x + 0.28, 20.8, 26.5], 'brass');
        for (const z of [11.4, 14.1, 17.2, 19.9])
            b('roof cable clip', [15.7, 26.3, z], [16.3, 26.55, z + 0.55], 'dark');
        b('center roof cable duct', [15.6, 26.15, 10.3], [16.4, 26.4, 21.7], 'bluegray');
        b('robot front status left', [9.2, 24.5, 9.3], [9.4, 25.15, 10.4], 'violet');
        b('robot front status right', [22.6, 24.5, 9.3], [22.8, 25.15, 10.4], 'violet');
    } else {
        throw new Error(`Unknown station: ${kind}`);
    }
}
