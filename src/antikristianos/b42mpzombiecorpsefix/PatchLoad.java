package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.iso.objects.IsoDeadBody", methodName="load", warmUp=true, strictMatch=true)
public final class PatchLoad {
    @Patch.OnExit
    public static void exit(@Patch.This zombie.iso.objects.IsoDeadBody body, @Patch.Argument(0) java.nio.ByteBuffer buffer,
            @Patch.Argument(1) int version, @Patch.Argument(2) boolean debug) { CorpseAuthority.afterLoad(body); }
}