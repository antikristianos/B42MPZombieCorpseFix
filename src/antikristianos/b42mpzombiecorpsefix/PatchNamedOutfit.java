package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.characters.IsoZombie", methodName="dressInNamedOutfit", warmUp=true, strictMatch=true)
public final class PatchNamedOutfit {
    @Patch.OnEnter
    public static void enter(@Patch.This zombie.characters.IsoZombie zombie, @Patch.Argument(0) String name) {
        OutfitReadiness.begin(zombie, name);
    }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.This zombie.characters.IsoZombie zombie) { OutfitReadiness.end(zombie); }
}
