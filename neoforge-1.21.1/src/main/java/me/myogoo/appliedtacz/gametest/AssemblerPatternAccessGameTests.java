package me.myogoo.appliedtacz.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.SlotSemantics;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.crafting.PatternEncodingInputs;
import me.myogoo.appliedtacz.crafting.TaCZPatternEncodingRecipes;
import me.myogoo.appliedtacz.crafting.TaCZPatternMetadata;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.registry.ModBlocks;
import me.myogoo.appliedtacz.menu.AEMolecularAssemblerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(AppliedTaCZ.MODID)
@PrefixGameTestTemplate(false)
public final class AssemblerPatternAccessGameTests {
    private static final BlockPos ROOT = new BlockPos(1, 1, 1);

    private AssemblerPatternAccessGameTests() {
    }

    @GameTest(template = "empty3x3x3")
    public static void assemblerPatternInsertionIsValidatedAndLegacyItemsSurvive(GameTestHelper helper) {
        ItemStack unrelated = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.BEDROCK), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.DIRT), 1)));
        for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
            var entry = TaCZPatternEncodingRecipes.getRecipes(helper.getLevel(), kind.defaultBlockId()).stream()
                    .filter(candidate -> PatternEncodingInputs.isEncodable(candidate.recipe()))
                    .findFirst().orElseThrow();
            List<AEItemKey> choices = new ArrayList<>();
            for (int i = 0; i < entry.recipe().getInputs().size(); i++) {
                choices.add(PatternEncodingInputs.choices(entry.recipe(), i).get(0));
            }
            ItemStack accepted = TaCZPatternEncodingRecipes.encode(helper.getLevel(), entry.recipe(), choices);
            for (var block : List.of(kind.assemblerBlock(), kind.extendedAssemblerBlock())) {
                helper.setBlock(ROOT, block);
                var assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
                assembler.setId(kind.defaultBlockId());
                String titleKey = block == kind.extendedAssemblerBlock()
                        ? "gui.appliedtacz.assembler.extended_title" : "gui.appliedtacz.assembler.title";
                String workbenchNameKey = switch (kind) {
                    case GUN_SMITH_TABLE -> "gui.appliedtacz.assembler.workbench.gun";
                    case AMMO_WORKBENCH -> "gui.appliedtacz.assembler.workbench.ammo";
                    case ATTACHMENT_WORKBENCH -> "gui.appliedtacz.assembler.workbench.attachment";
                };
                helper.assertTrue(assembler.getTerminalGroup().name().getContents() instanceof TranslatableContents title
                                && title.getKey().equals(titleKey) && title.getArgs().length == 1
                                && title.getArgs()[0] instanceof Component workbenchName
                                && workbenchName.getContents() instanceof TranslatableContents translatedWorkbench
                                && translatedWorkbench.getKey().equals(workbenchNameKey),
                        "Expected server-built translated group title with the bound workbench name");
                var inventory = assembler.getPatternInventory();
                helper.assertTrue(inventory.isItemValid(0, accepted), "Expected matching legacy processing pattern");
                for (var rejected : List.of(unrelated, AEItems.PROCESSING_PATTERN.stack(), new ItemStack(Items.DIRT))) {
                    helper.assertTrue(!inventory.isItemValid(0, rejected)
                                    && inventory.insertItem(0, rejected.copy(), true).getCount() == 1
                                    && inventory.insertItem(0, rejected.copy(), false).getCount() == 1
                                    && inventory.isEmpty(),
                            "Expected invalid simulation and automation insertion to leave all items unchanged");
                }

                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                ItemStack bound = accepted.copy();
                TaCZPatternMetadata.set(bound, kind.defaultBlockId(), player);
                ItemStack wrongWorkbench = accepted.copy();
                var otherKind = WorkbenchUpgradeKind.values()[(kind.ordinal() + 1)
                        % WorkbenchUpgradeKind.values().length];
                TaCZPatternMetadata.set(wrongWorkbench, otherKind.defaultBlockId(), player);
                helper.assertTrue(inventory.isItemValid(0, bound) && !inventory.isItemValid(0, wrongWorkbench)
                                && inventory.addItems(wrongWorkbench.copy()).getCount() == 1 && inventory.isEmpty(),
                        "Expected exact workbench binding even when recipe inputs and outputs are identical");
                var pos = helper.absolutePos(ROOT);
                player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                var menu = new AEMolecularAssemblerMenu(21, player.getInventory(), assembler);
                var slot = menu.getSlots(SlotSemantics.ENCODED_PATTERN).get(0);
                helper.assertTrue(slot.mayPlace(accepted) && !slot.mayPlace(wrongWorkbench),
                        "Expected GUI slots to use the assembler recipe filter");
                menu.setCarried(wrongWorkbench.copy());
                menu.clicked(slot.index, 0, ClickType.PICKUP, player);
                helper.assertTrue(inventory.isEmpty() && ItemStack.matches(wrongWorkbench, menu.getCarried()),
                        "Expected rejected click to preserve carried pattern");
                menu.setCarried(ItemStack.EMPTY);
                player.getInventory().setItem(0, wrongWorkbench.copy());
                var playerSlot = menu.slots.stream()
                        .filter(candidate -> candidate.container == player.getInventory()
                                && ItemStack.matches(candidate.getItem(), wrongWorkbench)).findFirst().orElseThrow();
                menu.quickMoveStack(player, playerSlot.index);
                helper.assertTrue(inventory.isEmpty() && player.getInventory().contains(wrongWorkbench),
                        "Expected rejected shift click not to enter the pattern inventory");

                var terminalSlot = new FilteredInternalInventory(
                        assembler.getTerminalPatternInventory().getSlotInv(0), new IAEItemFilter() {});
                helper.assertTrue(terminalSlot.addItems(wrongWorkbench.copy()).getCount() == 1 && inventory.isEmpty(),
                        "Expected terminal wrapper to retain the assembler insertion filter");
                helper.assertTrue(terminalSlot.addItems(accepted.copy()).isEmpty(),
                        "Expected terminal insertion of a matching pattern");
                helper.assertTrue(ItemStack.matches(accepted, terminalSlot.extractItem(0, 1, false)),
                        "Expected installed pattern to remain removable");

                // Direct setters and NBT loads intentionally bypass insertion checks to preserve old saves.
                inventory.setItemDirect(0, wrongWorkbench.copy());
                var saved = assembler.saveWithFullMetadata(helper.getLevel().registryAccess());
                var restored = new AEMolecularAssemblerBlockEntity(pos, block.defaultBlockState());
                restored.setLevel(helper.getLevel());
                restored.loadTag(saved, helper.getLevel().registryAccess());
                helper.assertTrue(ItemStack.matches(wrongWorkbench, restored.getPatternInventory().getStackInSlot(0))
                                && restored.getAvailablePatterns().isEmpty(),
                        "Expected old invalid patterns preserved but not advertised for crafting");
                helper.assertTrue(ItemStack.matches(wrongWorkbench,
                                restored.getPatternInventory().extractItem(0, 1, false))
                                && restored.collectPendingDrops().isEmpty(),
                        "Expected exactly one recoverable old pattern and no duplicate drops");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 100)
    public static void assemblerIsDiscoverableByPatternAccessTerminal(GameTestHelper helper) {
        helper.setBlock(ROOT, ModBlocks.TACZ_ASSEMBLER_UNIVERSAL.get());
        helper.setBlock(ROOT.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(30, () -> {
            var assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
            var grid = assembler.getMainNode().getGrid();
            helper.assertTrue(grid != null && assembler.getMainNode().isActive(),
                    "Expected a powered active assembler network");
            helper.assertTrue(java.util.stream.StreamSupport.stream(grid.getMachineClasses().spliterator(), false)
                            .anyMatch(type -> type == assembler.getClass() && PatternContainer.class.isAssignableFrom(type))
                            && grid.getActiveMachines(AEMolecularAssemblerBlockEntity.class).contains(assembler),
                    "Expected the exact Pattern Access Terminal machine discovery path to include the assembler");
            PatternContainer container = assembler;
            helper.assertTrue(container.getGrid() == grid && container.isVisibleInTerminal()
                            && container.getTerminalPatternInventory() == assembler.getPatternInventory()
                            && container.getTerminalPatternInventory().size() == 9
                            && container.getTerminalGroup() != null,
                    "Expected shared real pattern inventory, group and connected grid");
            helper.succeed();
        });
    }
}
