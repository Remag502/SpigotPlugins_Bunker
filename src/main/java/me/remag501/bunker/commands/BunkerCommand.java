package me.remag501.bunker.commands;

import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import me.remag501.bunker.util.Schematic;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import me.remag501.bunker.Bunker;
import me.remag501.bunker.util.ConfigUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.WorldType;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.bukkit.Bukkit.getLogger;

public class BunkerCommand implements CommandExecutor {

    private final Bunker plugin;
    private final Set<UUID> runningTasks = new HashSet<>();

    public BunkerCommand(Bunker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // May remove
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by players.");
            return true;
        }
        // Handle the "reload" argument
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            // Reload the configuration
            plugin.reloadConfig();
            sender.sendMessage("Configuration reloaded successfully.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("buy"))
            return assignBunker((Player) sender);
        if (args.length > 0 && args[0].equalsIgnoreCase("home"))
            return bunkerHome(sender);
        // Allows players to visit the bunker by using the "visit" argument
        if (args.length == 1 && args[0].equalsIgnoreCase("visit")) {
            sender.sendMessage("Usage: /bunker visit [player]");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("visit"))
            return visit(sender, args[1]); // Will need args[1] for the player name
//        if (args.length > 0 && args[0].equalsIgnoreCase("test")) // Will be removed later
//            return cloneNPC(sender); // Will need args[1] for the player name
        if (args.length > 1 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("add"))
            return addBunkers(Integer.parseInt(args[2]), sender); // Create more bunkers using multicore
        if (args.length > 0) {
            sender.sendMessage("Invalid arguments! Use /bunker [buy/home/visit]");
            return true;
        }
        // No arguments
        return bunkerHome(sender);
    }

