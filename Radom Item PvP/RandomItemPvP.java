package org.main.randomItemPvP;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class RandomItemPvP extends JavaPlugin implements Listener {

    private final List<Player> activePlayers = new ArrayList<>();
    private final Random randomGenerator = new Random();
    private boolean isGameActive = false;
    private World currentGameWorld;
    private BossBar itemTimerBossBar;

    public int pillarRadius = 15;
    public int itemDropIntervalTicks = 100;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ENABLED!");
    }

    @Override
    public void onDisable() {
        endGame();
        getLogger().info("DISABLED!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String[] args) {
        final String PLAYER_ONLY = "플레이어만 명령어를 실행할 수 있습니다!";
        final String GAME_ALREADY_RUNNING = "이미 게임이 시작되었습니다!";
        final String GAME_NOT_RUNNING = "실행 중인 게임이 없습니다!";
        final String USAGE_RIP = "사용법: /rip <start|end|setPillarRadius|setItemInterval>";

        if (command.getName().equalsIgnoreCase("rip")) {
            if (!(sender instanceof Player initiator)) {
                sender.sendMessage(PLAYER_ONLY);
                return false;
            } else if (args.length == 0) {
                sender.sendMessage(USAGE_RIP);
                return false;
            }

            switch (args[0].toLowerCase()) {
                case "start":
                    if (isGameActive) {
                        sender.sendMessage(GAME_ALREADY_RUNNING);
                        return false;
                    }
                    initializeGame(initiator);
                    break;

                case "end":
                    if (!isGameActive) {
                        sender.sendMessage(GAME_NOT_RUNNING);
                        return false;
                    }
                    endGame();
                    break;

                case "setpillarradius":
                    if (args.length < 2) {
                        sender.sendMessage("사용법: /rip setPillarRadius <value>");
                        return false;
                    }
                    try {
                        pillarRadius = parseInteger(args[1], sender, "올바른 정숫값을 적어주세요!");
                        sender.sendMessage("TNT 기둥의 중심과의 거리를 " + pillarRadius + " 블록으로 설정했습니다.");
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    break;

                case "setiteminterval":
                    if (args.length < 2) {
                        sender.sendMessage("사용법: /rip setItemInterval <value>");
                        return false;
                    }
                    try {
                        itemDropIntervalTicks = parseInteger(args[1], sender, "올바른 틱 정수값을 적어주세요!");
                        sender.sendMessage("아이템 지급 간격을 " + itemDropIntervalTicks + " 틱으로 설정했습니다.");
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    break;

                default:
                    sender.sendMessage("알 수 없는 명령어입니다. " + USAGE_RIP);
                    return false;
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("rip")) {
            if (args.length == 1) {
                return Arrays.asList("start", "end", "setPillarRadius", "setItemInterval");
            }

            if (args.length == 2) {
                return switch (args[0].toLowerCase()) {
                    case "setpillarradius", "setiteminterval" ->
                            Arrays.asList("10", "20", "50", "100");
                    default -> Collections.emptyList();
                };
            }
        }
        return Collections.emptyList();
    }

    private int parseInteger(String input, CommandSender sender, String errorMessage) throws NumberFormatException {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            sender.sendMessage(errorMessage);
            throw e;
        }
    }

    private void initializeGame(Player initiator) {
        activePlayers.clear();
        activePlayers.addAll(Bukkit.getOnlinePlayers());

        if (activePlayers.size() < 2) {
            initiator.sendMessage("§c게임을 시작할 플레이어가 부족합니다!");
            endGame();
        } else {
            isGameActive = true;
            final int[] countdownTime = {5};
            Bukkit.broadcastMessage(countdownTime[0] + "초 후 게임 시작...");

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (countdownTime[0] > 0) {
                        Bukkit.broadcastMessage(countdownTime[0] + "초 남음...");
                        countdownTime[0]--;
                        activePlayers.forEach(player -> player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 2.0f));
                    } else {
                        activePlayers.forEach(player -> player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f));
                        activePlayers.forEach(player -> player.setGameMode(GameMode.CREATIVE));
                        currentGameWorld = initiator.getWorld();
                        startGame();
                        cancel();
                    }
                }
            }.runTaskTimer(this, 0L, 20L);
        }
    }

    private void startGame() {
        itemTimerBossBar = Bukkit.createBossBar("랜덤 아이템 타이머", BarColor.WHITE, BarStyle.SEGMENTED_10);
        Location spawnPoint = new Location(currentGameWorld, 0, 51, 0);

        for (Player player : activePlayers) {
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            player.setRespawnLocation(spawnPoint, true);
            itemTimerBossBar.addPlayer(player);
            itemTimerBossBar.setProgress(0.0);
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancement revoke @a everything");
        Bukkit.broadcastMessage("랜덤 아이템 PvP가 시작되었습니다! 최후의 1인이 되어 살아남으세요!");

        clearGameArea();
        createTntPillars();
        giveItem();
    }

    private void clearGameArea() {
        Location center = new Location(currentGameWorld, 0, 0, 0);
        int radius = 50;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double sqrt = Math.sqrt(x * x + z * z);
                if (sqrt <= radius) {
                    for (int y = -64; y <= 320; y++) {
                        Location loc = center.clone().add(x, y, z);
                        if (sqrt >= radius - 1) {
                            loc.getBlock().setType(Material.BARRIER);
                        } else {
                            loc.getBlock().setType(Material.AIR);
                        }
                    }
                }
            }
        }
    }

    private void createTntPillars() {
        Location center = new Location(currentGameWorld, 0, 0, 0);

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                center.clone().add(x, 50, z).getBlock().setType(Material.GLASS);
            }
        }

        for (int i = 0; i < activePlayers.size(); i++) {
            double angle = 2 * Math.PI * i / activePlayers.size();
            double x = center.getX() + pillarRadius * Math.cos(angle);
            double z = center.getZ() + pillarRadius * Math.sin(angle);
            Location location = new Location(currentGameWorld, x, center.getY(), z);
            Location teleportLocation = new Location(currentGameWorld, x, 3, z);
            double dx = center.getX() - teleportLocation.getX();
            double dz = center.getZ() - teleportLocation.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
            teleportLocation.setYaw(yaw);
            activePlayers.get(i).teleport(teleportLocation);

            for (int y = -64; y <= 0; y++) {
                location.clone().add(0, y, 0).getBlock().setType(Material.TNT);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (isGameActive) {
            Player deadPlayer = event.getEntity();
            activePlayers.remove(deadPlayer);
            Bukkit.broadcastMessage("§c" + deadPlayer.getName() + "§c 님이 죽었습니다!");

            Location spawnPoint = new Location(currentGameWorld, 0, 51, 0);
            deadPlayer.setRespawnLocation(spawnPoint, true);
            deadPlayer.setGameMode(GameMode.CREATIVE);
            Location teleportLocation = new Location(currentGameWorld, 0, 51, 0);
            deadPlayer.teleport(teleportLocation);

            if (activePlayers.size() == 1) {
                endGame();
            }
        }
    }

    private void giveItem() {
        new BukkitRunnable() {
            int timeRemaining = 1;

            @Override
            public void run() {
                if (!isGameActive) {
                    cancel();
                    return;
                }

                timeRemaining++;
                if (timeRemaining >= itemDropIntervalTicks) {
                    for (Player player : activePlayers) {
                        ItemStack randomItem = generateRandomItem();
                        player.getInventory().addItem(randomItem);
                        player.sendMessage(randomItem.getType() + " 을(를) 받았습니다!");
                    }
                    timeRemaining = 1;
                }
                itemTimerBossBar.setProgress((double) timeRemaining / itemDropIntervalTicks);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private ItemStack generateRandomItem() {
        Material[] materials = Material.values();
        Material randomMaterial = materials[randomGenerator.nextInt(materials.length)];
        return new ItemStack(randomMaterial);
    }

    private void endGame() {
        if (currentGameWorld != null && isGameActive) {
            activePlayers.forEach(player -> player.setGameMode(GameMode.CREATIVE));

            if (activePlayers.size() == 1) {
                Player winner = activePlayers.getFirst();
                Bukkit.broadcastMessage("§6✪ " + winner.getName() + " §6님이 승리했습니다! ✪");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    player.sendTitle("§6✪ " + winner.getName() + " ✪", "§e님이 게임에서 승리했습니다!", 10, 70, 20);
                }
            } else {
                Bukkit.broadcastMessage("§c승리자 없이 게임이 끝났습니다.");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 0.5f);
                    player.sendTitle("§c게임 종료", "§7승리자가 없습니다.", 10, 70, 20);
                }
            }

            if (itemTimerBossBar != null) {
                itemTimerBossBar.removeAll();
            }

            activePlayers.clear();
        }
        isGameActive = false;
    }
}
