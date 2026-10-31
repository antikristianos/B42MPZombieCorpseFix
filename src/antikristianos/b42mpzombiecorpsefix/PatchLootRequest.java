package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.network.packets.RequestItemsForContainerPacket", methodName="processServer", warmUp=true, strictMatch=true)
public final class PatchLootRequest {
    @Patch.OnEnter(skipOn=true)
    public static boolean enter(@Patch.This zombie.network.packets.RequestItemsForContainerPacket packet,
            @Patch.Argument(0) zombie.network.PacketTypes.PacketType type, @Patch.Argument(1) zombie.core.raknet.UdpConnection connection) {
        return CorpseAuthority.retryLoot(packet);
    }
}