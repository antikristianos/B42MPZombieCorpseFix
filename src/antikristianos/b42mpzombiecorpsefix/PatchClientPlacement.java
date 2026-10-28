package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.iso.IsoGridSquare", methodName="addCorpse", warmUp=true, strictMatch=true)
public final class PatchClientPlacement {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.This zombie.iso.IsoGridSquare square, @Patch.Argument(0) zombie.iso.objects.IsoDeadBody body,
            @Patch.Argument(1) boolean remote) { return remote && CorpseAuthority.beforeClientPlacement(square, body); }
}
