/*
 * This file is part of DropParty.
 *
 * Copyright (c) 2013-2013 <http://dev.bukkit.org/server-mods/dropparty//>
 *
 * DropParty is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DropParty is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with DropParty.  If not, see <http://www.gnu.org/licenses/>.
 */
package me.ampayne2.dropparty.parties;

import me.ampayne2.dropparty.DPFireworkPoint;
import me.ampayne2.dropparty.DPItemPoint;
import me.ampayne2.dropparty.DPUtils;
import me.ampayne2.dropparty.DropParty;
import me.ampayne2.dropparty.config.ConfigType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Controls and contains all the information of a drop party.
 */
public abstract class Party {
    protected final DropParty dropParty;
    protected final String partyName;
    protected final PartyType type;
    protected long maxLength;
    protected long currentLength;
    protected long itemDelay;
    protected int maxStackSize;
    protected int fireworkAmount;
    protected long fireworkDelay;
    protected boolean isRunning = false;
    protected int taskId;
    protected boolean isShootingFireworks = false;
    protected int fireworkTaskId;
    protected int fireworksShot;
    protected int periodicTaskId;
    protected boolean startPeriodically;
    protected long startPeriod;
    protected boolean voteToStart;
    protected int requiredVotes;
    protected Set<String> voters = new HashSet<>();
    protected Location teleport;
    protected List<DPItemPoint> itemPoints = new ArrayList<>();
    protected List<DPFireworkPoint> fireworkPoints = new ArrayList<>();
    protected final static Random RANDOM = new Random();

    /**
     * Creates a Party from default settings.
     *
     * @param dropParty The DropParty instance.
     * @param partyName The name of the party.
     * @param type      The type of the party.
     * @param teleport  The teleport location of the party.
     */
    public Party(DropParty dropParty, String partyName, PartyType type, Location teleport) {
        this.dropParty = dropParty;
        this.partyName = partyName;
        this.type = type;
        this.teleport = teleport;

        FileConfiguration config = dropParty.getConfig();
        maxLength = config.getLong("defaultsettings." + PartySetting.MAX_LENGTH.getName(), 6000);
        itemDelay = config.getLong("defaultsettings." + PartySetting.ITEM_DELAY.getName(), 5);
        maxStackSize = config.getInt("defaultsettings." + PartySetting.MAX_STACK_SIZE.getName(), 8);
        fireworkAmount = config.getInt("defaultsettings." + PartySetting.FIREWORK_AMOUNT.getName(), 8);
        fireworkDelay = config.getLong("defaultsettings." + PartySetting.FIREWORK_DELAY.getName(), 2);
        startPeriodically = config.getBoolean("defaultsettings." + PartySetting.START_PERIODICALLY.getName(), false);
        startPeriod = config.getLong("defaultsettings." + PartySetting.START_PERIOD.getName(), 144000);
        voteToStart = config.getBoolean("defaultsettings." + PartySetting.VOTE_TO_START.getName(), false);
        requiredVotes = config.getInt("defaultsettings." + PartySetting.REQUIRED_VOTES.getName(), 50);
    }

    /**
     * Loads a Party from a ConfigurationSection.
     *
     * @param dropParty The DropParty instance.
     * @param section   The ConfigurationSection.
     */
    public Party(DropParty dropParty, ConfigurationSection section) {
        this.dropParty = dropParty;
        this.partyName = section.getString("name");
        this.type = PartyType.valueOf(section.getString("type"));
        maxLength = section.getLong(PartySetting.MAX_LENGTH.getName());
        itemDelay = section.getLong(PartySetting.ITEM_DELAY.getName());
        maxStackSize = section.getInt(PartySetting.MAX_STACK_SIZE.getName());
        fireworkAmount = section.getInt(PartySetting.FIREWORK_AMOUNT.getName());
        fireworkDelay = section.getLong(PartySetting.FIREWORK_DELAY.getName());
        startPeriodically = section.getBoolean(PartySetting.START_PERIODICALLY.getName());
        startPeriod = section.getLong(PartySetting.START_PERIOD.getName());
        voteToStart = section.getBoolean(PartySetting.VOTE_TO_START.getName());
        requiredVotes = section.getInt(PartySetting.REQUIRED_VOTES.getName());
        teleport = DPUtils.stringToLocation(section.getString("teleport"));

        for (String itemPoint : section.getStringList("itempoints")) {
            itemPoints.add(DPItemPoint.fromConfig(dropParty, this, itemPoint));
        }

        for (String fireworkPoint : section.getStringList("fireworkpoints")) {
            fireworkPoints.add(DPFireworkPoint.fromConfig(dropParty, this, fireworkPoint));
        }
    }

