package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.inventory.ItemUser", methodName="RemoveItem", warmUp=true, strictMatch=true)
public final class PatchDestroyed {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) zombie.inventory.InventoryItem item) { CorpseAuthority.beforeDestroyed(item); }
}
