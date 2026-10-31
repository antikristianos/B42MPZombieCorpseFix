package antikristianos.b42mpzombiecorpsefix;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;
import zombie.characters.WornItems.WornItems;
import zombie.core.skinnedmodel.visual.ItemVisual;
import zombie.core.skinnedmodel.visual.ItemVisuals;
import zombie.inventory.InventoryItem;
import zombie.inventory.InventoryItemFactory;
import zombie.inventory.ItemContainer;
import zombie.inventory.ItemPickerJava;
import zombie.inventory.ItemSpawner;
import zombie.inventory.types.Clothing;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoDeadBody;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.id.ObjectIDManager;
import zombie.network.packets.actions.AddCorpseToMapPacket;
import zombie.scripting.objects.ItemBodyLocation;
import zombie.scripting.objects.ItemTag;

/** Server equipment and new-death transaction. No loaded-corpse repair scan. */
public final class CorpseAuthority {
    public static final String MARKER = "B42MPZombieCorpseFix";
    private static final Field VISUALS = field(IsoZombie.class, "itemVisuals");
    private static final Field DEATH_ITEMS = field(IsoZombie.class, "itemsToSpawnAtDeath");
    private static final Map<IsoZombie, Equipment> EQUIPMENT = new WeakHashMap<>();
    private static final ThreadLocal<HitScope> HITS = ThreadLocal.withInitial(HitScope::new);
    private static long prepared, published, received;
    private static final Map<IsoDeadBody, Boolean> PUBLISHED = new WeakHashMap<>();

