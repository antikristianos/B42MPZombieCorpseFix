package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.characters.IsoGameCharacter", methodName="becomeCorpse", warmUp=true, strictMatch=true)
public final class PatchDeath {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.This zombie.characters.IsoGameCharacter character) { return CorpseAuthority.defer(character); }
}
