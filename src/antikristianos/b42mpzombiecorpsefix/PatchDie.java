package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.characters.IsoGameCharacter", methodName="die", warmUp=true, strictMatch=true)
public final class PatchDie {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.This zombie.characters.IsoGameCharacter character) { return OutfitReadiness.deferDie(character); }
}
