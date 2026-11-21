package mc.coconut;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

/** Everyone spins, hops and hears a funky note-block groove. */
public class DanceManager {

    // Note-block "semitones" 0..24 (-1 = rest). 16 steps per loop.
    private static final int[] BASS = {0, -1, 0, -1, 3, -1, 0, -1, 5, -1, 3, -1, 0, -1, 7, 5};
    private static final int[] LEAD = {12, -1, 15, -1, 17, 15, -1, 12, -1, 10, 12, -1, 15, -1, 17, 19};

    private final CoconutPlugin pl;
    private final Random rnd = new Random();
    private BukkitTask active;

    DanceManager(CoconutPlugin pl) {
        this.pl = pl;
    }

    boolean isRunning() {
        return active != null && !active.isCancelled();
    }

    void stop() {
        if (active != null) active.cancel();
        active = null;
    }

    @SuppressWarnings("deprecation")
    void start() {
        stop();
        pl.titleAll("dance-title", "dance-sub");
        final int totalTicks = Math.max(1, pl.getConfig().getInt("dance.seconds", 5)) * 20;
        active = new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= totalTicks) {
                    cancel();
                    active = null;
                    pl.titleAll("dance-end-title", "dance-end-sub");
                    return;
                }
                if (t % 3 == 0) playStep((t / 3) % 16);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Location l = p.getLocation();
                    p.setRotation(l.getYaw() + 24f, (float) (Math.sin(t * 0.5) * 20));
                    if (t % 10 == 0 && p.isOnGround() && !p.isFlying() && !p.isGliding() && p.getVehicle() == null) {
                        p.setVelocity(p.getVelocity().setY(0.38));
                    }
                    if (t % 4 == 0) {
                        p.getWorld().spawnParticle(Particle.NOTE, l.clone().add(0, 2.2, 0), 0, rnd.nextDouble(), 0, 0, 1.0);
                    }
                }
                t++;
            }
        }.runTaskTimer(pl, 0L, 1L);
    }

    private void playStep(int step) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            if (BASS[step] >= 0) play(p, l, Sound.BLOCK_NOTE_BLOCK_BASS, pitch(BASS[step]), 1.0f);
            if (LEAD[step] >= 0) play(p, l, Sound.BLOCK_NOTE_BLOCK_BANJO, pitch(LEAD[step]), 0.7f);
            play(p, l, Sound.BLOCK_NOTE_BLOCK_HAT, 1.5f, step % 2 == 0 ? 0.6f : 0.3f);
            if (step == 0 || step == 8 || step == 10) play(p, l, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 1.0f);
            if (step == 4 || step == 12) play(p, l, Sound.BLOCK_NOTE_BLOCK_SNARE, 1.0f, 0.9f);
        }
    }

    private void play(Player p, Location l, Sound s, float pitch, float vol) {
        p.playSound(l, s, SoundCategory.RECORDS, vol, pitch);
    }

    private float pitch(int semitone) {
        return (float) Math.pow(2.0, (semitone - 12) / 12.0);
    }
}