    private CorpseAuthority() { }
    static final class Equipment {
        final IdentityHashMap<ItemVisual, InventoryItem> clothing = new IdentityHashMap<>();
        boolean deathPrepared;
    }
    static final class HitScope {
        int depth;
        final LinkedHashSet<IsoZombie> deaths = new LinkedHashSet<>();
    }
    static Field field(Class<?> owner, String name) {
        try { Field f = owner.getDeclaredField(name); f.setAccessible(true); return f; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    static Object read(Field f, Object owner) {
        try { return f.get(owner); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    public static boolean server() { return GameServer.server && !GameClient.client; }
    public static void registerCorpseLootSelector(String containerType) {
        if (server() && ("inventoryfemale".equals(containerType) || "inventorymale".equals(containerType))) {
            // Despite its name, the installed registerZone delegates to the same
            // native string registry used by Container selectors. No invented IDs.
            zombie.inventory.ItemConfigurator.registerZone(containerType);
        }
    }
    static ItemVisuals rawVisuals(IsoZombie zombie) { return (ItemVisuals)read(VISUALS, zombie); }
    public static boolean eligible(IsoGameCharacter character) {
        return server() && character instanceof IsoZombie z && !z.isReanimatedPlayer() && !z.wasFakeDead();
    }
    public static void reset(IsoGameCharacter character) {
        if (character instanceof IsoZombie z) {
            Equipment previous = EQUIPMENT.remove(z);
            if (previous != null) for (InventoryItem item : previous.clothing.values()) {
                // A native corpse already owns transferred items. Never remove them there.
                if (item.getContainer() == z.getInventory()) {
                    z.getWornItems().remove(item);
                    z.getInventory().Remove(item);
                }
            }
            OutfitReadiness.reset(z);
            ClientCorpsePresentation.reset(z);
        }
    }
    public static void beforeNetwork(IsoZombie z) {
        if (!eligible(z) || z.isUsingWornItems()) return;
        if (!OutfitReadiness.ready(z)) return;
        Equipment known = EQUIPMENT.get(z);
        ItemVisuals visuals = (ItemVisuals) read(VISUALS, z);
        boolean changed = !z.isPersistentOutfitInit() || known == null || known.clothing.size() != visuals.size();
        if (!changed) for (ItemVisual visual : visuals) if (!known.clothing.containsKey(visual)) { changed = true; break; }
        if (changed) prepare(z, false);
    }

    /** Convert actual server visuals; never choose an alternative outfit for an empty list. */
    public static void prepare(IsoZombie z, boolean death) {
        if (!eligible(z)) return;
        if (!OutfitReadiness.ready(z)) {
            throw new IllegalStateException("Unresolved zombie outfit: " + z.pendingOutfitName);
        }
        Equipment old = EQUIPMENT.get(z);
        if (death && old != null && old.deathPrepared && z.isUsingWornItems()) {
            // Respect equipment changes made by OnZombieDead after the initial conversion.
            ItemContainer inventory = z.getInventory();
            for (int i = 0; i < z.getWornItems().size(); i++) addExact(inventory, z.getWornItems().get(i).getItem());
            for (int i = 0; i < z.getAttachedItems().size(); i++) addExact(inventory, z.getAttachedItems().get(i).getItem());
            spawnDeathItems(z, inventory);
            return;
        }
        if (old == null) old = new Equipment();
        ItemVisuals visuals = (ItemVisuals) read(VISUALS, z);
        WornItems staged = new WornItems(z.getWornItems().getBodyLocationGroup());
        Equipment next = new Equipment();
        next.deathPrepared = death;
        // Prepare everything before replacing worn slots or removing old owned clothing.
        for (int i = 0; i < visuals.size(); i++) {
            ItemVisual visual = visuals.get(i);
            InventoryItem item = old.clothing.get(visual);
            if (item == null) item = visual.getInventoryItem();
            if (item == null || !visual.getItemType().equals(item.getFullType())) {
                item = InventoryItemFactory.CreateItem(visual.getItemType());
            }
            if (item == null) throw new IllegalStateException("Missing worn item definition: " + visual.getItemType());
            ItemBodyLocation location = item instanceof Clothing ? item.getBodyLocation() : item.canBeEquipped();
            if (location == null) throw new IllegalStateException("Worn visual has no equipment location: " + item.getFullType());
            if (item.getVisual() != null) {
                item.getVisual().copyFrom(visual);
                item.synchWithVisual();
                int fromHoles = Math.max(0, item.getConditionMax() - visual.getHolesNumber() * 3);
                item.setConditionNoSound(Math.min(item.getCondition(), fromHoles));
            }
            staged.setItem(location, item);
            next.clothing.put(visual, item);
        }
        ItemContainer inventory = z.getInventory();
        for (InventoryItem previous : old.clothing.values()) {
            if (!next.clothing.containsValue(previous)) inventory.Remove(previous);
        }
        z.getWornItems().copyFrom(staged);
        for (int i = 0; i < staged.size(); i++) addExact(inventory, staged.get(i).getItem());
        for (int i = 0; i < z.getAttachedItems().size(); i++) {
            InventoryItem item = z.getAttachedItems().get(i).getItem();
            nameItem(z, item);
            addExact(inventory, item);
        }
        inventory.setSourceGrid(z.getCurrentSquare());
        EQUIPMENT.put(z, next);
        if (death) spawnDeathItems(z, inventory);
        if (++prepared == 1) log("SERVER equipment materialization active; no empty-outfit fallback.");
    }
    private static void nameItem(IsoZombie z, InventoryItem item) {
        if (z.getDescriptor() == null) return;
        if (item.hasTag(ItemTag.APPLY_OWNER_NAME)) item.nameAfterDescriptor(z.getDescriptor());
        else if (item.hasTag(ItemTag.MONOGRAM_OWNER_NAME)) item.monogramAfterDescriptor(z.getDescriptor());
    }
    @SuppressWarnings("unchecked")
    private static void spawnDeathItems(IsoZombie z, ItemContainer inventory) {
        List<InventoryItem> pending = (List<InventoryItem>) read(DEATH_ITEMS, z);
        if (pending == null) return;
        // Consume successful entries individually; a failed entry is never silently cleared.
        while (!pending.isEmpty()) {
            InventoryItem item = pending.get(0);
            nameItem(z, item);
            if (!inventory.getItems().contains(item) && ItemSpawner.spawnItem(item, inventory) == null) {
                throw new IllegalStateException("Unable to transfer configured death item: " + item.getFullType());
            }
            pending.remove(0);
        }
    }
    static void addExact(ItemContainer inventory, InventoryItem item) {
        if (item == null) throw new IllegalStateException("Null carried item");
        for (InventoryItem existing : inventory.getItems()) {
            if (existing == item) { item.setContainer(inventory); return; }
            if (existing.getID() == item.getID()) throw new IllegalStateException("Conflicting carried item ID " + item.getID());
        }
        item.setContainer(inventory);
        inventory.getItems().add(item);
    }

    public static void enterHit() { if (server()) HITS.get().depth++; }
    public static boolean defer(IsoGameCharacter character) {
        if (!eligible(character) || HITS.get().depth == 0) return false;
        HITS.get().deaths.add((IsoZombie) character);
        return true;
    }
    public static void exitHit() {
        if (!server()) return;
        HitScope scope = HITS.get();
        if (scope.depth == 0 || --scope.depth != 0) return;
        List<IsoZombie> deaths = new ArrayList<>(scope.deaths);
        scope.deaths.clear();
        for (IsoZombie z : deaths) if (z.isDead()) z.die();
    }
    public static void beforeCorpse(IsoGameCharacter character) {
        if (eligible(character)) prepare((IsoZombie) character, true);
    }
    public static void afterCorpse(IsoDeadBody body, IsoGameCharacter character, boolean addToWorld) {
        if (!eligible(character)) return;
        // The native constructor transferred the actual inventory and all slot references.
        verifyMembership(body);
        body.getModData().rawset(MARKER, "1");
        try { rollAdditionalLoot(body); }
        catch (RuntimeException e) {
            // Keep the complete carried inventory. An opening request can retry the
            // isolated loot transaction; it is never marked completed on failure.
            log("LOOT PENDING corpse=" + body.getObjectID().getObjectID() + ": " + e);
        }
        EQUIPMENT.remove((IsoZombie) character);
        if (addToWorld) publish(body);
    }
    static void verifyMembership(IsoDeadBody body) {
        ItemContainer c = body.getContainer();
        if (c == null) throw new IllegalStateException("New zombie corpse has no container");
        for (int i = 0; i < body.getWornItems().size(); i++) addExact(c, body.getWornItems().get(i).getItem());
        for (int i = 0; i < body.getAttachedItems().size(); i++) addExact(c, body.getAttachedItems().get(i).getItem());
    }
    static void rollAdditionalLoot(IsoDeadBody body) {
        generateLoot(body, extra -> ItemPickerJava.fillContainer(extra, null));
    }
    static void generateLoot(IsoDeadBody body, java.util.function.Consumer<ItemContainer> roll) {
        ItemContainer carried = body.getContainer();
        if (carried.isExplored()) return; // Fire / prior explicit generation remains authoritative.
        if (body.getSquare() == null) throw new IllegalStateException("Cannot roll corpse loot without its world square");
        ItemContainer extra = new ItemContainer(carried.getType(), body.getSquare(), body);
        extra.setCapacity(carried.getCapacity());
        // Isolate the random roll from clothes, embedded weapons and their weight/capacity.
        roll.accept(extra);
        java.util.HashSet<Integer> ids = new java.util.HashSet<>();
        for (InventoryItem item : carried.getItems()) ids.add(item.getID());
        for (InventoryItem item : extra.getItems()) {
            if (!ids.add(item.getID())) throw new IllegalStateException("Loot item ID collision");
        }
        for (InventoryItem item : new ArrayList<>(extra.getItems())) addExact(carried, item);
        extra.getItems().clear();
        carried.setExplored(true);
    }
    public static boolean managed(IsoDeadBody body) {
        return body != null && body.isZombie() && "1".equals(body.getModData().rawget(MARKER));
    }
    public static boolean replaceDeathMessage(IsoDeadBody body) {
        return server() && PUBLISHED.containsKey(body); // Per-object provenance, never an inherited save marker.
    }
    public static void publish(IsoDeadBody body) {
        if (!server() || !managed(body) || body.getSquare() == null) return;
        for (zombie.core.raknet.UdpConnection connection : GameServer.udpEngine.connections) {
            if (!connection.isFullyConnected()) {
                WAITING.computeIfAbsent(connection, ignored -> new LinkedHashSet<>()).add(body);
            } else if (connection.isRelevantTo(body.getX(), body.getY())) {
                sendBody(body, connection);
            }
        }
        PUBLISHED.put(body, Boolean.TRUE);
        if (++published == 1 || published % 100 == 0) log("SERVER full corpse snapshots=" + published);
    }

    /** Called only on receipt/placement, never when loading or scanning existing corpses. */
    public static boolean beforeClientPlacement(IsoGridSquare square, IsoDeadBody incoming) {
        if (!GameClient.client || GameServer.server || !managed(incoming)) return false;
        afterLoad(incoming); // Also guard wrappers that log and swallow a native load exception.
        for (var object : square.getStaticMovingObjects()) {
            if (object instanceof IsoDeadBody existing && existing.getObjectID().getObjectID() == incoming.getObjectID().getObjectID()) {
                // A chunk snapshot may have arrived first and may already have been looted.
                ObjectIDManager.getInstance().addObject(existing);
                return true;
            }
        }
        if (!ClientCorpsePresentation.isPlacing(incoming)) {
            IsoZombie living = GameClient.IDToZombieMap.get(incoming.getCharacterOnlineID());
            if (ClientCorpsePresentation.postpone(living, square, incoming)) return true;
        }
        receivedSnapshot();
        return false;
    }
    static void receivedSnapshot() {
        if (++received == 1 || received % 100 == 0) log("CLIENT full corpse snapshots=" + received);
    }
    public static void log(String message) { System.out.println("[B42MPZombieCorpseFix] " + message); }

    public static final String MANIFEST = MARKER + ".inventory";
    private static final Map<zombie.core.raknet.UdpConnection, LinkedHashSet<IsoDeadBody>> WAITING = new WeakHashMap<>();

    public static void connected(zombie.core.raknet.UdpConnection connection) {
        if (!server()) return;
        LinkedHashSet<IsoDeadBody> waiting = WAITING.remove(connection);
        if (waiting == null) return;
        for (IsoDeadBody body : waiting) {
            if (body.getSquare() == null || !body.getSquare().getStaticMovingObjects().contains(body)
                    || !connection.isRelevantTo(body.getX(), body.getY())) continue;
            sendBody(body, connection);
        }
    }
    private static void sendBody(IsoDeadBody body, zombie.core.raknet.UdpConnection connection) {
        AddCorpseToMapPacket packet = new AddCorpseToMapPacket();
        packet.set(body.getSquare(), body);
        body.getModData().rawset(ClientCorpsePresentation.SQUARE_INDEX, (double)body.getStaticMovingObjectIndex());
        try { packet.sendToClient(PacketTypes.PacketType.AddCorpseToMap, connection); }
        finally { body.getModData().rawset(ClientCorpsePresentation.SQUARE_INDEX, null); }
    }
    public static void beforeSave(IsoDeadBody body) {
        if (managed(body)) body.getModData().rawset(MANIFEST, inventoryManifest(body));
    }
    public static void afterLoad(IsoDeadBody body) {
        if (!managed(body)) return;
        if (!inventoryManifest(body).equals(body.getModData().rawget(MANIFEST))) {
            throw new IllegalStateException("B42MPZombieCorpseFix rejected incomplete corpse inventory "
                    + body.getObjectID().getObjectID());
        }
    }
    static String inventoryManifest(IsoDeadBody body) {
        java.util.TreeMap<String, String> rows = new java.util.TreeMap<>();
        inventoryRows(body.getContainer(), "root", rows, new java.util.HashSet<>());
        for (int i = 0; i < body.getWornItems().size(); i++) {
            var worn = body.getWornItems().get(i);
            if (!body.getContainer().getItems().contains(worn.getItem())) throw new IllegalStateException("Uncontained worn item");
            rows.put("worn/" + i, worn.getLocation() + "=" + worn.getItem().getID());
        }
        for (int i = 0; i < body.getAttachedItems().size(); i++) {
            var attached = body.getAttachedItems().get(i);
            if (!body.getContainer().getItems().contains(attached.getItem())) throw new IllegalStateException("Uncontained attached item");
            rows.put("attached/" + i, attached.getLocation() + "=" + attached.getItem().getID());
        }
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(rows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void inventoryRows(ItemContainer container, String path, Map<String,String> rows, java.util.Set<Integer> ids) {
        if (container == null) throw new IllegalStateException("Missing corpse container");
        rows.put(path, Integer.toString(container.getItems().size()));
        for (InventoryItem item : container.getItems()) {
            if (!ids.add(item.getID())) throw new IllegalStateException("Duplicate/cyclic corpse item " + item.getID());
            String key = path + "/" + item.getID();
            rows.put(key, item.getFullType());
            if (item instanceof zombie.inventory.types.InventoryContainer bag) inventoryRows(bag.getInventory(), key + "/bag", rows, ids);
        }
    }
    public static boolean retryLoot(zombie.network.packets.RequestItemsForContainerPacket packet) {
        if (!server()) return false;
        var id = (zombie.network.fields.ContainerID) read(REQUEST_CONTAINER, packet);
        ItemContainer container = id.getContainer();
        if (container == null || !(container.getParent() instanceof IsoDeadBody body) || !managed(body)) return false;
        if (container.isExplored()) return true;
        try {
            rollAdditionalLoot(body);
            zombie.network.packets.INetworkPacket.sendToRelative(PacketTypes.PacketType.AddInventoryItemToContainer,
                    body.getX(), body.getY(), container, container.getItems());
        } catch (RuntimeException e) { log("LOOT PENDING corpse=" + body.getObjectID().getObjectID() + ": " + e); }
        return true;
    }
    private static final Field REQUEST_CONTAINER = field(zombie.network.packets.RequestItemsForContainerPacket.class, "containerId");

    public static void beforeDestroyed(InventoryItem item) {
        if (!server() || item == null || item.getContainer() == null
                || !(item.getContainer().getParent() instanceof IsoZombie z) || !EQUIPMENT.containsKey(z)) return;
        // OnBreak replaces an item via ItemUser.RemoveItem, which does not clear
        // zombie attachment slots. Do not reintroduce that consumed weapon at death.
        z.getAttachedItems().remove(item);
    }

    public static void beforeAttachment(IsoGameCharacter character, String location, InventoryItem incoming) {
        if (!eligible(character) || incoming == null) return;
        IsoZombie z = (IsoZombie) character;
        ItemContainer inventory = z.getInventory();
        if (incoming.getContainer() != null && incoming.getContainer() != inventory) {
            throw new IllegalStateException("Embedded item still belongs to another container: " + incoming.getID());
        }
        InventoryItem previous = z.getAttachedItems().getItem(location);
        if (previous != null) addExact(inventory, previous);
        addExact(inventory, incoming);
        EQUIPMENT.computeIfAbsent(z, ignored -> new Equipment());
    }
}
