package mc.coconut;

import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** Falling coconuts: "Look up" rain and the loot event. */
public class CoconutRain {

    final CoconutPlugin pl;
    final NamespacedKey KEY;
    final NamespacedKey LOOT;
    private final Random rnd = new Random();
    private BukkitTask active;

    CoconutRain(CoconutPlugin pl) {
        this.pl = pl;
        this.KEY = new NamespacedKey(pl, "coconut");
        this.LOOT = new NamespacedKey(pl, "coconut_loot");
    }

    boolean isRunning() {
        return active != null && !active.isCancelled();
    }

    void stop() {
        if (active != null) active.cancel();
        active = null;
    }

    void lookUp() {
        pl.titleAll("lookup-title", "lookup-sub");
        pl.soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f);
        begin(pl.getConfig().getInt("lookup.seconds", 10), 0.0, null);
    }

    void startEvent() {
        pl.broadcast("event-start");
        pl.titleAll("event-title", "event-sub");
        pl.soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f);
        double chance = pl.getConfig().getDouble("event.loot-chance", 0.35);
        begin(pl.getConfig().getInt("event.seconds", 30), chance, () -> {
            pl.broadcast("event-end");
            pl.soundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.4f);
        });
    }

    private void begin(int seconds, double lootChance, Runnable onEnd) {
        stop();
        FileConfiguration cfg = pl.getConfig();
        final int interval = Math.max(1, cfg.getInt("rain.interval-ticks", 8));
        final int perWave = Math.max(1, cfg.getInt("rain.per-player-per-wave", 1));
        final int totalTicks = seconds * 20;
        active = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= totalTicks) {
                    cancel();
                    active = null;
                    if (onEnd != null) onEnd.run();
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR) continue;
                    for (int i = 0; i < perWave; i++) spawnNear(p.getLocation(), lootChance);
                }
                t += interval;
            }
        }.runTaskTimer(pl, 0L, interval);
    }

    /** Spawns one falling coconut somewhere around the given location. */
    void spawnNear(Location center, double lootChance) {
        World w = center.getWorld();
        if (w == null || w.getEnvironment() != World.Environment.NORMAL) return;
        FileConfiguration cfg = pl.getConfig();
        double r = cfg.getDouble("rain.radius", 12);
        double x = center.getX() + (rnd.nextDouble() * 2 - 1) * r;
        double z = center.getZ() + (rnd.nextDouble() * 2 - 1) * r;
        int top = w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
        if (top > center.getY() + 20) return; // player is underground, skip
        double y = Math.min(top + 1 + cfg.getDouble("rain.height", 18), w.getMaxHeight() - 2);
        Location loc = new Location(w, x, y, z);
        FallingBlock fb = w.spawnFallingBlock(loc, coconutData());
        fb.setDropItem(false);
        fb.setHurtEntities(false);
        fb.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        if (lootChance > 0 && rnd.nextDouble() < lootChance) {
            fb.getPersistentDataContainer().set(LOOT, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private BlockData coconutData() {
        Material m = Material.matchMaterial(pl.getConfig().getString("rain.block", "BROWN_MUSHROOM_BLOCK"));
        if (m == null || !m.isBlock()) m = Material.BROWN_MUSHROOM_BLOCK;
        return Bukkit.createBlockData(m);
    }

    void onLand(Location where, boolean loot) {
        World w = where.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.CLOUD, where, 8, 0.25, 0.2, 0.25, 0.02);
        w.playSound(where, Sound.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 0.8f, 0.9f + rnd.nextFloat() * 0.3f);
        if (loot) {
            ItemStack item = rollLoot();
            if (item != null) {
                w.spawnParticle(Particle.CRIT, where, 10, 0.3, 0.3, 0.3, 0.1);
                w.dropItemNaturally(where.clone().add(0, 0.4, 0), item);
            }
        }
    }

    private ItemStack rollLoot() {
        List<Map<?, ?>> table = pl.getConfig().getMapList("event.loot");
        int total = 0;
        for (Map<?, ?> e : table) total += Math.max(0, num(e.get("weight"), 1));
        if (total <= 0) return null;
        int roll = rnd.nextInt(total);
        for (Map<?, ?> e : table) {
            roll -= Math.max(0, num(e.get("weight"), 1));
            if (roll < 0) return build(e);
        }
        return null;
    }

    private ItemStack build(Map<?, ?> e) {
        Material m = Material.matchMaterial(String.valueOf(e.get("item")));
        if (m == null) return null;
        int min = Math.max(1, num(e.get("min"), 1));
        int max = Math.max(min, num(e.get("max"), min));
        ItemStack it = new ItemStack(m, min + rnd.nextInt(max - min + 1));
        Object name = e.get("name");
        if (name != null) {
            ItemMeta meta = it.getItemMeta();
            meta.displayName(CoconutPlugin.MM.deserialize(String.valueOf(name)).decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(meta);
        }
        return it;
    }

    private static int num(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }
}
