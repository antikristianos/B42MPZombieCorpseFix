package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.characters.IsoGameCharacter", methodName="setAttachedItem", warmUp=true, strictMatch=true)
public final class PatchAttachment {
    @Patch.OnEnter
    public static void enter(@Patch.This zombie.characters.IsoGameCharacter character, @Patch.Argument(0) String location,
            @Patch.Argument(1) zombie.inventory.InventoryItem item) { CorpseAuthority.beforeAttachment(character, location, item); }
}
