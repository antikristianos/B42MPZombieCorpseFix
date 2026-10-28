package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.core.raknet.UdpConnection", methodName="setFullyConnected", warmUp=true, strictMatch=true)
public final class PatchConnected {
    @Patch.OnExit
    public static void exit(@Patch.This zombie.core.raknet.UdpConnection connection) { CorpseAuthority.connected(connection); }
}
