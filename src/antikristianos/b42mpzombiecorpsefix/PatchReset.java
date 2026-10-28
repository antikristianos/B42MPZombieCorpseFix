package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.characters.IsoGameCharacter", methodName="clearDiedBody", warmUp=true, strictMatch=true)
public final class PatchReset {
    @Patch.OnEnter
    public static void enter(@Patch.This zombie.characters.IsoGameCharacter character) { CorpseAuthority.reset(character); }
}
