package mc.coconut;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CoconutPlugin extends JavaPlugin implements Listener, TabExecutor {

    static final MiniMessage MM = MiniMessage.miniMessage();

    CoconutRain rain;
    DanceManager dance;
    SurpriseManager surprise;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        rain = new CoconutRain(this);
        dance = new DanceManager(this);
        surprise = new SurpriseManager(this, rain);
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("coconut");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        if (rain != null) rain.stop();
        if (dance != null) dance.stop();
    }

    // ---------- message helpers ----------

    Component raw(String key, String... pairs) {
        String s = getConfig().getString("messages." + key, "<red>Missing message: " + key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = s.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return MM.deserialize(s);
    }

    Component msg(String key, String... pairs) {
        return MM.deserialize(getConfig().getString("prefix", "")).append(raw(key, pairs));
    }

    void broadcast(String key, String... pairs) {
        Bukkit.broadcast(msg(key, pairs));
    }

    void titleAll(String titleKey, String subKey, String... pairs) {
        Title t = Title.title(raw(titleKey, pairs), raw(subKey, pairs),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600)));
        for (Player p : Bukkit.getOnlinePlayers()) p.showTitle(t);
    }

    void soundAll(Sound sound, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, SoundCategory.MASTER, 1f, pitch);
        }
    }

    void console(String command, String... pairs) {
        String c = command.startsWith("/") ? command.substring(1) : command;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            c = c.replace(pairs[i], pairs[i + 1]);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
    }

    // ---------- landing handler ----------

    @EventHandler
    public void onLand(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock fb)) return;
        PersistentDataContainer pdc = fb.getPersistentDataContainer();
        if (!pdc.has(rain.KEY, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        Location where = fb.getLocation();
        boolean loot = pdc.has(rain.LOOT, PersistentDataType.BYTE);
        fb.remove();
        rain.onLand(where, loot);
    }

    // ---------- command ----------

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("coconut.admin")) {
            s.sendMessage(msg("no-permission"));
            return true;
        }
        if (a.length == 0 || a[0].equalsIgnoreCase("lookup")) {
            if (rain.isRunning()) { s.sendMessage(msg("already-running")); return true; }
            rain.lookUp();
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "event" -> {
                if (rain.isRunning()) { s.sendMessage(msg("already-running")); return true; }
                rain.startEvent();
            }
            case "dance" -> {
                if (dance.isRunning()) { s.sendMessage(msg("already-running")); return true; }
                dance.start();
            }
            case "sendall" -> sendAll(s, a);
            case "keyall" -> keyAll(s, a);
            case "surprise" -> surprise.run(s);
            case "reload" -> {
                reloadConfig();
                s.sendMessage(msg("reloaded"));
            }
            default -> s.sendMessage(msg("usage"));
        }
        return true;
    }

    private void sendAll(CommandSender s, String[] a) {
        if (a.length < 2 || !a[1].equalsIgnoreCase("spawn")) {
            s.sendMessage(msg("usage"));
            return;
        }
        String spawnCmd = getConfig().getString("spawn-command", "");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (spawnCmd != null && !spawnCmd.isBlank()) {
                console(spawnCmd, "%player%", p.getName());
            } else {
                World w = Bukkit.getWorlds().get(0);
                p.teleport(w.getSpawnLocation());
            }
        }
        titleAll("sendall-title", "sendall-sub");
        broadcast("sendall");
    }

    private void keyAll(CommandSender s, String[] a) {
        List<String> known = getConfig().getStringList("crates");
        int i = 1;
        if (i < a.length && a[i].equalsIgnoreCase("giveall")) i++;
        Map<String, Integer> keys = new LinkedHashMap<>();
        while (i < a.length) {
            if (i + 1 >= a.length) {
                s.sendMessage(msg("keyall-usage"));
                return;
            }
            String typed = a[i];
            String crate = null;
            for (String k : known) {
                if (k.equalsIgnoreCase(typed)) { crate = k; break; }
            }
            if (crate == null) {
                s.sendMessage(msg("unknown-crate", "crate", typed, "list", String.join(", ", known)));
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(a[i + 1]);
            } catch (NumberFormatException ex) {
                amount = -1;
            }
            if (amount <= 0) {
                s.sendMessage(msg("bad-amount", "amount", a[i + 1]));
                return;
            }
            keys.merge(crate, amount, Integer::sum);
            i += 2;
        }
        if (keys.isEmpty()) {
            s.sendMessage(msg("keyall-usage"));
            return;
        }
        String template = getConfig().getString("key-command", "");
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Map.Entry<String, Integer> en : keys.entrySet()) {
                console(template, "%player%", p.getName(), "%crate%", en.getKey(), "%amount%", String.valueOf(en.getValue()));
            }
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> en : keys.entrySet()) parts.add(en.getKey() + " x" + en.getValue());
        broadcast("keys-given", "keys", String.join(", ", parts));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("coconut.admin")) return List.of();
        if (a.length == 1) return filter(List.of("event", "dance", "sendall", "keyall", "surprise", "lookup", "reload"), a[0]);
        String sub = a[0].toLowerCase();
        if (sub.equals("sendall") && a.length == 2) return filter(List.of("spawn"), a[1]);
        if (sub.equals("keyall")) {
            List<String> crates = getConfig().getStringList("crates");
            int idx = a.length - 1;
            if (idx == 1) {
                List<String> opts = new ArrayList<>(crates);
                opts.add("giveall");
                return filter(opts, a[1]);
            }
            int start = a[1].equalsIgnoreCase("giveall") ? 2 : 1;
            if ((idx - start) % 2 == 0) return filter(crates, a[idx]);
            return filter(List.of("1", "2", "3", "4", "5"), a[idx]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String typed) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(typed.toLowerCase())) out.add(o);
        }
        return out;
    }
}
