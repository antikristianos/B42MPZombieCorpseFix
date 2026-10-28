package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.characters.IsoZombie", methodName="onKilled", warmUp=true, strictMatch=true)
public final class PatchKilled {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.This zombie.characters.IsoZombie zombie,
            @Patch.Argument(0) zombie.characters.IsoGameCharacter killer,
            @Patch.Argument(1) zombie.inventory.types.HandWeapon weapon, @Patch.Argument(2) boolean doDeath) {
        return OutfitReadiness.deferKilled(zombie, killer, weapon, doDeath);
    }
}
