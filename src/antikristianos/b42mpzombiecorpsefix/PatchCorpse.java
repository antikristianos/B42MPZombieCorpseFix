package antikristianos.b42mpzombiecorpsefix;
import me.zed_0xff.zombie_buddy.Patch;
@Patch(className="zombie.iso.objects.IsoDeadBody", methodName="<init>", warmUp=true, strictMatch=true)
public final class PatchCorpse {
    @Patch.OnEnter
    public static void enter(@Patch.Argument(0) zombie.characters.IsoGameCharacter character,
            @Patch.Argument(1) boolean wasCorpse, @Patch.Argument(2) boolean addToWorld) { CorpseAuthority.beforeCorpse(character); }
    @Patch.OnExit
    public static void exit(@Patch.This zombie.iso.objects.IsoDeadBody body, @Patch.Argument(0) zombie.characters.IsoGameCharacter character,
            @Patch.Argument(1) boolean wasCorpse, @Patch.Argument(2) boolean addToWorld) { CorpseAuthority.afterCorpse(body, character, addToWorld); }
}
