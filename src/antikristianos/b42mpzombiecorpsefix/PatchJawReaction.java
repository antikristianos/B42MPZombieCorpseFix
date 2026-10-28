package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.network.fields.hit.Zombie", methodName="react", warmUp=true, strictMatch=true)
public final class PatchJawReaction {
    @Patch.OnEnter
    public static void enter(@Patch.This zombie.network.fields.hit.Zombie target, @Patch.Argument(0) zombie.inventory.types.HandWeapon weapon) {
        // WeaponHit has already moved the real server item. A later visual reaction
        // must not replace it, or an existing blade fragment, with the packet weapon.
        if (CorpseAuthority.server()) target.getZombie().setJawStabAttach(false);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.This zombie.network.fields.hit.Zombie target, @Patch.Argument(0) zombie.inventory.types.HandWeapon weapon) {
        if (CorpseAuthority.server()) target.getZombie().setJawStabAttach(target.getZombie().getAttachedItem("JawStab") != null);
    }
}
