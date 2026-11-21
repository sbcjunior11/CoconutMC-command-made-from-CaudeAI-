package mc.coconut;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The surprise: THE GOLDEN COCONUT. A firework show above everyone, a lightning flash on one
 * random lucky player, a reward for them, and a mini loot shower around them.
 */
public class SurpriseManager {

    private final CoconutPlugin pl;
    private final CoconutRain rain;
    private final Random rnd = new Random();

    SurpriseManager(CoconutPlugin pl, CoconutRain rain) {
        this.pl = pl;
        this.rain = rain;
    }

    void run(CommandSender sender) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            sender.sendMessage(pl.msg("surprise-none"));
            return;
        }
        Player winner = players.get(rnd.nextInt(players.size()));
        String name = winner.getName();

        pl.titleAll("surprise-title", "surprise-sub", "player", name);
        pl.broadcast("surprise-chat", "player", name);
        pl.soundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);

        // Fireworks well above everyone's heads (explosions only hurt within ~5 blocks).
        for (Player p : players) {
            firework(p.getLocation().add(rnd.nextInt(7) - 3, 10, rnd.nextInt(7) - 3));
        }

        // Harmless lightning flash on the winner + reward.
        Location wl = winner.getLocation();
        wl.getWorld().strikeLightningEffect(wl);
        for (String cmd : pl.getConfig().getStringList("surprise.reward-commands")) {
            pl.console(cmd, "%player%", name);
        }

        // Mini loot shower around the winner.
        new BukkitRunnable() {
            int n = 0;

            @Override
            public void run() {
                if (n++ >= 12 || !winner.isOnline()) {
                    cancel();
                    return;
                }
                rain.spawnNear(winner.getLocation(), 0.5);
            }
        }.runTaskTimer(pl, 10L, 5L);
    }

    private void firework(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        Firework fw = w.spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.ORANGE, Color.fromRGB(46, 120, 48), Color.YELLOW)
                .withFade(Color.WHITE)
                .flicker(true)
                .build());
        fw.setFireworkMeta(meta);
        fw.detonate();
    }
}
