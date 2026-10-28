package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.characters.IsoZombie", methodName="DoZombieInventory", warmUp=true, strictMatch=true)
public final class PatchInventory {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.This zombie.characters.IsoZombie z, @Patch.Argument(0) boolean randomCorpse) {
        if (!CorpseAuthority.eligible(z)) return false;
        CorpseAuthority.prepare(z, true);
        return true;
    }
}
