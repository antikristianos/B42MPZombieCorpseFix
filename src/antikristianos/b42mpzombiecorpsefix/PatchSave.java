package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.iso.objects.IsoDeadBody", methodName="save", warmUp=true, strictMatch=true)
public final class PatchSave {
    @Patch.OnEnter
    public static void enter(@Patch.This zombie.iso.objects.IsoDeadBody body, @Patch.Argument(0) java.nio.ByteBuffer buffer,
            @Patch.Argument(1) boolean debug) { CorpseAuthority.beforeSave(body); }
}