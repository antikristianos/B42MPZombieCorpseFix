package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.characters.IsoZombie", methodName="getItemVisuals", warmUp=true, strictMatch=true)
public final class PatchClientVisuals {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.This zombie.characters.IsoZombie zombie,
            @Patch.Argument(0) zombie.core.skinnedmodel.visual.ItemVisuals destination) {
        return ClientCorpsePresentation.visuals(zombie, destination);
    }
}
