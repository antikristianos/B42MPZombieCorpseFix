package antikristianos.b42mpzombiecorpsefix;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className="zombie.inventory.ItemConfigurator", methodName="GetIdForString", warmUp=true, strictMatch=true)
public final class PatchLootSelector {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) String containerType) {
        CorpseAuthority.registerCorpseLootSelector(containerType);
    }
}