    private boolean addNPC(CommandSender sender, World world) {
        // Get config message strings
        FileConfiguration npcConfig = plugin.getConfig();
        int npcID = npcConfig.getInt("npcID");
        double npcX = npcConfig.getDouble("npcCoords.x");
        double npcY = npcConfig.getDouble("npcCoords.y");
        double npcZ = npcConfig.getDouble("npcCoords.z");
        float npcYaw = (float) npcConfig.getDouble("npcCoords.yaw");
        float npcPitch = (float) npcConfig.getDouble("npcCoords.pitch");
        // Copy the NPC
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcID);
        NPC clone = CitizensAPI.getNPCRegistry().createNPC(npc.getEntity().getType(), npc.getName());
        // Clone traits from the original NPC
        for (Trait trait : npc.getTraits()) {
            clone.addTrait(trait.getClass());
        }
        // Set the location of the cloned NPC
        Location newLocation = new Location(world, npcX, npcY, npcZ); // Provide the x, y, z coordinates
        newLocation.setYaw(npcYaw);
        newLocation.setPitch(npcPitch);
        // Spawn the cloned NPC at the new location
        clone.spawn(newLocation);
        return true;
    }

    private boolean assignBunker(Player buyer) {
        // Get config message strings
        FileConfiguration msgConfig = plugin.getConfig();
        String noBunkers = msgConfig.getString("noBunkers"),
                alreadyPurchased = msgConfig.getString("alreadyPurchased"),
                bunkerPurchased = msgConfig.getString("bunkerPurchased");
        // Check if the player has enough storage for bunkers and has not already bought one
        ConfigUtil config = new ConfigUtil(plugin, "bunkers.yml");
        int assignedBunkers = config.getConfig().getInt("assignedBunkers");
        int totalBunkers = config.getConfig().getInt("totalBunkers");
        if (assignedBunkers == totalBunkers) {
            buyer.sendMessage(noBunkers);
            return true;
        } else if (config.getConfig().contains(buyer.getName())) {
            buyer.sendMessage(alreadyPurchased);
            return true;
        }
        else {
            // Decrease the available bunkers count
            config.getConfig().set("assignedBunkers", assignedBunkers + 1);
            config.getConfig().set(buyer.getName(), assignedBunkers);
            config.save();
            buyer.sendMessage(bunkerPurchased);
            return true;
        }
    }
    private boolean visit(CommandSender sender, String playerName) {
        // Get config message strings
        FileConfiguration msgConfig = plugin.getConfig();
        String playerNotExist = msgConfig.getString("playerNotExist"),
                visitMsg = msgConfig.getString("visitMsg"); // Revisit
        playerNotExist = playerNotExist.replace("%player%", playerName);
        visitMsg = visitMsg.replace("%player%", playerName);
        // Check if the player exists
        ConfigUtil config = new ConfigUtil(plugin, "bunkers.yml");
        if (!config.getConfig().contains(playerName)) {
            sender.sendMessage(playerNotExist);
            return true;
        }
        // Teleport player to their bunker
        String worldName = "bunker_" + config.getConfig().getString(playerName);
        sender.sendMessage(visitMsg);
        teleportPlayer((Player) sender, worldName);
        return true;
    }

    private boolean bunkerHome(@NotNull CommandSender sender) {
        // Get config message strings
        String noBunker = plugin.getConfig().getString("noBunker");
        noBunker = noBunker.replace("%player%", sender.getName());
        String homeMsg = plugin.getConfig().getString("homeMsg");
        homeMsg = homeMsg.replace("%player%", sender.getName());
        // Handle the default case: no arguments or non-reload arguments
        ConfigUtil config = new ConfigUtil(plugin, "bunkers.yml");
        // Check if player has a bunker
        if (!config.getConfig().contains(sender.getName())) {
            sender.sendMessage(noBunker);
            return true;
        }
        // Send player teleport message
        Player player = (Player) sender;
        player.sendMessage(homeMsg);
        // Teleport player to their bunker
        // Get worldname
        String worldName = config.getConfig().getString(player.getName());
        worldName = "bunker_" + worldName;
        teleportPlayer(player, worldName);
        return true;
    }

    private void teleportPlayer(Player player, String worldName) {
        // Get the MVWorldManager
        MultiverseCore multiverseCore = (MultiverseCore) Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        MVWorldManager worldManager = multiverseCore.getMVWorldManager();
        // Get the target world
        MultiverseWorld targetWorld = worldManager.getMVWorld(worldName);
        // Teleport the player to the spawn location of the target world
        Location spawnLocation = targetWorld.getSpawnLocation();
        if (spawnLocation != null) {
            player.teleport(spawnLocation);
        } else {
            player.sendMessage("Spawn location not found in world: " + worldName + ". Contact an admin for help!");
        }
    }



    private boolean addBunkers(int bunkers, CommandSender sender) {
        // Check if schematic file exists
        File schematicFile = new File(plugin.getDataFolder(), "schematics/bunker.schem");
        if (!schematicFile.exists()) {
            ((Player) sender).sendMessage("No schematic found. Make sure you named it correctly and its placed in schematics/bunker.schem");
            return true;
        }
        // Get UUID and playerID
        Player player = null;
        UUID playerId = null;
        if (sender instanceof Player) {
            player = (Player) sender;
            playerId = player.getUniqueId();
        }
        // Check if the player already triggered the command
        if (runningTasks.contains(playerId)) {
            player.sendMessage("The bunker creation is still in progress. Please wait.");
            return true;
        }
        runningTasks.add(playerId);
        // Update bunker count in config
        ConfigUtil config = new ConfigUtil(plugin,"bunkers.yml");
        int totalBunkers = config.getConfig().getInt("totalBunkers");
        config.getConfig().set("totalBunkers", totalBunkers + bunkers);
        config.save();
        // Create the bunkers on a different world
        for (int i = 0; i < bunkers; i++)
            createBunkerWorld(sender, "bunker_" + (totalBunkers + i), schematicFile);
        sender.sendMessage("Created " + bunkers + " bunkers.");
        return true;
    }

    private void createBunkerWorld(CommandSender sender, String worldName, File schematicFile) {
        // Get config message strings
        FileConfiguration coordConfig = plugin.getConfig();
        int x = coordConfig.getInt("schematicCoords.x");
        int y = coordConfig.getInt("schematicCoords.y");
        int z = coordConfig.getInt("schematicCoords.z");
        double spawnX = coordConfig.getDouble("spawnCoords.x");
        double spawnY = coordConfig.getDouble("spawnCoords.y");
        double spawnZ = coordConfig.getDouble("spawnCoords.z");
        float yaw = (float) coordConfig.getDouble("spawnCoords.yaw");
        float pitch = (float) coordConfig.getDouble("spawnCoords.pitch");
        // Check if Multiverse-Core is installed
        Plugin multiversePlugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        MultiverseCore multiverseCore = (MultiverseCore) multiversePlugin;
        MVWorldManager worldManager = multiverseCore.getMVWorldManager();
//         Set the spawn for worldManager
//         Create the void world
        if (worldManager.getMVWorld(worldName) == null) {
            worldManager.addWorld(
                    worldName, // name of world
                    World.Environment.NORMAL, // enviornment
                    null, // seed
                    WorldType.FLAT, // World Type
                    false, // Generate structures
                    "VoidGen"); // Custom generator
            plugin.getLogger().info("World " + worldName + " created successfully.");
        } else {
            plugin.getLogger().info("World " + worldName + " already exists.");
            return;
        }
        // Add schematic to empty world via multithreading
        UUID playerId = ((Player) sender).getUniqueId();
        Location pasteLocation = new Location(Bukkit.getWorld(worldName), x, y, z);
        Schematic schematic = new Schematic(schematicFile, pasteLocation);
        schematic.loadAndPasteSchematic();
        // Not running anything asynchronously currently
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Add schematic file to bunker file
//                schematic.loadAndPasteSchematic();
            } finally {
                // Remove the player from the set after the task is completed
                sender.sendMessage("Loaded bunker schematics!");
                runningTasks.remove(playerId);
            }
                });
        // Set spawn location for new world
        Location newSpawn  = new Location(Bukkit.getWorld(worldName), spawnX, spawnY, spawnZ, yaw, pitch);
        World world = Bukkit.getWorld(worldName);
        worldManager.getMVWorld(world).setSpawnLocation(newSpawn); // multiverse world spawn
        world.setSpawnLocation(newSpawn); // Bukkit world spawn, wont set server spawn unless in main world
        if (!(world.getSpawnLocation().getX() == spawnX && world.getSpawnLocation().getY() == spawnY && world.getSpawnLocation().getZ() == spawnZ))
            sender.sendMessage("Failed to set spawn location for world " + worldName + ". Check your configurtion.yml to adjust coordinates and make sure there are no obstructions, or it is not on air.");
        plugin.getLogger().info("World spawn set to " + newSpawn.toString());
        // Add npc to the bunker
        addNPC(sender, world);

        // Create bunker world in a seperate thread for optimization
//        if (sender instanceof Player) {
//            Player player = (Player) sender;
//            UUID playerId = player.getUniqueId();
//
//            // Check if the player already triggered the command
//            if (runningTasks.contains(playerId)) {
//                player.sendMessage("The bunker creation is still in progress. Please wait.");
//                return true;
//            }
//            runningTasks.add(playerId);
            // Create the bunkers
//            for (int i = 0; i < bunkers; i++) {
//                // Run the task asynchronously
//                final int finalI = i;
//                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
//                    try {
//                        // Create bunker worlds with schematic file
//                        createBunkerWorld(sender, "bunker_" + (totalBunkers + finalI), schematicFile);
//                    } finally {
//                        // Remove the player from the set after the task is completed
//                        runningTasks.remove(playerId);
//                    }
//                });
//            }
//        }
    }

}
