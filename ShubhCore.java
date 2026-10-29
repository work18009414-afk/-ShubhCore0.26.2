package com.shubhcore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.stream.Collectors;

public final class ShubhCore extends JavaPlugin implements Listener, TabExecutor {
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private int animationFrame = 0;
    private org.bukkit.configuration.file.FileConfiguration economy;
    private java.io.File economyFile;
    private final String[] titleFrames = {"&6&lSHUBH HYPIXEL", "&e&lSHUBH &6&lHYPIXEL", "&b&lSHUBH &f&lHYPIXEL", "&6&lSHUBH &e&lHYPIXEL"};

    @Override public void onEnable() {
        saveDefaultConfig();
        economyFile = new java.io.File(getDataFolder(), "economy.yml");
        if (!economyFile.exists()) {
            try {
                getDataFolder().mkdirs();
                economyFile.createNewFile();
            } catch (java.io.IOException ex) { throw new IllegalStateException("Could not create economy.yml", ex); }
        }
        economy = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(economyFile);
        Objects.requireNonNull(getCommand("shc")).setExecutor(this);
        Objects.requireNonNull(getCommand("shc")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("shcb")).setExecutor(this);
        Objects.requireNonNull(getCommand("shcb")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) setupPlayer(p);
        long period = Math.max(10, getConfig().getLong("settings.scoreboard-update-ticks", 20));
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            animationFrame++;
            for (Player p : Bukkit.getOnlinePlayers()) updateBoard(p);
            updateAllTeams();
        }, 1L, period);
        getLogger().info("ShubhCore0.26.2 enabled.");
    }

    @Override public void onDisable() { saveEconomy(); boards.clear(); }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!getConfig().contains("players." + p.getUniqueId())) {
            getConfig().set("players." + p.getUniqueId(), getConfig().getString("settings.default-rank", "PLAYER"));
            saveConfig();
        }
        Bukkit.getScheduler().runTask(this, () -> { setupPlayer(p); updateAllTeams(); });
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(this, this::updateAllTeams);
    }

    private void setupPlayer(Player p) {
        if (!getConfig().contains("players." + p.getUniqueId())) {
            getConfig().set("players." + p.getUniqueId(), getConfig().getString("settings.default-rank", "PLAYER"));
            saveConfig();
        }
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
        Objective obj = board.registerNewObjective("shc", Criteria.DUMMY, Component.text("SHUBH HYPIXEL"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        updateBoard(p);
        updateTeamsFor(p, board);
        updateTab(p);
    }

    private void updateBoard(Player p) {
        Scoreboard board = boards.get(p.getUniqueId());
        if (board == null) return;
        Objective obj = board.getObjective("shc");
        if (obj == null) { obj = board.registerNewObjective("shc", Criteria.DUMMY, Component.text("SHUBH HYPIXEL")); obj.setDisplaySlot(DisplaySlot.SIDEBAR); }
        boolean animated = getConfig().getBoolean("settings.animated-title", true);
        String title = animated ? titleFrames[Math.floorMod(animationFrame / 2, titleFrames.length)] : "&6&lSHUBH HYPIXEL";
        obj.displayName(Component.text(color(title)));
        for (String entry : new ArrayList<>(board.getEntries())) {
            Team t = board.getEntryTeam(entry);
            if (t == null || !t.getName().startsWith("rank_")) board.resetScores(entry);
        }
        List<String> lines = getConfig().getStringList("scoreboard.lines");
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i).replace("%rank%", getRank(p)).replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size())).replace("%world%", p.getWorld().getName()).replace("%wallet%", formatMoney(getWallet(p.getUniqueId()))).replace("%bank%", formatMoney(getBank(p.getUniqueId()))).replace("%coins%", formatMoney(getWallet(p.getUniqueId())));
            String entry = ChatColor.values()[i % ChatColor.values().length].toString() + ChatColor.values()[(i + 8) % ChatColor.values().length];
            Team lineTeam = board.getTeam("line" + i);
            if (lineTeam == null) lineTeam = board.registerNewTeam("line" + i);
            for (String old : new HashSet<>(lineTeam.getEntries())) lineTeam.removeEntry(old);
            lineTeam.addEntry(entry);
            lineTeam.prefix(Component.text(color(raw)));
            obj.getScore(entry).setScore(score--);
        }
    }

    private void updateAllTeams() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boards.get(viewer.getUniqueId());
            if (board != null) { updateTeamsFor(viewer, board); updateTab(viewer); }
        }
    }

    private void updateTeamsFor(Player viewer, Scoreboard board) {
        for (String rank : getConfig().getConfigurationSection("ranks").getKeys(false)) {
            String teamName = "rank_" + String.format("%02d", getConfig().getInt("ranks." + rank + ".weight", 99)) + "_" + rank.toLowerCase(Locale.ROOT);
            if (teamName.length() > 16) teamName = teamName.substring(0, 16);
            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);
            team.prefix(Component.text(color(getConfig().getString("ranks." + rank + ".prefix", "&7"))));
            team.suffix(Component.empty());
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
            for (String entry : new HashSet<>(team.getEntries())) team.removeEntry(entry);
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            String rank = getRank(target);
            String teamName = "rank_" + String.format("%02d", getConfig().getInt("ranks." + rank + ".weight", 99)) + "_" + rank.toLowerCase(Locale.ROOT);
            if (teamName.length() > 16) teamName = teamName.substring(0, 16);
            Team team = board.getTeam(teamName);
            if (team != null) team.addEntry(target.getName());
        }
    }

    private void updateTab(Player viewer) {
        viewer.sendPlayerListHeaderAndFooter(Component.text(color(getConfig().getString("settings.tab-header", "&6&lSHUBH HYPIXEL"))), Component.text(color(getConfig().getString("settings.tab-footer", "&7Play fair • Have fun!"))));
    }
    private String getRank(OfflinePlayer p) {
        String rank = getConfig().getString("players." + p.getUniqueId(), getConfig().getString("settings.default-rank", "PLAYER"));
        if (!getConfig().isConfigurationSection("ranks." + rank)) return "PLAYER";
        return rank.toUpperCase(Locale.ROOT);
    }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private void tell(CommandSender sender, String msg) { sender.sendMessage(color("&6[ShubhCore] &r" + msg)); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("shcb")) return handleBankCommand(sender, args);
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            tell(sender, "&e/shc help &7- commands"); tell(sender, "&e/shc spawn &7- teleport to spawn");
            tell(sender, "&e/shc setspawn &7- set spawn (admin)"); tell(sender, "&e/shc rank <player> <rank> &7- assign rank");
            tell(sender, "&e/shc balance [player] &7- wallet balance (Shubhcoins)"); tell(sender, "&e/shcb &7- bank commands");
            tell(sender, "&e/shc loan bank <amount> &7- borrow from bank"); tell(sender, "&e/shc loan lend <player> <amount> &7- lend coins to a player"); tell(sender, "&e/shc loan repay <bank|player> <amount> &7- repay a loan");
            tell(sender, "&e/shc deposit <amount> &7- deposit wallet coins"); tell(sender, "&e/shc withdraw <amount> &7- withdraw bank coins");
            tell(sender, "&e/shc pay <player> <amount> &7- pay from wallet"); tell(sender, "&e/shc eco <give|take|set> <player> <amount> &7- admin economy");
            tell(sender, "&e/shc reload &7- reload config"); return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                if (!(sender instanceof Player p)) { tell(sender, "Only players can use this command."); return true; }
                if (!getConfig().isConfigurationSection("spawn")) { tell(sender, "Spawn not set yet. Ask an admin to use /shc setspawn."); return true; }
                World w = Bukkit.getWorld(getConfig().getString("spawn.world", "world"));
                if (w == null) { tell(sender, "Spawn world not found."); return true; }
                Location loc = new Location(w, getConfig().getDouble("spawn.x"), getConfig().getDouble("spawn.y"), getConfig().getDouble("spawn.z"), (float)getConfig().getDouble("spawn.yaw"), (float)getConfig().getDouble("spawn.pitch"));
                p.teleport(loc); tell(p, "Teleported to spawn."); return true;
            }
            case "setspawn" -> {
                if (!(sender instanceof Player p) || !sender.hasPermission("shubhcore.admin")) { tell(sender, "You do not have permission."); return true; }
                Location l = p.getLocation(); getConfig().set("spawn.world", l.getWorld().getName()); getConfig().set("spawn.x", l.getX()); getConfig().set("spawn.y", l.getY()); getConfig().set("spawn.z", l.getZ()); getConfig().set("spawn.yaw", l.getYaw()); getConfig().set("spawn.pitch", l.getPitch()); saveConfig(); tell(p, "Spawn location saved."); return true;
            }
            case "balance", "bal" -> {
                if (args.length >= 2) {
                    if (!sender.hasPermission("shubhcore.economy.others")) { tell(sender, "You do not have permission."); return true; }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    tell(sender, "&e" + (target.getName() == null ? args[1] : target.getName()) + " wallet: &a" + formatMoney(getWallet(target.getUniqueId())) + " Shubhcoins");
                    return true;
                }
                if (!(sender instanceof Player p)) { tell(sender, "Console use: /shc balance <player>"); return true; }
                tell(sender, "Wallet: &a" + formatMoney(getWallet(p.getUniqueId())) + " Shubhcoins");
                tell(sender, "Bank: &e" + formatMoney(getBank(p.getUniqueId())) + " Shubhcoins"); return true;
            }
            case "bank" -> { return handleBankCommand(sender, args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[]{"balance"}); }
            case "deposit" -> {
                if (!(sender instanceof Player p)) { tell(sender, "Only players can use this command."); return true; }
                Long amount = parseAmount(args, 1, sender); if (amount == null) return true;
                long wallet = getWallet(p.getUniqueId());
                if (wallet < amount) { tell(sender, "Not enough coins in your wallet."); return true; }
                setWallet(p.getUniqueId(), wallet - amount); setBank(p.getUniqueId(), safeAdd(getBank(p.getUniqueId()), amount)); saveEconomy();
                tell(sender, "Deposited &e" + formatMoney(amount) + " coins&7. Wallet: &a" + formatMoney(getWallet(p.getUniqueId())) + "&7, Bank: &e" + formatMoney(getBank(p.getUniqueId()))); return true;
            }
            case "withdraw" -> {
                if (!(sender instanceof Player p)) { tell(sender, "Only players can use this command."); return true; }
                Long amount = parseAmount(args, 1, sender); if (amount == null) return true;
                long bank = getBank(p.getUniqueId());
                if (bank < amount) { tell(sender, "Not enough coins in your bank."); return true; }
                setBank(p.getUniqueId(), bank - amount); setWallet(p.getUniqueId(), safeAdd(getWallet(p.getUniqueId()), amount)); saveEconomy();
                tell(sender, "Withdrew &e" + formatMoney(amount) + " coins&7. Wallet: &a" + formatMoney(getWallet(p.getUniqueId())) + "&7, Bank: &e" + formatMoney(getBank(p.getUniqueId()))); return true;
            }
            case "pay" -> {
                if (!(sender instanceof Player p)) { tell(sender, "Only players can use this command."); return true; }
                if (args.length != 3) { tell(sender, "Usage: /shc pay <player> <amount>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { tell(sender, "That player must be online."); return true; }
                if (target.getUniqueId().equals(p.getUniqueId())) { tell(sender, "You cannot pay yourself."); return true; }
                Long amount = parseAmount(args, 2, sender); if (amount == null) return true;
                long wallet = getWallet(p.getUniqueId()); if (wallet < amount) { tell(sender, "Not enough coins in your wallet."); return true; }
                setWallet(p.getUniqueId(), wallet - amount); setWallet(target.getUniqueId(), safeAdd(getWallet(target.getUniqueId()), amount)); saveEconomy();
                tell(sender, "Paid &e" + formatMoney(amount) + " coins &7to " + target.getName() + "."); tell(target, "Received &e" + formatMoney(amount) + " coins &7from " + p.getName() + "."); return true;
            }
            case "eco" -> {
                if (!sender.hasPermission("shubhcore.economy.admin")) { tell(sender, "You do not have permission."); return true; }
                if (args.length != 4) { tell(sender, "Usage: /shc eco <give|take|set> <player> <amount>"); return true; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]); Long amount = parseAmount(args, 3, sender); if (amount == null) return true;
                String action = args[1].toLowerCase(Locale.ROOT); long wallet = getWallet(target.getUniqueId());
                switch (action) {
                    case "give" -> setWallet(target.getUniqueId(), safeAdd(wallet, amount));
                    case "take" -> setWallet(target.getUniqueId(), Math.max(0L, wallet - amount));
                    case "set" -> setWallet(target.getUniqueId(), amount);
                    default -> { tell(sender, "Usage: /shc eco <give|take|set> <player> <amount>"); return true; }
                }
                saveEconomy(); tell(sender, "Wallet updated for " + (target.getName() == null ? args[2] : target.getName()) + ": &a" + formatMoney(getWallet(target.getUniqueId())) + " Shubhcoins"); return true;
            }
            case "loan" -> {
                if (!(sender instanceof Player p)) { tell(sender, "Only players can use loan commands."); return true; }
                if (args.length < 3) { tell(sender, "Usage: /shc loan <bank|lend|repay> ..."); return true; }
                String action = args[1].toLowerCase(Locale.ROOT);
                if (action.equals("bank")) {
                    Long amount = parseAmount(args, 2, sender); if (amount == null) return true;
                    long debt = economy.getLong("loans." + p.getUniqueId() + ".bank", 0L);
                    if (amount > getConfig().getLong("economy.bank-loan-max", 100000L) - debt) { tell(sender, "Bank loan limit reached."); return true; }
                    economy.set("loans." + p.getUniqueId() + ".bank", safeAdd(debt, amount));
                    setWallet(p.getUniqueId(), safeAdd(getWallet(p.getUniqueId()), amount)); saveEconomy();
                    tell(sender, "Bank loan received: &e" + formatMoney(amount) + " Shubhcoins&7. Bank debt: &c" + formatMoney(economy.getLong("loans." + p.getUniqueId() + ".bank", 0L)) + " Shubhcoins."); return true;
                }
                if (action.equals("lend")) {
                    if (args.length != 4) { tell(sender, "Usage: /shc loan lend <player> <amount>"); return true; }
                    Player borrower = Bukkit.getPlayerExact(args[2]); if (borrower == null) { tell(sender, "Borrower must be online."); return true; }
                    if (borrower.getUniqueId().equals(p.getUniqueId())) { tell(sender, "You cannot lend coins to yourself."); return true; }
                    Long amount = parseAmount(args, 3, sender); if (amount == null) return true;
                    if (getWallet(p.getUniqueId()) < amount) { tell(sender, "Not enough Shubhcoins in your wallet."); return true; }
                    String debtPath = "debts." + borrower.getUniqueId() + "." + p.getUniqueId();
                    long debt = economy.getLong(debtPath, 0L);
                    setWallet(p.getUniqueId(), getWallet(p.getUniqueId()) - amount);
                    setWallet(borrower.getUniqueId(), safeAdd(getWallet(borrower.getUniqueId()), amount));
                    economy.set(debtPath, safeAdd(debt, amount)); saveEconomy();
                    tell(sender, "You lent " + formatMoney(amount) + " Shubhcoins to " + borrower.getName() + ". Their debt to you: " + formatMoney(economy.getLong(debtPath, 0L)) + ".");
                    tell(borrower, p.getName() + " lent you " + formatMoney(amount) + " Shubhcoins. Repay with /shc loan repay " + p.getName() + " <amount>."); return true;
                }
                if (action.equals("repay")) {
                    if (args.length != 4) { tell(sender, "Usage: /shc loan repay <bank|player> <amount>"); return true; }
                    Long amount = parseAmount(args, 3, sender); if (amount == null) return true;
                    if (getWallet(p.getUniqueId()) < amount) { tell(sender, "Not enough Shubhcoins in your wallet."); return true; }
                    if (args[2].equalsIgnoreCase("bank")) {
                        String debtPath = "loans." + p.getUniqueId() + ".bank"; long debt = economy.getLong(debtPath, 0L);
                        if (debt == 0) { tell(sender, "You have no bank loan."); return true; }
                        if (amount > debt) { tell(sender, "You only owe " + formatMoney(debt) + " Shubhcoins."); return true; }
                        economy.set(debtPath, debt - amount); setWallet(p.getUniqueId(), getWallet(p.getUniqueId()) - amount); saveEconomy();
                        tell(sender, "Repaid " + formatMoney(amount) + " Shubhcoins to the bank. Remaining debt: " + formatMoney(debt - amount) + "."); return true;
                    }
                    OfflinePlayer lender = Bukkit.getOfflinePlayer(args[2]); String debtPath = "debts." + p.getUniqueId() + "." + lender.getUniqueId(); long debt = economy.getLong(debtPath, 0L);
                    if (debt == 0) { tell(sender, "No recorded loan from that player."); return true; }
                    if (amount > debt) { tell(sender, "You only owe " + formatMoney(debt) + " Shubhcoins to that player."); return true; }
                    economy.set(debtPath, debt - amount); setWallet(p.getUniqueId(), getWallet(p.getUniqueId()) - amount); setWallet(lender.getUniqueId(), safeAdd(getWallet(lender.getUniqueId()), amount)); saveEconomy();
                    tell(sender, "Repaid " + formatMoney(amount) + " Shubhcoins to " + args[2] + ". Remaining debt: " + formatMoney(debt - amount) + ".");
                    Player lenderOnline = Bukkit.getPlayer(lender.getUniqueId()); if (lenderOnline != null) tell(lenderOnline, p.getName() + " repaid " + formatMoney(amount) + " Shubhcoins. Remaining debt: " + formatMoney(debt - amount) + "."); return true;
                }
                tell(sender, "Usage: /shc loan <bank|lend|repay> ..."); return true;
            }
            case "rank" -> {
                if (!sender.hasPermission("shubhcore.rank")) { tell(sender, "You do not have permission."); return true; }
                if (args.length != 3) { tell(sender, "Usage: /shc rank <player> <OWNER|DEV|ADMIN|MOD|VIP|PLAYER>"); return true; }
                String rank = args[2].toUpperCase(Locale.ROOT);
                if (!getConfig().isConfigurationSection("ranks." + rank)) { tell(sender, "Unknown rank."); return true; }
                Player online = Bukkit.getPlayerExact(args[1]);
                OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayer(args[1]);
                getConfig().set("players." + target.getUniqueId(), rank); saveConfig();
                if (online != null) { updateBoard(online); updateAllTeams(); }
                tell(sender, "Set " + target.getName() + " rank to " + rank + "."); return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("shubhcore.reload")) { tell(sender, "You do not have permission."); return true; }
                reloadConfig(); updateAllTeams(); for (Player p : Bukkit.getOnlinePlayers()) updateBoard(p); tell(sender, "Config reloaded."); return true;
            }
            default -> { tell(sender, "Unknown subcommand. Use /shc help."); return true; }
        }
    }

    private boolean handleBankCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { tell(sender, "Only players can use bank commands."); return true; }
        String action = args.length == 0 ? "balance" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "balance", "bal" -> { tell(sender, "Bank balance: &e" + formatMoney(getBank(p.getUniqueId())) + " Shubhcoins"); tell(sender, "Wallet: &a" + formatMoney(getWallet(p.getUniqueId())) + " Shubhcoins"); }
            case "deposit" -> {
                Long amount = parseAmount(args, 1, sender); if (amount == null) return true;
                long wallet = getWallet(p.getUniqueId()); if (wallet < amount) { tell(sender, "Not enough Shubhcoins in wallet."); return true; }
                setWallet(p.getUniqueId(), wallet - amount); setBank(p.getUniqueId(), safeAdd(getBank(p.getUniqueId()), amount)); saveEconomy(); tell(sender, "Deposited " + formatMoney(amount) + " Shubhcoins.");
            }
            case "withdraw" -> {
                Long amount = parseAmount(args, 1, sender); if (amount == null) return true;
                long bank = getBank(p.getUniqueId()); if (bank < amount) { tell(sender, "Not enough Shubhcoins in bank."); return true; }
                setBank(p.getUniqueId(), bank - amount); setWallet(p.getUniqueId(), safeAdd(getWallet(p.getUniqueId()), amount)); saveEconomy(); tell(sender, "Withdrew " + formatMoney(amount) + " Shubhcoins.");
            }
            case "loan" -> {
                if (args.length < 2) { tell(sender, "Usage: /shcb loan <amount> or /shcb repay <amount>"); return true; }
                if (args[1].equalsIgnoreCase("repay")) {
                    Long amount = parseAmount(args, 2, sender); if (amount == null) return true;
                    String path = "loans." + p.getUniqueId() + ".bank"; long debt = economy.getLong(path, 0L);
                    if (debt == 0 || amount > debt || amount > getWallet(p.getUniqueId())) { tell(sender, "Check your bank debt and wallet balance."); return true; }
                    economy.set(path, debt - amount); setWallet(p.getUniqueId(), getWallet(p.getUniqueId()) - amount); saveEconomy(); tell(sender, "Bank loan repaid. Remaining debt: " + formatMoney(debt - amount) + " Shubhcoins."); return true;
                }
                String[] loanArgs = {"loan", "bank", args[1]}; return onCommand(sender, Objects.requireNonNull(getCommand("shc")), "shc", loanArgs);
            }
            default -> tell(sender, "Usage: /shcb <balance|deposit <amount>|withdraw <amount>|loan <amount>|loan repay <amount>");
        }
        return true;
    }

    private long getWallet(UUID uuid) { return Math.max(0L, economy.getLong("balances." + uuid + ".wallet", 0L)); }
    private long getBank(UUID uuid) { return Math.max(0L, economy.getLong("balances." + uuid + ".bank", 0L)); }
    private void setWallet(UUID uuid, long amount) { economy.set("balances." + uuid + ".wallet", Math.max(0L, amount)); }
    private void setBank(UUID uuid, long amount) { economy.set("balances." + uuid + ".bank", Math.max(0L, amount)); }
    private void saveEconomy() {
        if (economy == null || economyFile == null) return;
        try { economy.save(economyFile); } catch (java.io.IOException ex) { getLogger().severe("Could not save economy.yml: " + ex.getMessage()); }
    }
    private long safeAdd(long a, long b) { if (Long.MAX_VALUE - a < b) return Long.MAX_VALUE; return a + b; }
    private String formatMoney(long amount) { return java.text.NumberFormat.getIntegerInstance(Locale.US).format(amount); }
    private Long parseAmount(String[] args, int index, CommandSender sender) {
        if (args.length <= index) { tell(sender, "Enter a whole-number amount. Example: /shc withdraw 100"); return null; }
        try { long amount = Long.parseLong(args[index]); if (amount <= 0) { tell(sender, "Amount must be greater than zero."); return null; } return amount; }
        catch (NumberFormatException ex) { tell(sender, "Invalid amount. Use a whole number without commas."); return null; }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && command.getName().equalsIgnoreCase("shcb")) return List.of("balance", "deposit", "withdraw", "loan").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 1) return List.of("help", "spawn", "setspawn", "rank", "reload", "balance", "bank", "deposit", "withdraw", "pay", "eco", "loan").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("loan")) return List.of("bank", "lend", "repay").stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("eco") && sender.hasPermission("shubhcore.economy.admin")) return List.of("give", "take", "set").stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 2 && (args[0].equalsIgnoreCase("rank") || args[0].equalsIgnoreCase("pay") || args[0].equalsIgnoreCase("balance")) && (args[0].equalsIgnoreCase("rank") ? sender.hasPermission("shubhcore.rank") : true)) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 3 && args[0].equalsIgnoreCase("eco") && sender.hasPermission("shubhcore.economy.admin")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 4 && args[0].equalsIgnoreCase("eco") && sender.hasPermission("shubhcore.economy.admin")) return Collections.emptyList();
        if (args.length == 3 && args[0].equalsIgnoreCase("rank") && sender.hasPermission("shubhcore.rank")) return List.of("OWNER", "DEV", "ADMIN", "MOD", "VIP", "PLAYER").stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        return Collections.emptyList();
    }
}
