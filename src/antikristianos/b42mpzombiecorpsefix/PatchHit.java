package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.network.packets.hit.HitCharacter", methodName="processClient", warmUp=true, strictMatch=true)
public final class PatchHit {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) zombie.core.raknet.UdpConnection connection) { CorpseAuthority.enterHit(); }
    @Patch.OnExit(onThrowable=Throwable.class)
    public static void exit(@Patch.Argument(0) zombie.core.raknet.UdpConnection connection) { CorpseAuthority.exitHit(); }
}
