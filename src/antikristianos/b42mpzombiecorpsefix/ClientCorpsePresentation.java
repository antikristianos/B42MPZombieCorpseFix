package antikristianos.b42mpzombiecorpsefix;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import zombie.characters.IsoZombie;
import zombie.core.raknet.UdpConnection;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoDeadBody;
import zombie.network.id.ObjectIDManager;
import zombie.network.packets.INetworkPacket;
import zombie.network.GameClient;
import zombie.network.GameServer;

/** Native death timing, authoritative complete bodies and server container order. */
public final class ClientCorpsePresentation {
    public static final String SQUARE_INDEX = CorpseAuthority.MARKER + ".squareIndex";
    private static final Map<IsoZombie, WeakReference<Pending>> PENDING = new WeakHashMap<>();
    private static final Map<IsoDeadBody, Waiting> WITHOUT_ZOMBIE = new WeakHashMap<>();
    private record Waiting(IsoGridSquare square, int index) { }
    private static IsoDeadBody placing;

    private ClientCorpsePresentation() { }

    static boolean isPlacing(IsoDeadBody body) { return placing == body; }

    static boolean postpone(IsoZombie zombie, IsoGridSquare square, IsoDeadBody body) {
        Object value = body.getModData().rawget(SQUARE_INDEX);
        // Ordinary movement of an existing corpse is not a new animated death.
        if (!(value instanceof Double number) || number < 0 || number != Math.floor(number)) return false;
        int index = number.intValue();
        if (zombie == null) {
            for (IsoDeadBody previous : WITHOUT_ZOMBIE.keySet()) {
                if (previous.getObjectID().getObjectID() == body.getObjectID().getObjectID()) {
                    ObjectIDManager.getInstance().addObject(previous);
                    drain();
                    return true;
                }
            }
            WITHOUT_ZOMBIE.put(body, new Waiting(square, index));
            drain();
            return true;
        }
        Pending previous = pendingFor(zombie);
        if (previous != null) {
            if (previous.body.getObjectID().getObjectID() != body.getObjectID().getObjectID()) {
                throw new IllegalStateException("Conflicting pending corpse for zombie " + zombie.getOnlineID());
            }
            // Duplicates cannot restart the native timeout or replace item state.
            ObjectIDManager.getInstance().addObject(previous.body);
            return true;
        }
        Pending pending = new Pending(zombie, square, body, index);
        PENDING.put(zombie, new WeakReference<>(pending));
        zombie.resetModelNextFrame();
        zombie.getNetworkCharacterAI().setCorpse(pending, square, index);
        // DeadZombiePacket uses the same signal: Vanilla animates before onDied.
        zombie.applyDamage(zombie.getHealth());
        if (zombie.isOnDeathDone() && !zombie.isRagdollSimulationActive()) zombie.getNetworkCharacterAI().onDied();
        return true;
    }

    private static Pending pendingFor(IsoZombie zombie) {
        WeakReference<Pending> reference = PENDING.get(zombie);
        return reference == null ? null : reference.get();
    }

    public static boolean visuals(IsoZombie zombie, zombie.core.skinnedmodel.visual.ItemVisuals destination) {
        if (!GameClient.client || GameServer.server
                || zombie.isReanimatedPlayer() || zombie.wasFakeDead()) return false;
        Pending pending = pendingFor(zombie);
        zombie.core.skinnedmodel.visual.ItemVisuals source;
        if (pending != null) source = pending.visuals;
        else {
            if (!zombie.isUsingWornItems() || !zombie.getWornItems().isEmpty()) return false;
            source = CorpseAuthority.rawVisuals(zombie);
            if (source.isEmpty()) return false;
        }
        if (destination != source) { destination.clear(); destination.addAll(source); }
        return true;
    }

    public static void beforeOnDied(zombie.characters.NetworkCharacterAI ai) {
        if (!zombie.network.GameClient.client || zombie.network.GameServer.server
                || !(ai.getCharacter() instanceof IsoZombie living)) return;
        Pending pending = pendingFor(living);
        if (pending == null) return;
        for (var object : pending.square.getStaticMovingObjects()) {
            if (object instanceof IsoDeadBody existing
                    && existing.getObjectID().getObjectID() == pending.body.getObjectID().getObjectID()) {
                // A chunk already placed this corpse. Complete the animation, then
                // let the usual duplicate guard retain its possibly looted inventory.
                ai.setCorpse(pending, pending.square, pending.square.getStaticMovingObjects().size());
                return;
            }
        }
    }

    static void reset(IsoZombie zombie) {
        Pending pending = pendingFor(zombie);
        PENDING.remove(zombie);
        if (pending != null) zombie.getNetworkCharacterAI().setCorpse(null, null, -1);
    }

    private static void place(Pending pending) {
        placing = pending.body;
        try { pending.square.addCorpse(pending.body, true); }
        finally { placing = null; }
        // Ordering metadata is transport-only, not inherited when a corpse is moved.
        pending.body.getModData().rawset(SQUARE_INDEX, null);
    }

    static void drain() {
        boolean progress;
        do {
            progress = false;
            var iterator = WITHOUT_ZOMBIE.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                Waiting waiting = entry.getValue();
                if (waiting.square.getStaticMovingObjects().size() != waiting.index) continue;
                IsoDeadBody body = entry.getKey();
                iterator.remove();
                place(new Pending(null, waiting.square, body, waiting.index));
                progress = true;
                break;
            }
        } while (progress);
    }

    private static final class Pending implements INetworkPacket {
        private final WeakReference<IsoZombie> zombie;
        private final IsoGridSquare square;
        private final IsoDeadBody body;
        private final int index;
        private final zombie.core.skinnedmodel.visual.ItemVisuals visuals = new zombie.core.skinnedmodel.visual.ItemVisuals();

        private Pending(IsoZombie zombie, IsoGridSquare square, IsoDeadBody body, int index) {
            this.zombie = new WeakReference<>(zombie);
            this.square = square;
            this.body = body;
            this.index = index;
            zombie.core.skinnedmodel.visual.ItemVisuals source = new zombie.core.skinnedmodel.visual.ItemVisuals();
            body.getItemVisuals(source);
            for (zombie.core.skinnedmodel.visual.ItemVisual visual : source) {
                zombie.core.skinnedmodel.visual.ItemVisual copy = new zombie.core.skinnedmodel.visual.ItemVisual();
                copy.copyFrom(visual);
                copy.setInventoryItem(null); // Presentation never owns or mutates corpse items.
                visuals.add(copy);
            }
        }

        @Override public void parse(zombie.core.network.ByteBufferReader input, zombie.network.IConnection connection) {
            throw new UnsupportedOperationException("Local corpse animation handoff");
        }
        @Override public void write(zombie.core.network.ByteBufferWriter output) {
            throw new UnsupportedOperationException("Local corpse animation handoff");
        }
        @Override public void processClient(UdpConnection connection) {
            IsoZombie living = zombie.get();
            if (living == null || pendingFor(living) != this) return;
            PENDING.remove(living);
            living.removeFromWorld();
            living.removeFromSquare();
            place(this);
            drain();
        }
    }
}
