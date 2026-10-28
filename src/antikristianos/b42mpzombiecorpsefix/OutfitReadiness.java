package antikristianos.b42mpzombiecorpsefix;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import zombie.characters.IsoZombie;
import zombie.core.skinnedmodel.population.Outfit;
import zombie.core.skinnedmodel.population.OutfitManager;
import zombie.core.skinnedmodel.population.OutfitRNG;
import zombie.inventory.types.HandWeapon;
import zombie.network.GameClient;
import zombie.network.GameServer;

/** Keep asynchronous named outfits and their exact native random state together. */
public final class OutfitReadiness {
    private static final Field RNG = CorpseAuthority.field(OutfitRNG.class, "RNG");
    private static final Field S0 = CorpseAuthority.field(zombie.util.LocationRNG.class, "s0");
    private static final Field S1 = CorpseAuthority.field(zombie.util.LocationRNG.class, "s1");
    private static final Field STATE = CorpseAuthority.field(zombie.util.LocationRNG.class, "state");
    private static final Map<IsoZombie, PendingOutfit> OUTFITS = new WeakHashMap<>();
    private static final Map<IsoZombie, PendingDeath> DEATHS = new WeakHashMap<>();
    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private record RandomState(long s0, long s1, long state) { }
    private record PendingOutfit(String name, RandomState random) { }
    private record Frame(IsoZombie zombie, String name, RandomState initial, RandomState restore) { }
    private record PendingDeath(zombie.characters.IsoGameCharacter killer, HandWeapon weapon, boolean doDeath, boolean killed) { }

    private OutfitReadiness() { }

    private static Object rng() { return ((ThreadLocal<?>)CorpseAuthority.read(RNG, null)).get(); }
    private static RandomState capture() {
        Object rng = rng();
        return new RandomState((Long)CorpseAuthority.read(S0, rng), (Long)CorpseAuthority.read(S1, rng),
                (Long)CorpseAuthority.read(STATE, rng));
    }
    private static void restore(RandomState state) {
        try {
            Object rng = rng();
            S0.setLong(rng, state.s0); S1.setLong(rng, state.s1); STATE.setLong(rng, state.state);
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    public static void begin(IsoZombie zombie, String name) {
        if ((!GameClient.client && !GameServer.server) || zombie.isReanimatedPlayer() || zombie.wasFakeDead()) {
            FRAMES.get().push(new Frame(zombie, name, null, null));
            return;
        }
        RandomState ambient = capture();
        PendingOutfit pending = OUTFITS.get(zombie);
        boolean resume = pending != null && pending.name.equals(name) && name.equals(zombie.pendingOutfitName);
        if (resume) restore(pending.random);
        FRAMES.get().push(new Frame(zombie, name, capture(), resume ? ambient : null));
    }
    public static void end(IsoZombie zombie) {
        if (FRAMES.get().isEmpty()) return;
        Frame frame = FRAMES.get().pop();
        if (frame.zombie != zombie) throw new IllegalStateException("Unbalanced named outfit scope");
        if (frame.initial == null) return;
        if (frame.name != null && frame.name.equals(zombie.pendingOutfitName)) {
            OUTFITS.put(zombie, new PendingOutfit(frame.name, frame.initial));
        } else OUTFITS.remove(zombie);
        if (frame.restore != null) restore(frame.restore);
    }

    public static boolean ready(IsoZombie zombie) {
        if (!zombie.isPersistentOutfitInit()) zombie.dressInPersistentOutfitID(zombie.getPersistentOutfitID());
        String name = zombie.pendingOutfitName;
        if (name == null || name.isBlank()) { OUTFITS.remove(zombie); return true; }
        OutfitManager manager = OutfitManager.instance;
        Outfit outfit = manager == null ? null : zombie.isFemale() ? manager.FindFemaleOutfit(name) : manager.FindMaleOutfit(name);
        if (outfit == null) throw new IllegalStateException("Missing zombie outfit definition: " + name);
        if (outfit.isEmpty()) {
            outfit.loadItems();
            return false; // Ordinary loading is not a fatal network-packet error.
        }
        zombie.clothingItemChanged(null);
        return zombie.pendingOutfitName == null || zombie.pendingOutfitName.isBlank();
    }

    public static boolean deferKilled(IsoZombie zombie, zombie.characters.IsoGameCharacter killer, HandWeapon weapon, boolean doDeath) {
        if (!CorpseAuthority.eligible(zombie)) return false;
        if (DEATHS.containsKey(zombie)) return true;
        if (ready(zombie)) return false;
        DEATHS.putIfAbsent(zombie, new PendingDeath(killer, weapon, doDeath, true));
        CorpseAuthority.log("SERVER death waiting for outfit=" + zombie.pendingOutfitName + " zombie=" + zombie.getOnlineID());
        return true;
    }
    public static boolean deferDie(zombie.characters.IsoGameCharacter character) {
        if (!CorpseAuthority.eligible(character)) return false;
        IsoZombie zombie = (IsoZombie)character;
        if (!DEATHS.containsKey(zombie) && ready(zombie)) return false;
        DEATHS.putIfAbsent(zombie, new PendingDeath(null, null, true, false));
        return true;
    }
    public static void retryDeaths() {
        if (!CorpseAuthority.server() || DEATHS.isEmpty()) return;
        for (IsoZombie zombie : new ArrayList<>(DEATHS.keySet())) {
            if (!CorpseAuthority.eligible(zombie) || !zombie.isDead()) { DEATHS.remove(zombie); continue; }
            if (zombie.getCurrentSquare() == null || !ready(zombie)) continue;
            PendingDeath pending = DEATHS.remove(zombie);
            if (pending == null) continue;
            if (pending.killed) zombie.onKilled(pending.killer, pending.weapon, pending.doDeath);
            zombie.die();
        }
    }
    static void reset(IsoZombie zombie) { OUTFITS.remove(zombie); DEATHS.remove(zombie); }
}
