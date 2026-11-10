import java.nio.file.Files;
import java.nio.file.Path;

/** Source-level registration regression check; runtime tooltip rendering still needs a client. */
public class AssemblerJadeCheck {
    public static void main(String[] args) throws Exception {
        if (!AssemblerJadeCheck.class.desiredAssertionStatus()) {
            throw new IllegalStateException("Enable assertions with -ea");
        }
        Path root = Path.of(args.length == 0 ? "." : args[0]);
        for (String module : new String[] { "forge-1.20.1", "neoforge-1.21.1" }) {
            Path jade = root.resolve(module + "/src/main/java/me/myogoo/appliedtacz/compat/jade");
            String plugin = Files.readString(jade.resolve("AppliedTaczJadePlugin.java"));
            String assembler = Files.readString(jade.resolve("AssemblerJadeProvider.java"));
            assert !plugin.contains("registration.addConfig(") : module + " duplicates an AE2-owned config";
            assert plugin.contains("AppliedTaczJadeProvider.INSTANCE, AbstractGunSmithTableBlock.class") : "workbench compat lost";
            assert plugin.contains("AssemblerJadeProvider.INSTANCE, AEMolecularAssemblerBlockEntity.class") : "server state not registered";
            assert plugin.contains("AssemblerJadeProvider.INSTANCE, MolecularAssemblerBlock.class") : "tooltip not registered";
            assert assembler.contains("AssemblerNames.title(data.getString(WORKBENCH),") : "tier-aware localized title lost";
            assert assembler.contains("tooltip.remove(") && assembler.contains("CORE_OBJECT_NAME") : "default title not replaced";
            assert assembler.contains("assembler.isCrafting()") && assembler.contains("assembler.isNetworkOnline()") : "status not collected";
            String client = assembler.substring(assembler.indexOf("public void appendTooltip"), assembler.indexOf("public void appendServerData"));
            assert !client.contains("Component.literal") && !client.contains("PendingOutput") : "internal ID or buffer clutter";
            String registry = module.equals("forge-1.20.1") ? "items" : "item";
            String hidden = Files.readString(root.resolve(module + "/src/main/resources/data/c/tags/" + registry
                    + "/hidden_from_recipe_viewers.json"));
            assert hidden.replaceAll("\\s+", "").equals("{\"replace\":false,\"values\":["
                    + "\"appliedtacz:tacz_assembler_universal\",\"appliedtacz:molecular_assembler_universal\","
                    + "\"appliedtacz:molecular_assembler\"]}") : "hide tag must contain exactly the three universal items";
            for (String locale : new String[] { "en_us", "ko_kr" }) {
                String language = Files.readString(root.resolve(module
                        + "/src/main/resources/assets/appliedtacz/lang/" + locale + ".json"));
                assert language.matches("(?s).*\"config\\.jade\\.plugin_appliedtacz\\.assembler_status\"\\s*:\\s*\"[^\"]+\".*")
                        : module + "/" + locale + " missing the Jade provider config translation";
            }
        }
        System.out.println("PASS: both Jade registrations preserve workbenches, reuse AE2 config, translate provider config/title/status and hide universal viewer entries");
    }
}
