package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.network.packets.character.ZombiePacket", methodName="set", warmUp=true, strictMatch=true)
public final class PatchLivingPacket {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) zombie.characters.IsoZombie z) { CorpseAuthority.beforeNetwork(z); }
}
