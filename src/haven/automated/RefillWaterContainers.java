package haven.automated;

import haven.*;
import haven.resutil.WaterTile;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static haven.OCache.posres;

public class RefillWaterContainers implements Runnable {
    private static final Coord2d posres = Coord2d.of(0x1.0p-10, 0x1.0p-10).mul(11, 11);
    private final GameUI gui;

    public RefillWaterContainers(GameUI gui) {
        this.gui = gui;
    }

    private Gob findGobWithName(String name) {
        Gob result = null;
        synchronized (gui.map.glob.oc) {
            for (Gob gob : gui.map.glob.oc) {
                Resource res = gob.getres();
                if (res != null && res.name.contains(name)) {
                    if (result == null)
                        result = gob;
                    else if (gob.rc.dist(gui.map.player().rc) < result.rc.dist(gui.map.player().rc))
                        result = gob;
                }
            }
        }
        if (result == null) {
            gui.error("No gob found: " + name);
        }
        return result;
    }

    @Override
    public void run() {
        try {
            do {
                MCache mcache = gui.ui.sess.glob.map;
                int t = mcache.gettile(gui.map.player().rc.floor(MCache.tilesz));
                Tiler tl = mcache.tiler(t);
                if (tl instanceof WaterTile) {
                    Resource res = mcache.tilesetr(t);
                    if (res != null) {
                        if (res.name.equals("gfx/tiles/water") || res.name.equals("gfx/tiles/deep")) {
                            if (tryRefillAllContainersWithWater(gui.map.player().rc.floor(posres), (pos) -> {
                                gui.map.wdgmsg("itemact", Coord.z, pos, 0);
                            })) {
                                return;
                            }
                        } else if (res.name.equals("gfx/tiles/owater") || res.name.equals("gfx/tiles/odeep") || res.name.equals("gfx/tiles/odeeper")) {
                            gui.ui.error("Refill Water Script: This is salt water, you can't drink this!");
                            return;
                        }
                    } else {
                        gui.ui.error("Refill Water Script: Error checking tile, try again!");
                        return;
                    }
                } else {
                    Gob cistern = findGobWithName("cistern");
                    if (cistern == null) {
                        gui.ui.error("Refill Water Script: You must be on a water tile, in order to refill your containers!");
                        return;
                    } else {
                        if (tryRefillAllContainersWithWater(cistern.rc.floor(posres), (pos) -> {
                            gui.map.wdgmsg("itemact", Coord.z, pos, 0, 0, (int) cistern.id, pos, 0, -1);
                        })) {
                            return;
                        }
                    }
                }
            } while (!getInventoryContainers().isEmpty() || !getBeltContainers().isEmpty() || !getEquiporyPouchContainers().isEmpty());
            gui.ui.msg("Water Refilled!");
        } catch (Exception e) {
            gui.ui.error("Refill Water Containers Script: An Unknown Error has occured.");
        }
    }

    private boolean tryRefillAllContainersWithWater(Coord pos, Consumer<Coord> action) {
        Inventory belt = returnBelt();
        Equipory equipory = gui.getequipory();
        Map<WItem, Coord> inventoryItems = getInventoryContainers();
        for (Map.Entry<WItem, Coord> item : inventoryItems.entrySet()) {
            try {
                item.getKey().item.wdgmsg("take", Coord.z);
                Thread.sleep(5);
                action.accept(pos);
                Thread.sleep(30);
                gui.maininv.wdgmsg("drop", item.getValue());
                Thread.sleep(5);
            } catch (InterruptedException ignored) {
                return true;
            }
        }
        Map<WItem, Coord> beltItems = getBeltContainers();
        for (Map.Entry<WItem, Coord> item : beltItems.entrySet()) {
            try {
                item.getKey().item.wdgmsg("take", Coord.z);
                Thread.sleep(5);
                action.accept(pos);
                Thread.sleep(40);
                belt.wdgmsg("drop", item.getValue());
                Thread.sleep(5);
            } catch (InterruptedException ignored) {
                return true;
            }
        }
        Map<WItem, Integer> equiporyPouchItems = getEquiporyPouchContainers();
        for (Map.Entry<WItem, Integer> item : equiporyPouchItems.entrySet()) {
            try {
                item.getKey().item.wdgmsg("take", Coord.z);
                Thread.sleep(5);
                action.accept(pos);
                Thread.sleep(40);
                equipory.wdgmsg("drop", item.getValue());
                Thread.sleep(5);
            } catch (InterruptedException ignored) {
                return true;
            }
        }
        return false;
    }


