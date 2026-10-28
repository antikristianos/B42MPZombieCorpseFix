package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.characters.NetworkCharacterAI", methodName="onDied", warmUp=true, strictMatch=true)
public final class PatchClientDeath {
    @Patch.OnEnter
    public static void enter(@Patch.This zombie.characters.NetworkCharacterAI ai) {
        ClientCorpsePresentation.beforeOnDied(ai);
    }
}
