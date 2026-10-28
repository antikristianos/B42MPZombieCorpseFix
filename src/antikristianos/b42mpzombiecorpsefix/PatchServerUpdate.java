package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.network.ServerMap", methodName="postupdate", warmUp=true, strictMatch=true)
public final class PatchServerUpdate {
    @Patch.OnEnter
    public static void enter() { OutfitReadiness.retryDeaths(); }
}