    public Map<WItem, Coord> getBeltContainers() {
        Map<WItem, Coord> containers = new HashMap<>();
        Coord sqsz = Inventory.sqsz;
        for (Widget w = gui.lchild; w != null; w = w.prev) {
            if (!(w instanceof GItem.ContentsWindow) || !((GItem.ContentsWindow) w).myOwnEquipory) continue;
            for (Widget ww : w.children()) {
                if (!(ww instanceof Inventory)) continue;
                Coord inventorySize = ((Inventory) ww).isz;
                for (int i = 0; i < inventorySize.x; i++) {
                    for (int j = 0; j < inventorySize.y; j++) {
                        Coord indexCoord = new Coord(i, j);
                        Coord calculatedCoord = indexCoord.mul(sqsz).add(1, 1);
                        for (Map.Entry<GItem, WItem> entry : ((Inventory) ww).wmap.entrySet()) {
                            if (entry.getValue().c.equals(calculatedCoord)) {
                                String resName = entry.getKey().res.get().name;
                                ItemInfo.Contents.Content content = getContent(entry.getKey());
                                if (resName.equals("gfx/invobjs/small/waterskin") && shouldAddToContainers(content, 3.0F)) {
                                    containers.put(entry.getValue(), indexCoord);
                                } else if (resName.equals("gfx/invobjs/waterflask") && shouldAddToContainers(content, 2.0F)) {
                                    containers.put(entry.getValue(), indexCoord);
                                } else if (resName.equals("gfx/invobjs/small/glassjug") && shouldAddToContainers(content, 5.0F)) {
                                    containers.put(entry.getValue(), indexCoord);
                                }
                            }
                        }
                    }
                }
            }
        }
        return containers;
    }

    public Inventory returnBelt() {
        Inventory belt = null;
        for (Widget w = gui.lchild; w != null; w = w.prev) {
            if (!(w instanceof GItem.ContentsWindow) || !((GItem.ContentsWindow) w).myOwnEquipory) continue;
            if (!((GItem.ContentsWindow) w).cap.contains("Belt")) continue;
            for (Widget ww : w.children()) {
                if (!(ww instanceof Inventory)) continue;
                belt = (Inventory) ww;
            }
        }
        return belt;
    }

    public Map<WItem, Coord> getInventoryContainers() {
        Inventory playerInventory = gui.maininv;
        Coord inventorySize = playerInventory.isz;
        Coord sqsz = Inventory.sqsz;
        Map<WItem, Coord> containers = new HashMap<>();
        for (int i = 0; i < inventorySize.x; i++) {
            for (int j = 0; j < inventorySize.y; j++) {
                Coord indexCoord = new Coord(i, j);
                Coord calculatedCoord = indexCoord.mul(sqsz).add(1, 1);

                for (Map.Entry<GItem, WItem> entry : playerInventory.wmap.entrySet()) {
                    if (entry.getValue().c.equals(calculatedCoord)) {
                        String resName = entry.getKey().res.get().name;
                        ItemInfo.Contents.Content content = getContent(entry.getKey());
                        if (resName.equals("gfx/invobjs/waterskin") && shouldAddToContainers(content, 3.0F)) {
                            containers.put(entry.getValue(), indexCoord);
                        } else if (resName.equals("gfx/invobjs/waterflask") && shouldAddToContainers(content, 2.0F)) {
                            containers.put(entry.getValue(), indexCoord);
                        } else if (resName.equals("gfx/invobjs/glassjug") && shouldAddToContainers(content, 5.0F)) {
                            containers.put(entry.getValue(), indexCoord);
                        }
                    }
                }
            }
        }
        return containers;
    }

    public Map<WItem, Integer> getEquiporyPouchContainers() {
        WItem leftPouch = gui.getequipory().slots[19];
        WItem rightPouch = gui.getequipory().slots[20];
        Map<WItem, Integer> containers = new HashMap<>();
        if (leftPouch != null) {
            String resName = leftPouch.item.res.get().name;
            ItemInfo.Contents.Content content = getContent(leftPouch.item);
            if ((resName.equals("gfx/invobjs/small/waterskin") && shouldAddToContainers(content, 3.0F))
                    || (resName.equals("gfx/invobjs/waterflask") && shouldAddToContainers(content, 2.0F))
                    || (resName.equals("gfx/invobjs/small/glassjug") && shouldAddToContainers(content, 5.0F))) {
                containers.put(leftPouch, 19);
            }
        }
        if (rightPouch != null) {
            String resName = rightPouch.item.res.get().name;
            ItemInfo.Contents.Content content = getContent(rightPouch.item);
            if ((resName.equals("gfx/invobjs/small/waterskin") && shouldAddToContainers(content, 3.0F))
                    || (resName.equals("gfx/invobjs/waterflask") && shouldAddToContainers(content, 2.0F))
                    || (resName.equals("gfx/invobjs/small/glassjug") && shouldAddToContainers(content, 5.0F))) {
                containers.put(rightPouch, 20);
            }
        }
        return containers;
    }

    private ItemInfo.Contents.Content getContent(GItem item) {
        ItemInfo.Contents.Content content = null;
        for (ItemInfo info : item.info()) {
            if (info instanceof ItemInfo.Contents) {
                content = ((ItemInfo.Contents) info).content;
            }
        }
        return content;
    }

    private boolean shouldAddToContainers(ItemInfo.Contents.Content content, float contentCount) {
        return content == null || (content.count != contentCount && Objects.equals(content.name, "Water"));
    }
}
