package top.nkbe;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 管理 /tpa 系列請求、讀秒與冷卻。 */
public final class TpaManager {

    public enum Type { TPA, TPAHERE }

    private static final class Request {
        private final UUID requester;
        private final UUID target;
        private final Type type;
        private final long createdAt;
        private BukkitTask expiryTask;

        private Request(UUID requester, UUID target, Type type) {
            this.requester = requester;
            this.target = target;
            this.type = type;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private static final class Warmup {
        private final UUID traveler;
        private final UUID destination;
        private final UUID requester;
        private BukkitTask task;

        private Warmup(UUID traveler, UUID destination, UUID requester) {
            this.traveler = traveler;
            this.destination = destination;
            this.requester = requester;
        }
    }

    private final JavaPlugin plugin;
    private final Lang lang;
    private final Map<UUID, Map<UUID, Request>> incoming = new HashMap<>();
    private final Map<UUID, Warmup> warmups = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public TpaManager(JavaPlugin plugin, Lang lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    public void sendRequest(Player requester, Player target, Type type) {
        if (requester.equals(target)) {
            this.lang.send(requester, "tpa-self");
            return;
        }
        long remaining = cooldownRemaining(requester.getUniqueId());
        if (remaining > 0L && !requester.hasPermission("nyaeamc.tpa.exempt.cooldown")) {
            this.lang.send(requester, "tpa-cooldown", remaining);
            return;
        }

        Map<UUID, Request> requests = this.incoming.computeIfAbsent(target.getUniqueId(), ignored -> new HashMap<>());
        Request old = requests.get(requester.getUniqueId());
        boolean refreshed = old != null;
        if (old != null) removeRequest(old);

        Request request = new Request(requester.getUniqueId(), target.getUniqueId(), type);
        this.incoming.computeIfAbsent(target.getUniqueId(), ignored -> new HashMap<>()).put(requester.getUniqueId(), request);
        scheduleExpiry(request);

        this.lang.send(requester, refreshed ? "tpa-refreshed" : (type == Type.TPA ? "tpa-sent" : "tpahere-sent"), target.getName());
        this.lang.send(target, type == Type.TPA ? "tpa-received" : "tpahere-received", requester.getName());
    }

    public void accept(Player target, String requesterName) {
        Request request = findRequest(target, requesterName);
        if (request == null) {
            this.lang.send(target, "tpa-none-pending");
            return;
        }
        Player requester = Bukkit.getPlayer(request.requester);
        if (requester == null || !requester.isOnline()) {
            removeRequest(request);
            this.lang.send(target, "tpa-requester-offline");
            return;
        }

        removeRequest(request);
        Player traveler = request.type == Type.TPA ? requester : target;
        Player destination = request.type == Type.TPA ? target : requester;
        this.lang.send(requester, "tpa-accepted-by-target", target.getName());
        this.lang.send(target, "tpa-accepted-by-self", requester.getName());
        startWarmup(traveler, destination, requester.getUniqueId());
    }

    public void deny(Player target, String requesterName) {
        Request request = findRequest(target, requesterName);
        if (request == null) {
            this.lang.send(target, "tpa-none-pending");
            return;
        }
        removeRequest(request);
        Player requester = Bukkit.getPlayer(request.requester);
        this.lang.send(target, "tpa-denied-by-self", requester == null ? "" : requester.getName());
        if (requester != null && requester.isOnline()) this.lang.send(requester, "tpa-denied-by-target", target.getName());
    }

    public void cancel(Player player) {
        if (cancelWarmup(player.getUniqueId(), "tpa-teleport-cancelled-command")) return;

        List<Request> requests = new ArrayList<>();
        for (Map<UUID, Request> targetRequests : this.incoming.values()) {
            Request request = targetRequests.get(player.getUniqueId());
            if (request != null) requests.add(request);
        }
        if (requests.isEmpty()) {
            this.lang.send(player, "tpcancel-none");
            return;
        }
        for (Request request : requests) {
            removeRequest(request);
            Player target = Bukkit.getPlayer(request.target);
            if (target != null && target.isOnline()) this.lang.send(target, "tpa-cancelled-by-requester", player.getName());
        }
        this.lang.send(player, "tpcancel-success");
    }

    public boolean cancelWarmupBySneak(Player player) {
        return cancelWarmup(player.getUniqueId(), "tpa-teleport-cancelled-sneak");
    }

    public List<String> pendingRequesterNames(Player target) {
        Map<UUID, Request> requests = this.incoming.get(target.getUniqueId());
        if (requests == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (Request request : requests.values()) {
            Player requester = Bukkit.getPlayer(request.requester);
            if (requester != null && requester.isOnline()) names.add(requester.getName());
        }
        return names;
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        cancelWarmup(uuid, null);
        for (Warmup warmup : new ArrayList<>(this.warmups.values())) {
            if (warmup.destination.equals(uuid)) cancelWarmup(warmup.traveler, "tpa-teleport-cancelled-offline");
        }

        Map<UUID, Request> requestsToPlayer = this.incoming.remove(uuid);
        if (requestsToPlayer != null) {
            for (Request request : new ArrayList<>(requestsToPlayer.values())) {
                if (request.expiryTask != null) request.expiryTask.cancel();
                Player requester = Bukkit.getPlayer(request.requester);
                if (requester != null && requester.isOnline()) this.lang.send(requester, "tpa-target-offline", player.getName());
            }
        }
        for (Map<UUID, Request> requests : new ArrayList<>(this.incoming.values())) {
            Request request = requests.get(uuid);
            if (request != null) removeRequest(request);
        }
        this.cooldowns.remove(uuid);
    }

    /** 死亡只取消進行中的讀秒，保留尚未接受的傳送請求。 */
    public void handleDeath(Player player) {
        UUID uuid = player.getUniqueId();
        cancelWarmup(uuid, "tpa-teleport-cancelled-death");
        for (Warmup warmup : new ArrayList<>(this.warmups.values())) {
            if (warmup.destination.equals(uuid)) cancelWarmup(warmup.traveler, "tpa-teleport-cancelled-death");
        }
    }

    public void shutdown() {
        for (Map<UUID, Request> requests : this.incoming.values()) {
            for (Request request : requests.values()) if (request.expiryTask != null) request.expiryTask.cancel();
        }
        for (Warmup warmup : this.warmups.values()) if (warmup.task != null) warmup.task.cancel();
        this.incoming.clear();
        this.warmups.clear();
        this.cooldowns.clear();
    }

    private Request findRequest(Player target, String requesterName) {
        Map<UUID, Request> requests = this.incoming.get(target.getUniqueId());
        if (requests == null || requests.isEmpty()) return null;
        if (requesterName == null) return Collections.max(requests.values(), Comparator.comparingLong(request -> request.createdAt));
        Player requester = Bukkit.getPlayer(requesterName);
        return requester == null ? null : requests.get(requester.getUniqueId());
    }

    private void scheduleExpiry(Request request) {
        long delay = Math.max(0L, this.plugin.getConfig().getLong("tpa.request-timeout-seconds", 60L)) * 20L;
        request.expiryTask = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            Map<UUID, Request> requests = this.incoming.get(request.target);
            if (requests == null || requests.get(request.requester) != request) return;
            removeRequest(request);
            Player requester = Bukkit.getPlayer(request.requester);
            Player target = Bukkit.getPlayer(request.target);
            if (requester != null && requester.isOnline()) this.lang.send(requester, "tpa-expired-requester", target == null ? "" : target.getName());
            if (target != null && target.isOnline()) this.lang.send(target, "tpa-expired-target", requester == null ? "" : requester.getName());
        }, delay);
    }

    private void removeRequest(Request request) {
        Map<UUID, Request> requests = this.incoming.get(request.target);
        if (requests != null) {
            requests.remove(request.requester);
            if (requests.isEmpty()) this.incoming.remove(request.target);
        }
        if (request.expiryTask != null) request.expiryTask.cancel();
    }

    private void startWarmup(Player traveler, Player destination, UUID requester) {
        int seconds = Math.max(0, this.plugin.getConfig().getInt("tpa.warmup-seconds", 3));
        if (seconds == 0 || traveler.hasPermission("nyaeamc.tpa.exempt.warmup")) {
            teleport(traveler, destination, requester);
            return;
        }
        cancelWarmup(traveler.getUniqueId(), null);
        Warmup warmup = new Warmup(traveler.getUniqueId(), destination.getUniqueId(), requester);
        this.warmups.put(warmup.traveler, warmup);
        this.lang.send(traveler, "tpa-warmup-start", seconds, destination.getName());
        warmup.task = Bukkit.getScheduler().runTaskLater(this.plugin, () -> finishWarmup(warmup), seconds * 20L);
    }

    private void finishWarmup(Warmup warmup) {
        if (this.warmups.get(warmup.traveler) != warmup) return;
        this.warmups.remove(warmup.traveler);
        Player traveler = Bukkit.getPlayer(warmup.traveler);
        Player destination = Bukkit.getPlayer(warmup.destination);
        if (traveler == null || !traveler.isOnline()) return;
        if (destination == null || !destination.isOnline()) {
            this.lang.send(traveler, "tpa-teleport-cancelled-offline");
            return;
        }
        teleport(traveler, destination, warmup.requester);
    }

    private boolean cancelWarmup(UUID traveler, String messageKey) {
        Warmup warmup = this.warmups.remove(traveler);
        if (warmup == null) return false;
        if (warmup.task != null) warmup.task.cancel();
        Player player = Bukkit.getPlayer(traveler);
        if (messageKey != null && player != null && player.isOnline()) this.lang.send(player, messageKey);
        return true;
    }

    private void teleport(Player traveler, Player destination, UUID requester) {
        if (!traveler.isOnline() || !destination.isOnline() || destination.getWorld() == null) {
            if (traveler.isOnline()) this.lang.send(traveler, "tpa-teleport-cancelled-offline");
            return;
        }
        Location location = destination.getLocation().clone();
        if (!traveler.teleport(location)) {
            this.lang.send(traveler, "tpa-teleport-failed");
            return;
        }
        this.lang.send(traveler, "tpa-teleport-success", destination.getName());
        applyCooldown(requester);
    }

    private void applyCooldown(UUID requester) {
        int seconds = Math.max(0, this.plugin.getConfig().getInt("tpa.cooldown-seconds", 30));
        if (seconds > 0) this.cooldowns.put(requester, System.currentTimeMillis() + seconds * 1000L);
    }

    private long cooldownRemaining(UUID requester) {
        Long until = this.cooldowns.get(requester);
        if (until == null) return 0L;
        long remaining = (until - System.currentTimeMillis() + 999L) / 1000L;
        if (remaining <= 0L) this.cooldowns.remove(requester);
        return Math.max(0L, remaining);
    }
}