    /**
     * Gets the name of the party.
     *
     * @return The name of the party.
     */
    public String getName() {
        return partyName;
    }

    /**
     * Gets the type of the party.
     *
     * @return The type of the party.
     */
    public PartyType getType() {
        return type;
    }

    /**
     * Checks if the party is of a certain type.
     *
     * @param partyType The party type.
     * @return True if the party is of the party type, else false.
     */
    public boolean isType(PartyType partyType) {
        return type == partyType;
    }

    /**
     * Checks if the party is running.
     *
     * @return True if the party is running, else false.
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Starts the party.
     */
    public void start() {
        if (!isRunning) {
            isRunning = true;
            voters.clear();
            stopShootingFireworks();
            taskId = dropParty.getServer().getScheduler().scheduleSyncRepeatingTask(dropParty, new Runnable() {
                @Override
                public void run() {
                    currentLength += itemDelay;
                    if (currentLength > maxLength || !dropNext()) {
                        stop(true);
                        currentLength = 0;
                    }
                }
            }, 0, itemDelay);
            dropParty.getMessenger().sendMessage(dropParty.getServer(), "broadcast.start", partyName, partyName);
        }
    }

    /**
     * Stops the party.
     *
     * @param shootFireworks If the party should shoot fireworks.
     */
    public void stop(boolean shootFireworks) {
        if (isRunning) {
            isRunning = false;
            dropParty.getServer().getScheduler().cancelTask(taskId);
            dropParty.getMessenger().sendMessage(dropParty.getServer(), "broadcast.stop", partyName);

            if (shootFireworks) {
                startShootingFireworks();
            }
        }
    }

    /**
     * Teleports a player to the party.
     *
     * @param player The player to teleport to the party.
     */
    public void teleport(Player player) {
        player.teleport(teleport);
        dropParty.getMessenger().sendMessage(player, "party.teleport", partyName);
    }

    /**
     * Checks if the party is shooting fireworks.
     *
     * @return True if the party is shooting fireworks, else false.
     */
    public boolean isShootingFireworks() {
        return isShootingFireworks;
    }

    /**
     * Starts shooting fireworks.
     */
    public void startShootingFireworks() {
        if (!isShootingFireworks) {
            isShootingFireworks = true;
            fireworkTaskId = dropParty.getServer().getScheduler().scheduleSyncRepeatingTask(dropParty, new Runnable() {
                @Override
                public void run() {
                    fireworksShot++;
                    if (fireworksShot <= fireworkAmount) {
                        fireworkPoints.get(RANDOM.nextInt(fireworkPoints.size())).spawnFirework();
                    } else {
                        stopShootingFireworks();
                    }
                }
            }, 0, fireworkDelay);
        }
    }

    /**
     * Stops shooting fireworks.
     */
    public void stopShootingFireworks() {
        if (isShootingFireworks) {
            isShootingFireworks = false;
            fireworksShot = 0;
            dropParty.getServer().getScheduler().cancelTask(fireworkTaskId);
        }
    }

    /**
     * Drops the next item stack in the party.
     *
     * @return True if the next item stack was dropped successfully.
     */
    public abstract boolean dropNext();

