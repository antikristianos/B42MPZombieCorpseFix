package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.network.GameServer", methodName="sendCharacterDeath", warmUp=true, strictMatch=true)
public final class PatchDeathMessage {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.Argument(0) zombie.iso.objects.IsoDeadBody body) { return CorpseAuthority.replaceDeathMessage(body); }
}
