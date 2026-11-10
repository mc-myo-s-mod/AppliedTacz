import com.electronwill.nightconfig.core.CommentedConfig;
import me.myogoo.appliedtacz.config.AppliedTaczClientConfig;

/** Run against either loader's compiled config with assertions enabled. */
public class AssemblerClientConfigCheck {
    public static void main(String[] args) {
        if (!AssemblerClientConfigCheck.class.desiredAssertionStatus()) {
            throw new IllegalStateException("Enable assertions with -ea");
        }
        var spec = AppliedTaczClientConfig.SPEC;
        var config = CommentedConfig.inMemory();
        spec.correct(config);
        assert Boolean.TRUE.equals(config.get("assembler.renderWorkbench"));
        assert Boolean.TRUE.equals(config.get("assembler.rotateWorkbench"));
        for (int state = 0; state < 4; state++) {
            boolean render = (state & 1) != 0;
            boolean rotate = (state & 2) != 0;
            config.set("assembler.renderWorkbench", render);
            config.set("assembler.rotateWorkbench", rotate);
            assert spec.isCorrect(config) : "valid boolean combination rejected";
            spec.correct(config);
            assert Boolean.valueOf(render).equals(config.get("assembler.renderWorkbench"));
            assert Boolean.valueOf(rotate).equals(config.get("assembler.rotateWorkbench"));
        }
        config.set("assembler.renderWorkbench", 42);
        config.set("assembler.rotateWorkbench", 42);
        assert !spec.isCorrect(config) : "non-boolean settings accepted";
        spec.correct(config);
        assert spec.isCorrect(config);
        assert Boolean.TRUE.equals(config.get("assembler.renderWorkbench"));
        assert Boolean.TRUE.equals(config.get("assembler.rotateWorkbench"));
        System.out.println("PASS: client config defaults, four independent boolean combinations, invalid values corrected");
    }
}