    /**
     * Drops an item stack at a random item point.
     *
     * @param itemStack The item stack to drop.
     * @return True if the item stack is not null or air, and has a place to drop.
     */
    public boolean dropItemStack(ItemStack itemStack) {
        if (itemStack != null && itemStack.getType() != Material.AIR && itemPoints.size() > 0) {
            DPItemPoint itemPoint = itemPoints.get(RANDOM.nextInt(itemPoints.size()));
            itemPoint.getLocation().getWorld().dropItemNaturally(itemPoint.getLocation(), itemStack);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Starts the periodic start timer.
     */
    public void startPeriodicTimer() {
        if (startPeriodically) {
            periodicTaskId = dropParty.getServer().getScheduler().scheduleSyncRepeatingTask(dropParty, new Runnable() {
                @Override
                public void run() {
                    start();
                }
            }, startPeriod, startPeriod);
        }
    }

    /**
     * Stops the periodic start timer.
     */
    public void stopPeriodicTimer() {
        dropParty.getServer().getScheduler().cancelTask(periodicTaskId);
    }

    /**
     * Resets the periodic start timer.
     */
    public void resetPeriodicTimer() {
        stopPeriodicTimer();
        startPeriodicTimer();
    }

    /**
     * Checks if the party has an item point.
     *
     * @param location The location of the item point.
     * @return True if the party has the item point, else false.
     */
    public boolean hasItemPoint(Location location) {
        for (DPItemPoint dpItemPoint : itemPoints) {
            if (dpItemPoint.getLocation().equals(location)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an item point.
     *
     * @param itemPoint The item point.
     */
    public void addItemPoint(DPItemPoint itemPoint) {
        itemPoints.add(itemPoint);
        FileConfiguration partyConfig = dropParty.getConfigManager().getConfig(ConfigType.PARTY);
        String path = "Parties." + partyName + ".itempoints";
        List<String> dpItemPoints = partyConfig.getStringList(path);
        dpItemPoints.add(itemPoint.toConfig());
        partyConfig.set(path, dpItemPoints);
        dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
    }

    /**
     * Removes an item point.
     *
     * @param location The location of the item point.
     */
    public void removeItemPoint(Location location) {
        for (DPItemPoint dpItemPoint : new HashSet<>(itemPoints)) {
            if (dpItemPoint.getLocation().equals(location)) {
                itemPoints.remove(dpItemPoint);
                FileConfiguration partyConfig = dropParty.getConfigManager().getConfig(ConfigType.PARTY);
                String path = "Parties." + partyName + ".itempoints";
                List<String> dpItemPoints = partyConfig.getStringList(path);
                dpItemPoints.remove(dpItemPoint.toConfig());
                partyConfig.set(path, dpItemPoints);
                dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
            }
        }
    }

    /**
     * Gets the item points.
     *
     * @return The points items can spawn at during a party.
     */
    public List<DPItemPoint> getItemPoints() {
        return itemPoints;
    }

    /**
     * Checks if the party has a firework point.
     *
     * @param location The location of the firework point.
     * @return True if the party has the firework point, else false.
     */
    public boolean hasFireworkPoint(Location location) {
        for (DPFireworkPoint dpFireworkPoint : fireworkPoints) {
            if (dpFireworkPoint.getLocation().equals(location)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a firework point.
     *
     * @param fireworkPoint The firework point.
     */
    public void addFireworkPoint(DPFireworkPoint fireworkPoint) {
        fireworkPoints.add(fireworkPoint);
        FileConfiguration partyConfig = dropParty.getConfigManager().getConfig(ConfigType.PARTY);
        String path = "Parties." + partyName + ".fireworkpoints";
        List<String> dpFireworkPoints = partyConfig.getStringList(path);
        dpFireworkPoints.add(fireworkPoint.toConfig());
        partyConfig.set(path, dpFireworkPoints);
        dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
    }

    /**
     * Removes a firework point.
     *
     * @param location The location of the firework point.
     */
    public void removeFireworkPoint(Location location) {
        for (DPFireworkPoint dpFireworkPoint : new HashSet<>(fireworkPoints)) {
            if (dpFireworkPoint.getLocation().equals(location)) {
                fireworkPoints.remove(dpFireworkPoint);
                FileConfiguration partyConfig = dropParty.getConfigManager().getConfig(ConfigType.PARTY);
                String path = "Parties." + partyName + ".fireworkpoints";
                List<String> dpFireworkPoints = partyConfig.getStringList(path);
                dpFireworkPoints.remove(dpFireworkPoint.toConfig());
                partyConfig.set(path, dpFireworkPoints);
                dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
            }
        }
    }

    /**
     * Gets the firework points.
     *
     * @return The points fireworks can spawn at after a party.
     */
    public List<DPFireworkPoint> getFireworkPoints() {
        return fireworkPoints;
    }

    /**
     * Gets the teleport.
     *
     * @return The teleport location of the party.
     */
    public Location getTeleport() {
        return teleport;
    }

    /**
     * Sets the teleport.
     *
     * @param teleport The teleport location of the party.
     */
    public void setTeleport(Location teleport) {
        if (teleport != this.teleport) {
            this.teleport = teleport;
            dropParty.getConfigManager().getConfig(ConfigType.PARTY).set("Parties." + partyName + ".teleport", DPUtils.locationToString(teleport));
            dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
        }
    }

    /**
     * Gets the max length.
     *
     * @return The max amount of ticks the party can last.
     */
    public long getMaxLength() {
        return maxLength;
    }

    /**
     * Sets the max length.
     *
     * @param maxLength The max amount of ticks the party can last.
     */
    public void setMaxLength(long maxLength) {
        if (maxLength != this.maxLength) {
            this.maxLength = maxLength;
            dropParty.getConfigManager().getConfig(ConfigType.PARTY).set("Parties." + partyName + "." + PartySetting.MAX_LENGTH.getName(), maxLength);
            dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
        }
    }

    /**
     * Gets the item delay.
     *
     * @return The ticks between each item dropped.
     */
    public long getItemDelay() {
        return itemDelay;
    }

    /**
     * Sets the item delay.
     *
     * @param itemDelay The ticks between each item dropped.
     */
    public void setItemDelay(long itemDelay) {
        if (itemDelay != this.itemDelay) {
            this.itemDelay = itemDelay;
            dropParty.getConfigManager().getConfig(ConfigType.PARTY).set("Parties." + partyName + "." + PartySetting.ITEM_DELAY.getName(), itemDelay);
            dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
        }
    }

    /**
     * Gets the max stack size.
     *
     * @return The max stack size of dropped items.
     */
    public int getMaxStackSize() {
        return maxStackSize;
    }

    /**
     * Sets the max stack size.
     *
     * @param maxStackSize The max stack size of dropped items.
     */
    public void setMaxStackSize(int maxStackSize) {
        if (maxStackSize != this.maxStackSize) {
            this.maxStackSize = maxStackSize;
            dropParty.getConfigManager().getConfig(ConfigType.PARTY).set("Parties." + partyName + "." + PartySetting.MAX_STACK_SIZE.getName(), maxStackSize);
            dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
        }
    }

    /**
     * Gets the firework amount.
     *
     * @return The amount of fireworks launched after the party.
     */
    public int getFireworkAmount() {
        return fireworkAmount;
    }

    /**
     * Sets the firework amount.
     *
     * @param fireworkAmount The amount of fireworks launched after the party.
     */
    public void setFireworkAmount(int fireworkAmount) {
        if (fireworkAmount != this.fireworkAmount) {
            this.fireworkAmount = fireworkAmount;
            dropParty.getConfigManager().getConfig(ConfigType.PARTY).set("Parties." + partyName + "." + PartySetting.FIREWORK_AMOUNT.getName(), fireworkAmount);
            dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
        }
    }

    /**
     * Gets the firework delay.
     *
     * @return The ticks between each firework launch.
     */
    public long getFireworkDelay() {
        return fireworkDelay;
    }

    /**
     * Sets the firework delay.
     *
     * @param fireworkDelay The ticks between each firework launch.
     */
    public void setFireworkDelay(long fireworkDelay) {
        if (fireworkDelay != this.fireworkDelay) {
            this.fireworkDelay = fireworkDelay;
            dropParty.getConfigManager().getConfig(ConfigType.PARTY).set("Parties." + partyName + "." + PartySetting.FIREWORK_DELAY.getName(), fireworkDelay);
            dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
        }
    }

    /**
     * Checks if the party starts periodically.
     *
     * @return True if the party starts periodically, else false.
     */
    public boolean startsPeriodically() {
        return startPeriodically;
    }

    /**
     * Sets if the party starts periodically.
     *
     * @param startPeriodically If the party should start periodically.
     */
    public void setStartPeriodically(boolean startPeriodically) {
        if (startPeriodically != this.startPeriodically) {
            this.startPeriodically = startPeriodically;
            dropParty.getConfigManager().getConfig(ConfigType.PARTY).set("Parties." + partyName + "." + PartySetting.START_PERIODICALLY.getName(), startPeriodically);
            dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
            if (startPeriodically) {
                startPeriodicTimer();
            } else {
                stopPeriodicTimer();
            }
        }
    }

    /**
     * Gets the start period.
     *
     * @return The ticks between periodic starts.
     */
    public long getStartPeriod() {
        return startPeriod;
    }

    /**
     * Sets the start period.
     *
     * @param startPeriod The ticks between periodic starts.
     */
    public void setStartPeriod(long startPeriod) {
        if (startPeriod != this.startPeriod) {
            this.startPeriod = startPeriod;
            dropParty.getConfigManager().getConfig(ConfigType.PARTY).set("Parties." + partyName + "." + PartySetting.START_PERIOD.getName(), startPeriod);
            dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
            resetPeriodicTimer();
        }
    }

    /**
     * Checks if the party can be started by votes.
     *
     * @return True if the party can be started by votes, else false.
     */
    public boolean canVoteToStart() {
        return voteToStart;
    }

    /**
     * Sets if the party can be started by votes.
     *
     * @param voteToStart If the party can be started by votes.
     */
    public void setVoteToStart(boolean voteToStart) {
        if (voteToStart != this.voteToStart) {
            this.voteToStart = voteToStart;
            dropParty.getConfigManager().getConfig(ConfigType.PARTY).set("Parties." + partyName + "." + PartySetting.VOTE_TO_START.getName(), voteToStart);
            dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
            voters.clear();
        }
    }

    /**
     * Gets the required votes.
     *
     * @return Amount of votes required to start the party.
     */
    public int getRequiredVotes() {
        return requiredVotes;
    }

    /**
     * Sets the required votes.
     *
     * @param requiredVotes Amount of votes required to start the party.
     */
    public void setRequiredVotes(int requiredVotes) {
        if (requiredVotes != this.requiredVotes) {
            this.requiredVotes = requiredVotes;
            dropParty.getConfigManager().getConfig(ConfigType.PARTY).set("Parties." + partyName + "." + PartySetting.REQUIRED_VOTES.getName(), requiredVotes);
            dropParty.getConfigManager().getConfigAccessor(ConfigType.PARTY).saveConfig();
            voters.clear();
        }
    }

    /**
     * Checks if a player has voted for the party.
     *
     * @param playerName The name of the player.
     * @return True if the player has voted, else false.
     */
    public boolean hasVoted(String playerName) {
        return voters.contains(playerName);
    }

    /**
     * Adds a vote to start the party.
     *
     * @param playerName The name of the player who voted.
     */
    public void addVote(String playerName) {
        if (voteToStart && !isRunning) {
            voters.add(playerName);
            if (voters.size() >= requiredVotes) {
                start();
            }
        }
    }

    /**
     * Gets the current amount of votes.
     *
     * @return How many players have voted for the party.
     */
    public int getVotes() {
        return voters.size();
    }

    /**
     * Clears the votes of the party.
     */
    public void clearVotes() {
        voters.clear();
    }

    /**
     * Saves the party to a ConfigurationSection.
     *
     * @param section The ConfigurationSection to save the party to.
     */
    public void save(ConfigurationSection section) {
        section.set("name", partyName);
        section.set("type", type.name());
        section.set(PartySetting.MAX_LENGTH.getName(), maxLength);
        section.set(PartySetting.ITEM_DELAY.getName(), itemDelay);
        section.set(PartySetting.MAX_STACK_SIZE.getName(), maxStackSize);
        section.set(PartySetting.FIREWORK_AMOUNT.getName(), fireworkAmount);
        section.set(PartySetting.FIREWORK_DELAY.getName(), fireworkDelay);
        section.set(PartySetting.START_PERIODICALLY.getName(), startPeriodically);
        section.set(PartySetting.START_PERIOD.getName(), startPeriod);
        section.set(PartySetting.VOTE_TO_START.getName(), voteToStart);
        section.set(PartySetting.REQUIRED_VOTES.getName(), requiredVotes);
        section.set("teleport", DPUtils.locationToString(teleport));
        List<String> dpItemPoints = new ArrayList<>();
        for (DPItemPoint itemPoint : itemPoints) {
            dpItemPoints.add(itemPoint.toConfig());
        }
        section.set("itempoints", dpItemPoints);
        List<String> dpFireworkPoints = new ArrayList<>();
        for (DPFireworkPoint fireworkPoint : fireworkPoints) {
            dpFireworkPoints.add(fireworkPoint.toConfig());
        }
        section.set("fireworkpoints", dpFireworkPoints);
    }
}
